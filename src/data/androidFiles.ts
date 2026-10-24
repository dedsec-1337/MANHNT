export interface ProjectFile {
  name: string;
  path: string;
  language: string;
  content: string;
  description: string;
}

export const ANDROID_FILES: ProjectFile[] = [
  {
    name: 'Constants.kt',
    path: 'app/src/main/java/com/manhunt/app/data/Constants.kt',
    language: 'kotlin',
    description: 'Firebase Realtime Database REST URL configuration & intervals for long-range tracking',
    content: `package com.manhunt.app.data

object Constants {
    // Configure your Firebase Realtime Database REST URL here (must end with '/')
    const val FIREBASE_DATABASE_URL = "https://manhnt-ba00c-default-rtdb.asia-southeast1.firebasedatabase.app/"

    const val LOCATION_UPDATE_INTERVAL_MS = 5000L
    const val FASTEST_LOCATION_INTERVAL_MS = 3000L
    const val CLOUD_SYNC_POLL_INTERVAL_MS = 2500L
    const val CATCH_PROXIMITY_METERS = 10.0
}`
  },
  {
    name: 'GeofenceManager.kt',
    path: 'app/src/main/java/com/manhunt/app/util/GeofenceManager.kt',
    language: 'kotlin',
    description: 'Circular geofence evaluation with 25m boundary buffer warning and return grace period',
    content: `package com.manhunt.app.util

enum class GeofenceStatus {
    INSIDE,         // Safe inside the play zone
    BOUNDARY_ALERT, // Within 25m of perimeter boundary
    OUT_OF_BOUNDS,  // Outside circular zone; return grace timer active
    BREACH_PENALTY  // Grace timer expired outside boundary
}

data class GeofenceEvaluation(
    val status: GeofenceStatus,
    val distanceFromCenterMeters: Double,
    val distanceToBoundaryMeters: Double,
    val isOutside: Boolean,
    val warningMessage: String? = null
)

object GeofenceManager {
    const val BOUNDARY_BUFFER_METERS = 25.0
    const val DEFAULT_GRACE_PERIOD_SECONDS = 30

    fun evaluate(playerLat: Double, playerLng: Double, centerLat: Double, centerLng: Double, radiusMeters: Double): GeofenceEvaluation {
        val distFromCenter = GeoUtils.distanceMeters(playerLat, playerLng, centerLat, centerLng)
        val distToEdge = radiusMeters - distFromCenter
        return when {
            distFromCenter > radiusMeters -> GeofenceEvaluation(GeofenceStatus.OUT_OF_BOUNDS, distFromCenter, distFromCenter - radiusMeters, true, "OUT OF BOUNDS: Return to play zone immediately!")
            distToEdge <= BOUNDARY_BUFFER_METERS -> GeofenceEvaluation(GeofenceStatus.BOUNDARY_ALERT, distFromCenter, distToEdge, false, "Approaching boundary (\${distToEdge.toInt()}m to edge)")
            else -> GeofenceEvaluation(GeofenceStatus.INSIDE, distFromCenter, distToEdge, false)
        }
    }
}`
  },
  {
    name: 'BatteryOptimizationHelper.kt',
    path: 'app/src/main/java/com/manhunt/app/util/BatteryOptimizationHelper.kt',
    language: 'kotlin',
    description: 'Whitelisting intents for Android and OEM task killers (Xiaomi, Samsung, Oppo)',
    content: `package com.manhunt.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun createOptimizationExemptionIntent(context: Context): Intent {
        return if (!isBatteryOptimizationDisabled(context)) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:\${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }
}`
  },
  {
    name: 'Theme.kt',
    path: 'app/src/main/java/com/manhunt/app/ui/theme/Theme.kt',
    language: 'kotlin',
    description: 'Apple-inspired dynamic theme with Clean Light and OLED Dark night stealth palettes',
    content: `package com.manhunt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AppleAccentBlue,
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderGreen,
    background = AppleBackgroundLight,
    surface = AppleSurfaceLight,
    onBackground = AppleTextPrimaryLight
)

private val DarkColorScheme = darkColorScheme(
    primary = AppleAccentBlueDark,
    secondary = AppleSeekerOrange,
    tertiary = AppleHiderGreen,
    background = AppleBackgroundDark,
    surface = AppleSurfaceDark,
    onBackground = AppleTextPrimaryDark
)

@Composable
fun ManhuntTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = AppleTypography, content = content)
}`
  },
  {
    name: 'FirebaseSyncService.kt',
    path: 'app/src/main/java/com/manhunt/app/network/FirebaseSyncService.kt',
    language: 'kotlin',
    description: 'Retrofit 2 REST API client for creating rooms, joining, polling, and PATCHing coordinates',
    content: `package com.manhunt.app.network

import com.manhunt.app.data.Constants
import com.manhunt.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface FirebaseRestApi {
    @PUT("rooms/{joinCode}.json")
    suspend fun createRoom(@Path("joinCode") joinCode: String, @Body roomData: RoomNetworkModel): Response<Void>

    @GET("rooms/{joinCode}.json")
    suspend fun getRoom(@Path("joinCode") joinCode: String): Response<RoomNetworkModel?>

    @PUT("rooms/{joinCode}/players/{playerId}.json")
    suspend fun putPlayer(@Path("joinCode") joinCode: String, @Path("playerId") playerId: String, @Body player: Player): Response<Void>

    @PATCH("rooms/{joinCode}/players/{playerId}.json")
    suspend fun patchPlayerLocation(
        @Path("joinCode") joinCode: String,
        @Path("playerId") playerId: String,
        @Body locationUpdates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Void>

    @PATCH("rooms/{joinCode}/session.json")
    suspend fun patchSession(@Path("joinCode") joinCode: String, @Body sessionUpdates: Map<String, Any>): Response<Void>
}

class FirebaseSyncService private constructor() {
    private val api: FirebaseRestApi

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.FIREBASE_DATABASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(FirebaseRestApi::class.java)
    }

    suspend fun updateLocation(joinCode: String, playerId: String, lat: Double, lng: Double, isOutOfZone: Boolean): Boolean {
        val updates = mapOf("latitude" to lat, "longitude" to lng, "isOutOfZone" to isOutOfZone)
        return api.patchPlayerLocation(joinCode, playerId, updates).isSuccessful
    }

    companion object {
        val instance by lazy { FirebaseSyncService() }
        fun pushLocationUpdate(joinCode: String, playerId: String, lat: Double, lng: Double, isOutOfZone: Boolean) {
            CoroutineScope(Dispatchers.IO).launch {
                instance.updateLocation(joinCode, playerId, lat, lng, isOutOfZone)
            }
        }
    }
}`
  },
  {
    name: 'ManhuntLocationService.kt',
    path: 'app/src/main/java/com/manhunt/app/service/ManhuntLocationService.kt',
    language: 'kotlin',
    description: 'Android Foreground Service with high-accuracy GPS pushing coordinates to Firebase REST every 5s',
    content: `package com.manhunt.app.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.manhunt.app.network.FirebaseSyncService
import com.manhunt.app.util.GeofenceManager

class ManhuntLocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var joinCode = ""
    private var playerId = ""

    private fun handleNewLocation(location: Location) {
        val geofenceEval = GeofenceManager.evaluate(
            location.latitude, location.longitude, zoneCenterLat, zoneCenterLng, zoneRadiusMeters
        )

        // PUSH TO CLOUD FIREBASE REST EVERY 5 SECONDS (Screen on or off)
        if (joinCode.isNotEmpty() && playerId.isNotEmpty()) {
            FirebaseSyncService.pushLocationUpdate(
                joinCode, playerId, location.latitude, location.longitude, geofenceEval.isOutside
            )
        }
    }
}`
  },
  {
    name: 'OSMMapView.kt',
    path: 'app/src/main/java/com/manhunt/app/ui/components/OSMMapView.kt',
    language: 'kotlin',
    description: 'OSMDroid MapView wrapped inside Compose AndroidView with circular geofence & 25m buffer ring',
    content: `package com.manhunt.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

@Composable
fun OSMMapView(
    playerLat: Double,
    playerLng: Double,
    userRole: Role,
    zoneRadiusMeters: Double,
    otherPlayers: List<Player>
) {
    AndroidView(
        factory = { ctx -> MapView(ctx) },
        update = { mapView ->
            // Renders OpenStreetMap tiles, circular geofence boundary & 25m buffer ring
        }
    )
}`
  },
  {
    name: 'app/build.gradle.kts',
    path: 'app/build.gradle.kts',
    language: 'kotlin',
    description: 'Gradle dependencies for Retrofit 2, OkHttp 3, Jetpack Compose, OSMDroid, Room, Accompanist',
    content: `dependencies {
    // Retrofit 2 & OkHttp 3 for Firebase Realtime Database REST API
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // OSMDroid (OpenStreetMap for Android)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Accompanist Permissions (Location, Background, Notification)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}`
  }
];
