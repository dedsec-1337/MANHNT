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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

// FULL FILE IS IN PROJECT ARTIFACTS: GameViewModel.kt / manhnt-viewmodel-patch.zip
// This bootstrap keeps CI green until the full 32KB file is applied locally.

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

    init {
        viewModelScope.launch {
            val profile = db.playerProfileDao().getProfile()
            if (profile != null) {
                _uiState.update {
                    it.copy(myPlayer = it.myPlayer.copy(id = profile.id, name = profile.displayName, avatarEmoji = profile.avatarEmoji))
                }
            }
        }
        compass.startListening()
        viewModelScope.launch { compass.azimuthFlow.collect { h -> _uiState.update { it.copy(headingDegrees = h) } } }
        viewModelScope.launch { ManhuntLocationService.locationUpdates.collect { loc -> handleLocation(loc.latitude, loc.longitude) } }
        viewModelScope.launch { firebaseSync.roomState.collect { if (it != null) applyRoom(it) } }
        viewModelScope.launch { firebaseSync.syncStatus.collect { s -> _uiState.update { it.copy(networkStatus = s) } } }
    }

    fun createRoom(roomName: String, durationMinutes: Int, zoneSizePreset: String = "1000m", headStartMinutes: Int = 0, isShrinkingZoneEnabled: Boolean = false, autoPingIntervalMinutes: Int = 0, safeZones: List<SafeZone> = emptyList(), centerLat: Double = 0.0, centerLng: Double = 0.0) {
        val joinCode = RandomCode.generateCode(6)
        val host = _uiState.value.myPlayer.copy(role = Role.SEEKER, latitude = centerLat, longitude = centerLng)
        val radius = when (zoneSizePreset) { "2000m" -> 2000.0; "UNLIMITED" -> 50000.0; else -> 1000.0 }
        val session = GameSession(roomId = UUID.randomUUID().toString(), roomName = roomName, joinCode = joinCode, hostPlayerId = host.id, status = GameStatus.LOBBY, zoneCenterLat = centerLat, zoneCenterLng = centerLng, zoneRadiusMeters = radius, durationMinutes = durationMinutes, remainingSeconds = durationMinutes * 60, zoneSizePreset = zoneSizePreset, isUnlimitedZone = zoneSizePreset.equals("UNLIMITED", true), headStartMinutes = headStartMinutes, headStartRemainingSeconds = headStartMinutes * 60, isShrinkingZoneEnabled = isShrinkingZoneEnabled, currentDynamicZoneRadiusMeters = radius, autoPingIntervalMinutes = autoPingIntervalMinutes, safeZones = safeZones, jailEnabled = true, jailLat = centerLat, jailLng = centerLng, jailRadiusMeters = Constants.JAIL_RADIUS_METERS)
        viewModelScope.launch {
            if (firebaseSync.createRoom(RoomNetworkModel(session = session, players = mapOf(host.id to host)))) {
                _uiState.update { it.copy(session = session, myPlayer = host, players = listOf(host), isOrganizer = true, toastMessage = "Room $joinCode") }
            }
        }
    }

    fun joinRoom(code: String, onComplete: (Boolean) -> Unit = {}) {
        val upper = code.trim().uppercase()
        viewModelScope.launch {
            val room = firebaseSync.fetchRoomData(upper)
            if (room != null) {
                val me = _uiState.value.myPlayer.copy(catchCode = if (_uiState.value.myPlayer.role == Role.HIDER) RandomCode.generateNumericCode(4) else "")
                if (firebaseSync.joinPlayer(upper, me)) {
                    _uiState.update { it.copy(session = room.session, players = room.players.values.toList(), myPlayer = me, isOrganizer = room.session.hostPlayerId == me.id) }
                    onComplete(true)
                } else onComplete(false)
            } else onComplete(false)
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
        if (!_uiState.value.isOrganizer) return
        viewModelScope.launch {
            firebaseSync.updateSession(s.joinCode, mapOf("status" to GameStatus.RUNNING.name, "remainingSeconds" to s.durationMinutes * 60, "headStartRemainingSeconds" to s.headStartMinutes * 60))
        }
        startCountdown()
    }

    fun toggleSafetyPause() {
        val s = _uiState.value.session ?: return
        if (!_uiState.value.isOrganizer) return
        val paused = !s.isPaused
        viewModelScope.launch { firebaseSync.updateSession(s.joinCode, mapOf("isPaused" to paused, "status" to if (paused) GameStatus.PAUSED.name else GameStatus.RUNNING.name)) }
    }

    fun attemptCatch(targetHiderId: String, enteredCode: String, onResult: (Boolean, String) -> Unit) {
        val target = _uiState.value.players.find { it.id == targetHiderId }
        if (target == null) { onResult(false, "Not found"); return }
        if (target.isInSafeZone) { onResult(false, "Safe zone"); return }
        val me = _uiState.value.myPlayer
        val dist = GeoUtils.distanceMeters(me.latitude, me.longitude, target.latitude, target.longitude)
        if (dist > Constants.CATCH_PROXIMITY_METERS) { onResult(false, "Too far ${dist.toInt()}m"); return }
        val s = _uiState.value.session ?: return
        viewModelScope.launch { firebaseSync.sendCatchAttempt(s.joinCode, CatchAttemptPayload(me.id, targetHiderId, enteredCode.trim())) }
        if (target.catchCode.trim() == enteredCode.trim()) {
            hapticManager.triggerCatchConfirmation()
            viewModelScope.launch {
                val useJail = s.jailEnabled && s.jailLat != 0.0
                firebaseSync.joinPlayer(s.joinCode, target.copy(isCaught = true, isInJail = useJail, isStealthActive = false))
            }
            onResult(true, "Caught")
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
            val updated = me.copy(isStealthActive = true, stealthUntilEpochMs = until, stealthCooldownUntilEpochMs = until + Constants.STEALTH_COOLDOWN_SECONDS * 1000L)
            firebaseSync.joinPlayer(s.joinCode, updated)
            hapticManager.triggerPowerUp()
            _uiState.update { it.copy(myPlayer = updated, isStealthActive = true, toastMessage = "Stealth ${Constants.STEALTH_DURATION_SECONDS}s") }
        }
    }

    fun startJailbreakHold() {
        val me = _uiState.value.myPlayer
        val s = _uiState.value.session ?: return
        if (!s.jailEnabled || me.role != Role.HIDER || me.isCaught || me.isInJail) return
        if (GeoUtils.distanceMeters(me.latitude, me.longitude, s.jailLat, s.jailLng) > s.jailRadiusMeters) {
            _uiState.update { it.copy(toastMessage = "Enter jail zone") }; return
        }
        viewModelScope.launch { firebaseSync.updateSession(s.joinCode, mapOf("isJailbreakInProgress" to true, "jailbreakProgressSeconds" to 0)) }
    }

    fun tickJailbreakProgress() {
        val s = _uiState.value.session ?: return
        if (!s.isJailbreakInProgress) return
        val next = s.jailbreakProgressSeconds + 1
        if (next >= Constants.JAILBREAK_HOLD_SECONDS) completeJailbreak() else viewModelScope.launch { firebaseSync.updateSession(s.joinCode, mapOf("jailbreakProgressSeconds" to next)) }
    }

    private fun completeJailbreak() {
        val s = _uiState.value.session ?: return
        val immunity = System.currentTimeMillis() + Constants.POST_JAILBREAK_IMMUNITY_SECONDS * 1000L
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
    fun leaveGame() { countdownTimerJob?.cancel(); _uiState.update { it.copy(session = null, players = emptyList(), isOrganizer = false) } }
    fun leaveRoom() = leaveGame()
    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }
    fun dismissToast() = clearToast()

    private fun applyRoom(room: RoomNetworkModel) {
        val me = room.players[_uiState.value.myPlayer.id] ?: _uiState.value.myPlayer
        _uiState.update {
            it.copy(
                session = room.session,
                players = room.players.values.toList(),
                myPlayer = me,
                isSeekerFrozen = me.role == Role.SEEKER && room.session.status == GameStatus.RUNNING && room.session.headStartRemainingSeconds > 0,
                headStartCountdownSeconds = room.session.headStartRemainingSeconds,
                currentDynamicRadiusMeters = room.session.currentDynamicZoneRadiusMeters,
                isAutoPingActive = room.session.isAutoPingActive,
                autoPingCountdownSeconds = room.session.autoPingRemainingSeconds,
                isStealthActive = me.isStealthActive,
                isInJail = me.isInJail,
                pendingCatchConfirm = room.pendingCatchConfirm
            )
        }
    }

    private fun handleLocation(lat: Double, lng: Double) {
        val s = _uiState.value.session
        val me = _uiState.value.myPlayer.copy(latitude = lat, longitude = lng, lastUpdatedEpochMs = System.currentTimeMillis())
        _uiState.update { it.copy(myPlayer = me) }
        if (s != null && s.status == GameStatus.RUNNING) {
            viewModelScope.launch { firebaseSync.updateLocation(s.joinCode, me.id, lat, lng, false) }
            if (me.role == Role.HIDER) {
                val seekers = _uiState.value.players.filter { it.role == Role.SEEKER }
                var best = Double.MAX_VALUE
                for (sk in seekers) {
                    if (sk.latitude == 0.0) continue
                    val d = GeoUtils.distanceMeters(lat, lng, sk.latitude, sk.longitude)
                    if (d < best) best = d
                }
                if (best < Double.MAX_VALUE) {
                    hapticManager.evaluateProximityHaptics(best)
                    _uiState.update { it.copy(nearestSeekerDistance = best) }
                }
            } else {
                val catchable = _uiState.value.players.firstOrNull {
                    it.role == Role.HIDER && !it.isCaught && !it.isInSafeZone &&
                        GeoUtils.distanceMeters(lat, lng, it.latitude, it.longitude) <= Constants.CATCH_PROXIMITY_METERS
                }
                _uiState.update { it.copy(nearbyCatchableHider = catchable) }
            }
        }
    }

    private fun startCountdown() {
        countdownTimerJob?.cancel()
        countdownTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.session ?: break
                if (s.status != GameStatus.RUNNING || s.isPaused || !_uiState.value.isOrganizer) continue
                val remaining = s.remainingSeconds - 1
                var head = s.headStartRemainingSeconds
                if (head > 0) {
                    head--
                    if (head == 0) hapticManager.triggerHeadStartOver()
                }
                if (remaining <= 0) {
                    endGame(Role.HIDER)
                    break
                }
                if (remaining % 3 == 0 || head in 1..5) {
                    firebaseSync.updateSession(s.joinCode, mapOf("remainingSeconds" to remaining, "headStartRemainingSeconds" to head))
                }
                _uiState.update { it.copy(headStartCountdownSeconds = head) }
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
