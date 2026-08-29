import os
import joblib
import numpy as np
import pandas as pd
import time
import random
from datetime import datetime
from database import init_db, save_threat, mark_threat_on_chain
from blockchain_logger import log_threat

import sys
from sklearn.ensemble import IsolationForest, RandomForestClassifier
from sklearn.preprocessing import StandardScaler

# Resolve absolute paths to model directory relative to the script location or PyInstaller bundle
if getattr(sys, 'frozen', False):
    BUNDLE_DIR = sys._MEIPASS
else:
    BUNDLE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODEL_DIR = os.path.join(BUNDLE_DIR, "model")

# ── 1. DEFINITION OF FEATURES ──────────────────────────────────
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

# ── 2. LOAD TRAINED 3-LAYER MODELS ─────────────────────────────
print("Loading trained 3-layer model...")
try:
    if_model = joblib.load(os.path.join(MODEL_DIR, "layer1_isolation_forest.pkl"))
    if_scaler = joblib.load(os.path.join(MODEL_DIR, "layer1_scaler.pkl"))
    if_threshold = joblib.load(os.path.join(MODEL_DIR, "layer1_threshold.pkl"))
    rule_thresholds = joblib.load(os.path.join(MODEL_DIR, "layer2_rule_thresholds.pkl"))
    rf_model = joblib.load(os.path.join(MODEL_DIR, "layer3_classifier.pkl"))
    rf_scaler = joblib.load(os.path.join(MODEL_DIR, "layer3_scaler.pkl"))
    features = joblib.load(os.path.join(MODEL_DIR, "features.pkl"))
    print("All layers loaded successfully.")
except Exception as e:
    print(f"Error loading models: {e}")
    print("Please verify that all the PKL files are in the model/ folder.")
    sys.exit(1)

init_db()

# ── 3. SIMULATE NETWORK TRAFFIC ────────────────────────────────
def generate_traffic():
    """Simulates network flow raw features (base + extra raw)"""
    is_attack = random.random() < 0.3  # 30% chance of attack
    raw_features = BASE_FEATURES + EXTRA_RAW_FEATURES
    
    flow = {}
    if is_attack:
        attack_type = random.choice(["DDoS", "DoS Hulk", "Brute Force", "Port Scan"])
        
        # Initialize default values
        for f in raw_features:
            flow[f] = random.uniform(0, 100)
            
        if attack_type == "DDoS":
            # High SYN flag count, low ACK flag count, high forward packet count
            flow['SYN Flag Count'] = random.uniform(1000, 5000)
            flow['ACK Flag Count'] = 0.0
            flow['Total Fwd Packets'] = random.uniform(500, 2000)
        elif attack_type == "DoS Hulk":
            # High packets per second, high bytes per second
            flow['Flow Packets/s'] = random.uniform(150000, 500000)
            flow['Flow Bytes/s'] = random.uniform(800000, 2000000)
        elif attack_type == "Brute Force":
            # High flow bytes/s, high average packet size
            flow['Flow Bytes/s'] = random.uniform(600000, 1500000)
            flow['Average Packet Size'] = random.uniform(800, 1500)
        elif attack_type == "Port Scan":
            # Short flow duration, low packets, no back packets
            flow['Flow Duration'] = random.uniform(10, 500)
            flow['Total Fwd Packets'] = random.uniform(1, 3)
            flow['Total Backward Packets'] = 0.0
            
        source_ip = random.choice(["192.168.1.99", "192.168.1.105", "10.0.0.55", "172.16.0.200"])
    else:
        # Normal traffic — typical values
        for f in raw_features:
            flow[f] = random.uniform(10, 500)
        # Normal traffic shouldn't trigger syn flood or port scan heuristics:
        flow['SYN Flag Count'] = random.choice([0.0, 1.0])
        flow['ACK Flag Count'] = random.uniform(5, 50)
        flow['Total Fwd Packets'] = random.uniform(5, 30)
        flow['Total Backward Packets'] = random.uniform(5, 30)
        flow['Flow Duration'] = random.uniform(5000, 50000)
        flow['Flow Packets/s'] = random.uniform(10, 1000)
        flow['Flow Bytes/s'] = random.uniform(100, 5000)
        
        source_ip = f"192.168.1.{random.randint(2, 50)}"
        
    return flow, is_attack, source_ip

