package com.smartmove.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
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
import com.google.android.gms.location.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

private const val BUS_ID = "KCrQsdijjF3H3BR616C6"
private val DarkGreen   = Color(0xFF0D3D26)
private val AccentGreen = Color(0xFF7DCCA0)

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private var locationCallback             : LocationCallback? = null
    private var timerHandler                 : android.os.Handler? = null
    private var timerRunnable                : Runnable? = null
    private val db = Firebase.database.reference

    // Shared state for UI updates
    var onLocationSent: ((Double, Double, Double, Int) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        var updateCount = 0

        setContent {
            var isTracking  by remember { mutableStateOf(false) }
            var statusText  by remember { mutableStateOf("Not tracking") }
            var latText     by remember { mutableStateOf("--") }
            var lonText     by remember { mutableStateOf("--") }
            var speedText   by remember { mutableStateOf("--") }
            var countText   by remember { mutableStateOf(0) }

            // Connect callback to update UI
            onLocationSent = { lat, lon, speed, count ->
                latText   = "%.6f".format(lat)
                lonText   = "%.6f".format(lon)
                speedText = "%.1f".format(speed)
                countText = count
            }

            MaterialTheme {
                TrackerScreen(
                    isTracking    = isTracking,
                    statusText    = statusText,
                    latText       = latText,
                    lonText       = lonText,
                    speedText     = speedText,
                    updateCount   = countText,
                    onStartTracking = {
                        startTracking { lat, lon, speed ->
                            updateCount++
                            onLocationSent?.invoke(lat, lon, speed, updateCount)
                        }
                        isTracking = true
                        statusText = "Sending location every 5 sec..."
                    },
                    onStopTracking = {
                        stopTracking()
                        isTracking = false
                        statusText = "Tracking stopped"
                        updateCount = 0
                    },
                    hasPermission     = { hasLocationPermission() },
                    requestPermission = {
                        requestPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
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

    private fun sendToFirebase(lat: Double, lon: Double, speed: Double, callback: (Double, Double, Double) -> Unit) {
        db.child("busLocations").child(BUS_ID).setValue(
            mapOf(
                "busId"       to BUS_ID,
                "latitude"    to lat,
                "longitude"   to lon,
                "speed"       to speed,
                "isOnline"    to true,
                "lastUpdated" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            android.util.Log.d("TrackerApp", "✅ Sent to Firebase: $lat, $lon")
            callback(lat, lon, speed)
        }.addOnFailureListener { e ->
            android.util.Log.e("TrackerApp", "❌ Failed: ${e.message}")
        }
    }

    private fun startTracking(onSent: (Double, Double, Double) -> Unit) {
        if (!hasLocationPermission()) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).apply {
            setMinUpdateIntervalMillis(3000L)
            setMinUpdateDistanceMeters(0f)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val speedKmh = location.speed * 3.6
                sendToFirebase(location.latitude, location.longitude, speedKmh, onSent)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Timer — forces update every 5 seconds even when stationary
        timerHandler  = android.os.Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (locationCallback != null) {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val speedKmh = location.speed * 3.6
                                sendToFirebase(location.latitude, location.longitude, speedKmh, onSent)
                            }
                        }
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }
                    timerHandler?.postDelayed(this, 5000)
                }
            }
        }
        timerHandler?.postDelayed(timerRunnable!!, 5000)
    }

    private fun stopTracking() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        timerRunnable?.let { timerHandler?.removeCallbacks(it) }
        timerRunnable = null
        timerHandler  = null
        db.child("busLocations").child(BUS_ID).child("isOnline").setValue(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }
}

@Composable
fun TrackerScreen(
    isTracking        : Boolean,
    statusText        : String,
    latText           : String,
    lonText           : String,
    speedText         : String,
    updateCount       : Int,
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

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier  = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("Bus ID",    BUS_ID.take(12) + "...")
                InfoRow("Latitude",  latText)
                InfoRow("Longitude", lonText)
                InfoRow("Speed",     "$speedText km/h")
                InfoRow("Updates",   "$updateCount sent")
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                if (!hasPermission()) {
                    requestPermission()
                    return@Button
                }
                if (isTracking) onStopTracking() else onStartTracking()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
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