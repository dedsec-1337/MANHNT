package com.manhunt.app.viewmodel

import android.app.Application
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
    val session: GameSession? = null,
    val players: List<Player> = emptyList(),
    val isOrganizer: Boolean = false,
    val headingDegrees: Float = 0f,
    val nearestSeekerDistance: Double = -1.0,
    val nearestSeekerBearing: Float = 0f,
    val nearbyCatchableHider: Player? = null,
    val isOutOfZone: Boolean = false,
    val geofenceStatus: GeofenceStatus = GeofenceStatus.INSIDE,
    val geofenceGraceSeconds: Int = 30,
    val geofenceMessage: String? = null,
    val isDarkMode: Boolean = false,
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
    val pendingCatchConfirm: CatchConfirmPayload? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as ManhuntApp).database
    private val firebaseSync = FirebaseSyncService.instance
    private val compass = CompassSensorManager(application)
    private val hapticManager = HapticFeedbackManager(application)
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    private var countdownTimerJob: Job? = null
    private val firebaseAuthMutex = Mutex()
    private var jailbreakJob: Job? = null
    private var lastHandledCatchAttemptTimestamp: Long = 0L

    init {
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
        compass.startListening()
        viewModelScope.launch { compass.azimuthFlow.collect { h -> _uiState.update { it.copy(headingDegrees = h) } } }
        viewModelScope.launch { ManhuntLocationService.locationUpdates.collect { loc -> handleLocation(loc.latitude, loc.longitude) } }
        viewModelScope.launch { firebaseSync.roomState.collect { if (it != null) applyRoom(it) } }
        viewModelScope.launch { firebaseSync.syncStatus.collect { s -> _uiState.update { it.copy(networkStatus = s) } } }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                refreshStealthState()
            }
        }
        refreshBatteryOptimizationStatus()
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
                val room = firebaseSync.fetchRoomData(upper)
                if (room != null) {
                    val me = authenticatedPlayer.copy(catchCode = if (authenticatedPlayer.role == Role.HIDER) RandomCode.generateNumericCode(4) else "")
                    if (firebaseSync.joinPlayer(upper, me)) {
                        _uiState.update { it.copy(session = room.session, players = room.players.values.toList(), myPlayer = me, isOrganizer = room.session.hostPlayerId == me.id, isJoiningRoom = false, currentDynamicRadiusMeters = room.session.currentDynamicZoneRadiusMeters, toastMessage = "Joined ${room.session.roomName}!" ) }
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
        val next = if (p.role == Role.SEEKER) Role.HIDER else Role.SEEKER
        viewModelScope.launch { firebaseSync.joinPlayer(_uiState.value.session?.joinCode ?: return@launch, p.copy(role = next, catchCode = if (next == Role.HIDER) RandomCode.generateNumericCode(4) else "")) }
    }

    fun autoAssignRoles(ratio: Int = 3) {
        if (!_uiState.value.isOrganizer) return
        val code = _uiState.value.session?.joinCode ?: return
        val shuffled = _uiState.value.players.shuffled()
        val hiders = maxOf(1, shuffled.size / (ratio + 1))
        viewModelScope.launch {
            shuffled.forEachIndexed { i, p ->
                val hider = i < hiders
                firebaseSync.joinPlayer(code, p.copy(role = if (hider) Role.HIDER else Role.SEEKER, catchCode = if (hider) RandomCode.generateNumericCode(4) else ""))
            }
        }
    }

    fun startGame() {
        val s = _uiState.value.session ?: return
        if (!_uiState.value.isOrganizer || s.status == GameStatus.FINISHED) return
        val runningSession = s.copy(status = GameStatus.RUNNING, isPaused = false, remainingSeconds = s.durationMinutes * 60, headStartRemainingSeconds = s.headStartMinutes * 60, currentDynamicZoneRadiusMeters = s.zoneRadiusMeters, isAutoPingActive = false, autoPingRemainingSeconds = 0)
        _uiState.update { it.copy(session = runningSession, headStartCountdownSeconds = runningSession.headStartRemainingSeconds, currentDynamicRadiusMeters = runningSession.zoneRadiusMeters, isShrinkingZoneActive = false, isAutoPingActive = false, autoPingCountdownSeconds = 0) }
        viewModelScope.launch {
            firebaseSync.updateSession(s.joinCode, mapOf("status" to GameStatus.RUNNING.name, "isPaused" to false, "remainingSeconds" to s.durationMinutes * 60, "headStartRemainingSeconds" to s.headStartMinutes * 60, "currentDynamicZoneRadiusMeters" to s.zoneRadiusMeters, "isAutoPingActive" to false, "autoPingRemainingSeconds" to 0))
        }
        startCountdown()
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
        val dist = GeoUtils.distanceMeters(me.latitude, me.longitude, target.latitude, target.longitude)
        if (dist > Constants.CATCH_PROXIMITY_METERS) { onResult(false, "Too far ${dist.toInt()}m"); return }
        viewModelScope.launch {
            val submitted = firebaseSync.sendCatchAttempt(s.joinCode, CatchAttemptPayload(me.id, targetHiderId, enteredCode.trim()))
            if (!submitted) _uiState.update { it.copy(toastMessage = "Catch attempt could not sync. Check your connection.") }
        }
        if (target.catchCode.trim() == enteredCode.trim()) {
            hapticManager.triggerCatchConfirmation()
            onResult(true, "Catch submitted for confirmation")
        } else onResult(false, "Wrong code")
    }

    fun activateStealth() {
        val me = _uiState.value.myPlayer
        val s = _uiState.value.session ?: return
        if (me.role != Role.HIDER || me.isCaught || me.isInJail) return
        val now = System.currentTimeMillis()
        if (me.stealthCooldownUntilEpochMs > now) { _uiState.update { it.copy(toastMessage = "Cooldown") }; return }
        viewModelScope.launch {
            val until = now + Constants.STEALTH_DURATION_SECONDS * 1000L
            val cooldownUntil = until + Constants.STEALTH_COOLDOWN_SECONDS * 1000L
            val updated = me.copy(isStealthActive = true, stealthUntilEpochMs = until, stealthCooldownUntilEpochMs = cooldownUntil)
            firebaseSync.patchPlayer(s.joinCode, me.id, mapOf("isStealthActive" to true, "stealthUntilEpochMs" to until, "stealthCooldownUntilEpochMs" to cooldownUntil))
            hapticManager.triggerPowerUp()
            _uiState.update { it.copy(myPlayer = updated, isStealthActive = true, toastMessage = "Stealth ${Constants.STEALTH_DURATION_SECONDS}s") }
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
        if (GeoUtils.distanceMeters(me.latitude, me.longitude, session.jailLat, session.jailLng) > session.jailRadiusMeters) {
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
        val inJailZone = GeoUtils.distanceMeters(requester.latitude, requester.longitude, session.jailLat, session.jailLng) <= session.jailRadiusMeters
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
            val updated = me.copy(isStealthActive = false)
            _uiState.update { it.copy(myPlayer = updated) }
            state.session?.let { s -> viewModelScope.launch { firebaseSync.patchPlayer(s.joinCode, me.id, mapOf("isStealthActive" to false)) } }
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

    fun respondToCatchConfirm(accept: Boolean) {
        val pending = _uiState.value.pendingCatchConfirm ?: return
        val me = _uiState.value.myPlayer
        if (pending.targetHiderId != me.id) return
        val s = _uiState.value.session ?: return
        viewModelScope.launch {
            if (accept) firebaseSync.joinPlayer(s.joinCode, me.copy(isCaught = true, isInJail = s.jailEnabled, isStealthActive = false))
            else if (me.disputesRemaining > 0) firebaseSync.joinPlayer(s.joinCode, me.copy(disputesRemaining = me.disputesRemaining - 1))
            _uiState.update { it.copy(pendingCatchConfirm = null) }
        }
    }

    fun updateProfile(name: String, emoji: String) {
        viewModelScope.launch {
            val u = _uiState.value.myPlayer.copy(name = name, avatarEmoji = emoji)
            db.playerProfileDao().upsertProfile(PlayerProfileEntity(u.id, u.name, u.avatarEmoji))
            _uiState.update { it.copy(myPlayer = u) }
        }
    }

    fun toggleDarkMode() { _uiState.update { it.copy(isDarkMode = !it.isDarkMode) } }
    fun refreshBatteryOptimizationStatus() {
        val app = getApplication<Application>()
        _uiState.update { it.copy(isBatteryOptimizationExempt = BatteryOptimizationHelper.isBatteryOptimizationDisabled(app)) }
    }

    fun leaveGame() {
        countdownTimerJob?.cancel()
        if (jailbreakJob?.isActive == true) {
            jailbreakJob?.cancel()
            _uiState.value.session?.joinCode?.let { code -> viewModelScope.launch { resetJailbreak(code) } }
        }
        val session = _uiState.value.session
        val playerId = _uiState.value.myPlayer.id
        if (session != null) viewModelScope.launch { firebaseSync.leaveRoom(session.joinCode, playerId) } else firebaseSync.stopSync()
        _uiState.update { it.copy(session = null, players = emptyList(), isOrganizer = false, isJoiningRoom = false, isCreatingRoom = false, pendingCatchConfirm = null, canJailbreak = false, isInJail = false) }
    }
    fun leaveRoom() = leaveGame()
    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }
    fun dismissToast() = clearToast()

    private fun applyRoom(room: RoomNetworkModel) {
        val state = _uiState.value
        val me = room.players[state.myPlayer.id] ?: state.myPlayer
        val session = room.session
        val now = System.currentTimeMillis()
        val isStealthActive = me.isStealthActive && now < me.stealthUntilEpochMs
        val canJailbreak = session.jailEnabled && me.role == Role.HIDER && !me.isCaught && !me.isInJail &&
            session.status == GameStatus.RUNNING &&
            GeoUtils.distanceMeters(me.latitude, me.longitude, session.jailLat, session.jailLng) <= session.jailRadiusMeters &&
            room.players.values.any { it.role == Role.HIDER && it.isInJail }
        val huntersNearJail = room.players.values.filter { player ->
            player.role == Role.SEEKER && player.latitude != 0.0 &&
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
                pendingCatchConfirm = room.pendingCatchConfirm
            )
        }
        recalculateDistancesAndProximity()
        val attempt = room.lastCatchAttempt
        if (state.isOrganizer && attempt != null && attempt.timestamp > lastHandledCatchAttemptTimestamp) {
            lastHandledCatchAttemptTimestamp = attempt.timestamp
            processCatchAttempt(attempt)
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
        val session = _uiState.value.session
        val radius = session?.currentDynamicZoneRadiusMeters?.takeIf { it > 0.0 }
            ?: session?.zoneRadiusMeters ?: 1000.0
        val unlimited = session?.isUnlimitedZone ?: false
        val geofence = if (session != null && radius > 0.0 && session.zoneCenterLat != 0.0 && !unlimited) {
            GeofenceManager.evaluate(lat, lng, session.zoneCenterLat, session.zoneCenterLng, radius)
        } else {
            com.manhunt.app.util.GeofenceEvaluation(GeofenceStatus.INSIDE, 0.0, 0.0, false)
        }
        val safeZone = session?.safeZones?.firstOrNull { zone ->
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
        if (session != null && session.joinCode.isNotEmpty() &&
            (previousMe.isInSafeZone != me.isInSafeZone || previousMe.safeZoneName != me.safeZoneName)) {
            viewModelScope.launch {
                firebaseSync.patchPlayer(session.joinCode, me.id, mapOf(
                    "isInSafeZone" to me.isInSafeZone,
                    "safeZoneName" to (me.safeZoneName ?: "")
                ))
            }
        }
        recalculateDistancesAndProximity()
    }

    private fun recalculateDistancesAndProximity() {
        val state = _uiState.value
        val me = state.myPlayer
        val others = state.players.filter { it.id != me.id }
        if (me.role == Role.HIDER) {
            var nearest = Double.MAX_VALUE
            var bearing = 0f
            others.filter { it.role == Role.SEEKER && it.latitude != 0.0 && it.longitude != 0.0 }.forEach { seeker ->
                val distance = GeoUtils.distanceMeters(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                if (distance < nearest) {
                    nearest = distance
                    bearing = GeoUtils.bearingDegrees(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                }
            }
            val distance = if (nearest == Double.MAX_VALUE) -1.0 else nearest
            if (distance >= 0.0) hapticManager.evaluateProximityHaptics(distance)
            _uiState.update { it.copy(nearestSeekerDistance = distance, nearestSeekerBearing = bearing, nearbyCatchableHider = null) }
        } else {
            val hiders = others.filter { it.role == Role.HIDER && !it.isCaught }
            val catchable = hiders.firstOrNull { hider ->
                !hider.isInSafeZone && hider.immunityUntilEpochMs <= System.currentTimeMillis() &&
                    hider.latitude != 0.0 && hider.longitude != 0.0 &&
                    GeoUtils.distanceMeters(me.latitude, me.longitude, hider.latitude, hider.longitude) <= Constants.CATCH_PROXIMITY_METERS
            }
            val protected = hiders.firstOrNull { it.isInSafeZone }
            _uiState.update {
                it.copy(nearbyCatchableHider = catchable, safeZoneNotice = protected?.let { h -> "${h.name} is protected in ${h.safeZoneName?.takeIf { it.isNotBlank() } ?: "Safe Zone"}!" })
            }
        }
    }

    private fun processCatchAttempt(attempt: CatchAttemptPayload) {
        val state = _uiState.value
        val session = state.session ?: return
        if (!state.isOrganizer || session.status != GameStatus.RUNNING || session.isPaused) return
        val seeker = state.players.firstOrNull { it.id == attempt.seekerId } ?: return
        val target = state.players.firstOrNull { it.id == attempt.targetHiderId } ?: return
        if (seeker.role != Role.SEEKER || target.role != Role.HIDER || target.isCaught || target.isInSafeZone) return
        if (target.immunityUntilEpochMs > System.currentTimeMillis()) return
        if (seeker.id == target.id || seeker.id != attempt.seekerId) return
        val distance = GeoUtils.distanceMeters(seeker.latitude, seeker.longitude, target.latitude, target.longitude)
        if (distance > Constants.CATCH_PROXIMITY_METERS || target.catchCode.trim() != attempt.enteredCode.trim()) return
        val sendToJail = session.jailEnabled && session.jailLat != 0.0
        val caught = target.copy(isCaught = true, isInJail = sendToJail, isStealthActive = false)
        viewModelScope.launch {
            if (firebaseSync.joinPlayer(session.joinCode, caught)) {
                _uiState.update { current ->
                    current.copy(players = current.players.map { player -> if (player.id == target.id) caught else player })
                }
                val remainingHiders = state.players.filter { it.role == Role.HIDER && it.id != target.id && !it.isCaught }
                if (remainingHiders.isEmpty()) endGame(Role.SEEKER)
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
        super.onCleared()
        countdownTimerJob?.cancel()
        compass.stopListening()
    }
}
