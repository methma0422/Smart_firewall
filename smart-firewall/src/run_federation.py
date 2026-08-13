import subprocess
import time
import sys

print("=" * 50)
print("  Federated Learning — Smart Home Firewall")
print("=" * 50)
print("\nStarting FL Server and 3 Clients...\n")

# Start server
server = subprocess.Popen(
    [sys.executable, "src/fl_server.py"],
    creationflags=subprocess.CREATE_NEW_CONSOLE
)
time.sleep(3)  # Wait for server to start

# Start 3 clients in separate windows
clients = []
for i in range(1, 4):
    client = subprocess.Popen(
        [sys.executable, "src/fl_client.py", str(i)],
        creationflags=subprocess.CREATE_NEW_CONSOLE
    )
    clients.append(client)
    time.sleep(1)

print("Server and 3 clients started in separate windows.")
print("Watch each window for progress...\n")

# Wait for all to finish
server.wait()
print("\nFederated Learning complete!")