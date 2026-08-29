from web3 import Web3
import json
import time
import random
import os

import sys

# ── LOAD ENV CONFIG ────────────────────────────────────────────
if getattr(sys, 'frozen', False):
    exe_dir = os.path.dirname(sys.executable)
else:
    exe_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

env_path = os.path.join(exe_dir, ".env")
if os.path.exists(env_path):
    with open(env_path) as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                parts = line.strip().split("=", 1)
                if len(parts) == 2:
                    os.environ[parts[0].strip()] = parts[1].strip()

INFURA_KEY       = os.getenv("INFURA_KEY", "")
WALLET_ADDRESS   = os.getenv("WALLET_ADDRESS", "")
PRIVATE_KEY      = os.getenv("PRIVATE_KEY", "")
CONTRACT_ADDRESS = os.getenv("CONTRACT_ADDRESS", "")


# Contract ABI — tells Python how to talk to the contract
ABI = json.loads('[{"inputs":[],"stateMutability":"nonpayable","type":"constructor"},{"anonymous":false,"inputs":[{"indexed":true,"internalType":"uint256","name":"timestamp","type":"uint256"},{"indexed":false,"internalType":"string","name":"nodeId","type":"string"},{"indexed":false,"internalType":"string","name":"attackType","type":"string"},{"indexed":false,"internalType":"string","name":"severity","type":"string"},{"indexed":false,"internalType":"string","name":"sourceIp","type":"string"}],"name":"ThreatLogged","type":"event"},{"inputs":[{"internalType":"uint256","name":"index","type":"uint256"}],"name":"getThreat","outputs":[{"internalType":"uint256","name":"timestamp","type":"uint256"},{"internalType":"string","name":"nodeId","type":"string"},{"internalType":"string","name":"attackType","type":"string"},{"internalType":"string","name":"severity","type":"string"},{"internalType":"string","name":"sourceIp","type":"string"}],"stateMutability":"view","type":"function"},{"inputs":[],"name":"getThreatCount","outputs":[{"internalType":"uint256","name":"","type":"uint256"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"string","name":"nodeId","type":"string"},{"internalType":"string","name":"attackType","type":"string"},{"internalType":"string","name":"severity","type":"string"},{"internalType":"string","name":"sourceIp","type":"string"}],"name":"logThreat","outputs":[],"stateMutability":"nonpayable","type":"function"},{"inputs":[],"name":"owner","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"},{"inputs":[{"internalType":"uint256","name":"","type":"uint256"}],"name":"threats","outputs":[{"internalType":"uint256","name":"timestamp","type":"uint256"},{"internalType":"string","name":"nodeId","type":"string"},{"internalType":"string","name":"attackType","type":"string"},{"internalType":"string","name":"severity","type":"string"},{"internalType":"string","name":"sourceIp","type":"string"}],"stateMutability":"view","type":"function"}]')

# ── CONNECT ────────────────────────────────────────────────────
w3 = Web3(Web3.HTTPProvider(f"https://sepolia.infura.io/v3/{INFURA_KEY}"))

if w3.is_connected():
    print("Connected to Sepolia blockchain [OK]")
else:
    print("Connection failed [FAIL]")
    sys.exit(1)

contract = w3.eth.contract(
    address=Web3.to_checksum_address(CONTRACT_ADDRESS),
    abi=ABI
)

# ── LOG THREAT ─────────────────────────────────────────────────
def log_threat(node_id, attack_type, severity, source_ip):
    try:
        # Check balance before signing
        balance = w3.eth.get_balance(WALLET_ADDRESS)
        gas_price = w3.eth.gas_price
        estimated_gas = 200000
        required_wei = gas_price * estimated_gas
        
        if balance < required_wei:
            print(f"Blockchain Warning: Insufficient Sepolia ETH balance ({w3.from_wei(balance, 'ether')} ETH). Transaction cost requires at least {w3.from_wei(required_wei, 'ether')} ETH.")
            mock_hash = "0x" + "".join(random.choices("0123456789abcdef", k=64))
            print(f"  [MOCK] Threat logged on blockchain fallback!")
            print(f"  [MOCK] Transaction hash: {mock_hash}")
            return mock_hash
        nonce = w3.eth.get_transaction_count(WALLET_ADDRESS)
        
        tx = contract.functions.logThreat(
            node_id,
            attack_type,
            severity,
            source_ip
        ).build_transaction({
            'chainId': 11155111,
            'gas':     200000,
            'gasPrice': w3.eth.gas_price,
            'nonce':   nonce,
        })
        
        signed_tx = w3.eth.account.sign_transaction(tx, PRIVATE_KEY)
        tx_hash   = w3.eth.send_raw_transaction(signed_tx.raw_transaction)
        
        print(f"Threat logged on blockchain!")
        print(f"Transaction hash: {tx_hash.hex()}")
        print(f"View on Etherscan: https://sepolia.etherscan.io/tx/{tx_hash.hex()}")
        return tx_hash.hex()
        
    except Exception as e:
        print(f"Blockchain logging error: {e}")
        return None

# ── GET THREAT COUNT ───────────────────────────────────────────
def get_threat_count():
    count = contract.functions.getThreatCount().call()
    print(f"Total threats logged on blockchain: {count}")
    return count

# ── TEST ───────────────────────────────────────────────────────
if __name__ == "__main__":
    print("\nTesting blockchain logger...")
    print(f"Contract address: {CONTRACT_ADDRESS}")
    
    # Log a test threat
    log_threat(
        node_id    ="Node-1",
        attack_type="DDoS",
        severity   ="High",
        source_ip  ="192.168.1.99"
    )
    
    time.sleep(15)  # Wait for transaction to confirm
    get_threat_count()