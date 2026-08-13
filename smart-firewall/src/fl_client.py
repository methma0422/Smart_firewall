import flwr as fl
import numpy as np
import pandas as pd
import joblib
import glob
import os
import sys
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report

# Get client ID from command line (1, 2, or 3)
CLIENT_ID = int(sys.argv[1]) if len(sys.argv) > 1 else 1

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

def load_client_data(client_id):
    """Each client gets a different slice of the dataset"""
    all_files = sorted(glob.glob("data/*.csv"))
    
    # Split 8 files across 3 clients
    splits = {
        1: all_files[0:3],   # Client 1 gets files 1-3
        2: all_files[3:6],   # Client 2 gets files 4-6
        3: all_files[6:8],   # Client 3 gets files 7-8
    }
    
    client_files = splits[client_id]
    print(f"\n[Client {client_id}] Loading {len(client_files)} files:")
    
    dfs = []
    for f in client_files:
        print(f"  {os.path.basename(f)}")
        df = pd.read_csv(f, low_memory=False)
        dfs.append(df)
    
    data = pd.concat(dfs, ignore_index=True)
    data.columns = data.columns.str.strip()
    data.replace([np.inf, -np.inf], np.nan, inplace=True)
    data.dropna(inplace=True)
    
    # ── Feature Engineering ─────────────────────────────────────────
    eps = 1e-9
    data['fwd_bwd_pkt_ratio'] = data['Total Fwd Packets'] / (data['Total Backward Packets'] + eps)
    data['fwd_bwd_bytes_ratio'] = data['Total Length of Fwd Packets'] / (data['Total Length of Bwd Packets'] + eps)
    data['syn_ack_ratio'] = data['SYN Flag Count'] / (data['ACK Flag Count'] + eps)
    data['flow_iat_cv'] = data['Flow IAT Std'] / (data['Flow IAT Mean'] + eps)
    
    for col in DERIVED_FEATURES:
        data[col] = data[col].clip(upper=data[col].quantile(0.99))
        
    all_candidates = BASE_FEATURES + EXTRA_RAW_FEATURES + DERIVED_FEATURES
    available = [f for f in all_candidates if f in data.columns]
    
    X = data[available].values
    y = (data['Label'] != 'BENIGN').astype(int).values
    
    print(f"[Client {client_id}] Total rows: {len(data):,}")
    print(f"[Client {client_id}] Benign: {(y==0).sum():,} | Attacks: {(y==1).sum():,}")
    
    return X, y

class FirewallClient(fl.client.NumPyClient):
    
    def __init__(self, client_id):
        self.client_id = client_id
        self.X, self.y = load_client_data(client_id)
        self.scaler = StandardScaler()
        # Tuned parameters from Phase 1
        self.model = IsolationForest(
            n_estimators=200,
            max_samples=0.8,
            max_features=1.0,
            contamination=0.15,
            random_state=42,
            n_jobs=-1
        )
        # Train locally on benign data only
        X_benign = self.X[self.y == 0]
        X_scaled = self.scaler.fit_transform(X_benign)
        self.model.fit(X_scaled)
        print(f"[Client {self.client_id}] Local model trained.")

    def get_parameters(self, config):
        """Send model parameters to server"""
        # Send estimator count and contamination as parameters
        params = [
            np.array([self.model.n_estimators], dtype=np.float32),
            np.array([self.model.contamination], dtype=np.float32),
            np.array([len(self.X)], dtype=np.float32),
            np.array([float((self.y==0).sum())], dtype=np.float32),
        ]
        return params

    def set_parameters(self, parameters):
        """Receive aggregated parameters from server"""
        print(f"[Client {self.client_id}] Received global parameters from server.")

    def fit(self, parameters, config):
        """Train on local data and return updated parameters"""
        self.set_parameters(parameters)
        
        X_benign = self.X[self.y == 0]
        X_scaled = self.scaler.fit_transform(X_benign)
        self.model.fit(X_scaled)
        
        print(f"[Client {self.client_id}] Local training complete.")
        return self.get_parameters(config={}), len(X_benign), {}

    def evaluate(self, parameters, config):
        """Evaluate model and return metrics to server"""
        self.set_parameters(parameters)
        
        X_scaled = self.scaler.transform(self.X)
        preds_raw = self.model.predict(X_scaled)
        preds = (preds_raw == -1).astype(int)
        
        correct = (preds == self.y).sum()
        accuracy = correct / len(self.y)
        loss = 1.0 - accuracy
        
        attack_correct = ((preds == 1) & (self.y == 1)).sum()
        attack_total = (self.y == 1).sum()
        attack_recall = float(attack_correct / attack_total) if attack_total > 0 else 0.0
        
        print(f"[Client {self.client_id}] Accuracy: {accuracy:.2%} | Attack Recall: {attack_recall:.2%}")
        
        return loss, len(self.X), {
            "accuracy": accuracy,
            "attack_recall": attack_recall
        }

# Start the client
print(f"\nStarting Federated Client {CLIENT_ID}...")
fl.client.start_client(
    server_address="localhost:8080",
    client=FirewallClient(CLIENT_ID).to_client(),
)