package lk.nibm.kandy.hdse252ft.smartfirewall.data.model

data class ThreatEvent(
    val id: Int,
    val nodeId: String,
    val attackType: String,
    val severity: String,
    val sourceIp: String,
    val timestamp: String
)

fun getSampleThreats(): List<ThreatEvent> = listOf(
    ThreatEvent(1, "Node-1", "DDoS",       "High",   "192.168.1.99",  "2025-01-18 07:48:12"),
    ThreatEvent(2, "Node-2", "Port Scan",  "Medium", "192.168.1.105", "2025-01-18 07:52:33"),
    ThreatEvent(3, "Node-3", "Brute Force","High",   "192.168.1.200", "2025-01-18 07:55:01"),
    ThreatEvent(4, "Node-1", "Bot",        "Low",    "192.168.1.88",  "2025-01-18 07:58:44"),
    ThreatEvent(5, "Node-2", "DoS Hulk",   "High",   "192.168.1.77",  "2025-01-18 08:01:15"),
)
