import sqlite3
import os

import sys

if getattr(sys, 'frozen', False):
    EXE_DIR = os.path.dirname(sys.executable)
else:
    EXE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BASE_DIR = EXE_DIR
DB_PATH = os.path.join(EXE_DIR, "model", "threats.db")

import hashlib
import bcrypt

def init_db():
    os.makedirs(os.path.join(BASE_DIR, "model"), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS threats (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp   TEXT NOT NULL,
            node_id     TEXT NOT NULL,
            attack_type TEXT NOT NULL,
            severity    TEXT NOT NULL,
            source_ip   TEXT NOT NULL,
            on_chain    INTEGER DEFAULT 0
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS users (
            username TEXT PRIMARY KEY,
            password TEXT NOT NULL
        )
    """)
    conn.commit()
    conn.close()
    print("Database initialized.")

def hash_password(password: str) -> str:
    # Use bcrypt with a generated salt for industrial-grade secure hashing
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def register_user(username, password):
    if not username or not password:
        return False, "Username and password cannot be empty."
    hashed = hash_password(password)
    try:
        conn = sqlite3.connect(DB_PATH)
        conn.execute("""
            INSERT INTO users (username, password) VALUES (?, ?)
        """, (username, hashed))
        conn.commit()
        conn.close()
        return True, "User registered successfully."
    except sqlite3.IntegrityError:
        return False, "Username already exists."
    except Exception as e:
        return False, f"Database error: {e}"

def login_user(username, password):
    if not username or not password:
        return False, "Username and password cannot be empty."
    try:
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.execute("""
            SELECT password FROM users WHERE username = ?
        """, (username,))
        row = cursor.fetchone()
        conn.close()
        if row:
            hashed = row[0]
            try:
                # Primary check using bcrypt
                if bcrypt.checkpw(password.encode('utf-8'), hashed.encode('utf-8')):
                    return True, "Login successful."
            except Exception:
                # Backward compatibility fallback for legacy SHA-256 test hashes
                sha_salt = "smart_firewall_salt_123"
                old_hash = hashlib.sha256((password + sha_salt).encode('utf-8')).hexdigest()
                if old_hash == hashed:
                    return True, "Login successful."
        return False, "Invalid username or password."
    except Exception as e:
        return False, f"Database error: {e}"


def save_threat(node_id, attack_type, severity, source_ip):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.execute("""
        INSERT INTO threats (timestamp, node_id, attack_type, severity, source_ip)
        VALUES (datetime('now'), ?, ?, ?, ?)
    """, (node_id, attack_type, severity, source_ip))
    inserted_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return inserted_id

def mark_threat_on_chain(threat_id):
    conn = sqlite3.connect(DB_PATH)
    conn.execute("UPDATE threats SET on_chain = 1 WHERE id = ?", (threat_id,))
    conn.commit()
    conn.close()

def get_all_threats():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.execute("""
        SELECT id, timestamp, node_id, attack_type, severity, source_ip, on_chain
        FROM threats ORDER BY id DESC LIMIT 50
    """)
    rows = cursor.fetchall()
    conn.close()
    return [
        {
            "id":          r[0],
            "timestamp":   r[1],
            "node_id":     r[2],
            "attack_type": r[3],
            "severity":    r[4],
            "source_ip":   r[5],
            "on_chain":    bool(r[6])
        }
        for r in rows
    ]

def get_threat_stats():
    conn = sqlite3.connect(DB_PATH)
    
    # 1. Fetch All-time stats
    cursor = conn.execute("""
        SELECT severity, COUNT(*) FROM threats GROUP BY severity
    """)
    all_rows = cursor.fetchall()
    all_time = {"High": 0, "Medium": 0, "Low": 0, "total": 0}
    for severity, count in all_rows:
        if severity in all_time:
            all_time[severity] = count
        all_time["total"] += count
        
    # 2. Fetch Weekly stats (last 7 days)
    cursor = conn.execute("""
        SELECT severity, COUNT(*) FROM threats 
        WHERE timestamp >= datetime('now', '-7 days') 
        GROUP BY severity
    """)
    weekly_rows = cursor.fetchall()
    weekly = {"High": 0, "Medium": 0, "Low": 0, "total": 0}
    for severity, count in weekly_rows:
        if severity in weekly:
            weekly[severity] = count
        weekly["total"] += count
        
    conn.close()
    return {
        "all_time": all_time,
        "weekly": weekly
    }