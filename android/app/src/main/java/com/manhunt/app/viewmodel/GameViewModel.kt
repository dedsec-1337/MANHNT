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
import com.manhunt.app.util.BleNearbyAssist
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
    val nearbyBlePlayerIds: List<String> = emptyList(),
    val bleStatusMessage: String? = null,
    /** Last reveal snapshots for map pins (frozen until next auto-ping). */
    val frozenRevealLocations: Map<String, LocationRevealSnapshot> = emptyMap(),
    val lastRevealEpochMs: Long = 0L,
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
    private val bleAssist = BleNearbyAssist(application.applicationContext)
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
    private var wasAutoPingActive: Boolean = false

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

        viewModelScope.launch {
            bleAssist.nearbyPlayers.collect { list ->
                _uiState.update { it.copy(nearbyBlePlayerIds = list.map { n -> n.playerId }) }
            }
        }
        viewModelScope.launch {
            bleAssist.statusMessage.collect { msg ->
                _uiState.update { it.copy(bleStatusMessage = msg) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                bleAssist.pruneStale()
                syncBleMode()
            }
        }
        refreshBatteryOptimizationStatus()
        restoreActiveRoomIfPossible()
    }

    /**
     * Restores the last room after process death so a running match is not silently dropped.
     * The Firebase UID, not the join code, identifies the player, so this is safe to re-run.
     */
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
                    // The room tree is unreadable before membership, so fetchRoomData() returns null
                    // for a first-time joiner. Read the single public player record instead, which is
                    // readable by UID, so a rejoin keeps the role and catch code already assigned.
                    val existingPlayer = firebaseSync.fetchPublicPlayer(upper, authenticatedPlayer.id)
                        ?: firebaseSync.fetchRoomData(upper)?.players?.get(authenticatedPlayer.id)
                    // The public player record intentionally omits the private catch code, so a
                    // returning Hider must read their own code back before re-persisting the role.
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

    fun togglePlayerRole(playerId: String) {
        if (!_uiState.value.isOrganizer) return
        val p = _uiState.value.players.find { it.id == playerId } ?: return
        val code = _uiState.value.session?.joinCode ?: return
        val next = if (p.role == Role.SEEKER) Role.HIDER else Role.SEEKER
        val nextCatchCode = if (next == Role.HIDER) RandomCode.generateNumericCode(4) else ""
        viewModelScope.launch {
            if (!firebaseSync.setCatchCode(code, p.id, nextCatchCode)) {
                _uiState.update { it.copy(toastMessage = "Could not update private catch code") }
                return@launch
            }
            if (!firebaseSync.joinPlayer(code, p.copy(role = next, catchCode = nextCatchCode))) {
                firebaseSync.setCatchCode(code, p.id, p.catchCode)
                _uiState.update { it.copy(toastMessage = "Could not update player role") }
            }
        }
    }

    fun autoAssignRoles(ratio: Int = 3) {
        if (!_uiState.value.isOrganizer) return
        val code = _uiState.value.session?.joinCode ?: return
        val shuffled = _uiState.value.players.shuffled()
        val hiders = maxOf(1, shuffled.size / (ratio + 1))
        viewModelScope.launch {
            shuffled.forEachIndexed { i, p ->
                val hider = i < hiders
                val role = if (hider) Role.HIDER else Role.SEEKER
                val catchCode = if (hider) RandomCode.generateNumericCode(4) else ""
                val codeUpdated = firebaseSync.setCatchCode(code, p.id, catchCode)
                if (codeUpdated) {
                    firebaseSync.joinPlayer(code, p.copy(role = role, catchCode = catchCode))
                } else {
                    _uiState.update { it.copy(toastMessage = "Could not update private catch codes. Check Firebase rules.") }
                }
            }
        }
    }

    fun startGame() {
        val state = _uiState.value
        val session = state.session ?: return
        if (!state.isOrganizer || session.status != GameStatus.LOBBY || startGameInFlight) return

        val hasSeeker = state.players.any { it.role == Role.SEEKER }
        val hasHider = state.players.any { it.role == Role.HIDER }
        if (!hasSeeker || !hasHider) {
            _uiState.update { it.copy(toastMessage = "Assign at least one Seeker and one Hider before starting.") }
            return
        }

        val runningSession = session.copy(
            status = GameStatus.RUNNING,
            isPaused = false,
            remainingSeconds = session.durationMinutes * 60,
            headStartRemainingSeconds = session.headStartMinutes * 60,
            currentDynamicZoneRadiusMeters = session.zoneRadiusMeters,
            isAutoPingActive = false,
            autoPingRemainingSeconds = 0
        )
        startGameInFlight = true
        viewModelScope.launch {
            try {
                val started = firebaseSync.updateSession(
                    session.joinCode,
                    mapOf(
                        "status" to GameStatus.RUNNING.name,
                        "isPaused" to false,
                        "remainingSeconds" to session.durationMinutes * 60,
                        "headStartRemainingSeconds" to session.headStartMinutes * 60,
                        "currentDynamicZoneRadiusMeters" to session.zoneRadiusMeters,
                        "isAutoPingActive" to false,
                        "autoPingRemainingSeconds" to 0
                    )
                )
                if (!started) {
                    _uiState.update { it.copy(toastMessage = "Could not start the game. Check the connection and try again.") }
                    return@launch
                }
                _uiState.update { current ->
                    if (current.session?.joinCode != session.joinCode || !current.isOrganizer) {
                        current
                    } else {
                        current.copy(
                            session = runningSession,
                            headStartCountdownSeconds = runningSession.headStartRemainingSeconds,
                            currentDynamicRadiusMeters = runningSession.zoneRadiusMeters,
                            isShrinkingZoneActive = false,
                            isAutoPingActive = false,
                            autoPingCountdownSeconds = 0
                        )
                    }
                }
                if (_uiState.value.session?.joinCode == session.joinCode) startCountdown()
            } finally {
                startGameInFlight = false
            }
        }
    }

    fun toggleSafetyPause() {
        val s = _uiState.value.session ?: return
        if (!_uiState.value.isOrganizer) return
        val paused = !s.isPaused
        _uiState.update { state -> state.copy(session = state.session?.copy(isPaused = paused, status = if (paused) GameStatus.PAUSED else GameStatus.RUNNING)) }
        viewModelScope.launch { firebaseSync.updateSession(s.joinCode, mapOf("isPaused" to paused, "status" to if (paused) GameStatus.PAUSED.name else GameStatus.RUNNING.name)) }
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
        // Catch is code-based only (no GPS distance / freshness gate).
        if (!enteredCode.trim().matches(Regex("^[0-9]{4}$"))) {
            onResult(false, "Enter the four-digit catch code")
            return
        }
        viewModelScope.launch {
            val submitted = firebaseSync.sendCatchAttempt(s.joinCode, CatchAttemptPayload(me.id, targetHiderId, enteredCode.trim()))
            if (submitted) {
                _uiState.update {
                    it.copy(toastMessage = "Tag submitted — confirming…")
                }
                onResult(true, "Catch code sent")
            } else {
                _uiState.update { it.copy(toastMessage = "Catch attempt could not sync. Check the code, connection, and room rules.") }
                onResult(false, "Catch attempt rejected")
            }
        }
    }

    fun activateStealth() {
        val me = _uiState.value.myPlayer
        val s = _uiState.value.session ?: return
        if (me.role != Role.HIDER || me.isCaught || me.isInJail || me.isStealthActive) return
        val now = System.currentTimeMillis()
        if (me.stealthCooldownUntilEpochMs > now) {
            _uiState.update { it.copy(toastMessage = "Cooldown") }
            return
        }
        viewModelScope.launch {
            val requested = firebaseSync.patchPlayer(
                s.joinCode,
                me.id,
                mapOf("stealthActivationRequestedAtEpochMs" to now)
            )
            _uiState.update {
                it.copy(toastMessage = if (requested) "Stealth activation requested" else "Stealth request could not sync")
            }
        }
    }

    fun startJailbreakHold() {
        val me = _uiState.value.myPlayer
        val session = _uiState.value.session ?: return
        if (!session.jailEnabled || session.status != GameStatus.RUNNING || session.isPaused ||
            me.role != Role.HIDER || me.isCaught || me.isInJail) return
        if (_uiState.value.players.none { it.role == Role.HIDER && it.isInJail }) {
            _uiState.update { it.copy(toastMessage = "No Hiders are in jail") }
            return
        }
        if (!GeoUtils.hasValidCoordinates(me.latitude, me.longitude) ||
            !GeoUtils.isLocationRecent(me.lastUpdatedEpochMs) ||
            !GeoUtils.hasValidCoordinates(session.jailLat, session.jailLng) ||
            GeoUtils.distanceMeters(me.latitude, me.longitude, session.jailLat, session.jailLng) > session.jailRadiusMeters
        ) {
            _uiState.update { it.copy(toastMessage = "Enter jail zone") }
            return
        }

        // Any free Hider can request a jailbreak through their own player record. The host alone
        // advances shared session progress and releases jailed players, matching the database rules.
        val requestedAt = System.currentTimeMillis()
        viewModelScope.launch {
            val success = firebaseSync.patchPlayer(
                session.joinCode,
                me.id,
                mapOf("jailbreakHoldStartedAtEpochMs" to requestedAt)
            )
            _uiState.update {
                it.copy(
                    jailbreakHoldProgress = 0,
                    toastMessage = if (success) "Hold position to start jailbreak" else "Jailbreak request could not sync"
                )
            }
        }
    }

    private fun startHostJailbreak(requesterId: String, requestedAt: Long) {
        if (!_uiState.value.isOrganizer || jailbreakJob?.isActive == true) return
        val initial = _uiState.value
        val session = initial.session ?: return
        val requester = initial.players.firstOrNull { it.id == requesterId } ?: return
        val hasPrisoners = initial.players.any { it.role == Role.HIDER && it.isInJail }
        val inJailZone = GeoUtils.hasValidCoordinates(requester.latitude, requester.longitude) &&
            GeoUtils.isLocationRecent(requester.lastUpdatedEpochMs) &&
            GeoUtils.hasValidCoordinates(session.jailLat, session.jailLng) &&
            GeoUtils.distanceMeters(requester.latitude, requester.longitude, session.jailLat, session.jailLng) <= session.jailRadiusMeters
        if (session.status != GameStatus.RUNNING || session.isPaused || !session.jailEnabled ||
            requester.role != Role.HIDER || requester.isCaught || requester.isInJail ||
            requester.jailbreakHoldStartedAtEpochMs != requestedAt || !hasPrisoners || !inJailZone) {
            viewModelScope.launch {
                firebaseSync.patchPlayer(session.joinCode, requesterId, mapOf("jailbreakHoldStartedAtEpochMs" to 0L))
            }
            return
        }

        jailbreakJob = viewModelScope.launch {
            firebaseSync.updateSession(session.joinCode, mapOf("isJailbreakInProgress" to true, "jailbreakProgressSeconds" to 0))
            for (progress in 1..Constants.JAILBREAK_HOLD_SECONDS) {
                delay(1000)
                val state = _uiState.value
                val current = state.session ?: return@launch
                val player = state.players.firstOrNull { it.id == requesterId }
                val stillInZone = player != null &&
                    GeoUtils.hasValidCoordinates(player.latitude, player.longitude) &&
                    GeoUtils.isLocationRecent(player.lastUpdatedEpochMs) &&
                    GeoUtils.hasValidCoordinates(current.jailLat, current.jailLng) &&
                    GeoUtils.distanceMeters(player.latitude, player.longitude, current.jailLat, current.jailLng) <= current.jailRadiusMeters
                val valid = current.status == GameStatus.RUNNING && !current.isPaused && player != null &&
                    player.jailbreakHoldStartedAtEpochMs == requestedAt && player.role == Role.HIDER &&
                    !player.isCaught && !player.isInJail && stillInZone &&
                    state.players.any { it.role == Role.HIDER && it.isInJail }
                if (!valid) {
                    resetJailbreak(current.joinCode)
                    player?.let {
                        firebaseSync.patchPlayer(current.joinCode, requesterId, mapOf("jailbreakHoldStartedAtEpochMs" to 0L))
                    }
                    _uiState.update { it.copy(toastMessage = "Jailbreak interrupted") }
                    return@launch
                }
                if (progress < Constants.JAILBREAK_HOLD_SECONDS) {
                    firebaseSync.updateSession(current.joinCode, mapOf("jailbreakProgressSeconds" to progress))
                }
            }

            val requesterStillHolding = _uiState.value.players.any {
                it.id == requesterId && it.jailbreakHoldStartedAtEpochMs == requestedAt
            }
            if (requesterStillHolding) {
                firebaseSync.patchPlayer(session.joinCode, requesterId, mapOf("jailbreakHoldStartedAtEpochMs" to 0L))
                completeJailbreak()
            }
        }
    }

    private suspend fun resetJailbreak(joinCode: String) {
        firebaseSync.updateSession(joinCode, mapOf("isJailbreakInProgress" to false, "jailbreakProgressSeconds" to 0))
    }

    private fun refreshStealthState() {
        val state = _uiState.value
        val me = state.myPlayer
        val now = System.currentTimeMillis()
        val active = me.isStealthActive && now < me.stealthUntilEpochMs
        val cooldown = (((me.stealthCooldownUntilEpochMs - now).coerceAtLeast(0L) + 999L) / 1000L).toInt()
        if (me.isStealthActive && !active) {
            _uiState.update { it.copy(myPlayer = me.copy(isStealthActive = false)) }
        }
        if (active != state.isStealthActive || cooldown != state.stealthCooldownSeconds) {
            _uiState.update { it.copy(isStealthActive = active, stealthCooldownSeconds = cooldown) }
        }
    }

    private fun completeJailbreak() {
        val s = _uiState.value.session ?: return
        if (s.status != GameStatus.RUNNING) return
        val immunity = System.currentTimeMillis() + Constants.POST_JAILBREAK_IMMUNITY_SECONDS * 1000L
        if (!_uiState.value.isOrganizer) return
        viewModelScope.launch {
            _uiState.value.players.filter { it.role == Role.HIDER && it.isInJail }.forEach { p ->
                firebaseSync.joinPlayer(s.joinCode, p.copy(isCaught = false, isInJail = false, immunityUntilEpochMs = immunity))
            }
            firebaseSync.updateSession(s.joinCode, mapOf("isJailbreakInProgress" to false, "jailbreakProgressSeconds" to 0))
            hapticManager.triggerJailbreakAlarm()
            _uiState.update { it.copy(toastMessage = "JAILBREAK") }
        }
    }

    fun updateProfile(name: String, emoji: String) {
        viewModelScope.launch {
            val u = _uiState.value.myPlayer.copy(name = name, avatarEmoji = emoji)
            db.playerProfileDao().upsertProfile(PlayerProfileEntity(u.id, u.name, u.avatarEmoji))
            _uiState.update { it.copy(myPlayer = u) }
        }
    }

    private fun loadAppearanceSettings(): AppearanceSettings {
        val storedMode = settingsPreferences.getString(PREFERENCE_APPEARANCE_MODE, null)
        val hasLegacyMode = settingsPreferences.contains(PREFERENCE_DARK_MODE)
        val legacyMode = if (settingsPreferences.getBoolean(PREFERENCE_DARK_MODE, false)) {
            AppearanceMode.OLED
        } else {
            AppearanceMode.LIGHT
        }
        val mode = storedMode?.let { value ->
            AppearanceMode.values().firstOrNull { it.name == value }
        } ?: if (hasLegacyMode) legacyMode else AppearanceMode.FOLLOW_DEVICE
        val accent = settingsPreferences.getString(PREFERENCE_ACCENT_MODE, null)?.let { value ->
            AccentMode.values().firstOrNull { it.name == value }
        } ?: AccentMode.DEFAULT
        val highRefreshRateEnabled = settingsPreferences.getBoolean(PREFERENCE_HIGH_REFRESH_RATE, false)

        settingsPreferences.edit()
            .putString(PREFERENCE_APPEARANCE_MODE, mode.name)
            .putString(PREFERENCE_ACCENT_MODE, accent.name)
            .apply()

        return AppearanceSettings(
            mode = mode,
            accent = accent,
            highRefreshRateEnabled = highRefreshRateEnabled
        )
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        settingsPreferences.edit().putString(PREFERENCE_APPEARANCE_MODE, mode.name).apply()
        _uiState.update { it.copy(appearanceSettings = it.appearanceSettings.copy(mode = mode)) }
    }

    fun setAccentMode(accent: AccentMode) {
        settingsPreferences.edit().putString(PREFERENCE_ACCENT_MODE, accent.name).apply()
        _uiState.update { it.copy(appearanceSettings = it.appearanceSettings.copy(accent = accent)) }
    }

    fun setHighRefreshRateEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(PREFERENCE_HIGH_REFRESH_RATE, enabled).apply()
        _uiState.update {
            it.copy(appearanceSettings = it.appearanceSettings.copy(highRefreshRateEnabled = enabled))
        }
    }
    fun refreshBatteryOptimizationStatus() {
        val app = getApplication<Application>()
        _uiState.update { it.copy(isBatteryOptimizationExempt = BatteryOptimizationHelper.isBatteryOptimizationDisabled(app)) }
    }

    fun leaveGame() {
        countdownTimerJob?.cancel()
        clearPersistedActiveRoom()
        if (jailbreakJob?.isActive == true) {
            jailbreakJob?.cancel()
            _uiState.value.session?.joinCode?.let { code -> viewModelScope.launch { resetJailbreak(code) } }
        }
        val session = _uiState.value.session
        val playerId = _uiState.value.myPlayer.id
        if (session != null) viewModelScope.launch { firebaseSync.leaveRoom(session.joinCode, playerId) } else firebaseSync.stopSync()
        _uiState.update { it.copy(session = null, players = emptyList(), isOrganizer = false, isJoiningRoom = false, isCreatingRoom = false, canJailbreak = false, isInJail = false) }
    }
    fun leaveRoom() = leaveGame()
    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }
    fun dismissToast() = clearToast()

    fun sendQuickSignal(code: String) {
        val state = _uiState.value
        val session = state.session ?: return
        if (code !in GameSignalCode.allowedCodes ||
            session.status != GameStatus.RUNNING ||
            session.isPaused
        ) return

        val nowEpochMs = System.currentTimeMillis()
        if (nowEpochMs - lastQuickSignalSentAtEpochMs < Constants.GAME_SIGNAL_SEND_COOLDOWN_MS) return
        lastQuickSignalSentAtEpochMs = nowEpochMs
        val signal = GameSignalPayload(code = code, timestampEpochMs = nowEpochMs)

        viewModelScope.launch {
            val sent = firebaseSync.sendQuickSignal(session.joinCode, state.myPlayer.id, signal)
            if (!sent) {
                lastQuickSignalSentAtEpochMs = 0L
                _uiState.update {
                    it.copy(toastMessage = "Signal could not sync. Check your connection and try again.")
                }
            }
        }
    }

    private fun applyRoom(room: RoomNetworkModel) {
        val state = _uiState.value
        val serverMe = room.players[state.myPlayer.id]
        val localMe = state.myPlayer
        // The polled server copy carries coordinates only when this device's location node exists.
        // A missing node yields 0.0 and an older node yields a stale fix; either would wipe the
        // local fresh GPS reading and disable the catch button and compass until the next write.
        val me = when {
            serverMe == null -> localMe
            !GeoUtils.hasValidCoordinates(serverMe.latitude, serverMe.longitude) -> serverMe.copy(
                latitude = localMe.latitude,
                longitude = localMe.longitude,
                isOutOfZone = localMe.isOutOfZone,
                isInSafeZone = localMe.isInSafeZone,
                safeZoneName = localMe.safeZoneName,
                lastUpdatedEpochMs = localMe.lastUpdatedEpochMs
            )
            serverMe.lastUpdatedEpochMs < localMe.lastUpdatedEpochMs -> serverMe.copy(
                latitude = localMe.latitude,
                longitude = localMe.longitude,
                isOutOfZone = localMe.isOutOfZone,
                isInSafeZone = localMe.isInSafeZone,
                safeZoneName = localMe.safeZoneName,
                lastUpdatedEpochMs = localMe.lastUpdatedEpochMs
            )
            else -> serverMe
        }
        val session = room.session
        val now = System.currentTimeMillis()
        val isStealthActive = me.isStealthActive && now < me.stealthUntilEpochMs
        if (me.id == state.myPlayer.id && !state.isStealthActive && isStealthActive) {
            hapticManager.triggerPowerUp()
        }
        val canJailbreak = session.jailEnabled &&
            GeoUtils.hasValidCoordinates(me.latitude, me.longitude) &&
            GeoUtils.isLocationRecent(me.lastUpdatedEpochMs) &&
            GeoUtils.hasValidCoordinates(session.jailLat, session.jailLng) &&
            me.role == Role.HIDER && !me.isCaught && !me.isInJail &&
            session.status == GameStatus.RUNNING &&
            GeoUtils.distanceMeters(me.latitude, me.longitude, session.jailLat, session.jailLng) <= session.jailRadiusMeters &&
            room.players.values.any { it.role == Role.HIDER && it.isInJail }
        val huntersNearJail = room.players.values.filter { player ->
            player.role == Role.SEEKER && GeoUtils.hasValidCoordinates(player.latitude, player.longitude) &&
                GeoUtils.isLocationRecent(player.lastUpdatedEpochMs) &&
                GeoUtils.distanceMeters(player.latitude, player.longitude, session.jailLat, session.jailLng) <= session.jailRadiusMeters * 2
        }
        _uiState.update {
            it.copy(
                session = session,
                players = room.players.values.toList(),
                myPlayer = me,
                isSeekerFrozen = me.role == Role.SEEKER && session.status == GameStatus.RUNNING && session.headStartRemainingSeconds > 0,
                headStartCountdownSeconds = session.headStartRemainingSeconds,
                currentDynamicRadiusMeters = session.currentDynamicZoneRadiusMeters,
                isShrinkingZoneActive = session.isShrinkingZoneEnabled && !session.isUnlimitedZone && session.currentDynamicZoneRadiusMeters < session.zoneRadiusMeters,
                isAutoPingActive = session.isAutoPingActive,
                autoPingCountdownSeconds = session.autoPingRemainingSeconds,
                isStealthActive = isStealthActive,
                stealthCooldownSeconds = (((me.stealthCooldownUntilEpochMs - now).coerceAtLeast(0L) + 999L) / 1000L).toInt(),
                isInJail = me.isInJail,
                canJailbreak = canJailbreak,
                jailbreakHoldProgress = session.jailbreakProgressSeconds,
                huntersNearJail = huntersNearJail,
            )
        }
        recalculateDistancesAndProximity()
        val attempt = room.lastCatchAttempt
        if (attempt != null && attempt.timestamp > lastHandledCatchAttemptTimestamp) {
            if (state.isOrganizer && catchAttemptInFlightTimestamp != attempt.timestamp) {
                catchAttemptInFlightTimestamp = attempt.timestamp
                processCatchAttempt(attempt)
            }
        }
        // `lastCatchAttempt` is host-readable only, so a non-host Seeker learns the outcome from the
        // per-seeker ack node the host writes after validation.
        
        // Frozen manhunt reveals: when a ping window opens, snapshot hider positions once and keep them local.
        val pingActive = room.session.isAutoPingActive
        if (pingActive && !wasAutoPingActive) {
            val snaps = room.players.values
                .filter { it.role == Role.HIDER && !it.isCaught && GeoUtils.hasValidCoordinates(it.latitude, it.longitude) }
                .filterNot { it.isStealthActive && now < it.stealthUntilEpochMs }
                .associate { p ->
                    p.id to LocationRevealSnapshot(
                        playerId = p.id,
                        latitude = p.latitude,
                        longitude = p.longitude,
                        capturedAtEpochMs = now,
                        accuracyMeters = 0.0
                    )
                }
            if (snaps.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        frozenRevealLocations = snaps,
                        lastRevealEpochMs = now,
                        toastMessage = if (it.myPlayer.role == Role.SEEKER) {
                            "Location reveal — pins frozen until next ping"
                        } else it.toastMessage
                    )
                }
            }
        }
        wasAutoPingActive = pingActive

        val ack = room.catchAck
        if (ack != null && ack.seekerId == me.id && ack.timestamp > lastCatchAckTimestamp) {
            lastCatchAckTimestamp = ack.timestamp
            _uiState.update {
                it.copy(
                    toastMessage = if (ack.accepted) {
                        "Catch confirmed: ${ack.targetName.ifBlank { "Hider" }}"
                    } else {
                        "Catch rejected — check the code or safe zone"
                    }
                )
            }
            if (ack.accepted) hapticManager.triggerCatchConfirmation()
        }
        if (state.isOrganizer) {
            room.players.values.filter { it.stealthActivationRequestedAtEpochMs > 0L }
                .forEach { processStealthRequest(it) }
            room.players.values.filter {
                it.isStealthActive && it.stealthUntilEpochMs > 0L && now >= it.stealthUntilEpochMs
            }.forEach { processExpiredStealth(it) }
        }
        if (state.isOrganizer && jailbreakJob?.isActive != true) {
            room.players.values.firstOrNull {
                it.role == Role.HIDER && !it.isCaught && !it.isInJail && it.jailbreakHoldStartedAtEpochMs > 0L
            }?.let { requester ->
                startHostJailbreak(requester.id, requester.jailbreakHoldStartedAtEpochMs)
            }
        }
    }

    private fun handleLocation(lat: Double, lng: Double) {
        if (!GeoUtils.hasValidCoordinates(lat, lng)) return
        compass.updateLocation(lat, lng)

        val session = _uiState.value.session
        val radius = session?.currentDynamicZoneRadiusMeters?.takeIf { it > 0.0 }
            ?: session?.zoneRadiusMeters ?: 1000.0
        val unlimited = session?.isUnlimitedZone ?: false
        val geofence = if (session != null && radius > 0.0 && GeoUtils.hasValidCoordinates(session.zoneCenterLat, session.zoneCenterLng) && !unlimited) {
            GeofenceManager.evaluate(lat, lng, session.zoneCenterLat, session.zoneCenterLng, radius)
        } else {
            com.manhunt.app.util.GeofenceEvaluation(GeofenceStatus.INSIDE, 0.0, 0.0, false)
        }
        val safeZone = session?.safeZones?.firstOrNull { zone ->
            GeoUtils.hasValidCoordinates(zone.latitude, zone.longitude) &&
                GeoUtils.distanceMeters(lat, lng, zone.latitude, zone.longitude) <= zone.radiusMeters
        }
        val now = System.currentTimeMillis()
        val previousMe = _uiState.value.myPlayer
        val me = previousMe.copy(
            latitude = lat,
            longitude = lng,
            isOutOfZone = geofence.isOutside,
            isInSafeZone = safeZone != null,
            safeZoneName = safeZone?.name,
            lastUpdatedEpochMs = now
        )
        _uiState.update {
            it.copy(
                myPlayer = me,
                isOutOfZone = geofence.isOutside,
                geofenceStatus = geofence.status,
                geofenceMessage = geofence.warningMessage,
                isInSafeZone = safeZone != null,
                currentSafeZoneName = safeZone?.name
            )
        }
        val seekerFrozen = me.role == Role.SEEKER && (session?.headStartRemainingSeconds ?: 0) > 0
        if (!seekerFrozen && session != null && session.status == GameStatus.RUNNING && session.joinCode.isNotEmpty()) {
            viewModelScope.launch { firebaseSync.updateLocation(session.joinCode, me.id, lat, lng, geofence.isOutside) }
        }
        recalculateDistancesAndProximity()
    }

    private fun recalculateDistancesAndProximity() {
        val state = _uiState.value
        val me = state.myPlayer
        val others = state.players.filter { it.id != me.id }
        val activeHiderHeartbeat = me.role == Role.HIDER &&
            !me.isCaught && !me.isInJail &&
            state.session?.status == GameStatus.RUNNING &&
            state.session.isPaused == false
        if (!activeHiderHeartbeat) hapticManager.stop()

        if (!GeoUtils.hasValidCoordinates(me.latitude, me.longitude) ||
            !GeoUtils.isLocationRecent(me.lastUpdatedEpochMs)
        ) {
            hapticManager.stop()
            _uiState.update {
                it.copy(
                    nearestSeekerDistance = -1.0,
                    nearestSeekerBearing = 0f,
                    nearbyCatchableHider = null,
                    safeZoneNotice = null
                )
            }
            return
        }

        if (me.role == Role.HIDER) {
            var nearest = Double.MAX_VALUE
            var bearing = 0f
            others.filter {
                it.role == Role.SEEKER &&
                    GeoUtils.hasValidCoordinates(it.latitude, it.longitude) &&
                    GeoUtils.isLocationRecent(it.lastUpdatedEpochMs)
            }.forEach { seeker ->
                val distance = GeoUtils.distanceMeters(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                if (distance < nearest) {
                    nearest = distance
                    bearing = GeoUtils.bearingDegrees(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                }
            }
            val distance = if (nearest == Double.MAX_VALUE) -1.0 else nearest
            if (distance >= 0.0 && activeHiderHeartbeat) {
                hapticManager.evaluateProximityHaptics(distance)
            }
            _uiState.update {
                it.copy(
                    nearestSeekerDistance = distance,
                    nearestSeekerBearing = bearing,
                    nearbyCatchableHider = null
                )
            }
        } else {
            val session = state.session
            val hiders = others.filter {
                it.role == Role.HIDER && !it.isCaught
            }
            fun isActuallyInSafeZone(player: Player): Boolean = session?.safeZones?.any { zone ->
                GeoUtils.hasValidCoordinates(zone.latitude, zone.longitude) &&
                    GeoUtils.hasValidCoordinates(player.latitude, player.longitude) &&
                    GeoUtils.distanceMeters(player.latitude, player.longitude, zone.latitude, zone.longitude) <= zone.radiusMeters
            } == true
            // No distance gate: any free, non-immune, non-safe-zone hider can be tagged by code.
            val catchable = hiders.firstOrNull { hider ->
                !isActuallyInSafeZone(hider) && hider.immunityUntilEpochMs <= System.currentTimeMillis()
            }
            val protected = hiders.firstOrNull { isActuallyInSafeZone(it) }
            _uiState.update {
                it.copy(
                    nearbyCatchableHider = catchable,
                    safeZoneNotice = protected?.let {
                        h -> "${h.name} is protected in ${h.safeZoneName?.takeIf { name -> name.isNotBlank() } ?: "Safe Zone"}!"
                    }
                )
            }
        }
    }
    private fun processStealthRequest(requester: Player) {
        if (!_uiState.value.isOrganizer) return
        val session = _uiState.value.session ?: return
        val requestedAt = requester.stealthActivationRequestedAtEpochMs
        if (requestedAt <= 0L || handledStealthRequests[requester.id] == requestedAt) return
        handledStealthRequests[requester.id] = requestedAt
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = _uiState.value.players.firstOrNull { it.id == requester.id } ?: requester
            val valid = requestedAt <= now + 5_000L && now - requestedAt <= 30_000L &&
                current.role == Role.HIDER && !current.isCaught && !current.isInJail &&
                current.stealthCooldownUntilEpochMs <= now
            val success = if (valid) {
                val until = now + Constants.STEALTH_DURATION_SECONDS * 1000L
                val cooldownUntil = until + Constants.STEALTH_COOLDOWN_SECONDS * 1000L
                firebaseSync.joinPlayer(
                    session.joinCode,
                    current.copy(
                        isStealthActive = true,
                        stealthUntilEpochMs = until,
                        stealthCooldownUntilEpochMs = cooldownUntil,
                        stealthActivationRequestedAtEpochMs = 0L
                    )
                )
            } else {
                firebaseSync.patchPlayer(
                    session.joinCode,
                    requester.id,
                    mapOf("stealthActivationRequestedAtEpochMs" to 0L)
                )
            }
            if (!success) handledStealthRequests.remove(requester.id)
        }
    }

    private fun processExpiredStealth(player: Player) {
        if (!_uiState.value.isOrganizer) return
        val session = _uiState.value.session ?: return
        val expiry = player.stealthUntilEpochMs
        if (expiry <= 0L || handledStealthExpiries[player.id] == expiry) return
        handledStealthExpiries[player.id] = expiry
        viewModelScope.launch {
            val success = firebaseSync.patchPlayer(
                session.joinCode,
                player.id,
                mapOf("isStealthActive" to false)
            )
            if (!success) handledStealthExpiries.remove(player.id)
        }
    }

    private fun processCatchAttempt(attempt: CatchAttemptPayload) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val session = state.session
                val now = System.currentTimeMillis()
                val targetName = state.players.firstOrNull { it.id == attempt.targetHiderId }?.name.orEmpty()
                suspend fun ack(accepted: Boolean) {
                    firebaseSync.publishCatchAck(
                        session?.joinCode ?: return,
                        CatchAckPayload(
                            seekerId = attempt.seekerId,
                            targetHiderId = attempt.targetHiderId,
                            targetName = targetName,
                            accepted = accepted,
                            timestamp = attempt.timestamp
                        )
                    )
                }
                if (!state.isOrganizer || session == null || session.status != GameStatus.RUNNING || session.isPaused || session.headStartRemainingSeconds > 0 ||
                    attempt.timestamp > now + 5_000L || now - attempt.timestamp > 30_000L
                ) {
                    lastHandledCatchAttemptTimestamp = attempt.timestamp
                    return@launch
                }

                val seeker = state.players.firstOrNull { it.id == attempt.seekerId }
                val target = state.players.firstOrNull { it.id == attempt.targetHiderId }
                // Local/code proof only — do not reject on GPS freshness or distance.
                if (seeker == null || target == null ||
                    seeker.role != Role.SEEKER || target.role != Role.HIDER ||
                    target.isCaught || target.isInJail || target.immunityUntilEpochMs > now ||
                    seeker.id == target.id
                ) {
                    lastHandledCatchAttemptTimestamp = attempt.timestamp
                    ack(false)
                    return@launch
                }

                val targetInSafeZone = session.safeZones.any { zone ->
                    GeoUtils.hasValidCoordinates(zone.latitude, zone.longitude) &&
                        GeoUtils.distanceMeters(target.latitude, target.longitude, zone.latitude, zone.longitude) <= zone.radiusMeters
                }
                // Distance is not enforced — catch code is the real-world tag proof.
                if (targetInSafeZone ||
                    target.catchCode.trim() != attempt.enteredCode.trim()
                ) {
                    lastHandledCatchAttemptTimestamp = attempt.timestamp
                    ack(false)
                    return@launch
                }

                val sendToJail = session.jailEnabled &&
                    GeoUtils.hasValidCoordinates(session.jailLat, session.jailLng)
                val caught = target.copy(
                    isCaught = true,
                    isInJail = sendToJail,
                    isStealthActive = false
                )
                if (!firebaseSync.joinPlayer(session.joinCode, caught)) {
                    _uiState.update { it.copy(toastMessage = "Catch could not sync. Check the host connection.") }
                    ack(false)
                    return@launch
                }

                lastHandledCatchAttemptTimestamp = attempt.timestamp
                ack(true)
                _uiState.update { current ->
                    current.copy(
                        players = current.players.map { player -> if (player.id == target.id) caught else player },
                        myPlayer = if (current.myPlayer.id == caught.id) caught else current.myPlayer,
                        toastMessage = "Catch confirmed: ${target.name}"
                    )
                }
                if (_uiState.value.myPlayer.id == caught.id) hapticManager.stop()
                val remainingHiders = _uiState.value.players.filter {
                    it.role == Role.HIDER && !it.isCaught
                }
                if (remainingHiders.isEmpty()) {
                    // Ensure local session flips immediately so UI navigates even if network lags.
                    _uiState.update { cur ->
                        cur.copy(
                            session = cur.session?.copy(
                                status = GameStatus.FINISHED,
                                winnerRole = Role.SEEKER,
                                totalCatches = cur.players.count { it.role == Role.HIDER && it.isCaught }
                            ),
                            toastMessage = "All runners tagged — seekers win!"
                        )
                    }
                    endGame(Role.SEEKER)
                }
            } finally {
                if (catchAttemptInFlightTimestamp == attempt.timestamp) {
                    catchAttemptInFlightTimestamp = 0L
                }
            }
        }
    }

    private fun startCountdown() {
        countdownTimerJob?.cancel()
        countdownTimerJob = viewModelScope.launch {
            var remaining = _uiState.value.session?.remainingSeconds ?: 0
            var headStart = _uiState.value.session?.headStartRemainingSeconds ?: 0
            while (isActive) {
                delay(1000)
                val current = _uiState.value.session ?: break
                if (current.status != GameStatus.RUNNING || current.isPaused || !_uiState.value.isOrganizer) continue
                remaining = (remaining - 1).coerceAtLeast(0)
                if (headStart > 0) {
                    headStart--
                    if (headStart == 0) hapticManager.triggerHeadStartOver()
                }
                val totalSeconds = current.durationMinutes * 60
                var dynamicRadius = current.zoneRadiusMeters
                var shrinking = false
                if (current.isShrinkingZoneEnabled && !current.isUnlimitedZone) {
                    val threshold = (totalSeconds * 0.25).toInt()
                    if (threshold > 0 && remaining <= threshold) {
                        shrinking = true
                        val progress = remaining.toDouble() / threshold.toDouble()
                        dynamicRadius = maxOf(100.0, 100.0 + (current.zoneRadiusMeters - 100.0) * progress)
                    }
                }
                var pingActive = false
                var pingRemaining = 0
                if (current.autoPingIntervalMinutes > 0) {
                    val interval = current.autoPingIntervalMinutes * 60
                    val elapsed = totalSeconds - remaining
                    if (elapsed > 0) {
                        val cycle = elapsed % interval
                        if (cycle in 0..9) {
                            pingActive = true
                            pingRemaining = 10 - cycle
                        }
                    }
                }
                if (remaining <= 0) {
                    endGame(Role.HIDER)
                    break
                }
                _uiState.update { state ->
                    state.copy(
                        session = state.session?.copy(remainingSeconds = remaining, headStartRemainingSeconds = headStart, currentDynamicZoneRadiusMeters = dynamicRadius, isAutoPingActive = pingActive, autoPingRemainingSeconds = pingRemaining),
                        headStartCountdownSeconds = headStart,
                        isShrinkingZoneActive = shrinking,
                        currentDynamicRadiusMeters = dynamicRadius,
                        isAutoPingActive = pingActive,
                        autoPingCountdownSeconds = pingRemaining
                    )
                }
                if (remaining % 3 == 0 || headStart in 1..5 || pingActive) {
                    firebaseSync.updateSession(current.joinCode, mapOf(
                        "remainingSeconds" to remaining,
                        "headStartRemainingSeconds" to headStart,
                        "currentDynamicZoneRadiusMeters" to dynamicRadius,
                        "isAutoPingActive" to pingActive,
                        "autoPingRemainingSeconds" to pingRemaining
                    ))
                }
            }
        }
    }


    /** BLE assist: hiders advertise, seekers scan. Never required for a successful tag. */
    private fun syncBleMode() {
        val state = _uiState.value
        val session = state.session
        if (session == null || session.status != GameStatus.RUNNING || session.isPaused) {
            bleAssist.stopAll()
            return
        }
        val me = state.myPlayer
        when (me.role) {
            Role.HIDER -> {
                if (!me.isCaught && !me.isInJail) {
                    bleAssist.stopScanning()
                    bleAssist.startAdvertising(session.joinCode, me.id)
                } else {
                    bleAssist.stopAll()
                }
            }
            Role.SEEKER -> {
                bleAssist.stopAdvertising()
                bleAssist.startScanning(session.joinCode)
            }
        }
    }

    private fun endGame(winner: Role) {
        val s = _uiState.value.session ?: return
        countdownTimerJob?.cancel()
        val catches = _uiState.value.players.count { it.role == Role.HIDER && it.isCaught }
        viewModelScope.launch {
            firebaseSync.updateSession(s.joinCode, mapOf("status" to GameStatus.FINISHED.name, "winnerRole" to winner.name, "totalCatches" to catches))
            db.matchHistoryDao().insertMatch(MatchHistoryEntity(joinCode = s.joinCode, rolePlayed = _uiState.value.myPlayer.role.name, isWin = _uiState.value.myPlayer.role == winner, durationSeconds = (s.durationMinutes * 60) - s.remainingSeconds, catchesCount = catches))
        }
    }

    override fun onCleared() {
        bleAssist.stopAll()
        super.onCleared()
        countdownTimerJob?.cancel()
        compass.stopListening()
    }
}
