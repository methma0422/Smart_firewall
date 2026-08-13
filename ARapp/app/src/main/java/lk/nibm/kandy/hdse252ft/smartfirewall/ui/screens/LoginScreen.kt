package lk.nibm.kandy.hdse252ft.smartfirewall.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.ApiClient
import lk.nibm.kandy.hdse252ft.smartfirewall.data.api.AuthRequest

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sharedPrefs = remember {
        context.getSharedPreferences("smart_firewall_prefs", Context.MODE_PRIVATE)
    }

    var isRegisterMode by remember { mutableStateOf(false) }
    var gatewayIp by remember {
        mutableStateOf(sharedPrefs.getString("gateway_ip", "192.168.1.4") ?: "192.168.1.4")
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Curated dark cyber theme colors
    val darkBlueBg = Color(0xFF0A1128)
    val cardBg = Color(0xFF101F42)
    val neonCyan = Color(0xFF00C2FF)
    val dangerRed = Color(0xFFE63946)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBlueBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, neonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "FIREWALL ACCESS GATEWAY",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "DECENTRALIZED INTRUSION SYSTEM",
                    color = neonCyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )

                // Error alert
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = dangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Gateway IP field
                OutlinedTextField(
                    value = gatewayIp,
                    onValueChange = {
                        gatewayIp = it
                        errorMessage = null
                    },
                    label = { Text("Firewall Gateway IP", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = darkBlueBg,
                        unfocusedContainerColor = darkBlueBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonCyan,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Username field
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text("Agent ID / Username", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = darkBlueBg,
                        unfocusedContainerColor = darkBlueBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonCyan,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Access Cipher / Password", color = Color.Gray) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = Color.White
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = darkBlueBg,
                        unfocusedContainerColor = darkBlueBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = neonCyan,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (isRegisterMode) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm Password field (Register mode only)
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Cipher / Password", color = Color.Gray) },
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (isConfirmPasswordVisible) "Hide password" else "Show password",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = darkBlueBg,
                            unfocusedContainerColor = darkBlueBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = neonCyan,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Submit Button
                Button(
                    onClick = {
                        val cleanIp = gatewayIp.trim()
                        if (cleanIp.isBlank()) {
                            errorMessage = "Gateway IP must be populated."
                            return@Button
                        }
                        
                        // IPv4 Address Pattern Validation
                        val ipRegex = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
                        if (!cleanIp.matches(ipRegex)) {
                            errorMessage = "Please enter a valid IPv4 address (e.g. 192.168.1.4)."
                            return@Button
                        }

                        if (username.isBlank() || password.isBlank()) {
                            errorMessage = "All credentials must be populated."
                            return@Button
                        }
                        if (isRegisterMode && password != confirmPassword) {
                            errorMessage = "Ciphers do not match."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null

                        // Dynamically update the base URL before making the API request
                        ApiClient.updateBaseUrl(gatewayIp)

                        coroutineScope.launch {
                            try {
                                if (isRegisterMode) {
                                    val response = ApiClient.api.register(AuthRequest(username, password))
                                    if (response.success) {
                                        // Save IP to preferences
                                        sharedPrefs.edit()
                                            .putString("gateway_ip", gatewayIp)
                                            .apply()

                                        Toast.makeText(context, "Registration approved! Please login.", Toast.LENGTH_LONG).show()
                                        isRegisterMode = false
                                        password = ""
                                        confirmPassword = ""
                                    } else {
                                        errorMessage = response.message
                                    }
                                } else {
                                    val response = ApiClient.api.login(AuthRequest(username, password))
                                    if (response.success) {
                                        // Save session to Shared Preferences
                                        sharedPrefs.edit()
                                            .putBoolean("is_logged_in", true)
                                            .putString("username", username)
                                            .putString("gateway_ip", gatewayIp)
                                            .apply()

                                        onLoginSuccess(username)
                                    } else {
                                        errorMessage = response.message
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Unable to contact Security API at $gatewayIp: ${e.localizedMessage}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = if (isRegisterMode) "AUTHORIZE REGISTRATION" else "AUTHORIZE ACCESS",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Mode text link
                TextButton(
                    onClick = {
                        isRegisterMode = !isRegisterMode
                        errorMessage = null
                        password = ""
                        confirmPassword = ""
                    }
                ) {
                    Text(
                        text = if (isRegisterMode) "Existing Agent? Access Dashboard" else "New Resident? Register Gateway",
                        color = neonCyan.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
