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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

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

    // Advanced Mechanics State
    val isSeekerFrozen: Boolean = false,
    val headStartCountdownSeconds: Int = 0,
    val isShrinkingZoneActive: Boolean = false,
    val currentDynamicRadiusMeters: Double = 1000.0,
    val isAutoPingActive: Boolean = false,
    val autoPingCountdownSeconds: Int = 0,
    val isInSafeZone: Boolean = false,
    val currentSafeZoneName: String? = null,
    val safeZoneNotice: String? = null
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ManhuntApp).database
    private val firebaseSync = FirebaseSyncService.instance
    private val compass = CompassSensorManager(application)
    private val hapticManager = HapticFeedbackManager(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var countdownTimerJob: Job? = null
    private var lastHandledCatchAttemptTimestamp: Long = 0L

    init {
        loadUserProfile()
        observeSensorsAndLocation()
        observeCloudSync()
        refreshBatteryOptimizationStatus()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val profile = db.playerProfileDao().getProfile()
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        myPlayer = it.myPlayer.copy(
                            id = profile.id,
                            name = profile.displayName,
                            avatarEmoji = profile.avatarEmoji
                        )
                    )
                }
            } else {
                val newProfile = PlayerProfileEntity(
                    id = _uiState.value.myPlayer.id,
                    displayName = "Agent ${RandomCode.generateCode(3)}",
                    avatarEmoji = "🏃"
                )
                db.playerProfileDao().upsertProfile(newProfile)
                _uiState.update {
                    it.copy(
                        myPlayer = it.myPlayer.copy(
                            id = newProfile.id,
                            name = newProfile.displayName,
                            avatarEmoji = newProfile.avatarEmoji
                        )
                    )
                }
            }
        }
    }

    fun updateProfile(name: String, emoji: String) {
        viewModelScope.launch {
            val updated = _uiState.value.myPlayer.copy(name = name, avatarEmoji = emoji)
            db.playerProfileDao().upsertProfile(
                PlayerProfileEntity(updated.id, updated.name, updated.avatarEmoji)
            )
            _uiState.update { it.copy(myPlayer = updated) }
        }
    }

    private fun observeSensorsAndLocation() {
        compass.startListening()
        viewModelScope.launch {
            compass.azimuthFlow.collect { heading ->
                _uiState.update { it.copy(headingDegrees = heading) }
            }
        }

        viewModelScope.launch {
            ManhuntLocationService.locationUpdates.collect { loc ->
                handleMyLocationUpdate(loc.latitude, loc.longitude)
            }
        }
    }

    private fun observeCloudSync() {
        viewModelScope.launch {
            firebaseSync.syncStatus.collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }

        viewModelScope.launch {
            firebaseSync.roomState.collect { roomModel ->
                if (roomModel != null) {
                    processIncomingRoomState(roomModel)
                }
            }
        }
    }

    /**
     * Organizer creates room on Firebase Realtime Database with Advanced Settings
     */
    fun createRoom(
        roomName: String,
        durationMinutes: Int,
        zoneSizePreset: String = "1000m", // "1000m", "2000m", "UNLIMITED"
        headStartMinutes: Int = 0,
        isShrinkingZoneEnabled: Boolean = false,
        autoPingIntervalMinutes: Int = 0,
        safeZones: List<SafeZone> = emptyList(),
        centerLat: Double = 40.785091,
        centerLng: Double = -73.968285
    ) {
        val joinCode = RandomCode.generateCode(6)
        val hostPlayer = _uiState.value.myPlayer.copy(
            role = Role.SEEKER,
            latitude = centerLat,
            longitude = centerLng
        )

        val isUnlimited = zoneSizePreset.equals("UNLIMITED", ignoreCase = true)
        val baseRadius = when (zoneSizePreset) {
            "2000m" -> 2000.0
            "UNLIMITED" -> 50000.0
            else -> 1000.0
        }

        val newSession = GameSession(
            roomId = UUID.randomUUID().toString(),
            roomName = roomName,
            joinCode = joinCode,
            hostPlayerId = hostPlayer.id,
            status = GameStatus.LOBBY,
            zoneCenterLat = centerLat,
            zoneCenterLng = centerLng,
            zoneRadiusMeters = baseRadius,
            durationMinutes = durationMinutes,
            remainingSeconds = durationMinutes * 60,
            zoneSizePreset = zoneSizePreset,
            isUnlimitedZone = isUnlimited,
            headStartMinutes = headStartMinutes,
            headStartRemainingSeconds = headStartMinutes * 60,
            isShrinkingZoneEnabled = isShrinkingZoneEnabled,
            currentDynamicZoneRadiusMeters = baseRadius,
            autoPingIntervalMinutes = autoPingIntervalMinutes,
            safeZones = safeZones
        )

        val roomNetworkModel = RoomNetworkModel(
            session = newSession,
            players = mapOf(hostPlayer.id to hostPlayer)
        )

        viewModelScope.launch {
            val success = firebaseSync.createRoom(roomNetworkModel)
            if (success) {
                _uiState.update {
                    it.copy(
                        session = newSession,
                        myPlayer = hostPlayer,
                        players = listOf(hostPlayer),
                        isOrganizer = true,
                        currentDynamicRadiusMeters = baseRadius,
                        toastMessage = "Room created! Share code: $joinCode"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(toastMessage = "Failed to create room. Check cellular data connection.")
                }
            }
        }
    }

    /**
     * Player joins room by fetching /rooms/{joinCode}.json from Firebase REST
     */
    fun joinRoom(code: String, onComplete: (Boolean) -> Unit = {}) {
        val upperCode = code.trim().uppercase()
        if (upperCode.length < 4) return

        _uiState.update { it.copy(isJoiningRoom = true) }

        viewModelScope.launch {
            val roomData = firebaseSync.fetchRoomData(upperCode)
            if (roomData != null && roomData.session.joinCode.isNotEmpty()) {
                val catchCode = if (_uiState.value.myPlayer.role == Role.HIDER) {
                    RandomCode.generateNumericCode(4)
                } else ""
                val playerToJoin = _uiState.value.myPlayer.copy(catchCode = catchCode)

                val joinSuccess = firebaseSync.joinPlayer(upperCode, playerToJoin)
                if (joinSuccess) {
                    _uiState.update {
                        it.copy(
                            session = roomData.session,
                            players = roomData.players.values.toList(),
                            myPlayer = playerToJoin,
                            isOrganizer = roomData.session.hostPlayerId == playerToJoin.id,
                            currentDynamicRadiusMeters = roomData.session.currentDynamicZoneRadiusMeters,
                            isJoiningRoom = false,
                            toastMessage = "Joined ${roomData.session.roomName}!"
                        )
                    }
                    onComplete(true)
                } else {
                    _uiState.update {
                        it.copy(isJoiningRoom = false, toastMessage = "Failed to join room. Check connection.")
                    }
                    onComplete(false)
                }
            } else {
                _uiState.update {
                    it.copy(isJoiningRoom = false, toastMessage = "Game code '$upperCode' not found.")
                }
                onComplete(false)
            }
        }
    }

    private fun processIncomingRoomState(roomModel: RoomNetworkModel) {
        val playerList = roomModel.players.values.toList()
        val myId = _uiState.value.myPlayer.id
        val updatedMe = roomModel.players[myId] ?: _uiState.value.myPlayer
        val s = roomModel.session

        val isSeekerFrozen = updatedMe.role == Role.SEEKER && s.status == GameStatus.RUNNING && s.headStartRemainingSeconds > 0

        _uiState.update {
            it.copy(
                session = s,
                players = playerList,
                myPlayer = updatedMe,
                isSeekerFrozen = isSeekerFrozen,
                headStartCountdownSeconds = s.headStartRemainingSeconds,
                currentDynamicRadiusMeters = s.currentDynamicZoneRadiusMeters,
                isAutoPingActive = s.isAutoPingActive,
                autoPingCountdownSeconds = s.autoPingRemainingSeconds
            )
        }

        recalculateDistancesAndProximity()

        // Handle catch attempts (Organizer authoritative referee)
        if (_uiState.value.isOrganizer && roomModel.lastCatchAttempt != null) {
            val attempt = roomModel.lastCatchAttempt
            if (attempt.timestamp > lastHandledCatchAttemptTimestamp) {
                lastHandledCatchAttemptTimestamp = attempt.timestamp
                processCatchAttempt(attempt)
            }
        }
    }

    /**
     * Organizer toggles player role in Cloud Realtime Database
     */
    fun togglePlayerRole(playerId: String) {
        if (!_uiState.value.isOrganizer) return
        val current = _uiState.value.players.find { it.id == playerId } ?: return
        val nextRole = if (current.role == Role.SEEKER) Role.HIDER else Role.SEEKER
        val catchCode = if (nextRole == Role.HIDER) RandomCode.generateNumericCode(4) else ""
        val updatedPlayer = current.copy(role = nextRole, catchCode = catchCode)

        viewModelScope.launch {
            val joinCode = _uiState.value.session?.joinCode ?: return@launch
            firebaseSync.joinPlayer(joinCode, updatedPlayer)
        }
    }

    /**
     * Organizer auto balances roles
     */
    fun autoAssignRoles(ratio: Int = 3) {
        if (!_uiState.value.isOrganizer) return
        val players = _uiState.value.players
        if (players.isEmpty()) return
        val hiderCount = maxOf(1, players.size / (ratio + 1))
        val shuffled = players.shuffled()
        val joinCode = _uiState.value.session?.joinCode ?: return

        viewModelScope.launch {
            shuffled.forEachIndexed { index, p ->
                val isHider = index < hiderCount
                val updated = p.copy(
                    role = if (isHider) Role.HIDER else Role.SEEKER,
                    catchCode = if (isHider) RandomCode.generateNumericCode(4) else ""
                )
                firebaseSync.joinPlayer(joinCode, updated)
            }
        }
    }

    /**
     * Start match with Head Start & Advanced Mechanics
     */
    fun startGame() {
        if (!_uiState.value.isOrganizer) return
        val s = _uiState.value.session ?: return
        val headStartSec = s.headStartMinutes * 60

        viewModelScope.launch {
            firebaseSync.updateSession(
                s.joinCode,
                mapOf(
                    "status" to GameStatus.RUNNING.name,
                    "remainingSeconds" to (s.durationMinutes * 60),
                    "headStartRemainingSeconds" to headStartSec,
                    "currentDynamicZoneRadiusMeters" to s.zoneRadiusMeters,
                    "isAutoPingActive" to false,
                    "autoPingRemainingSeconds" to 0
                )
            )
        }
        startMasterCountdown()
    }

    /**
     * Safety pause toggle
     */
    fun toggleSafetyPause() {
        if (!_uiState.value.isOrganizer) return
        val s = _uiState.value.session ?: return
        val nextPaused = !s.isPaused

        viewModelScope.launch {
            firebaseSync.updateSession(
                s.joinCode,
                mapOf(
                    "isPaused" to nextPaused,
                    "status" to (if (nextPaused) GameStatus.PAUSED.name else GameStatus.RUNNING.name)
                )
            )
        }
    }

    private fun startMasterCountdown() {
        countdownTimerJob?.cancel()
        countdownTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.session ?: break
                if (s.status == GameStatus.RUNNING && !s.isPaused && _uiState.value.isOrganizer) {
                    val remaining = s.remainingSeconds - 1
                    val totalSec = s.durationMinutes * 60

                    // 1. Head Start Countdown
                    var headStartLeft = s.headStartRemainingSeconds
                    if (headStartLeft > 0) {
                        headStartLeft--
                        if (headStartLeft == 0) {
                            hapticManager.triggerHeadStartOver()
                        }
                    }

                    // 2. Shrinking Zone: Activates during the last 25% of the match duration
                    var dynamicRadius = s.zoneRadiusMeters
                    var isShrinking = false
                    if (s.isShrinkingZoneEnabled && !s.isUnlimitedZone) {
                        val shrinkingThreshold = (totalSec * 0.25).toInt()
                        if (remaining <= shrinkingThreshold) {
                            isShrinking = true
                            val progress = remaining.toDouble() / maxOf(1, shrinkingThreshold).toDouble()
                            dynamicRadius = maxOf(100.0, 100.0 + (s.zoneRadiusMeters - 100.0) * progress)
                        }
                    }

                    // 3. Auto-Ping Mechanic (Anti-Camping)
                    var isPingActive = false
                    var pingLeft = 0
                    if (s.autoPingIntervalMinutes > 0) {
                        val intervalSec = s.autoPingIntervalMinutes * 60
                        val elapsedSec = totalSec - remaining
                        if (elapsedSec > 0) {
                            val cyclePos = elapsedSec % intervalSec
                            if (cyclePos in 0..9) { // 10-second reveal window
                                isPingActive = true
                                pingLeft = 10 - cyclePos
                            }
                        }
                    }

                    if (remaining <= 0) {
                        endGame(winner = Role.HIDER) // Hiders survived!
                        break
                    } else {
                        // Periodic push to Firebase REST
                        if (remaining % 3 == 0 || headStartLeft in 1..5 || isPingActive) {
                            firebaseSync.updateSession(
                                s.joinCode,
                                mapOf(
                                    "remainingSeconds" to remaining,
                                    "headStartRemainingSeconds" to headStartLeft,
                                    "currentDynamicZoneRadiusMeters" to dynamicRadius,
                                    "isAutoPingActive" to isPingActive,
                                    "autoPingRemainingSeconds" to pingLeft
                                )
                            )
                        }

                        _uiState.update {
                            it.copy(
                                headStartCountdownSeconds = headStartLeft,
                                isShrinkingZoneActive = isShrinking,
                                currentDynamicRadiusMeters = dynamicRadius,
                                isAutoPingActive = isPingActive,
                                autoPingCountdownSeconds = pingLeft
                            )
                        }
                    }
                }
            }
        }
    }

    fun toggleDarkMode() {
        _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    fun refreshBatteryOptimizationStatus() {
        val app = getApplication<Application>()
        val isExempt = BatteryOptimizationHelper.isBatteryOptimizationDisabled(app)
        _uiState.update { it.copy(isBatteryOptimizationExempt = isExempt) }
    }

    private fun handleMyLocationUpdate(lat: Double, lng: Double) {
        val s = _uiState.value.session
        val effectiveRadius = s?.currentDynamicZoneRadiusMeters ?: s?.zoneRadiusMeters ?: 1000.0
        val isUnlimited = s?.isUnlimitedZone ?: false

        // 1. Geofence Evaluation
        val geofenceEval = if (s != null && effectiveRadius > 0 && s.zoneCenterLat != 0.0 && !isUnlimited) {
            GeofenceManager.evaluate(lat, lng, s.zoneCenterLat, s.zoneCenterLng, effectiveRadius)
        } else {
            com.manhunt.app.util.GeofenceEvaluation(
                status = GeofenceStatus.INSIDE,
                distanceFromCenterMeters = 0.0,
                distanceToBoundaryMeters = 0.0,
                isOutside = false
            )
        }

        // 2. Safe Zone Evaluation
        val safeZone = s?.safeZones?.firstOrNull { zone ->
            GeoUtils.distanceMeters(lat, lng, zone.latitude, zone.longitude) <= zone.radiusMeters
        }
        val inSafeZone = safeZone != null
        val safeZoneName = safeZone?.name

        val updatedMe = _uiState.value.myPlayer.copy(
            latitude = lat,
            longitude = lng,
            isOutOfZone = geofenceEval.isOutside,
            isInSafeZone = inSafeZone,
            safeZoneName = safeZoneName,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                myPlayer = updatedMe,
                isOutOfZone = geofenceEval.isOutside,
                geofenceStatus = geofenceEval.status,
                geofenceMessage = geofenceEval.warningMessage,
                isInSafeZone = inSafeZone,
                currentSafeZoneName = safeZoneName
            )
        }

        // If Seeker is frozen under head start, suppress broadcasting location to protect Hiders
        val isSeekerFrozen = updatedMe.role == Role.SEEKER && (s?.headStartRemainingSeconds ?: 0) > 0
        if (!isSeekerFrozen && s != null && s.joinCode.isNotEmpty()) {
            viewModelScope.launch {
                firebaseSync.updateLocation(s.joinCode, updatedMe.id, lat, lng, geofenceEval.isOutside)
            }
        }

        recalculateDistancesAndProximity()
    }

    private fun recalculateDistancesAndProximity() {
        val me = _uiState.value.myPlayer
        val others = _uiState.value.players.filter { it.id != me.id }

        if (me.role == Role.HIDER) {
            val seekers = others.filter { it.role == Role.SEEKER }
            var minDist = Double.MAX_VALUE
            var bestBearing = 0f

            for (seeker in seekers) {
                if (seeker.latitude != 0.0 && seeker.longitude != 0.0) {
                    val dist = GeoUtils.distanceMeters(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                    if (dist < minDist) {
                        minDist = dist
                        bestBearing = GeoUtils.bearingDegrees(me.latitude, me.longitude, seeker.latitude, seeker.longitude)
                    }
                }
            }

            val finalDist = if (minDist == Double.MAX_VALUE) -1.0 else minDist

            // Trigger outdoor haptic feedback alert for Hider
            if (finalDist >= 0.0) {
                hapticManager.evaluateProximityHaptics(finalDist)
            }

            _uiState.update {
                it.copy(
                    nearestSeekerDistance = finalDist,
                    nearestSeekerBearing = bestBearing
                )
            }
        } else {
            // Seeker view
            val uncaughtHiders = others.filter { it.role == Role.HIDER && !it.isCaught }
            val catchable = uncaughtHiders.firstOrNull { hider ->
                if (hider.latitude != 0.0 && hider.longitude != 0.0) {
                    val dist = GeoUtils.distanceMeters(me.latitude, me.longitude, hider.latitude, hider.longitude)
                    dist <= Constants.CATCH_PROXIMITY_METERS
                } else false
            }

            // Safe Zone notice if any nearby hider is shielded
            val protectedHider = uncaughtHiders.firstOrNull { it.isInSafeZone }
            val safeNotice = protectedHider?.let { "${it.name} is protected in ${it.safeZoneName ?: "Safe Zone"}!" }

            _uiState.update {
                it.copy(
                    nearbyCatchableHider = catchable,
                    safeZoneNotice = safeNotice
                )
            }
        }
    }

    /**
     * Seeker attempts to catch Hider by entering their 4-digit code
     */
    fun attemptCatch(targetHiderId: String, enteredCode: String, onResult: (Boolean, String) -> Unit) {
        val s = _uiState.value.session ?: return
        val me = _uiState.value.myPlayer
        val target = _uiState.value.players.find { it.id == targetHiderId }

        if (target == null) {
            onResult(false, "Player not found.")
            return
        }

        // Safe zone invulnerability check
        if (target.isInSafeZone) {
            onResult(false, "${target.name} is inside a Safe Zone and cannot be caught!")
            return
        }

        // Distance proximity verification (<10m)
        val distance = GeoUtils.distanceMeters(me.latitude, me.longitude, target.latitude, target.longitude)
        if (distance > Constants.CATCH_PROXIMITY_METERS) {
            onResult(false, "Too far! You must be within 10 meters (Current: ${distance.toInt()}m).")
            return
        }

        // Push attempt to Firebase REST
        viewModelScope.launch {
            val payload = CatchAttemptPayload(
                seekerId = me.id,
                targetHiderId = targetHiderId,
                enteredCode = enteredCode.trim()
            )
            firebaseSync.submitCatchAttempt(s.joinCode, payload)
        }

        // Instant optimistic feedback
        if (target.catchCode.trim() == enteredCode.trim()) {
            hapticManager.triggerCatchConfirmation()
            onResult(true, "Catch confirmed! ${target.name} is OUT.")
        } else {
            onResult(false, "Incorrect code! Ask the hider for their 4-digit PIN.")
        }
    }

    private fun processCatchAttempt(attempt: CatchAttemptPayload) {
        val target = _uiState.value.players.find { it.id == attempt.targetHiderId } ?: return
        val joinCode = _uiState.value.session?.joinCode ?: return

        if (!target.isInSafeZone && target.catchCode.trim() == attempt.enteredCode.trim()) {
            viewModelScope.launch {
                val updatedTarget = target.copy(isCaught = true)
                firebaseSync.joinPlayer(joinCode, updatedTarget)

                val updatedPlayers = _uiState.value.players.map {
                    if (it.id == target.id) updatedTarget else it
                }

                val allCaught = updatedPlayers.filter { it.role == Role.HIDER }.all { it.isCaught }
                if (allCaught) {
                    endGame(winner = Role.SEEKER)
                }
            }
        }
    }

    private fun endGame(winner: Role) {
        val s = _uiState.value.session ?: return
        countdownTimerJob?.cancel()

        val catches = _uiState.value.players.count { it.role == Role.HIDER && it.isCaught }

        viewModelScope.launch {
            firebaseSync.updateSession(
                s.joinCode,
                mapOf(
                    "status" to GameStatus.FINISHED.name,
                    "winnerRole" to winner.name,
                    "totalCatches" to catches
                )
            )

            db.matchHistoryDao().insertMatch(
                MatchHistoryEntity(
                    roomId = s.roomId,
                    roomName = s.roomName,
                    role = _uiState.value.myPlayer.role.name,
                    durationSeconds = (s.durationMinutes * 60) - s.remainingSeconds,
                    catches = catches,
                    didWin = (_uiState.value.myPlayer.role == winner)
                )
            )
        }
    }

    fun leaveRoom() {
        countdownTimerJob?.cancel()
        _uiState.update {
            it.copy(
                session = null,
                players = emptyList(),
                isOrganizer = false
            )
        }
    }

    fun leaveGame() = leaveRoom()

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun dismissToast() = clearToast()

    override fun onCleared() {
        super.onCleared()
        countdownTimerJob?.cancel()
        compass.stopListening()
    }
}
