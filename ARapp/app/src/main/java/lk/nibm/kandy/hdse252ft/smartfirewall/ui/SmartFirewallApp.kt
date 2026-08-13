package lk.nibm.kandy.hdse252ft.smartfirewall.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ApiClient
import lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens.ARScreen
import lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens.DashboardScreen
import lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens.ThreatListScreen
import lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens.LoginScreen

@Composable
fun SmartFirewallApp() {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("smart_firewall_prefs", Context.MODE_PRIVATE)
    }

    var currentScreen by remember {
        val isLoggedIn = sharedPrefs.getBoolean("is_logged_in", false)
        val savedIp = sharedPrefs.getString("gateway_ip", "192.168.1.4") ?: "192.168.1.4"
        if (isLoggedIn) {
            // Auto-initialize ApiClient using the saved network IP on startup
            ApiClient.updateBaseUrl(savedIp)
        }
        mutableStateOf(if (isLoggedIn) "dashboard" else "login")
    }

    when (currentScreen) {
        "login"     -> LoginScreen(
            onLoginSuccess = { currentScreen = "dashboard" }
        )
        "dashboard" -> DashboardScreen(
            onViewThreats = { currentScreen = "threats" },
            onViewAR      = { currentScreen = "ar" },
            onLogout      = {
                sharedPrefs.edit().putBoolean("is_logged_in", false).apply()
                currentScreen = "login"
            }
        )
        "threats"   -> ThreatListScreen(
            onBack = { currentScreen = "dashboard" }
        )
        "ar"        -> ARScreen(
            onBack = { currentScreen = "dashboard" }
        )
    }
}
