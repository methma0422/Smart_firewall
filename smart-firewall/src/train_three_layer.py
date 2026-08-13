import pandas as pd
import numpy as np
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split
import matplotlib.pyplot as plt
import seaborn as sns
import joblib
import os
import glob

# ── 1. LOAD DATA ───────────────────────────────────────────────
def load_dataset(data_folder="data/"):
    all_files = glob.glob(os.path.join(data_folder, "*.csv"))
    print(f"Found {len(all_files)} CSV files")
    if not all_files:
        raise FileNotFoundError(f"No CSV files found in '{data_folder}'.")
    dfs = [pd.read_csv(f, low_memory=False) for f in all_files]
    data = pd.concat(dfs, ignore_index=True)
    print(f"Total rows loaded: {len(data):,}")
    return data


def clean_data(df):
    df.columns = df.columns.str.strip()
    df.replace([np.inf, -np.inf], np.nan, inplace=True)
    df.dropna(inplace=True)
    print(f"Rows after cleaning: {len(df):,}")
    return df


# ── 2. FEATURE ENGINEERING (same as previous pass) ──────────────
BASE_FEATURES = [
    'Flow Duration', 'Total Fwd Packets', 'Total Backward Packets',
    'Total Length of Fwd Packets', 'Total Length of Bwd Packets',
    'Fwd Packet Length Max', 'Fwd Packet Length Min', 'Fwd Packet Length Mean',
    'Bwd Packet Length Max', 'Bwd Packet Length Min', 'Bwd Packet Length Mean',
    'Flow Bytes/s', 'Flow Packets/s', 'Flow IAT Mean', 'Flow IAT Std',
    'Fwd IAT Total', 'Bwd IAT Total', 'SYN Flag Count', 'RST Flag Count',
    'PSH Flag Count', 'ACK Flag Count', 'URG Flag Count',
    'Avg Fwd Segment Size', 'Avg Bwd Segment Size'
]
EXTRA_RAW_FEATURES = [
    'Fwd Packets/s', 'Bwd Packets/s', 'Min Packet Length', 'Max Packet Length',
    'Packet Length Mean', 'Packet Length Std', 'Down/Up Ratio',
    'Average Packet Size', 'Fwd Header Length', 'Bwd Header Length',
]
DERIVED_FEATURES = ['fwd_bwd_pkt_ratio', 'fwd_bwd_bytes_ratio', 'syn_ack_ratio', 'flow_iat_cv']

def engineer_features(df):
    eps = 1e-9
    df['fwd_bwd_pkt_ratio'] = df['Total Fwd Packets'] / (df['Total Backward Packets'] + eps)
    df['fwd_bwd_bytes_ratio'] = df['Total Length of Fwd Packets'] / (df['Total Length of Bwd Packets'] + eps)
    df['syn_ack_ratio'] = df['SYN Flag Count'] / (df['ACK Flag Count'] + eps)
    df['flow_iat_cv'] = df['Flow IAT Std'] / (df['Flow IAT Mean'] + eps)
    for col in DERIVED_FEATURES:
        df[col] = df[col].clip(upper=df[col].quantile(0.99))
    return df

def prepare_features(df):
    df = engineer_features(df)
    all_candidates = BASE_FEATURES + EXTRA_RAW_FEATURES + DERIVED_FEATURES
    available = [f for f in all_candidates if f in df.columns]
    print(f"Using {len(available)} features")
    X = df[available].copy()
    y = (df['Label'] != 'BENIGN').astype(int)
    y_multiclass = df['Label'].copy()
    return X, y, y_multiclass, available


