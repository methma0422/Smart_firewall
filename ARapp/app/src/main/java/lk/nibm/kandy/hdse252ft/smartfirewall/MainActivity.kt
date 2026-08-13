package lk.nibm.kandy.hdse252ft.smartfirewall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import lk.nibm.kandy.hdse252ft.smartfirewall.ui.SmartFirewallApp
import lk.nibm.kandy.hdse252ft.smartfirewall.ui.theme.SmartFirewallARTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFirewallARTheme {
                SmartFirewallApp()
            }
        }
    }
}