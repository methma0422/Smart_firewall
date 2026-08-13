package lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens

import lk.nibm.kandy.hdse252ft.smartfirewall.ui.components.*

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ApiClient
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.NodeResponse
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.StatsResponse

@Composable
fun DashboardScreen(
    onViewThreats: () -> Unit,
    onViewAR: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("smart_firewall_prefs", Context.MODE_PRIVATE)
    }
    var gatewayIp by remember {
        mutableStateOf(sharedPrefs.getString("gateway_ip", "192.168.1.4") ?: "192.168.1.4")
    }
    var stats     by remember { mutableStateOf<StatsResponse?>(null) }
    var nodes     by remember { mutableStateOf<List<NodeResponse>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var isLoading  by remember { mutableStateOf(true) }
    var apiError   by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var showEditIpDialog by remember { mutableStateOf(false) }
    var tempIp by remember { mutableStateOf(gatewayIp) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Fetch live data every 5 seconds, reacting to gatewayIp changes
    LaunchedEffect(gatewayIp) {
        while (true) {
            try {
                stats    = ApiClient.api.getStats()
                nodes    = ApiClient.api.getNodes()
                isLoading = false
                apiError  = false
            } catch (e: Exception) {
                isLoading = false
                apiError  = true
            }
            delay(5000)
        }
    }

    var isWeeklyView by remember { mutableStateOf(true) }
    val summary = if (isWeeklyView) stats?.weekly else stats?.all_time
    val highCount   = summary?.High   ?: 0
    val mediumCount = summary?.Medium ?: 0
    val lowCount    = summary?.Low    ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1628))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Smart Home Firewall",
                    color = Color(0xFF00C2FF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Decentralized Network Monitor",
                    color = Color(0xFF8899AA),
                    fontSize = 14.sp
                )
            }
            TextButton(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFE63946)
                )
            ) {
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF132040)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusColor = when {
                            apiError -> Color(0xFFE63946)
                            else -> Color(0xFF2DC653)
                        }
                        val statusPulseTransition = rememberInfiniteTransition()
                        val statusPulseAlpha by statusPulseTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    statusColor.copy(alpha = statusPulseAlpha),
                                    RoundedCornerShape(5.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                apiError   -> "API Disconnected"
                                isScanning -> "Scanning Network..."
                                else       -> "System Active"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Edit IP link button
                    TextButton(
                        onClick = {
                            tempIp = gatewayIp
                            showEditIpDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Configure Gateway", color = Color(0xFF00C2FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gateway Address: http://$gatewayIp:8000/",
                    color = Color(0xFF00C2FF).copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Federated Learning: Active  •  Blockchain: Connected",
                    color = Color(0xFF8899AA),
                    fontSize = 12.sp
                )
                Text(
                    text = "Nodes online: ${nodes.size}  •  FL Rounds: 3",
                    color = Color(0xFF8899AA),
                    fontSize = 12.sp
                )
                if (apiError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠ Cannot reach firewall API — showing cached data",
                        color = Color(0xFFF4A261),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Gateway IP Dialog
        if (showEditIpDialog) {
            AlertDialog(
                onDismissRequest = { showEditIpDialog = false },
                title = { Text("Configure Firewall Endpoint", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column {
                        Text("Enter the new firewall gateway IP address or endpoint URL:", color = Color(0xFF8899AA), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempIp,
                            onValueChange = { tempIp = it },
                            label = { Text("Firewall Gateway IP", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0A1628),
                                unfocusedContainerColor = Color(0xFF0A1628),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00C2FF),
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cleanIp = tempIp.trim()
                            if (cleanIp.isNotEmpty()) {
                                gatewayIp = cleanIp
                                sharedPrefs.edit().putString("gateway_ip", cleanIp).apply()
                                ApiClient.updateBaseUrl(cleanIp)
                                showEditIpDialog = false
                                isLoading = true
                            }
                        }
                    ) {
                        Text("Save Access config", color = Color(0xFF00C2FF), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditIpDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF132040),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Confirm Logout", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to terminate access to the firewall gateway?", color = Color(0xFF8899AA)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            onLogout()
                        }
                    ) {
                        Text("Logout", color = Color(0xFFE63946), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF132040),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Threat Stats Header with Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Threat Summary",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Toggle Segment Controller
            Row(
                modifier = Modifier
                    .background(Color(0xFF132040), RoundedCornerShape(20.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = { isWeeklyView = true },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isWeeklyView) Color(0xFF00C2FF) else Color.Transparent,
                        contentColor = if (isWeeklyView) Color(0xFF0A1628) else Color(0xFF8899AA)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Weekly (7d)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                
                TextButton(
                    onClick = { isWeeklyView = false },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (!isWeeklyView) Color(0xFF00C2FF) else Color.Transparent,
                        contentColor = if (!isWeeklyView) Color(0xFF0A1628) else Color(0xFF8899AA)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("All-Time", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Text("Loading live data...", color = Color(0xFF8899AA), fontSize = 12.sp)
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("High",   highCount.toString(),   Color(0xFFE63946), Modifier.weight(1f))
                    StatCard("Medium", mediumCount.toString(), Color(0xFFF4A261), Modifier.weight(1f))
                    StatCard("Low",    lowCount.toString(),    Color(0xFF2DC653), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                ThreatDistributionBar(highCount, mediumCount, lowCount)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Node Status
        Text(
            text = "Network Nodes",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Text("Loading nodes...", color = Color(0xFF8899AA), fontSize = 12.sp)
        } else if (nodes.isEmpty()) {
            Text("No nodes found", color = Color(0xFF8899AA), fontSize = 12.sp)
        } else {
            nodes.forEach { node ->
                NodeCard(node.id, node.ip, node.status)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Button(
            onClick = {
                isScanning = true
                scope.launch {
                    try {
                        stats = ApiClient.api.getStats()
                        nodes = ApiClient.api.getNodes()
                    } catch (e: Exception) {
                        apiError = true
                    }
                    delay(2000)
                    isScanning = false
                }
            },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E5FA8),
                disabledContainerColor = Color(0xFF1E5FA8).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning Network...", color = Color.White)
                }
            } else {
                Text("Scan Network", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onViewThreats,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132040)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("View Threat Log", color = Color(0xFF00C2FF))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onViewAR,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C2FF)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("View AR Network", color = Color(0xFF0A1628), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}