# ── 3. LAYER 1: WIDENED ISOLATION FOREST ────────────────────────
def train_isolation_forest_wide_recall(X_train_benign, X_val, y_val, target_recall=0.95):
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train_benign)
    X_val_scaled = scaler.transform(X_val)

    # Use last run's best-performing region as the base model
    model = IsolationForest(
        n_estimators=200, max_samples=0.8, max_features=1.0,
        contamination='auto', random_state=42, n_jobs=-1
    )
    model.fit(X_train_scaled)

    # decision_function: lower score = more anomalous. Sweep thresholds
    # to find the cutoff that hits our target recall on validation data.
    scores = model.decision_function(X_val_scaled)
    thresholds = np.percentile(scores, np.arange(1, 100, 1))

    best_threshold, best_recall_gap = None, np.inf
    for t in thresholds:
        preds = (scores < t).astype(int)
        tp = ((preds == 1) & (y_val == 1)).sum()
        fn = ((preds == 0) & (y_val == 1)).sum()
        recall = tp / (tp + fn + 1e-9)
        gap = abs(recall - target_recall)
        if recall >= target_recall and gap < best_recall_gap:
            best_recall_gap, best_threshold = gap, t

    if best_threshold is None:
        best_threshold = thresholds[-1]  # fallback: most permissive

    val_preds = (scores < best_threshold).astype(int)
    tp = ((val_preds == 1) & (y_val == 1)).sum()
    fp = ((val_preds == 1) & (y_val == 0)).sum()
    fn = ((val_preds == 0) & (y_val == 1)).sum()
    recall = tp / (tp + fn + 1e-9)
    precision = tp / (tp + fp + 1e-9)
    print(f"Layer 1 (Isolation Forest) @ recall-targeted threshold:")
    print(f"  threshold={best_threshold:.4f}  recall={recall:.3f}  precision={precision:.3f}")

    return model, scaler, best_threshold


def apply_isolation_forest(model, scaler, threshold, X):
    X_scaled = scaler.transform(X)
    scores = model.decision_function(X_scaled)
    return (scores < threshold).astype(int)


# ── 4. LAYER 2: RULE-BASED SIGNATURES ───────────────────────────
def compute_rule_thresholds(df_train_benign):
    """Derive rule thresholds from benign traffic percentiles, not guesses."""
    thresholds = {
        'syn_flood_pkt_count': df_train_benign['Total Fwd Packets'].quantile(0.999),
        'high_rate_pps': df_train_benign['Flow Packets/s'].quantile(0.999),
    }
    print(f"Rule thresholds derived from benign data: {thresholds}")
    return thresholds

def apply_rules(df, thresholds):
    """
    Returns a binary flag array. Each rule targets a distinct, well-known
    attack shape - independent of whatever Isolation Forest is doing.
    """
    # Rule 1: SYN flood - many SYNs, no corresponding ACKs, high fwd packet count
    syn_flood = (
        (df['SYN Flag Count'] > 0) &
        (df['ACK Flag Count'] == 0) &
        (df['Total Fwd Packets'] > thresholds['syn_flood_pkt_count'])
    )

    # Rule 2: Port scan probe - very short flow, minimal packets, no response at all
    port_scan = (
        (df['Flow Duration'] < 1000) &          # microseconds - near-instant probe
        (df['Total Fwd Packets'] <= 3) &
        (df['Total Backward Packets'] == 0)
    )

    # Rule 3: High-rate flood - packets/sec far beyond anything seen in benign traffic
    high_rate_flood = (df['Flow Packets/s'] > thresholds['high_rate_pps'])

    combined = (syn_flood | port_scan | high_rate_flood).astype(int)
    return combined, {'syn_flood': syn_flood.sum(), 'port_scan': port_scan.sum(), 'high_rate_flood': high_rate_flood.sum()}


# ── 5. LAYER 3: CLASSIFIER CONFIRMATION ─────────────────────────
def train_confirmation_classifier(X_train, y_train):
    """
    Supervised classifier trained on ALL labeled training data (not just
    flagged rows) so it learns general attack signatures well. At
    inference time we only consult it for rows already flagged by
    Layer 1 or 2 - its job is to add confidence, not to gate.
    """
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X_train)
    model = RandomForestClassifier(
        n_estimators=200, class_weight="balanced", random_state=42, n_jobs=-1
    )
    model.fit(X_scaled, y_train)
    return model, scaler


def tier_predictions(flagged, confirm_proba, confidence_threshold=0.6):
    """
    flagged: binary array (Layer 1 OR Layer 2 result)
    confirm_proba: classifier's predicted probability of "attack" class
    Returns tier labels: 'Normal', 'Suspicious', 'Confirmed Attack'
    """
    tiers = np.array(['Normal'] * len(flagged), dtype=object)
    tiers[(flagged == 1)] = 'Suspicious'
    tiers[(flagged == 1) & (confirm_proba >= confidence_threshold)] = 'Confirmed Attack'
    return tiers


