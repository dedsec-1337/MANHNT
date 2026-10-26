package com.manhunt.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.manhunt.app.data.model.AccentMode
import com.manhunt.app.data.model.AppearanceMode
import com.manhunt.app.data.model.GameStatus
import com.manhunt.app.service.ManhuntLocationService
import com.manhunt.app.ui.components.AppBottomBar
import com.manhunt.app.ui.components.AppTab
import com.manhunt.app.ui.screens.*
import com.manhunt.app.ui.theme.ManhuntTheme
import com.manhunt.app.ui.theme.MotionTokens
import com.manhunt.app.util.RefreshRateController
import com.manhunt.app.viewmodel.GameViewModel

private enum class TransientDestination {
    CREATE_ROOM,
    RESULTS
}

private enum class RootDestination {
    HUNT,
    CREW,
    YOU,
    SETTINGS,
    CREATE_ROOM,
    RESULTS
}

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var selectedTab by rememberSaveable { mutableStateOf(AppTab.HUNT) }
            var transientDestination by rememberSaveable { mutableStateOf<TransientDestination?>(null) }
            // Persist as a name so it survives process death; a plain `remember` reset to null after
            // recreation, which made the effect below treat a live match as "just ended" and stop
            // the location service.
            var previousStatusName by rememberSaveable { mutableStateOf<String?>(null) }
            val previousStatus = previousStatusName?.let { name ->
                runCatching { GameStatus.valueOf(name) }.getOrNull()
            }

            ManhuntTheme(settings = uiState.appearanceSettings) {
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
                            } catch (_: Exception) {
                            }
                        }
                    }

                    val hasLocationPermission = permissionsState.permissions.any { permission ->
                        permission.status.isGranted &&
                            (permission.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
                                permission.permission == Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    LaunchedEffect(uiState.session?.status, hasLocationPermission) {
                        val currentStatus = uiState.session?.status
                        // A saved transient destination can outlive the session it belonged to (for
                        // example, process death restores RESULTS while no session exists). Results
                        // renders nothing without a session, which showed as a black screen.
                        if (uiState.session == null && transientDestination == TransientDestination.RESULTS) {
                            transientDestination = null
                        }
                        when (currentStatus) {
                            GameStatus.LOBBY -> {
                                if (previousStatus == null) selectedTab = AppTab.CREW
                            }
                            GameStatus.RUNNING, GameStatus.PAUSED -> {
                                if (previousStatus == null || previousStatus == GameStatus.LOBBY) selectedTab = AppTab.HUNT
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
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Location permission is required for GPS tracking.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            GameStatus.FINISHED -> {
                                transientDestination = TransientDestination.RESULTS
                                stopLocationService()
                            }
                            null -> {
                                if (previousStatus != null && transientDestination != TransientDestination.CREATE_ROOM) {
                                    transientDestination = null
                                    selectedTab = AppTab.HUNT
                                }
                                stopLocationService()
                            }
                        }
                        previousStatusName = currentStatus?.name
                    }

                    LaunchedEffect(uiState.appearanceSettings.highRefreshRateEnabled) {
                        RefreshRateController.apply(
                            window,
                            uiState.appearanceSettings.highRefreshRateEnabled
                        )
                    }

                    LaunchedEffect(uiState.toastMessage) {
                        uiState.toastMessage?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            viewModel.dismissToast()
                        }
                    }

                    val activeMatch = uiState.session?.status == GameStatus.RUNNING ||
                        uiState.session?.status == GameStatus.PAUSED
                    // CREATE_ROOM is valid with no session (that's how you open the create flow).
                    // RESULTS still requires a finished session or it paints a black screen.
                    val resolvedTransientDestination = when {
                        transientDestination == TransientDestination.CREATE_ROOM ->
                            TransientDestination.CREATE_ROOM
                        uiState.session == null -> null
                        transientDestination == TransientDestination.RESULTS &&
                            uiState.session?.status != GameStatus.FINISHED -> null
                        else -> transientDestination
                    }
                    val route = when (resolvedTransientDestination) {
                        TransientDestination.CREATE_ROOM -> RootDestination.CREATE_ROOM
                        TransientDestination.RESULTS -> RootDestination.RESULTS
                        null -> when (selectedTab) {
                            AppTab.HUNT -> RootDestination.HUNT
                            AppTab.CREW -> RootDestination.CREW
                            AppTab.YOU -> RootDestination.YOU
                            AppTab.SETTINGS -> RootDestination.SETTINGS
                        }
                    }
                    val highRefreshRateAvailable =
                        RefreshRateController.hasHighRefreshRate(window.decorView.display)

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (resolvedTransientDestination == null) {
                                AppBottomBar(
                                    selectedTab = selectedTab,
                                    onSelectTab = { selectedTab = it }
                                )
                            }
                        }
                    ) { contentPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding)
                        ) {
                            // Keep the live map composed underneath other tabs so its state survives tab changes.
                            if (activeMatch) {
                                GameScreen(
                                    state = uiState,
                                    headingFlow = viewModel.headingDegrees,
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
                                        transientDestination = null
                                        selectedTab = AppTab.HUNT
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (activeMatch && route != RootDestination.HUNT) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(route) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent().changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                )
                            }

                            AnimatedContent(
                                targetState = route,
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = {
                                    (fadeIn(MotionTokens.smoothTween(MotionTokens.ScreenTransitionMillis)) +
                                        slideInHorizontally(MotionTokens.NavigationOffset) { it / 24 })
                                        .togetherWith(
                                            fadeOut(MotionTokens.smoothTween(MotionTokens.ScreenTransitionMillis)) +
                                                slideOutHorizontally(MotionTokens.NavigationOffset) { -it / 24 }
                                        )
                                },
                                label = "fieldPlayNavigation"
                            ) { destination ->
                                when (destination) {
                                    RootDestination.HUNT -> {
                                        if (activeMatch) {
                                            Box(Modifier.fillMaxSize())
                                        } else {
                                            HomeScreen(
                                                state = uiState,
                                                onCreateRoomClick = {
                                                    transientDestination = TransientDestination.CREATE_ROOM
                                                },
                                                onJoinRoomClick = { code ->
                                                    viewModel.joinRoom(code) { success ->
                                                        if (success) {
                                                            transientDestination = null
                                                            selectedTab = AppTab.CREW
                                                        }
                                                    }
                                                },
                                                onEditProfileClick = { selectedTab = AppTab.YOU }
                                            )
                                        }
                                    }

                                    RootDestination.CREW -> {
                                        val session = uiState.session
                                        if (session == null) {
                                            HomeScreen(
                                                state = uiState,
                                                onCreateRoomClick = {
                                                    transientDestination = TransientDestination.CREATE_ROOM
                                                },
                                                onJoinRoomClick = { code ->
                                                    viewModel.joinRoom(code) { success ->
                                                        if (success) {
                                                            transientDestination = null
                                                            selectedTab = AppTab.CREW
                                                        }
                                                    }
                                                },
                                                onEditProfileClick = { selectedTab = AppTab.YOU }
                                            )
                                        } else {
                                            LobbyScreen(
                                                state = uiState,
                                                headingFlow = viewModel.headingDegrees,
                                                onStartGameClick = { viewModel.startGame() },
                                                onToggleRole = { playerId -> viewModel.togglePlayerRole(playerId) },
                                                onAutoBalanceRoles = { viewModel.autoAssignRoles() },
                                                onLeaveLobbyClick = {
                                                    viewModel.leaveGame()
                                                    transientDestination = null
                                                    selectedTab = AppTab.HUNT
                                                },
                                                isActiveMatch = activeMatch
                                            )
                                        }
                                    }

                                    RootDestination.YOU -> ProfileScreen(
                                        state = uiState,
                                        onSaveProfile = { name, emoji -> viewModel.updateProfile(name, emoji) }
                                    )

                                    RootDestination.SETTINGS -> AppearanceScreen(
                                        settings = uiState.appearanceSettings,
                                        highRefreshRateAvailable = highRefreshRateAvailable,
                                        isBatteryOptimizationExempt = uiState.isBatteryOptimizationExempt,
                                        onAppearanceModeChange = { viewModel.setAppearanceMode(it) },
                                        onAccentModeChange = { viewModel.setAccentMode(it) },
                                        onHighRefreshRateChange = { viewModel.setHighRefreshRateEnabled(it) }
                                    )

                                    RootDestination.CREATE_ROOM -> CreateRoomScreen(
                                        isCreatingRoom = uiState.isCreatingRoom,
                                        onBackClick = { transientDestination = null },
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
                                                if (success) {
                                                    transientDestination = null
                                                    selectedTab = AppTab.CREW
                                                }
                                            }
                                        }
                                    )

                                    RootDestination.RESULTS -> ResultsScreen(
                                        state = uiState,
                                        onBackToHome = {
                                            viewModel.leaveGame()
                                            transientDestination = null
                                            selectedTab = AppTab.HUNT
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationStatus()
        RefreshRateController.apply(
            window,
            viewModel.uiState.value.appearanceSettings.highRefreshRateEnabled
        )
    }

    override fun onPause() {
        RefreshRateController.apply(window, enabled = false)
        super.onPause()
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
