export interface ProjectFile {
  name: string;
  path: string;
  language: string;
  content: string;
  description: string;
}

export const ANDROID_FILES: ProjectFile[] = [
  {
    name: 'MainActivity.kt',
    path: 'app/src/main/java/com/manhunt/app/MainActivity.kt',
    language: 'kotlin',
    description: 'Single-activity entry point with Accompanist permissions & battery optimization intent',
    content: `package com.manhunt.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.manhunt.app.data.model.GameStatus
import com.manhunt.app.service.ManhuntLocationService
import com.manhunt.app.ui.screens.*
import com.manhunt.app.ui.theme.ManhuntTheme
import com.manhunt.app.viewmodel.GameViewModel

enum class Screen { HOME, CREATE_ROOM, LOBBY, GAME, RESULTS, PROFILE }

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ManhuntTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    var currentScreen by remember { mutableStateOf(Screen.HOME) }

                    // Runtime Location & Notification Permissions
                    val permissionsState = rememberMultiplePermissionsState(
                        permissions = listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )

                    LaunchedEffect(Unit) {
                        if (!permissionsState.allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                        ManhuntLocationService.createBatteryOptimizationIntent(this@MainActivity)?.let {
                            startActivity(it)
                        }
                    }

                    Crossfade(targetState = currentScreen, label = "screenTransition") { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                state = uiState,
                                onCreateRoomClick = { currentScreen = Screen.CREATE_ROOM },
                                onJoinRoomClick = { code, hostIp -> viewModel.joinRoom(code, hostIp) },
                                onEditProfileClick = { currentScreen = Screen.PROFILE }
                            )
                            Screen.CREATE_ROOM -> CreateRoomScreen(
                                onBackClick = { currentScreen = Screen.HOME },
                                onCreateRoomSubmit = { name, duration, radius, ratio ->
                                    viewModel.createRoom(name, duration, radius, ratio)
                                    currentScreen = Screen.LOBBY
                                }
                            )
                            Screen.LOBBY -> LobbyScreen(
                                state = uiState,
                                onStartGameClick = { viewModel.startGame() },
                                onToggleRole = { viewModel.togglePlayerRole(it) },
                                onAutoBalanceRoles = { viewModel.autoAssignRoles() },
                                onLeaveLobbyClick = { viewModel.leaveGame(); currentScreen = Screen.HOME }
                            )
                            Screen.GAME -> GameScreen(
                                state = uiState,
                                onToggleSafetyPause = { viewModel.toggleSafetyPause() },
                                onAttemptCatch = { id, pin -> viewModel.attemptCatch(id, pin) },
                                onLeaveGame = { viewModel.leaveGame(); currentScreen = Screen.HOME }
                            )
                            Screen.RESULTS -> ResultsScreen(
                                state = uiState,
                                onBackToHome = { viewModel.leaveGame(); currentScreen = Screen.HOME }
                            )
                            Screen.PROFILE -> ProfileScreen(
                                state = uiState,
                                onSaveProfile = { name, emoji -> viewModel.updateProfile(name, emoji) },
                                onClose = { currentScreen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }
}`
  },
  {
    name: 'ManhuntLocationService.kt',
    path: 'app/src/main/java/com/manhunt/app/service/ManhuntLocationService.kt',
    language: 'kotlin',
    description: 'Android Foreground Service for continuous GPS tracking & boundary geofencing',
    content: `package com.manhunt.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.manhunt.app.util.GeoUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class ManhuntLocationService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var remainingSeconds = 1800
    private var isOutOfZone = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildOngoingNotification(remainingSeconds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startLocationTracking()
        return START_STICKY
    }

    private fun startLocationTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun buildOngoingNotification(seconds: Int): Notification {
        val mins = seconds / 60
        val secs = seconds % 60
        val timeString = String.format("%02d:%02d", mins, secs)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manhunt GPS Active")
            .setContentText("Manhunt is running — $timeString remaining")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}`
  },
  {
    name: 'P2PNetworkManager.kt',
    path: 'app/src/main/java/com/manhunt/app/network/P2PNetworkManager.kt',
    language: 'kotlin',
    description: 'UDP Multicast peer discovery & in-memory TCP socket relay on Wi-Fi / Hotspot',
    content: `package com.manhunt.app.network

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.Gson
import com.manhunt.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.*

class P2PNetworkManager(private val context: Context) {
    private val UDP_PORT = 8888
    private val TCP_PORT = 9876
    private val MULTICAST_IP = "239.255.42.99"
    private val gson = Gson()

    fun startHosting(session: GameSession) {
        startTcpServer()
        startUdpBroadcasting(session)
    }

    private fun startUdpBroadcasting(session: GameSession) {
        CoroutineScope(Dispatchers.IO).launch {
            val group = InetAddress.getByName(MULTICAST_IP)
            val socket = DatagramSocket()
            while (isActive) {
                val packet = P2PPacket.DiscoveryBroadcast(
                    joinCode = session.joinCode,
                    roomName = session.roomName,
                    hostPort = TCP_PORT,
                    playerCount = 1,
                    zoneRadiusMeters = session.zoneRadiusMeters
                )
                val data = gson.toJson(packet).toByteArray()
                socket.send(DatagramPacket(data, data.size, group, UDP_PORT))
                delay(2000)
            }
        }
    }
}`
  },
  {
    name: 'OSMMapView.kt',
    path: 'app/src/main/java/com/manhunt/app/ui/components/OSMMapView.kt',
    language: 'kotlin',
    description: 'OSMDroid OpenStreetMap wrapped in Compose AndroidView with zone circle and player markers',
    content: `package com.manhunt.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.manhunt.app.data.model.Player
import com.manhunt.app.data.model.Role
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun OSMMapView(
    playerLat: Double,
    playerLng: Double,
    userRole: Role,
    zoneCenterLat: Double,
    zoneCenterLng: Double,
    zoneRadiusMeters: Double,
    otherPlayers: List<Player>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(17.0)
                if (playerLat != 0.0) controller.setCenter(GeoPoint(playerLat, playerLng))
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            // Draw Play Zone Circle
            if (zoneRadiusMeters > 0) {
                val circle = Polygon(mapView).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(zoneCenterLat, zoneCenterLng), zoneRadiusMeters)
                }
                mapView.overlays.add(circle)
            }
            // Seekers see Hiders; Hiders only see self
            if (userRole == Role.SEEKER) {
                otherPlayers.filter { it.role == Role.HIDER && !it.isCaught }.forEach { hider ->
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(hider.latitude, hider.longitude)
                        title = "\${hider.name} (Hider)"
                    }
                    mapView.overlays.add(marker)
                }
            }
            mapView.invalidate()
        }
    )
}`
  },
  {
    name: 'AndroidManifest.xml',
    path: 'app/src/main/AndroidManifest.xml',
    language: 'xml',
    description: 'All Android permissions, background GPS service & activity declaration',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".ManhuntApp"
        android:label="@string/app_name"
        android:theme="@style/Theme.Manhunt">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".service.ManhuntLocationService"
            android:foregroundServiceType="location"
            android:exported="false" />
    </application>
</manifest>`
  },
  {
    name: 'app/build.gradle.kts',
    path: 'app/build.gradle.kts',
    language: 'kotlin',
    description: 'Gradle configuration with Jetpack Compose, OSMDroid, Room, Accompanist, Coroutines',
    content: `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.manhunt.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.manhunt.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("com.google.code.gson:gson:2.10.1")
}`
  }
];
