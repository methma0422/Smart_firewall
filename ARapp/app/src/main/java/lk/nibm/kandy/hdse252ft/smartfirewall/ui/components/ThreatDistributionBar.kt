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

@Composable
fun ThreatDistributionBar(high: Int, medium: Int, low: Int) {
    val total = high + medium + low
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132040)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Proportional Severity",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (total == 0) {
                // Empty state bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    val highWeight = high.toFloat() / total
                    val mediumWeight = medium.toFloat() / total
                    val lowWeight = low.toFloat() / total

                    if (highWeight > 0) {
                        Box(
                            modifier = Modifier
                                .weight(highWeight)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFFE63946),
                                    shape = when {
                                        mediumWeight == 0f && lowWeight == 0f -> RoundedCornerShape(5.dp)
                                        else -> RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)
                                    }
                                )
                        )
                    }
                    if (mediumWeight > 0) {
                        Box(
                            modifier = Modifier
                                .weight(mediumWeight)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFFF4A261),
                                    shape = when {
                                        highWeight == 0f && lowWeight == 0f -> RoundedCornerShape(5.dp)
                                        highWeight == 0f -> RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)
                                        lowWeight == 0f -> RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)
                                        else -> androidx.compose.ui.graphics.RectangleShape
                                    }
                                )
                        )
                    }
                    if (lowWeight > 0) {
                        Box(
                            modifier = Modifier
                                .weight(lowWeight)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFF2DC653),
                                    shape = when {
                                        highWeight == 0f && mediumWeight == 0f -> RoundedCornerShape(5.dp)
                                        else -> RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)
                                    }
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val highPct = if (total > 0) (high * 100 / total) else 0
                val medPct = if (total > 0) (medium * 100 / total) else 0
                val lowPct = if (total > 0) (low * 100 / total) else 0
                Text("High: $highPct%", color = Color(0xFFE63946), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Medium: $medPct%", color = Color(0xFFF4A261), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Low: $lowPct%", color = Color(0xFF2DC653), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
