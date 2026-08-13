package lk.nibm.kandy.hdse252ft.smartfirewall.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NodeCard(name: String, ip: String, status: String) {
    val statusColor = when (status) {
        "High"   -> Color(0xFFE63946)
        "Medium" -> Color(0xFFF4A261)
        else     -> Color(0xFF2DC653)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132040)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E5FA8), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = ip,   color = Color(0xFF8899AA), fontSize = 12.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val statusPulseTransition = rememberInfiniteTransition()
                val pulseAlpha by statusPulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor.copy(alpha = pulseAlpha), RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = status,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
