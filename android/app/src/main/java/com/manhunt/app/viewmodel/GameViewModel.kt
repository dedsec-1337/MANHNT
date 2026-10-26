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
        const val PREFERENCE_ACTIVE_JOIN_CODE = "active_join_code"
        const val PREFERENCE_ACTIVE_ROLE = "active_role"
        const val PREFERENCE_APPEARANCE_MODE = "appearance_mode"
        const val PREFERENCE_ACCENT_MODE = "accent_mode"
        const val PREFERENCE_HIGH_REFRESH_RATE = "high_refresh_rate_enabled"
    }
    private val db = (application as ManhuntApp).database
    private val firebaseSync = FirebaseSyncService.instance
    private val compass = CompassSensorManager(application)
    private val hapticManager = HapticFeedbackManager(application)
    private val settingsPreferences = application.getSharedPreferences("manhunt_settings", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(GameUiState(appearanceSettings = loadAppearanceSettings()))
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
        viewModelScope.launch { db.matchHistoryDao().getAllMatchesFlow().collect { matches -> _uiState.update { it.copy(latestMatch = matches.firstOrNull()) } } }
        viewModelScope.launch {
            try {
                val uid = ensureFirebasePlayerIdentity().id
                val profile = db.playerProfileDao().getProfile()
                if (profile != null) {
                    _uiState.update { it.copy(myPlayer = it.myPlayer.copy(id = uid, name = profile.displayName, avatarEmoji = profile.avatarEmoji)) }
                    if (profile.id != uid) db.playerProfileDao().upsertProfile(PlayerProfileEntity(uid, profile.displayName, profile.avatarEmoji))
                } else {
                    val newProfile = PlayerProfileEntity(uid, "Agent ${RandomCode.generateCode(3)}", "🏃")
                    db.playerProfileDao().upsertProfile(newProfile)
                    _uiState.update { it.copy(myPlayer = it.myPlayer.copy(id = uid, name = newProfile.displayName, avatarEmoji = newProfile.avatarEmoji)) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(toastMessage = "Firebase sign-in failed. Check connection and Anonymous Authentication.") }
            }
        }
        viewModelScope.launch { compass.azimuthFlow.collect { _headingDegrees.value = it } }
        viewModelScope.launch { compass.hasHeadingFlow.collect { available -> _uiState.update { it.copy(hasCompassHeading = available) } } }
        viewModelScope.launch {
            _uiState.map { it.session?.status }.distinctUntilChanged().collect { status ->
                if (status == GameStatus.RUNNING || status == GameStatus.PAUSED) compass.startListening() else compass.stopListening()
            }
        }
        viewModelScope.launch { ManhuntLocationService.locationUpdates.collect { loc -> handleLocation(loc.latitude, loc.longitude) } }
        viewModelScope.launch { firebaseSync.roomState.collect { if (it != null) applyRoom(it) } }
        viewModelScope.launch { firebaseSync.roomSignals.collect { signals -> _uiState.update { it.copy(roomSignals = signals) } } }
        viewModelScope.launch { firebaseSync.syncStatus.collect { s -> _uiState.update { it.copy(networkStatus = s) } } }
        viewModelScope.launch { while (isActive) { delay(1000); refreshStealthState() } }
        refreshBatteryOptimizationStatus()
        restoreActiveRoomIfPossible()
    }

    private fun restoreActiveRoomIfPossible() {
        if (_uiState.value.session != null) return
        val savedCode = settingsPreferences.getString(PREFERENCE_ACTIVE_JOIN_CODE, null)?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (firebaseSync.fetchRoomSession(savedCode) == null) { clearPersistedActiveRoom(); return@launch }
            joinRoom(savedCode)
        }
    }
    private fun persistActiveRoom(session: GameSession, role: Role) {
        settingsPreferences.edit().putString(PREFERENCE_ACTIVE_JOIN_CODE, session.joinCode).putString(PREFERENCE_ACTIVE_ROLE, role.name).apply()
    }
    private fun clearPersistedActiveRoom() {
        settingsPreferences.edit().remove(PREFERENCE_ACTIVE_JOIN_CODE).remove(PREFERENCE_ACTIVE_ROLE).apply()
    }
    private suspend fun ensureFirebasePlayerIdentity(): Player = firebaseAuthMutex.withLock {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: throw IllegalStateException("Firebase anonymous sign-in failed")
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
                    onComplete(false); return@launch
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
                    _uiState.update { it.copy(isCreatingRoom = false, toastMessage = "Couldn't create room. Check connection and Firebase rules.") }
                    onComplete(false)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isCreatingRoom = false, toastMessage = "Firebase sign-in failed.") }
                onComplete(false)
            }
        }
    }

    fun joinRoom(code: String, onComplete: (Boolean) -> Unit = {}) {
        val upper = code.trim().uppercase()
        if (upper.length < 4) { _uiState.update { it.copy(toastMessage = "Enter a valid room code") }; onComplete(false); return }
        _uiState.update { it.copy(isJoiningRoom = true) }
        viewModelScope.launch {
            try {
                val authenticatedPlayer = ensureFirebasePlayerIdentity()
                val session = firebaseSync.fetchRoomSession(upper)
                if (session == null) {
                    _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Room $upper not found or unreachable") }
                    onComplete(false); return@launch
                }
                val existingPlayer = firebaseSync.fetchPublicPlayer(upper, authenticatedPlayer.id)
                    ?: firebaseSync.fetchRoomData(upper)?.players?.get(authenticatedPlayer.id)
                val existingCatchCode = if (existingPlayer?.role == Role.HIDER) firebaseSync.fetchCatchCode(upper, authenticatedPlayer.id) ?: "" else ""
                val me = existingPlayer?.copy(name = authenticatedPlayer.name, avatarEmoji = authenticatedPlayer.avatarEmoji, catchCode = existingCatchCode)
                    ?: authenticatedPlayer.copy(role = Role.SEEKER, catchCode = "")
                if (me.role == Role.HIDER && !me.catchCode.matches(Regex("^[0-9]{4}$"))) {
                    _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Your Hider catch code is missing. Ask organizer to reassign role.") }
                    onComplete(false); return@launch
                }
                if (!firebaseSync.joinPlayer(upper, me)) {
                    _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Couldn't join room. Check connection and rules.") }
                    onComplete(false); return@launch
                }
                val room = firebaseSync.fetchRoomData(upper)
                if (room == null) {
                    _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Joined, but room data could not be loaded.") }
                    onComplete(false); return@launch
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
                _uiState.update { it.copy(session = room.session, players = room.players.values.toList(), myPlayer = syncedMe, isOrganizer = room.session.hostPlayerId == me.id, isJoiningRoom = false, currentDynamicRadiusMeters = room.session.currentDynamicZoneRadiusMeters, toastMessage = "Joined ${room.session.roomName}!") }
                onComplete(true)
            } catch (_: Exception) {
                _uiState.update { it.copy(isJoiningRoom = false, toastMessage = "Couldn't join room.") }
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
        if (!enteredCode.trim().matches(Regex("^[0-9]{4}$"))) { onResult(false, "Enter the four-digit catch code"); return }
        viewModelScope.launch {
            val submitted = firebaseSync.sendCatchAttempt(s.joinCode, CatchAttemptPayload(me.id, targetHiderId, enteredCode.trim()))
            if (submitted) {
                _uiState.update { it.copy(toastMessage = "Catch code sent. Waiting for host validation.") }
                onResult(true, "Catch code sent")
            } else {
                _uiState.update { it.copy(toastMessage = "Catch attempt could not sync.") }
                onResult(false, "Could not sync catch attempt")
            }
        }
    }

    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }
    fun dismissToast() = clearToast()
    fun leaveRoom() = leaveGame()
    fun leaveGame() {
        val code = _uiState.value.session?.joinCode
        val me = _uiState.value.myPlayer
        clearPersistedActiveRoom()
        viewModelScope.launch {
            if (code != null) try { firebaseSync.deletePlayer(code, me.id) } catch (_: Exception) {}
            firebaseSync.stopSync()
            _uiState.update { it.copy(session = null, players = emptyList(), isOrganizer = false, nearbyCatchableHider = null, toastMessage = "Left the match") }
        }
    }

    fun togglePlayerRole(playerId: String) {
        if (!_uiState.value.isOrganizer) return
        val p = _uiState.value.players.find { it.id == playerId } ?: return
        val code = _uiState.value.session?.joinCode ?: return
        val next = if (p.role == Role.SEEKER) Role.HIDER else Role.SEEKER
        val nextCatchCode = if (next == Role.HIDER) RandomCode.generateNumericCode(4) else ""
        viewModelScope.launch {
            if (!firebaseSync.setCatchCode(code, p.id, nextCatchCode)) {
                _uiState.update { it.copy(toastMessage = "Could not update private catch code") }; return@launch
            }
            if (!firebaseSync.joinPlayer(code, p.copy(role = next, catchCode = nextCatchCode))) {
                _uiState.update { it.copy(toastMessage = "Could not update role") }
            }
        }
    }

    fun autoAssignRoles(ratio: Int = 3) {
        if (!_uiState.value.isOrganizer) return
        val code = _uiState.value.session?.joinCode ?: return
        val players = _uiState.value.players
        if (players.isEmpty()) return
        viewModelScope.launch {
            players.forEachIndexed { index, p ->
                val role = if (index % (ratio + 1) == 0) Role.SEEKER else Role.HIDER
                val catchCode = if (role == Role.HIDER) RandomCode.generateNumericCode(4) else ""
                firebaseSync.setCatchCode(code, p.id, catchCode)
                firebaseSync.joinPlayer(code, p.copy(role = role, catchCode = catchCode))
            }
        }
    }

    fun startGame() {
        if (!_uiState.value.isOrganizer || startGameInFlight) return
        val s = _uiState.value.session ?: return
        if (s.status != GameStatus.LOBBY) return
        startGameInFlight = true
        viewModelScope.launch {
            try {
                val ok = firebaseSync.updateSession(s.joinCode, mapOf(
                    "status" to GameStatus.RUNNING.name,
                    "remainingSeconds" to s.durationMinutes * 60,
                    "headStartRemainingSeconds" to s.headStartMinutes * 60,
                    "isPaused" to false
                ))
                if (ok) startCountdown() else _uiState.update { it.copy(toastMessage = "Could not start game") }
            } finally { startGameInFlight = false }
        }
    }

    fun toggleSafetyPause() {
        if (!_uiState.value.isOrganizer) return
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            firebaseSync.updateSession(s.joinCode, mapOf("isPaused" to !s.isPaused))
        }
    }

    fun activateStealth() {
        val me = _uiState.value.myPlayer
        if (me.role != Role.HIDER || me.isCaught || _uiState.value.isStealthActive || _uiState.value.stealthCooldownSeconds > 0) return
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            val until = System.currentTimeMillis() + Constants.STEALTH_DURATION_SECONDS * 1000L
            val cooldownUntil = until + Constants.STEALTH_COOLDOWN_SECONDS * 1000L
            firebaseSync.patchPlayer(
                s.joinCode,
                me.id,
                mapOf(
                    "isStealthActive" to true,
                    "stealthUntilEpochMs" to until,
                    "stealthCooldownUntilEpochMs" to cooldownUntil,
                    "stealthActivationRequestedAtEpochMs" to System.currentTimeMillis()
                )
            )
        }
    }

    fun startJailbreakHold() {
        val s = _uiState.value.session ?: return
        if (!s.jailEnabled || s.isJailbreakInProgress) return
        val me = _uiState.value.myPlayer
        if (me.role != Role.HIDER || me.isCaught || me.isInJail) return
        viewModelScope.launch {
            firebaseSync.patchPlayer(
                s.joinCode,
                me.id,
                mapOf("jailbreakHoldStartedAtEpochMs" to System.currentTimeMillis())
            )
        }
    }

    fun updateProfile(name: String, emoji: String) {
        viewModelScope.launch {
            val uid = _uiState.value.myPlayer.id
            db.playerProfileDao().upsertProfile(PlayerProfileEntity(uid, name, emoji))
            _uiState.update { it.copy(myPlayer = it.myPlayer.copy(name = name, avatarEmoji = emoji)) }
        }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        settingsPreferences.edit().putString(PREFERENCE_APPEARANCE_MODE, mode.name).apply()
        _uiState.update { it.copy(appearanceSettings = it.appearanceSettings.copy(appearanceMode = mode)) }
    }

    fun setAccentMode(mode: AccentMode) {
        settingsPreferences.edit().putString(PREFERENCE_ACCENT_MODE, mode.name).apply()
        _uiState.update { it.copy(appearanceSettings = it.appearanceSettings.copy(accentMode = mode)) }
    }

    fun setHighRefreshRateEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(PREFERENCE_HIGH_REFRESH_RATE, enabled).apply()
        _uiState.update { it.copy(appearanceSettings = it.appearanceSettings.copy(highRefreshRateEnabled = enabled)) }
    }

    fun refreshBatteryOptimizationStatus() {
        _uiState.update { it.copy(isBatteryOptimizationExempt = BatteryOptimizationHelper.isExempt(getApplication())) }
    }

    private fun loadAppearanceSettings(): AppearanceSettings {
        val appearance = settingsPreferences.getString(PREFERENCE_APPEARANCE_MODE, AppearanceMode.SYSTEM.name)
            ?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: AppearanceMode.SYSTEM
        val accent = settingsPreferences.getString(PREFERENCE_ACCENT_MODE, AccentMode.BLUE.name)
            ?.let { runCatching { AccentMode.valueOf(it) }.getOrNull() } ?: AccentMode.BLUE
        val highRefresh = settingsPreferences.getBoolean(PREFERENCE_HIGH_REFRESH_RATE, false)
        return AppearanceSettings(appearanceMode = appearance, accentMode = accent, highRefreshRateEnabled = highRefresh)
    }

    fun sendQuickSignal(code: String) {
        val s = _uiState.value.session ?: return
        val now = System.currentTimeMillis()
        if (now - lastQuickSignalSentAtEpochMs < Constants.GAME_SIGNAL_SEND_COOLDOWN_MS) return
        lastQuickSignalSentAtEpochMs = now
        viewModelScope.launch {
            firebaseSync.sendQuickSignal(
                s.joinCode,
                _uiState.value.myPlayer.id,
                GameSignalPayload(code = code, timestampEpochMs = now)
            )
        }
    }

    private fun handleLocation(lat: Double, lng: Double) {
        if (!GeoUtils.hasValidCoordinates(lat, lng)) return
        val state = _uiState.value
        val s = state.session ?: return
        val me = state.myPlayer
        val out = if (!s.isUnlimitedZone && s.zoneRadiusMeters > 0) {
            GeoUtils.distanceMeters(lat, lng, s.zoneCenterLat, s.zoneCenterLng) > s.currentDynamicZoneRadiusMeters
        } else false
        _uiState.update {
            it.copy(
                myPlayer = me.copy(latitude = lat, longitude = lng, isOutOfZone = out, lastUpdatedEpochMs = System.currentTimeMillis()),
                isOutOfZone = out
            )
        }
        viewModelScope.launch {
            firebaseSync.updateLocation(s.joinCode, me.id, lat, lng, out)
        }
        recalculateDistancesAndProximity()
    }

    private fun recalculateDistancesAndProximity() {
        val state = _uiState.value
        val me = state.myPlayer
        if (!GeoUtils.hasValidCoordinates(me.latitude, me.longitude)) return
        val hiders = state.players.filter { it.id != me.id && it.role == Role.HIDER && !it.isCaught }
        fun isActuallyInSafeZone(player: Player): Boolean {
            if (!GeoUtils.hasValidCoordinates(player.latitude, player.longitude)) return false
            return state.session?.safeZones?.any { zone ->
                GeoUtils.distanceMeters(player.latitude, player.longitude, zone.latitude, zone.longitude) <= zone.radiusMeters
            } == true
        }
        if (me.role == Role.SEEKER) {
            val catchable = hiders.firstOrNull { hider -> !isActuallyInSafeZone(hider) && hider.immunityUntilEpochMs <= System.currentTimeMillis() }
            val protected = hiders.firstOrNull { isActuallyInSafeZone(it) }
            _uiState.update {
                it.copy(
                    nearbyCatchableHider = catchable,
                    safeZoneNotice = protected?.let { h -> "${h.name} is protected in ${h.safeZoneName?.takeIf { n -> n.isNotBlank() } ?: "Safe Zone"}!" }
                )
            }
        } else if (me.role == Role.HIDER) {
            val seekers = state.players.filter { it.role == Role.SEEKER }
            var nearest = -1.0
            var bearing = 0f
            for (seeker in seekers) {
                if (!GeoUtils.hasValidCoordinates(seeker.latitude, seeker.longitude)) continue
                val d = GeoUtils.distanceMeters(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                if (nearest < 0 || d < nearest) {
                    nearest = d
                    bearing = GeoUtils.bearingDegrees(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                }
            }
            _uiState.update { it.copy(nearestSeekerDistance = nearest, nearestSeekerBearing = bearing) }
        }
    }

    private fun applyRoom(room: RoomNetworkModel) {
        val state = _uiState.value
        val serverMe = room.players[state.myPlayer.id]
        val localMe = state.myPlayer
        val me = when {
            serverMe == null -> localMe
            !GeoUtils.hasValidCoordinates(serverMe.latitude, serverMe.longitude) -> serverMe.copy(latitude = localMe.latitude, longitude = localMe.longitude, isOutOfZone = localMe.isOutOfZone, isInSafeZone = localMe.isInSafeZone, safeZoneName = localMe.safeZoneName, lastUpdatedEpochMs = localMe.lastUpdatedEpochMs)
            serverMe.lastUpdatedEpochMs < localMe.lastUpdatedEpochMs -> serverMe.copy(latitude = localMe.latitude, longitude = localMe.longitude, isOutOfZone = localMe.isOutOfZone, isInSafeZone = localMe.isInSafeZone, safeZoneName = localMe.safeZoneName, lastUpdatedEpochMs = localMe.lastUpdatedEpochMs)
            else -> serverMe
        }
        val session = room.session
        _uiState.update {
            it.copy(
                session = session,
                players = room.players.values.toList(),
                myPlayer = me,
                isOrganizer = session.hostPlayerId == me.id,
                currentDynamicRadiusMeters = session.currentDynamicZoneRadiusMeters,
                isSeekerFrozen = me.role == Role.SEEKER && session.headStartRemainingSeconds > 0,
                headStartCountdownSeconds = session.headStartRemainingSeconds,
                isAutoPingActive = session.isAutoPingActive,
                autoPingCountdownSeconds = session.autoPingRemainingSeconds,
                isInJail = me.isInJail,
                isInSafeZone = me.isInSafeZone,
                currentSafeZoneName = me.safeZoneName
            )
        }
        room.lastCatchAttempt?.let { attempt ->
            if (state.isOrganizer) processCatchAttempt(attempt)
        }
        recalculateDistancesAndProximity()
    }

    private fun processCatchAttempt(attempt: CatchAttemptPayload) {
        if (!_uiState.value.isOrganizer) return
        if (attempt.timestamp <= lastHandledCatchAttemptTimestamp) return
        val target = _uiState.value.players.find { it.id == attempt.targetHiderId } ?: return
        val targetInSafeZone = target.isInSafeZone
        // Distance is not enforced — catch code is the real-world tag proof.
        if (targetInSafeZone || target.catchCode.trim() != attempt.enteredCode.trim()) {
            lastHandledCatchAttemptTimestamp = attempt.timestamp
            return
        }
        lastHandledCatchAttemptTimestamp = attempt.timestamp
        val code = _uiState.value.session?.joinCode ?: return
        viewModelScope.launch {
            firebaseSync.joinPlayer(code, target.copy(isCaught = true))
            firebaseSync.publishCatchAck(
                code,
                CatchAckPayload(
                    seekerId = attempt.seekerId,
                    targetHiderId = attempt.targetHiderId,
                    targetName = target.name,
                    accepted = true,
                    timestamp = attempt.timestamp
                )
            )
        }
    }

    private fun refreshStealthState() {
        val now = System.currentTimeMillis()
        val me = _uiState.value.myPlayer
        val stealthActive = me.isStealthActive && now < me.stealthUntilEpochMs
        val cooldown = if (me.stealthCooldownUntilEpochMs > now) ((me.stealthCooldownUntilEpochMs - now) / 1000).toInt() else 0
        _uiState.update { it.copy(isStealthActive = stealthActive, stealthCooldownSeconds = cooldown) }
    }

    private fun startCountdown() {
        countdownTimerJob?.cancel()
        countdownTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.session ?: break
                if (s.status != GameStatus.RUNNING || s.isPaused) continue
                val remaining = (s.remainingSeconds - 1).coerceAtLeast(0)
                val head = (s.headStartRemainingSeconds - 1).coerceAtLeast(0)
                firebaseSync.updateSession(s.joinCode, mapOf(
                    "remainingSeconds" to remaining,
                    "headStartRemainingSeconds" to head
                ))
                if (remaining <= 0) {
                    endGame(Role.HIDER)
                    break
                }
            }
        }
    }

    private fun endGame(winner: Role) {
        val s = _uiState.value.session ?: return
        val catches = _uiState.value.players.count { it.isCaught }
        viewModelScope.launch {
            firebaseSync.updateSession(s.joinCode, mapOf(
                "status" to GameStatus.FINISHED.name,
                "winnerRole" to winner.name,
                "totalCatches" to catches
            ))
            db.matchHistoryDao().insertMatch(MatchHistoryEntity(
                roomName = s.roomName,
                joinCode = s.joinCode,
                winnerRole = winner.name,
                durationSeconds = (s.durationMinutes * 60) - s.remainingSeconds,
                catchesCount = catches
            ))
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownTimerJob?.cancel()
        compass.stopListening()
    }
}
