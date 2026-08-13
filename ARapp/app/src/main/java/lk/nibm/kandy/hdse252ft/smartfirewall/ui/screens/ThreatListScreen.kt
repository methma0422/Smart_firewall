package lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens

import lk.nibm.kandy.hdse252ft.smartfirewall.ui.components.*

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ApiClient
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ThreatResponse

@Composable
fun ThreatListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("smart_firewall_prefs", Context.MODE_PRIVATE)
    }
    val gatewayIp = remember {
        sharedPrefs.getString("gateway_ip", "192.168.1.4") ?: "192.168.1.4"
    }

    var threats   by remember { mutableStateOf<List<ThreatResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var apiError  by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSeverityFilter by remember { mutableStateOf("All") }

    val filteredThreats = remember(threats, searchQuery, selectedSeverityFilter) {
        threats.filter { threat ->
            val matchesSearch = threat.attack_type.contains(searchQuery, ignoreCase = true) ||
                    threat.source_ip.contains(searchQuery, ignoreCase = true) ||
                    threat.node_id.contains(searchQuery, ignoreCase = true)
            
            val matchesSeverity = selectedSeverityFilter == "All" || 
                    threat.severity.equals(selectedSeverityFilter, ignoreCase = true)
                    
            matchesSearch && matchesSeverity
        }
    }

    LaunchedEffect(Unit) {
        try {
            threats   = ApiClient.api.getThreats()
            isLoading = false
            apiError  = false
        } catch (e: Exception) {
            isLoading = false
            apiError  = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1628))
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color(0xFF00C2FF))
            }
            Text(
                text = "Threat Log",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Blockchain-verified threat events",
            color = Color(0xFF8899AA),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
        )

        if (!isLoading && !apiError) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by IP, Attack type, or Node ID", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF132040),
                    unfocusedContainerColor = Color(0xFF132040),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00C2FF),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = listOf("All", "High", "Medium", "Low")
                chips.forEach { severity ->
                    val isSelected = selectedSeverityFilter == severity
                    val chipColor = when (severity) {
                        "High" -> Color(0xFFE63946)
                        "Medium" -> Color(0xFFF4A261)
                        "Low" -> Color(0xFF2DC653)
                        else -> Color(0xFF00C2FF)
                    }
                    
                    TextButton(
                        onClick = { selectedSeverityFilter = severity },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isSelected) chipColor.copy(alpha = 0.2f) else Color(0xFF132040),
                            contentColor = if (isSelected) chipColor else Color(0xFF8899AA)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(severity, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00C2FF))
                }
            }
            apiError -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A0808), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "⚠ Cannot reach firewall API at $gatewayIp",
                            color = Color(0xFFE63946),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ensure that the FastAPI endpoint is running on port 8000 at this address and both your device and host are connected to the same local network.",
                            color = Color(0xFF8899AA),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            filteredThreats.isEmpty() -> {
                Text(
                    if (threats.isEmpty()) "No threats detected yet." else "No threats matching search criteria.",
                    color = Color(0xFF8899AA),
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                Text(
                    "${filteredThreats.size} threats found" + if (searchQuery.isNotEmpty() || selectedSeverityFilter != "All") " (filtered)" else "",
                    color = Color(0xFF8899AA),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredThreats) { threat ->
                        LiveThreatCard(threat)
                    }
                }
            }
        }
    }
}