# ── 6. EVALUATION ────────────────────────────────────────────────
def evaluate_combined(if_flag, rule_flag, y_test):
    combined = ((if_flag == 1) | (rule_flag == 1)).astype(int)

    print("\n── Layer 1 + Layer 2 Combined (OR) Results ──────────────────")
    print(classification_report(y_test, combined, target_names=["Benign", "Attack"]))

    cm = confusion_matrix(y_test, combined)
    plt.figure(figsize=(6, 4))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                xticklabels=["Benign", "Attack"], yticklabels=["Benign", "Attack"])
    plt.title("Confusion Matrix — Isolation Forest OR Rules")
    plt.ylabel("Actual"); plt.xlabel("Predicted")
    plt.tight_layout()
    os.makedirs("model", exist_ok=True)
    plt.savefig("model/confusion_matrix_layer1_2.png")
    # plt.show()

    return combined


def evaluate_tiers(tiers, y_test):
    print("\n── Tier Breakdown (Layer 3 added) ──────────────────")
    df_summary = pd.DataFrame({'tier': tiers, 'actual': y_test})
    print(df_summary.groupby('tier')['actual'].value_counts().unstack(fill_value=0))

    # How many actual attacks ended up in each tier?
    attack_rows = df_summary[df_summary['actual'] == 1]
    print(f"\nOf {len(attack_rows)} actual attacks:")
    print(attack_rows['tier'].value_counts())
    missed = (attack_rows['tier'] == 'Normal').sum()
    print(f"\nAttacks that reached 'Normal' (fully missed by all layers): {missed} "
          f"({missed/len(attack_rows)*100:.1f}%)")


# ── MAIN ───────────────────────────────────────────────────────
if __name__ == "__main__":
    df = load_dataset("data/")
    df = clean_data(df)
    X, y, y_multiclass, features = prepare_features(df)

    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.4, random_state=42, stratify=y
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.5, random_state=42, stratify=y_temp
    )
    # Keep the raw (unscaled) dataframe rows aligned with the same splits,
    # for the rule layer which needs raw feature values, not scaled ones.
    df_train_raw = df.loc[X_train.index]
    df_val_raw = df.loc[X_val.index]
    df_test_raw = df.loc[X_test.index]

    X_train_benign = X_train[y_train == 0]

    # ---- Layer 1 ----
    if_model, if_scaler, if_threshold = train_isolation_forest_wide_recall(
        X_train_benign, X_val, y_val, target_recall=0.95
    )

    # ---- Layer 2 ----
    rule_thresholds = compute_rule_thresholds(df_train_raw[df_train_raw['Label'] == 'BENIGN'])
    rule_flag_test, rule_hits = apply_rules(df_test_raw, rule_thresholds)
    print(f"Layer 2 rule hits on test set: {rule_hits}")

    # ---- Combine Layer 1 + Layer 2 ----
    if_flag_test = apply_isolation_forest(if_model, if_scaler, if_threshold, X_test)
    combined_flag = evaluate_combined(if_flag_test, rule_flag_test, y_test)

    # ---- Layer 3 ----
    rf_model, rf_scaler = train_confirmation_classifier(X_train, y_train)
    X_test_scaled = rf_scaler.transform(X_test)
    confirm_proba = rf_model.predict_proba(X_test_scaled)[:, 1]  # P(attack)

    tiers = tier_predictions(combined_flag, confirm_proba, confidence_threshold=0.6)
    evaluate_tiers(tiers, y_test.values)

    # ---- Save everything ----
    os.makedirs("model", exist_ok=True)
    joblib.dump(if_model, "model/layer1_isolation_forest.pkl")
    joblib.dump(if_scaler, "model/layer1_scaler.pkl")
    joblib.dump(if_threshold, "model/layer1_threshold.pkl")
    joblib.dump(rule_thresholds, "model/layer2_rule_thresholds.pkl")
    joblib.dump(rf_model, "model/layer3_classifier.pkl")
    joblib.dump(rf_scaler, "model/layer3_scaler.pkl")
    joblib.dump(features, "model/features.pkl")
    print("\nAll layers saved to model/")
