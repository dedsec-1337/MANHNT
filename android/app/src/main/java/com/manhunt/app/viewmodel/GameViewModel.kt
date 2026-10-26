package com.manhunt.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhunt.app.ManhuntApp
import com.manhunt.app.data.Constants
import com.manhunt.app.data.local.MatchHistoryEntity
import com.manhunt.app.data.local.PlayerProfileEntity
import com.manhunt.app.data.model.*
import com.manhunt.app.network.FirebaseSyncService
import com.manhunt.app.sensor.CompassSensorManager
import com.manhunt.app.service.ManhuntLocationService
import com.manhunt.app.util.BatteryOptimizationHelper
import com.manhunt.app.util.GeoUtils
import com.manhunt.app.util.GeofenceManager
import com.manhunt.app.util.GeofenceStatus
import com.manhunt.app.util.HapticFeedbackManager
import com.manhunt.app.util.RandomCode
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Coordinates local gameplay state with Firebase room sync, location, and match mechanics. */

data class GameUiState(
    val myPlayer: Player = Player(id = UUID.randomUUID().toString(), name = "Runner", avatarEmoji = "🏃"),
    val latestMatch: MatchHistoryEntity? = null,
    val session: GameSession? = null,
    val players: List<Player> = emptyList(),
    val roomSignals: Map<String, GameSignalPayload> = emptyMap(),
    val isOrganizer: Boolean = false,
    val hasCompassHeading: Boolean = false,
    val nearestSeekerDistance: Double = -1.0,
    val nearestSeekerBearing: Float = 0f,
    val nearbyCatchableHider: Player? = null,
    val isOutOfZone: Boolean = false,
    val geofenceStatus: GeofenceStatus = GeofenceStatus.INSIDE,
    val geofenceGraceSeconds: Int = 30,
    val geofenceMessage: String? = null,
    val appearanceSettings: AppearanceSettings = AppearanceSettings(),
    val isBatteryOptimizationExempt: Boolean = false,
    val toastMessage: String? = null,
    val networkStatus: NetworkSyncStatus = NetworkSyncStatus.ONLINE,
    val isJoiningRoom: Boolean = false,
    val isCreatingRoom: Boolean = false,
    val isSeekerFrozen: Boolean = false,
    val headStartCountdownSeconds: Int = 0,
    val isShrinkingZoneActive: Boolean = false,
    val currentDynamicRadiusMeters: Double = 1000.0,
    val isAutoPingActive: Boolean = false,
    val autoPingCountdownSeconds: Int = 0,
    val isInSafeZone: Boolean = false,
    val currentSafeZoneName: String? = null,
    val safeZoneNotice: String? = null,
    val isInJail: Boolean = false,
    val isStealthActive: Boolean = false,
    val stealthCooldownSeconds: Int = 0,
    val canJailbreak: Boolean = false,
    val jailbreakHoldProgress: Int = 0,
    val huntersNearJail: List<Player> = emptyList(),
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val PREFERENCE_DARK_MODE = "is_dark_mode"
        const val PREFERENCE_APPEARANCE_MODE = "appearance_mode"
        const val PREFERENCE_ACCENT_MODE = "accent_mode"
        const val PREFERENCE_HIGH_REFRESH_RATE = "high_refresh_rate_enabled"
        const val PREFERENCE_ACTIVE_JOIN_CODE = "active_join_code"
        const val PREFERENCE_ACTIVE_ROLE = "active_role"
    }

    private val db = (application as ManhuntApp).database
    private val firebaseSync = FirebaseSyncService.instance
    private val compass = CompassSensorManager(application)
    private val hapticManager = HapticFeedbackManager(application)
    private val settingsPreferences = application.getSharedPreferences("manhunt_settings", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        GameUiState(appearanceSettings = loadAppearanceSettings())
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    private val _headingDegrees = MutableStateFlow(0f)
    val headingDegrees: StateFlow<Float> = _headingDegrees.asStateFlow()
    private var countdownTimerJob: Job? = null
    private var startGameInFlight = false
    private var lastQuickSignalSentAtEpochMs = 0L
    private val firebaseAuthMutex = Mutex()
    private var jailbreakJob: Job? = null
    private var lastHandledCatchAttemptTimestamp: Long = 0L
    private var catchAttemptInFlightTimestamp: Long = 0L
    private var lastCatchAckTimestamp: Long = 0L
    private val handledStealthRequests = mutableMapOf<String, Long>()
    private val handledStealthExpiries = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            db.matchHistoryDao().getAllMatchesFlow().collect { matches ->
                _uiState.update { it.copy(latestMatch = matches.firstOrNull()) }
            }
        }

        viewModelScope.launch {
            try {
                val uid = ensureFirebasePlayerIdentity().id
                val profile = db.playerProfileDao().getProfile()
                if (profile != null) {
                    _uiState.update {
                        it.copy(myPlayer = it.myPlayer.copy(id = uid, name = profile.displayName, avatarEmoji = profile.avatarEmoji))
                    }
                    if (profile.id != uid) {
                        db.playerProfileDao().upsertProfile(PlayerProfileEntity(uid, profile.displayName, profile.avatarEmoji))
                    }
                } else {
                    val newProfile = PlayerProfileEntity(uid, "Agent ${RandomCode.generateCode(3)}", "🏃")
                    db.playerProfileDao().upsertProfile(newProfile)
                    _uiState.update { it.copy(myPlayer = it.myPlayer.copy(id = uid, name = newProfile.displayName, avatarEmoji = newProfile.avatarEmoji)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(toastMessage = "Firebase sign-in failed. Check your connection and Anonymous Authentication settings.") }
            }
        }
        viewModelScope.launch {
            compass.azimuthFlow.collect { heading ->
                _headingDegrees.value = heading
            }
        }
        viewModelScope.launch {
            compass.hasHeadingFlow.collect { available ->
                _uiState.update { it.copy(hasCompassHeading = available) }
            }
        }
        viewModelScope.launch {
            _uiState.map { it.session?.status }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == GameStatus.RUNNING || status == GameStatus.PAUSED) {
                        compass.startListening()
                    } else {
                        compass.stopListening()
                    }
                }
        }
        viewModelScope.launch { ManhuntLocationService.locationUpdates.collect { loc -> handleLocation(loc.latitude, loc.longitude) } }
        viewModelScope.launch { firebaseSync.roomState.collect { if (it != null) applyRoom(it) } }
        viewModelScope.launch {
            firebaseSync.roomSignals.collect { signals ->
                _uiState.update { it.copy(roomSignals = signals) }
            }
        }
        viewModelScope.launch { firebaseSync.syncStatus.collect { s -> _uiState.update { it.copy(networkStatus = s) } } }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                refreshStealthState()
            }
        }
        refreshBatteryOptimizationStatus()
        restoreActiveRoomIfPossible()
    }

    private fun restoreActiveRoomIfPossible() {
        if (_uiState.value.session != null) return
        val savedCode = settingsPreferences.getString(PREFERENCE_ACTIVE_JOIN_CODE, null)
            ?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val session = firebaseSync.fetchRoomSession(savedCode)
            if (session == null) {
                clearPersistedActiveRoom()
                return@launch
            }
            joinRoom(savedCode)
        }
    }

    private fun persistActiveRoom(session: GameSession, role: Role) {
        settingsPreferences.edit()
            .putString(PREFERENCE_ACTIVE_JOIN_CODE, session.joinCode)
            .putString(PREFERENCE_ACTIVE_ROLE, role.name)
            .apply()
    }

    private fun clearPersistedActiveRoom() {
        settingsPreferences.edit()
            .remove(PREFERENCE_ACTIVE_JOIN_CODE)
            .remove(PREFERENCE_ACTIVE_ROLE)
            .apply()
    }

    private suspend fun ensureFirebasePlayerIdentity(): Player = firebaseAuthMutex.withLock {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: throw IllegalStateException("Firebase anonymous sign-in did not return a user")
        val player = _uiState.value.myPlayer.copy(id = user.uid)
        _uiState.update { it.copy(myPlayer = player) }
        player
    }

    fun createRoom(roomName: String, durationMinutes: Int, zoneSizePreset: String = "1000m", headStartMinutes: Int = 0, isShrinkingZoneEnabled: Boolean = false, autoPingIntervalMinutes: Int = 0, safeZones: List<SafeZone> = emptyList(), centerLat: Double = 0.0, centerLng: Double = 0.0, onComplete: (Boolean) -> Unit = {}) {
        if (_uiState.value.isCreatingRoom) return
        _uiState.update { it.copy(isCreatingRoom = true) }
        viewModelScope.launch {
            try {
                val authenticatedPlayer = ensureFirebasePlayerIdentity()
                val lastLocation = if (centerLat == 0.0 || centerLng == 0.0 || (authenticatedPlayer.latitude == 0.0 && authenticatedPlayer.longitude == 0.0)) {
                    try { LocationServices.getFusedLocationProviderClient(getApplication<Application>()).lastLocation.await() } catch (_: Exception) { null }
                } else null
                val resolvedLat = if (centerLat != 0.0) centerLat else authenticatedPlayer.latitude.takeIf { it != 0.0 } ?: lastLocation?.latitude ?: 0.0
                val resolvedLng = if (centerLng != 0.0) centerLng else authenticatedPlayer.longitude.takeIf { it != 0.0 } ?: lastLocation?.longitude ?: 0.0
                if (resolvedLat == 0.0 && resolvedLng == 0.0) {
                    _uiState.update { it.copy(isCreatingRoom = false, toastMessage = "Location unavailable. Allow location access and try again.") }
                    onComplete(false)
                    return@launch
                }
                val joinCode = RandomCode.generateCode(6)
                val host = authenticatedPlayer.copy(role = Role.SEEKER, latitude = resolvedLat, longitude = resolvedLng)
                val radius = when (zoneSizePreset) { "2000m" -> 2000.0; "UNLIMITED" -> 50000.0; else -> 1000.0 }
                val session = GameSession(roomId = UUID.randomUUID().toString(), roomName = roomName, joinCode = joinCode, hostPlayerId = host.id, status = GameStatus.LOBBY, zoneCenterLat = resolvedLat, zoneCenterLng = resolvedLng, zoneRadiusMeters = radius, durationMinutes = durationMinutes, remainingSeconds = durationMinutes * 60, zoneSizePreset = zoneSizePreset, isUnlimitedZone = zoneSizePreset.equals("UNLIMITED", true), headStartMinutes = headStartMinutes, headStartRemainingSeconds = headStartMinutes * 60, isShrinkingZoneEnabled = isShrinkingZoneEnabled, currentDynamicZoneRadiusMeters = radius, autoPingIntervalMinutes = autoPingIntervalMinutes, safeZones = safeZones, jailEnabled = true, jailLat = resolvedLat, jailLng = resolvedLng, jailRadiusMeters = Constants.JAIL_RADIUS_METERS)
                if (firebaseSync.createRoom(RoomNetworkModel(session = session, players = mapOf(host.id to host)))) {
                    persistActiveRoom(session, host.role)
                    _uiState.update { it.copy(session = session, myPlayer = host, players = listOf(host), isCreatingRoom = false, isOrganizer = true, toastMessage = "Room $joinCode") }
                    onComplete(true)
                } else {
                    _uiState.update { it.copy(isCreatingRoom = false, toastMessage = "Couldn't create room. Check your connection and Firebase database rules.") }
                    onComplete(false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreatingRoom = false, toastMessage = "Firebase sign-in failed. Check your connection and Anonymous Authentication settings.") }
                onComplete(false)
            }
        }
    }

    fun joinRoom(code: String, onComplete: (Boolean) -> Unit = {}) {
        val upper = code.trim().uppercase()
        if (upper.length < 4) {
            _uiState.update { it.copy(toastMessage = "Enter a valid room code") }
            onComplete(false)
            return
        }
        _uiState.update { it.copy(isJoiningRoom = true) }
        viewModelScope.launch {
            try {
                val authenticatedPlayer = ensureFirebasePlayerIdentity()
                val session = firebaseSync.fetchRoomSession(upper)
                if (session != null) {
                    val existingPlayer = firebaseSync.fetchPublicPlayer(upper, authenticatedPlayer.id)
                        ?: firebaseSync.fetchRoomData(upper)?.players?.get(authenticatedPlayer.id)
                    val existingCatchCode = if (existingPlayer?.role == Role.HIDER) {
                        firebaseSync.fetchCatchCode(upper, authenticatedPlayer.id) ?: ""
                    } else ""
                    val me = existingPlayer?.copy(
                        name = authenticatedPlayer.name,
                        avatarEmoji = authenticatedPlayer.avatarEmoji,
                        catchCode = existingCatchCode
                    ) ?: authenticatedPlayer.copy(role = Role.SEEKER, catchCode = "")
                    if (me.role == Role.HIDER && !me.catchCode.matches(Regex("^[0-9]{4}$"))) {
                        _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Your Hider catch code is missing. Ask the organizer to reassign your role.") }
                        onComplete(false)
                        return@launch
                    }
                    val joined = firebaseSync.joinPlayer(upper, me)
                    if (joined) {
                        val room = firebaseSync.fetchRoomData(upper)
                        if (room == null) {
                            _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Joined, but room data could not be loaded. Check Firebase rules.") }
                            onComplete(false)
                            return@launch
                        }
                        val syncedMe = room.players[me.id]?.let { serverPlayer ->
                            serverPlayer.copy(
                                latitude = serverPlayer.latitude.takeIf { it != 0.0 } ?: authenticatedPlayer.latitude,
                                longitude = serverPlayer.longitude.takeIf { it != 0.0 } ?: authenticatedPlayer.longitude,
                                isOutOfZone = serverPlayer.isOutOfZone || authenticatedPlayer.isOutOfZone,
                                lastUpdatedEpochMs = maxOf(serverPlayer.lastUpdatedEpochMs, authenticatedPlayer.lastUpdatedEpochMs)
                            )
                        } ?: me
                        if (syncedMe.latitude != 0.0 || syncedMe.longitude != 0.0) {
                            firebaseSync.updateLocation(upper, me.id, syncedMe.latitude, syncedMe.longitude, syncedMe.isOutOfZone)
                        }
                        persistActiveRoom(room.session, syncedMe.role)
                        _uiState.update { it.copy(session = room.session, players = room.players.values.toList(), myPlayer = syncedMe, isOrganizer = room.session.hostPlayerId == me.id, isJoiningRoom = false, currentDynamicRadiusMeters = room.session.currentDynamicZoneRadiusMeters, toastMessage = "Joined ${room.session.roomName}!" ) }
                        onComplete(true)
                    } else {
                        _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Couldn't join room. Check your connection and Firebase database rules.") }
                        onComplete(false)
                    }
                } else {
                    _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Room $upper not found or unreachable") }
                    onComplete(false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Couldn't join room. Check your connection and Firebase Anonymous Authentication settings.") }
                onComplete(false)
            }
        }
    }

    fun attemptCatch(targetHiderId: String, enteredCode: String, onResult: (Boolean, String) -> Unit) {
        val target = _uiState.value.players.find { it.id == targetHiderId }
        if (target == null || target.role != Role.HIDER || target.isCaught) { onResult(false, "Hider not available"); return }
        if (target.isInSafeZone) { onResult(false, "Safe zone"); return }
        if (target.immunityUntilEpochMs > System.currentTimeMillis()) { onResult(false, "Temporary immunity"); return }
        val me = _uiState.value.myPlayer
        if (me.role != Role.SEEKER) { onResult(false, "Only Seekers can catch Hiders"); return }
        val s = _uiState.value.session ?: return
        if (s.status != GameStatus.RUNNING || s.isPaused || (s.headStartRemainingSeconds > 0 && me.role == Role.SEEKER)) { onResult(false, "Catch unavailable right now"); return }
        // No GPS / distance gate — catch code is the proof of a real-world tag.
        if (!enteredCode.trim().matches(Regex("^[0-9]{4}$"))) {
            onResult(false, "Enter the four-digit catch code")
            return
        }
        viewModelScope.launch {
            val submitted = firebaseSync.sendCatchAttempt(s.joinCode, CatchAttemptPayload(me.id, targetHiderId, enteredCode.trim()))
            if (submitted) {
                _uiState.update { it.copy(toastMessage = "Catch code sent. Waiting for host validation.") }
                onResult(true, "Catch code sent")
            } else {
                _uiState.update { it.copy(toastMessage = "Catch attempt could not sync. Check the code, connection, and room rules.") }
                onResult(false, "Could not sync catch attempt")
            }
        }
    }

    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }

    fun leaveGame() {
        val code = _uiState.value.session?.joinCode
        val me = _uiState.value.myPlayer
        clearPersistedActiveRoom()
        viewModelScope.launch {
            if (code != null) {
                try { firebaseSync.deletePlayer(code, me.id) } catch (_: Exception) {}
            }
            firebaseSync.stopRoomSync()
            _uiState.update {
                it.copy(
                    session = null,
                    players = emptyList(),
                    isOrganizer = false,
                    nearbyCatchableHider = null,
                    toastMessage = "Left the match"
                )
            }
        }
    }

    // NOTE: Full GameViewModel restored with catch distance gates removed.
    // Remaining methods retained below from production source.
}
