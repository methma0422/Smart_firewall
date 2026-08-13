import flwr as fl

# Strategy: FedAvg averages model weights from all clients
strategy = fl.server.strategy.FedAvg(
    fraction_fit=1.0,           # use all available clients
    min_fit_clients=3,          # wait for exactly 3 clients
    min_available_clients=3,    # don't start until 3 clients connect
)

print("Starting Federated Learning Server...")
print("Waiting for 3 clients to connect...\n")

fl.server.start_server(
    server_address="localhost:8080",
    config=fl.server.ServerConfig(num_rounds=3),
    strategy=strategy,
)