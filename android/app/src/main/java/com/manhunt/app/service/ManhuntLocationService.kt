package com.manhunt.app.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.manhunt.app.MainActivity
import com.manhunt.app.R
import com.manhunt.app.util.GeoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ManhuntLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var remainingSeconds = 1800
    private var isOutOfZone = false
    private var zoneCenterLat = 0.0
    private var zoneCenterLng = 0.0
    private var zoneRadiusMeters = 500.0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    handleNewLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        remainingSeconds = intent?.getIntExtra(EXTRA_REMAINING_SECONDS, 1800) ?: 1800
        zoneCenterLat = intent?.getDoubleExtra(EXTRA_ZONE_LAT, 0.0) ?: 0.0
        zoneCenterLng = intent?.getDoubleExtra(EXTRA_ZONE_LNG, 0.0) ?: 0.0
        zoneRadiusMeters = intent?.getDoubleExtra(EXTRA_ZONE_RADIUS, 500.0) ?: 500.0

        val notification = buildOngoingNotification(remainingSeconds)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationTracking()
        startCountdownTimer()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(3000)
            setMaxUpdateDelayMillis(6000)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun handleNewLocation(location: Location) {
        val outOfBounds = if (zoneRadiusMeters > 0 && zoneCenterLat != 0.0) {
            GeoUtils.isOutsideZone(
                location.latitude,
                location.longitude,
                zoneCenterLat,
                zoneCenterLng,
                zoneRadiusMeters
            )
        } else false

        isOutOfZone = outOfBounds

        serviceScope.launch {
            _locationUpdates.emit(location)
        }

        if (outOfBounds) {
            updateNotification("WARNING: You are outside the play zone boundary!")
        }
    }

    private fun startCountdownTimer() {
        serviceScope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                val notif = buildOngoingNotification(remainingSeconds)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notif)
            }
        }
    }

    private fun buildOngoingNotification(seconds: Int): Notification {
        val mins = seconds / 60
        val secs = seconds % 60
        val timeString = String.format("%02d:%02d", mins, secs)
        val contentText = if (isOutOfZone) {
            "OUT OF ZONE! Return immediately ($timeString remaining)"
        } else {
            "Manhunt is running — $timeString remaining"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manhunt GPS Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(alertText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manhunt Alert")
            .setContentText(alertText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
        manager.notify(NOTIFICATION_ID, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.location_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.location_service_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "manhunt_chase_service"
        const val NOTIFICATION_ID = 4242
        const val LOCATION_INTERVAL_MS = 5000L

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_REMAINING_SECONDS = "EXTRA_REMAINING_SECONDS"
        const val EXTRA_ZONE_LAT = "EXTRA_ZONE_LAT"
        const val EXTRA_ZONE_LNG = "EXTRA_ZONE_LNG"
        const val EXTRA_ZONE_RADIUS = "EXTRA_ZONE_RADIUS"

        private val _locationUpdates = MutableSharedFlow<Location>(extraBufferCapacity = 32)
        val locationUpdates = _locationUpdates.asSharedFlow()

        /**
         * Intent to request disabling battery optimization (vital for Samsung/Xiaomi/Oppo).
         */
        @SuppressLint("BatteryLife")
        fun createBatteryOptimizationIntent(context: Context): Intent? {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else null
        }
    }
}
