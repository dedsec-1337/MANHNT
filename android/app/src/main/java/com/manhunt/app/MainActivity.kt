package com.manhunt.app

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
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.manhunt.app.data.model.GameStatus
import com.manhunt.app.service.ManhuntLocationService
import com.manhunt.app.ui.screens.*
import com.manhunt.app.ui.theme.ManhuntTheme
import com.manhunt.app.viewmodel.GameViewModel

enum class Screen {
    HOME,
    CREATE_ROOM,
    LOBBY,
    GAME,
    RESULTS,
    PROFILE
}

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var currentScreen by remember { mutableStateOf(Screen.HOME) }

            ManhuntTheme(darkTheme = uiState.isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val permissionsList = remember {
                        buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsList)

                    LaunchedEffect(Unit) {
                        if (!permissionsState.allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                        ManhuntLocationService.createBatteryOptimizationIntent(this@MainActivity)?.let { intent ->
                            try {
                                startActivity(intent)
                            } catch (ignored: Exception) {}
                        }
                    }

                    val hasLocationPermission = permissionsState.permissions.any { permission ->
                        permission.status.isGranted &&
                            (permission.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
                                permission.permission == Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    LaunchedEffect(uiState.session?.status, hasLocationPermission) {
                        when (uiState.session?.status) {
                            GameStatus.LOBBY -> currentScreen = Screen.LOBBY
                            GameStatus.RUNNING, GameStatus.PAUSED -> {
                                currentScreen = Screen.GAME
                                if (hasLocationPermission) {
                                    startLocationService(
                                        remainingSeconds = uiState.session?.remainingSeconds ?: 1800,
                                        isPaused = uiState.session?.isPaused ?: false,
                                        zoneLat = uiState.session?.zoneCenterLat ?: 0.0,
                                        zoneLng = uiState.session?.zoneCenterLng ?: 0.0,
                                        zoneRadius = uiState.session?.zoneRadiusMeters ?: 500.0,
                                        joinCode = uiState.session?.joinCode ?: "",
                                        playerId = uiState.myPlayer.id
                                    )
                                } else {
                                    Toast.makeText(this@MainActivity, "Location permission is required for GPS tracking.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            GameStatus.FINISHED -> {
                                currentScreen = Screen.RESULTS
                                stopLocationService()
                            }
                            null -> {
                                if (currentScreen != Screen.PROFILE && currentScreen != Screen.CREATE_ROOM) {
                                    currentScreen = Screen.HOME
                                }
                                stopLocationService()
                            }
                        }
                    }

                    LaunchedEffect(uiState.toastMessage) {
                        uiState.toastMessage?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            viewModel.dismissToast()
                        }
                    }

                    Crossfade(targetState = currentScreen, label = "screenTransition") { screen ->
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                state = uiState,
                                onCreateRoomClick = { currentScreen = Screen.CREATE_ROOM },
                                onJoinRoomClick = { code ->
                                    viewModel.joinRoom(code) { success ->
                                        if (success) currentScreen = Screen.LOBBY
                                    }
                                },
                                onEditProfileClick = { currentScreen = Screen.PROFILE }
                            )

                            Screen.CREATE_ROOM -> CreateRoomScreen(
                                isCreatingRoom = uiState.isCreatingRoom,
                                onBackClick = { currentScreen = Screen.HOME },
                                onCreateRoomSubmit = { name, duration, zonePreset, headStart, shrinking, autoPing, safeZones ->
                                    viewModel.createRoom(
                                        roomName = name,
                                        durationMinutes = duration,
                                        zoneSizePreset = zonePreset,
                                        headStartMinutes = headStart,
                                        isShrinkingZoneEnabled = shrinking,
                                        autoPingIntervalMinutes = autoPing,
                                        safeZones = safeZones
                                    ) { success ->
                                        if (success) currentScreen = Screen.LOBBY
                                    }
                                }
                            )

                            Screen.LOBBY -> LobbyScreen(
                                state = uiState,
                                onStartGameClick = { viewModel.startGame() },
                                onToggleRole = { playerId -> viewModel.togglePlayerRole(playerId) },
                                onAutoBalanceRoles = { viewModel.autoAssignRoles() },
                                onLeaveLobbyClick = {
                                    viewModel.leaveGame()
                                    currentScreen = Screen.HOME
                                }
                            )

                            Screen.GAME -> GameScreen(
                                state = uiState,
                                onToggleSafetyPause = { viewModel.toggleSafetyPause() },
                                onAttemptCatch = { targetHiderId, code ->
                                    viewModel.attemptCatch(targetHiderId, code) { _, _ -> }
                                },
                                onActivateStealth = { viewModel.activateStealth() },
                                onStartJailbreak = { viewModel.startJailbreakHold() },
                                onSendQuickSignal = { code -> viewModel.sendQuickSignal(code) },
                                onLeaveGame = {
                                    viewModel.leaveGame()
                                    stopLocationService()
                                    currentScreen = Screen.HOME
                                }
                            )

                            Screen.RESULTS -> ResultsScreen(
                                state = uiState,
                                onBackToHome = {
                                    viewModel.leaveGame()
                                    currentScreen = Screen.HOME
                                }
                            )

                            Screen.PROFILE -> ProfileScreen(
                                state = uiState,
                                onSaveProfile = { name, emoji -> viewModel.updateProfile(name, emoji) },
                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                onClose = { currentScreen = Screen.HOME }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startLocationService(
        remainingSeconds: Int,
        isPaused: Boolean,
        zoneLat: Double,
        zoneLng: Double,
        zoneRadius: Double,
        joinCode: String,
        playerId: String
    ) {
        val intent = Intent(this, ManhuntLocationService::class.java).apply {
            action = ManhuntLocationService.ACTION_START
            putExtra(ManhuntLocationService.EXTRA_REMAINING_SECONDS, remainingSeconds)
            putExtra(ManhuntLocationService.EXTRA_IS_PAUSED, isPaused)
            putExtra(ManhuntLocationService.EXTRA_ZONE_LAT, zoneLat)
            putExtra(ManhuntLocationService.EXTRA_ZONE_LNG, zoneLng)
            putExtra(ManhuntLocationService.EXTRA_ZONE_RADIUS, zoneRadius)
            putExtra(ManhuntLocationService.EXTRA_JOIN_CODE, joinCode)
            putExtra(ManhuntLocationService.EXTRA_PLAYER_ID, playerId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLocationService() {
        val intent = Intent(this, ManhuntLocationService::class.java).apply {
            action = ManhuntLocationService.ACTION_STOP
        }
        startService(intent)
    }
}
