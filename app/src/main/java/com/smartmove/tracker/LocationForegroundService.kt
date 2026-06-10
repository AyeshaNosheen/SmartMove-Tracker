package com.smartmove.tracker

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import androidx.annotation.RequiresApi
import android.os.Build

class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private var locationCallback             : LocationCallback? = null
    private val db = Firebase.database.reference

    companion object {
        const val CHANNEL_ID    = "location_channel"
        const val NOTIFICATION_ID = 1
        const val BUS_ID        = "KCrQsdijjF3H3BR616C6"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Starting GPS..."))
        startLocationUpdates()
        return START_STICKY // restart if killed
    }

    private fun startLocationUpdates() {
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

                // Update notification with current location
                updateNotification("📍 Lat: ${"%.4f".format(location.latitude)}, Speed: ${"%.1f".format(speedKmh)} km/h")

                // Send to Firebase
                db.child("busLocations").child(BUS_ID).setValue(
                    mapOf(
                        "busId"       to BUS_ID,
                        "latitude"    to location.latitude,
                        "longitude"   to location.longitude,
                        "speed"       to speedKmh,
                        "isOnline"    to true,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
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
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SmartMove bus location tracking"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent        = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚌 SmartMove Tracker")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        db.child("busLocations").child(BUS_ID).child("isOnline").setValue(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}