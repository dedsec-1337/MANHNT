package com.manhunt.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.manhunt.app.MainActivity
import com.manhunt.app.R
import com.manhunt.app.data.Constants
import com.manhunt.app.network.FirebaseSyncService
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
    private var joinCode = ""
    private var playerId = ""
    private var isPaused = false
    private var locationTrackingStarted = false
    private var countdownTimerJob: Job? = null

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
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                countdownTimerJob?.cancel()
                stopLocationTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        remainingSeconds = intent.getIntExtra(EXTRA_REMAINING_SECONDS, 1800)
        zoneCenterLat = intent.getDoubleExtra(EXTRA_ZONE_LAT, 0.0)
        zoneCenterLng = intent.getDoubleExtra(EXTRA_ZONE_LNG, 0.0)
        zoneRadiusMeters = intent.getDoubleExtra(EXTRA_ZONE_RADIUS, 500.0)
        joinCode = intent.getStringExtra(EXTRA_JOIN_CODE).orEmpty()
        playerId = intent.getStringExtra(EXTRA_PLAYER_ID).orEmpty()
        isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)

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

        return START_REDELIVER_INTENT
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        if (locationTrackingStarted) return
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            updateNotification("Location permission is required for player tracking.")
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(Constants.FASTEST_LOCATION_INTERVAL_MS)
            setMaxUpdateDelayMillis(Constants.LOCATION_UPDATE_INTERVAL_MS + 1000)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
        locationTrackingStarted = true
    }

    private fun stopLocationTracking() {
        if (!locationTrackingStarted) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationTrackingStarted = false
    }

    private fun handleNewLocation(location: Location) {
        val outOfBounds = if (zoneRadiusMeters > 0 && GeoUtils.hasValidCoordinates(zoneCenterLat, zoneCenterLng)) {
            GeoUtils.isOutsideZone(
                location.latitude,
                location.longitude,
                zoneCenterLat,
                zoneCenterLng,
                zoneRadiusMeters
            )
        } else false

        isOutOfZone = outOfBounds

        // Emit to local UI ViewModel
        serviceScope.launch {
            _locationUpdates.emit(location)
        }

        // PUSH TO CLOUD FIREBASE REST EVERY 5s (even when app is backgrounded/screen off)
        if (joinCode.isNotEmpty() && playerId.isNotEmpty()) {
            FirebaseSyncService.pushLocationUpdate(
                joinCode = joinCode,
                playerId = playerId,
                lat = location.latitude,
                lng = location.longitude,
                isOutOfZone = outOfBounds
            )
        }

        if (outOfBounds) {
            updateNotification("WARNING: Out of boundary! Return immediately.")
        }
    }

    private fun startCountdownTimer() {
        countdownTimerJob?.cancel()
        countdownTimerJob = null
        if (isPaused || remainingSeconds <= 0) return

        countdownTimerJob = serviceScope.launch {
            while (isActive && !isPaused && remainingSeconds > 0) {
                delay(1000)
                if (isPaused) break
                remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
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
            "Cellular GPS active — $timeString remaining"
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
            .setContentTitle("Manhunt Cloud Tracking Active")
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
            .setContentTitle("Manhunt Boundary Warning")
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
        countdownTimerJob?.cancel()
        stopLocationTracking()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "manhunt_chase_service"
        const val NOTIFICATION_ID = 4242

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_REMAINING_SECONDS = "EXTRA_REMAINING_SECONDS"
        const val EXTRA_ZONE_LAT = "EXTRA_ZONE_LAT"
        const val EXTRA_ZONE_LNG = "EXTRA_ZONE_LNG"
        const val EXTRA_ZONE_RADIUS = "EXTRA_ZONE_RADIUS"
        const val EXTRA_JOIN_CODE = "EXTRA_JOIN_CODE"
        const val EXTRA_PLAYER_ID = "EXTRA_PLAYER_ID"
        const val EXTRA_IS_PAUSED = "EXTRA_IS_PAUSED"

        private val _locationUpdates = MutableSharedFlow<Location>(extraBufferCapacity = 32)
        val locationUpdates = _locationUpdates.asSharedFlow()

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
