package lk.nibm.kandy.hdse252ft.smartfirewall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ThreatResponse

@Composable
fun LiveThreatCard(threat: ThreatResponse) {
    val severityColor = when (threat.severity) {
        "High"   -> Color(0xFFE63946)
        "Medium" -> Color(0xFFF4A261)
        else     -> Color(0xFF2DC653)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132040)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = threat.attack_type,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Box(
                    modifier = Modifier
                        .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = threat.severity,
                        color = severityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Source IP: ${threat.source_ip}", color = Color(0xFF8899AA), fontSize = 12.sp)
            Text("Node: ${threat.node_id}",        color = Color(0xFF8899AA), fontSize = 12.sp)
            Text(threat.timestamp,                 color = Color(0xFF8899AA), fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (threat.on_chain) "✓ Logged on Blockchain" else "⏳ Pending blockchain log",
                color = if (threat.on_chain) Color(0xFF2DC653) else Color(0xFFF4A261),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
