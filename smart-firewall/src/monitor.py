from scapy.all import sniff, IP, TCP
from collections import defaultdict
import time
from database import save_threat, init_db

# Track SYN packets per source IP to detect port scans
syn_tracker = defaultdict(list)
last_alert = {}          # tracks last alert time per source IP

SCAN_THRESHOLD = 15      # distinct ports within window to count as a scan
SCAN_WINDOW = 10         # seconds
ALERT_COOLDOWN = 30      # don't re-alert same IP within this many seconds


def detect_port_scan(pkt):
    if IP in pkt and TCP in pkt:
        if pkt[TCP].flags == "S":  # SYN flag = connection attempt
            src = pkt[IP].src
            dst_port = pkt[TCP].dport
            now = time.time()

            syn_tracker[src].append((dst_port, now))
            # keep only recent entries within window
            syn_tracker[src] = [
                (p, t) for (p, t) in syn_tracker[src] if now - t < SCAN_WINDOW
            ]

            distinct_ports = len(set(p for p, t in syn_tracker[src]))
            if distinct_ports >= SCAN_THRESHOLD:
                if src not in last_alert or now - last_alert[src] > ALERT_COOLDOWN:
                    print(f"Port scan detected from {src} ({distinct_ports} ports)")
                    save_threat(
                        node_id=f"Device-{src.split('.')[-1]}",  # e.g. "Device-102" for 192.168.1.102
                        attack_type="Port Scan",
                        severity="High",
                        source_ip=src
                    )
                    last_alert[src] = now
                syn_tracker[src] = []  # reset after alert so it starts counting fresh


def start_monitor(iface=None):
    init_db()
    print("Monitoring network traffic... (Ctrl+C to stop)")
    sniff(prn=detect_port_scan, store=False, iface=iface)


if __name__ == "__main__":
    start_monitor(iface="Wi-Fi")