def engineer_flow_features(flow_dict):
    """Add derived ratio features to the simulated flow dictionary."""
    eps = 1e-9
    flow = flow_dict.copy()
    flow['fwd_bwd_pkt_ratio'] = flow['Total Fwd Packets'] / (flow['Total Backward Packets'] + eps)
    flow['fwd_bwd_bytes_ratio'] = flow['Total Length of Fwd Packets'] / (flow['Total Length of Bwd Packets'] + eps)
    flow['syn_ack_ratio'] = flow['SYN Flag Count'] / (flow['ACK Flag Count'] + eps)
    flow['flow_iat_cv'] = flow['Flow IAT Std'] / (flow['Flow IAT Mean'] + eps)
    return flow

def apply_rules(flow):
    """Checks if any rule-based signature triggers."""
    syn_flood = (
        (flow['SYN Flag Count'] > 0) &
        (flow['ACK Flag Count'] == 0) &
        (flow['Total Fwd Packets'] > rule_thresholds['syn_flood_pkt_count'])
    )
    port_scan = (
        (flow['Flow Duration'] < 1000) &
        (flow['Total Fwd Packets'] <= 3) &
        (flow['Total Backward Packets'] == 0)
    )
    high_rate_flood = (flow['Flow Packets/s'] > rule_thresholds['high_rate_pps'])
    
    return bool(syn_flood or port_scan or high_rate_flood)

def get_attack_type(flow, rule_triggered):
    """Determines the specific attack label based on rules and features."""
    if rule_triggered:
        syn = (flow['SYN Flag Count'] > 0) and (flow['ACK Flag Count'] == 0) and (flow['Total Fwd Packets'] > rule_thresholds['syn_flood_pkt_count'])
        high_rate = (flow['Flow Packets/s'] > rule_thresholds['high_rate_pps'])
        port_scan = (flow['Flow Duration'] < 1000) and (flow['Total Fwd Packets'] <= 3) and (flow['Total Backward Packets'] == 0)
        
        if syn:
            return "DDoS"
        elif high_rate:
            return "DoS Hulk"
        elif port_scan:
            return "Port Scan"
            
    # Fallback/ML alert types based on flow signature
    if flow.get('Flow Bytes/s', 0) > 500000:
        return "Brute Force"
    return "Anomaly (Unclassified)"

# ── 4. MAIN DETECTION LOOP ─────────────────────────────────────
print("\n3-Layer Firewall monitoring started...")
print("Press Ctrl+C to stop\n")

node_ids = ["Node-1", "Node-2", "Node-3"]
threat_count = 0

try:
    while True:
        raw_flow, is_attack_sim, source_ip = generate_traffic()
        node_id = random.choice(node_ids)

        # 1. Feature Engineering
        flow = engineer_flow_features(raw_flow)
        X = pd.DataFrame([flow])[features]

        # 2. Layer 1 Anomaly Detection (Isolation Forest)
        X_scaled_if = if_scaler.transform(X)
        score_if = if_model.decision_function(X_scaled_if)[0]
        if_flag = score_if < if_threshold

        # 3. Layer 2 Signature Rules
        rule_flag = apply_rules(flow)

        # Combine Layer 1 and Layer 2 (OR relationship)
        flagged = if_flag or rule_flag

        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        if flagged:
            # 4. Layer 3 Confirmation & Triage (Random Forest)
            X_scaled_rf = rf_scaler.transform(X)
            confirm_proba = rf_model.predict_proba(X_scaled_rf)[0, 1]

            if confirm_proba >= 0.6:
                tier = "Confirmed Attack"
                severity = "High"
            else:
                tier = "Suspicious"
                severity = "Medium"

            attack_type = get_attack_type(flow, rule_flag)
            threat_count += 1

            print(f"[{timestamp}] [!] THREAT DETECTED! (Tier: {tier})")
            print(f"  Node:       {node_id}")
            print(f"  Attack:     {attack_type}")
            print(f"  Severity:   {severity}")
            print(f"  Source IP:  {source_ip}")
            print(f"  IF Score:   {score_if:.4f} (Threshold: {if_threshold:.4f})")
            print(f"  RF Prob:    {confirm_proba:.2%}")

            # Save to SQLite and get the row ID
            threat_id = save_threat(node_id, f"{attack_type} ({tier})", severity, source_ip)
            print(f"  SQLite:     Saved [OK]")

            # Log to blockchain (every 5th threat to save gas)
            if threat_count % 5 == 0:
                tx = log_threat(node_id, attack_type, severity, source_ip)
                if tx:
                    print(f"  Blockchain: Logged [OK]")
                    mark_threat_on_chain(threat_id)
            print()
        else:
            print(f"[{timestamp}] [OK] Normal traffic — {node_id} — {source_ip}")

        time.sleep(2)  # Check every 2 seconds

except KeyboardInterrupt:
    print(f"\nFirewall stopped. Total threats detected: {threat_count}")