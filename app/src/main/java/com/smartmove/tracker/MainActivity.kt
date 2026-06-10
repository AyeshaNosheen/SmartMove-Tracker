package com.smartmove.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val DarkGreen   = Color(0xFF0D3D26)
private val AccentGreen = Color(0xFF7DCCA0)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isTracking by remember { mutableStateOf(false) }
            var statusText by remember { mutableStateOf("Not tracking") }

            MaterialTheme {
                TrackerScreen(
                    isTracking    = isTracking,
                    statusText    = statusText,
                    onStartTracking = {
                        startTrackingService()
                        isTracking = true
                        statusText = "Tracking in background..."
                    },
                    onStopTracking = {
                        stopTrackingService()
                        isTracking = false
                        statusText = "Tracking stopped"
                    },
                    hasPermission     = { hasLocationPermission() },
                    requestPermission = {
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        requestPermissionLauncher.launch(permissions.toTypedArray())
                    }
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startTrackingService() {
        val intent = Intent(this, LocationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTrackingService() {
        val intent = Intent(this, LocationForegroundService::class.java)
        stopService(intent)
    }
}

@Composable
fun TrackerScreen(
    isTracking        : Boolean,
    statusText        : String,
    onStartTracking   : () -> Unit,
    onStopTracking    : () -> Unit,
    hasPermission     : () -> Boolean,
    requestPermission : () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F0E8)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Header ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGreen)
                .padding(top = 48.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🚌 SmartMove", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Bus Tracker", fontSize = 13.sp, color = AccentGreen)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ── Status Circle ──────────────────────────────────
        Box(
            modifier         = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(if (isTracking) Color(0xFF1B5E20) else Color(0xFF424242)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isTracking) "🟢" else "⚫", fontSize = 40.sp)
                Text(
                    text       = if (isTracking) "LIVE" else "OFF",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text       = statusText,
            fontSize   = 14.sp,
            color      = if (isTracking) Color(0xFF2E7D32) else Color(0xFF666666),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Info card ──────────────────────────────────────
        Card(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Bus ID",  LocationForegroundService.BUS_ID.take(12) + "...")
                InfoRow("Status", if (isTracking) "🟢 Sending every 5 sec" else "⚫ Not sending")
                InfoRow("Mode",   if (isTracking) "Background (Foreground Service)" else "Idle")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isTracking) {
            Card(
                modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ℹ️", fontSize = 16.sp)
                    Text(
                        text     = "You can minimize this app — tracking continues in background!",
                        fontSize = 12.sp,
                        color    = Color(0xFF2E7D32)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Start / Stop Button ────────────────────────────
        Button(
            onClick = {
                if (!hasPermission()) {
                    requestPermission()
                    return@Button
                }
                if (isTracking) onStopTracking() else onStartTracking()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color(0xFFB71C1C) else DarkGreen
            )
        ) {
            Text(
                text       = if (isTracking) "Stop Tracking" else "Start Tracking",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF666666))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A1A))
    }
}