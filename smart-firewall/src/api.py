from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from scapy.all import ARP, Ether, srp
from database import get_all_threats, get_threat_stats, init_db, register_user, login_user, DB_PATH
from pydantic import BaseModel
import uvicorn
import jwt
import time
import os

app = FastAPI(title="Smart Firewall API")

# JWT Configuration
JWT_SECRET = os.getenv("JWT_SECRET", "smart_firewall_jwt_secret_key_987654321")
JWT_ALGORITHM = "HS256"

def create_access_token(username: str) -> str:
    payload = {
        "sub": username,
        "exp": int(time.time()) + 86400,  # Token valid for 24 hours
        "iat": int(time.time())
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class AuthRequest(BaseModel):
    username: str
    password: str

@app.on_event("startup")
def startup():
    init_db()

@app.post("/register")
def register(req: AuthRequest):
    success, msg = register_user(req.username, req.password)
    token = None
    if success:
        token = create_access_token(req.username)
    return {"success": success, "message": msg, "token": token}

@app.post("/login")
def login(req: AuthRequest):
    success, msg = login_user(req.username, req.password)
    token = None
    if success:
        token = create_access_token(req.username)
    return {"success": success, "message": msg, "token": token}


@app.get("/")
def root():
    return {"status": "Smart Firewall API running"}

@app.get("/threats")
def threats():
    return get_all_threats()

@app.get("/stats")
def stats():
    return get_threat_stats()

import sqlite3

def get_latest_node_severity(node_id: str) -> str:
    try:
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()
        cursor.execute(
            "SELECT severity FROM threats WHERE node_id = ? ORDER BY id DESC LIMIT 1",
            (node_id,)
        )
        row = cursor.fetchone()
        conn.close()
        if row:
            return row[0]  # "High", "Medium", or "Low"
    except Exception as e:
        print(f"Database status lookup failed for {node_id}: {e}")
    return "Safe"

import socket
import ipaddress

def get_local_subnet():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Using a public DNS server to find the local IP that routes to internet
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        
        # Assume a standard /24 subnet for home networks
        network = ipaddress.ip_network(f"{local_ip}/255.255.255.0", strict=False)
        subnet_str = str(network)
        print(f"Detected local subnet: {subnet_str} (based on local IP {local_ip})")
        return subnet_str
    except Exception as e:
        print(f"Subnet detection failed: {e}. Falling back to default '192.168.1.0/24'")
        return "192.168.1.0/24"

def scan_network():
    ip_range = get_local_subnet()
    arp = ARP(pdst=ip_range)
    ether = Ether(dst="ff:ff:ff:ff:ff:ff")
    result = srp(ether / arp, timeout=5, verbose=0)[0]
    return [
        {"id": f"Node-{i+1}", "ip": r.psrc, "mac": r.hwsrc, "status": "Active"}
        for i, (sent, r) in enumerate(result)
    ]

@app.get("/nodes")
def nodes():
    try:
        scanned_nodes = scan_network()
        # Enrich node status with latest database severity level
        for node in scanned_nodes:
            node["status"] = get_latest_node_severity(node["id"])
        return scanned_nodes
    except Exception as e:
        print(f"Network scan failed: {e}")
        return []

if __name__ == "__main__":
    import sys
    if getattr(sys, 'frozen', False):
        exe_dir = os.path.dirname(sys.executable)
    else:
        exe_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        
    ssl_key = os.path.join(exe_dir, "ssl_key.pem")
    ssl_cert = os.path.join(exe_dir, "ssl_cert.pem")
    
    # Run securely over HTTPS using generated self-signed certificates
    if os.path.exists(ssl_key) and os.path.exists(ssl_cert):
        print("Launching secure API server over HTTPS...")
        uvicorn.run(app, host="0.0.0.0", port=8000, ssl_keyfile=ssl_key, ssl_certfile=ssl_cert, reload=False)
    else:
        print("SSL keys missing. Falling back to HTTP...")
        uvicorn.run(app, host="0.0.0.0", port=8000, reload=False)