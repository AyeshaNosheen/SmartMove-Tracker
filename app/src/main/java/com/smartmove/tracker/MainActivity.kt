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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

private val DarkGreen   = Color(0xFF0D3D26)
private val AccentGreen = Color(0xFF7DCCA0)

// ── Bus data class ─────────────────────────────────────────────
data class BusItem(
    val busId      : String = "",
    val busNumber  : String = "",
    val driverName : String = ""
)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isTracking   by remember { mutableStateOf(false) }
            var statusText   by remember { mutableStateOf("Select your bus to start") }
            var buses        by remember { mutableStateOf<List<BusItem>>(emptyList()) }
            var selectedBus  by remember { mutableStateOf<BusItem?>(null) }
            var isLoadingBuses by remember { mutableStateOf(true) }

            // Fetch buses from Firestore
            LaunchedEffect(Unit) {
                Firebase.firestore.collection("buses")
                    .whereEqualTo("isActive", true)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        buses = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            BusItem(
                                busId      = doc.id,
                                busNumber  = data["busNumber"]  as? String ?: "",
                                driverName = data["driverName"] as? String ?: ""
                            )
                        }
                        isLoadingBuses = false
                    }
                    .addOnFailureListener {
                        isLoadingBuses = false
                    }
            }

            MaterialTheme {
                TrackerScreen(
                    isTracking     = isTracking,
                    statusText     = statusText,
                    buses          = buses,
                    selectedBus    = selectedBus,
                    isLoadingBuses = isLoadingBuses,
                    onBusSelected  = { bus -> selectedBus = bus },
                    onStartTracking = {
                        selectedBus?.let { bus ->
                            // Pass busId to service
                            val intent = Intent(this, LocationForegroundService::class.java)
                            intent.putExtra("BUS_ID", bus.busId)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                            isTracking = true
                            statusText = "Tracking Bus ${bus.busNumber} in background..."
                        }
                    },
                    onStopTracking = {
                        stopService(Intent(this, LocationForegroundService::class.java))
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    isTracking        : Boolean,
    statusText        : String,
    buses             : List<BusItem>,
    selectedBus       : BusItem?,
    isLoadingBuses    : Boolean,
    onBusSelected     : (BusItem) -> Unit,
    onStartTracking   : () -> Unit,
    onStopTracking    : () -> Unit,
    hasPermission     : () -> Boolean,
    requestPermission : () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        Spacer(modifier = Modifier.height(32.dp))

        // ── Bus Selection Dropdown ─────────────────────────
        Card(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Your Bus", fontSize = 13.sp, color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingBuses) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DarkGreen, modifier = Modifier.size(24.dp))
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded         = dropdownExpanded && !isTracking,
                        onExpandedChange = { if (!isTracking) dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value         = selectedBus?.let { "Bus ${it.busNumber} — ${it.driverName}" } ?: "Select bus...",
                            onValueChange = {},
                            readOnly      = true,
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                            modifier      = Modifier.fillMaxWidth().menuAnchor(),
                            shape         = RoundedCornerShape(10.dp),
                            enabled       = !isTracking,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = DarkGreen,
                                unfocusedBorderColor    = Color(0xFFCCCCCC),
                                focusedContainerColor   = Color.White,
                                unfocusedContainerColor = Color.White,
                                disabledBorderColor     = Color(0xFFCCCCCC),
                                disabledContainerColor  = Color(0xFFF5F5F5)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded         = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            if (buses.isEmpty()) {
                                DropdownMenuItem(
                                    text    = { Text("No active buses found", fontSize = 13.sp) },
                                    onClick = { dropdownExpanded = false }
                                )
                            } else {
                                buses.forEach { bus ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Bus ${bus.busNumber}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("Driver: ${bus.driverName}", fontSize = 11.sp, color = Color(0xFF666666))
                                            }
                                        },
                                        onClick = {
                                            onBusSelected(bus)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Status Circle ──────────────────────────────────
        Box(
            modifier         = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(if (isTracking) Color(0xFF1B5E20) else Color(0xFF424242)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isTracking) "🟢" else "⚫", fontSize = 36.sp)
                Text(
                    text       = if (isTracking) "LIVE" else "OFF",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text       = statusText,
            fontSize   = 13.sp,
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
                InfoRow("Bus",    selectedBus?.let { "Bus ${it.busNumber}" } ?: "Not selected")
                InfoRow("Driver", selectedBus?.driverName ?: "Not selected")
                InfoRow("Status", if (isTracking) "🟢 Sending every 5 sec" else "⚫ Not sending")
            }
        }

        if (isTracking) {
            Spacer(modifier = Modifier.height(12.dp))
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

        Spacer(modifier = Modifier.height(24.dp))

        // ── Start / Stop Button ────────────────────────────
        Button(
            onClick = {
                if (!hasPermission()) { requestPermission(); return@Button }
                if (selectedBus == null && !isTracking) return@Button
                if (isTracking) onStopTracking() else onStartTracking()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            enabled  = selectedBus != null || isTracking,
            colors   = ButtonDefaults.buttonColors(
                containerColor        = if (isTracking) Color(0xFFB71C1C) else DarkGreen,
                disabledContainerColor = Color(0xFFCCCCCC)
            )
        ) {
            Text(
                text       = if (isTracking) "Stop Tracking"
                else if (selectedBus == null) "Select a bus first"
                else "Start Tracking",
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