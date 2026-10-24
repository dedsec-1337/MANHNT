package com.manhunt.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manhunt.app.ManhuntApp
import com.manhunt.app.data.local.MatchHistoryEntity
import com.manhunt.app.data.local.PlayerProfileEntity
import com.manhunt.app.data.model.*
import com.manhunt.app.network.P2PNetworkManager
import com.manhunt.app.sensor.CompassSensorManager
import com.manhunt.app.service.ManhuntLocationService
import com.manhunt.app.util.GeoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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
    val toastMessage: String? = null,
    val discoveredRooms: List<DiscoveredRoom> = emptyList()
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ManhuntApp).database
    private val p2p = P2PNetworkManager(application)
    private val compass = CompassSensorManager(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var locationJob: Job? = null

    init {
        loadUserProfile()
        observeSensorAndLocation()
        observeNetwork()
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

    private fun observeSensorAndLocation() {
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

    private fun observeNetwork() {
        p2p.startDiscoveryListener()

        viewModelScope.launch {
            p2p.discoveredRooms.collect { map ->
                _uiState.update { it.copy(discoveredRooms = map.values.toList()) }
            }
        }

        viewModelScope.launch {
            p2p.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    /**
     * Organizer creates a new Manhunt room.
     */
    fun createRoom(
        roomName: String,
        durationMinutes: Int,
        zoneRadiusMeters: Double,
        hiderRatio: Int,
        centerLat: Double = 40.785091,
        centerLng: Double = -73.968285
    ) {
        val joinCode = RandomCode.generateCode(6)
        val hostPlayer = _uiState.value.myPlayer.copy(
            role = Role.SEEKER,
            latitude = centerLat,
            longitude = centerLng
        )

        val newSession = GameSession(
            roomId = UUID.randomUUID().toString(),
            roomName = roomName,
            joinCode = joinCode,
            hostPlayerId = hostPlayer.id,
            status = GameStatus.LOBBY,
            zoneCenterLat = centerLat,
            zoneCenterLng = centerLng,
            zoneRadiusMeters = zoneRadiusMeters,
            durationMinutes = durationMinutes,
            remainingSeconds = durationMinutes * 60
        )

        _uiState.update {
            it.copy(
                session = newSession,
                myPlayer = hostPlayer,
                players = listOf(hostPlayer),
                isOrganizer = true
            )
        }

        p2p.startHosting(newSession)
    }

    /**
     * Player joins room using 6-character code or discovered room.
     */
    fun joinRoom(code: String, hostIp: String? = null) {
        val upperCode = code.trim().uppercase()
        val targetRoom = _uiState.value.discoveredRooms.find { it.joinCode.equals(upperCode, ignoreCase = true) }
        val ipToConnect = hostIp ?: targetRoom?.hostIp ?: "127.0.0.1"

        p2p.connectToHost(ipToConnect) {
            p2p.sendToHost(P2PPacket.JoinRequest(player = _uiState.value.myPlayer))
        }
    }

    /**
     * Organizer toggles player role between SEEKER and HIDER.
     */
    fun togglePlayerRole(playerId: String) {
        if (!_uiState.value.isOrganizer) return
        val currentPlayers = _uiState.value.players.map { p ->
            if (p.id == playerId) {
                val nextRole = if (p.role == Role.SEEKER) Role.HIDER else Role.SEEKER
                val catchCode = if (nextRole == Role.HIDER) RandomCode.generateNumericCode(4) else ""
                p.copy(role = nextRole, catchCode = catchCode)
            } else p
        }
        _uiState.update { it.copy(players = currentPlayers) }
        syncStateToClients()
    }

    /**
     * Automatically assigns roles according to 1 Hider : N Seekers ratio.
     */
    fun autoAssignRoles(ratio: Int = 3) {
        val players = _uiState.value.players
        if (players.isEmpty()) return
        val hiderCount = maxOf(1, players.size / (ratio + 1))
        val shuffled = players.shuffled()

        val updated = shuffled.mapIndexed { index, player ->
            val isHider = index < hiderCount
            player.copy(
                role = if (isHider) Role.HIDER else Role.SEEKER,
                catchCode = if (isHider) RandomCode.generateNumericCode(4) else ""
            )
        }

        val myUpdated = updated.find { it.id == _uiState.value.myPlayer.id } ?: _uiState.value.myPlayer

        _uiState.update {
            it.copy(
                players = updated,
                myPlayer = myUpdated
            )
        }
        syncStateToClients()
    }

    /**
     * Starts the game session.
     */
    fun startGame() {
        if (!_uiState.value.isOrganizer) return
        val s = _uiState.value.session ?: return
        val updatedSession = s.copy(status = GameStatus.RUNNING)

        _uiState.update { it.copy(session = updatedSession) }
        syncStateToClients()
        startMasterCountdown()
    }

    /**
     * Organizer triggers Safety Pause to freeze time and alerts players.
     */
    fun toggleSafetyPause() {
        if (!_uiState.value.isOrganizer) return
        val s = _uiState.value.session ?: return
        val newPaused = !s.isPaused
        val updated = s.copy(
            isPaused = newPaused,
            status = if (newPaused) GameStatus.PAUSED else GameStatus.RUNNING
        )
        _uiState.update { it.copy(session = updated) }
        p2p.broadcastToClients(P2PPacket.PauseToggle(isPaused = newPaused))
        syncStateToClients()
    }

    private fun startMasterCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.session ?: break
                if (s.status == GameStatus.RUNNING && !s.isPaused) {
                    val remaining = s.remainingSeconds - 1
                    if (remaining <= 0) {
                        endGame(winner = Role.HIDER) // Hiders survived!
                        break
                    } else {
                        val updated = s.copy(remainingSeconds = remaining)
                        _uiState.update { it.copy(session = updated) }
                        if (remaining % 5 == 0) {
                            syncStateToClients()
                        }
                    }
                }
            }
        }
    }

    private fun handleMyLocationUpdate(lat: Double, lng: Double) {
        val s = _uiState.value.session
        val isOut = if (s != null && s.zoneRadiusMeters > 0 && s.zoneCenterLat != 0.0) {
            GeoUtils.isOutsideZone(lat, lng, s.zoneCenterLat, s.zoneCenterLng, s.zoneRadiusMeters)
        } else false

        val updatedMe = _uiState.value.myPlayer.copy(
            latitude = lat,
            longitude = lng,
            isOutOfZone = isOut,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )

        _uiState.update { it.copy(myPlayer = updatedMe, isOutOfZone = isOut) }

        // Send to peers
        val updatePacket = P2PPacket.LocationUpdate(
            playerId = updatedMe.id,
            latitude = lat,
            longitude = lng,
            isOutOfZone = isOut
        )

        if (_uiState.value.isOrganizer) {
            // Update in local player list & broadcast
            val updatedList = _uiState.value.players.map { if (it.id == updatedMe.id) updatedMe else it }
            _uiState.update { it.copy(players = updatedList) }
            recalculateDistancesAndProximity()
            p2p.broadcastToClients(updatePacket)
        } else {
            p2p.sendToHost(updatePacket)
        }
    }

    private fun recalculateDistancesAndProximity() {
        val me = _uiState.value.myPlayer
        val others = _uiState.value.players.filter { it.id != me.id }

        if (me.role == Role.HIDER) {
            // Find nearest Seeker
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

            _uiState.update {
                it.copy(
                    nearestSeekerDistance = if (minDist == Double.MAX_VALUE) -1.0 else minDist,
                    nearestSeekerBearing = bestBearing
                )
            }
        } else {
            // Seeker: check if any uncaught Hider is within 10 meters!
            val uncaughtHiders = others.filter { it.role == Role.HIDER && !it.isCaught }
            val catchable = uncaughtHiders.firstOrNull { hider ->
                if (hider.latitude != 0.0 && hider.longitude != 0.0) {
                    GeoUtils.distanceMeters(me.latitude, me.longitude, hider.latitude, hider.longitude) <= 10.0
                } else false
            }

            _uiState.update { it.copy(nearbyCatchableHider = catchable) }
        }
    }

    /**
     * Seeker attempts to catch a Hider by entering their 4-digit code.
     */
    fun attemptCatch(targetHiderId: String, enteredCode: String) {
        val packet = P2PPacket.CatchAttempt(
            seekerId = _uiState.value.myPlayer.id,
            targetHiderId = targetHiderId,
            enteredCode = enteredCode.trim()
        )

        if (_uiState.value.isOrganizer) {
            processCatchAttempt(packet)
        } else {
            p2p.sendToHost(packet)
        }
    }

    private fun processCatchAttempt(attempt: P2PPacket.CatchAttempt) {
        val target = _uiState.value.players.find { it.id == attempt.targetHiderId }
        val success = target != null && target.catchCode == attempt.enteredCode

        if (success) {
            val updatedPlayers = _uiState.value.players.map {
                if (it.id == attempt.targetHiderId) it.copy(isCaught = true) else it
            }
            val s = _uiState.value.session
            val totalCatches = (s?.totalCatches ?: 0) + 1
            val updatedSession = s?.copy(totalCatches = totalCatches)

            _uiState.update {
                it.copy(
                    players = updatedPlayers,
                    session = updatedSession,
                    toastMessage = "${target?.name} was caught!"
                )
            }

            p2p.broadcastToClients(
                P2PPacket.CatchResult(
                    success = true,
                    targetHiderId = attempt.targetHiderId,
                    message = "${target?.name} caught!"
                )
            )

            // Check if all Hiders are caught!
            val remainingHiders = updatedPlayers.count { it.role == Role.HIDER && !it.isCaught }
            if (remainingHiders == 0) {
                endGame(winner = Role.SEEKER)
            } else {
                syncStateToClients()
            }
        } else {
            p2p.broadcastToClients(
                P2PPacket.CatchResult(
                    success = false,
                    targetHiderId = attempt.targetHiderId,
                    message = "Incorrect catch code."
                )
            )
        }
    }

    private fun endGame(winner: Role) {
        val s = _uiState.value.session ?: return
        val finalSession = s.copy(
            status = GameStatus.FINISHED,
            winnerRole = winner
        )
        _uiState.update { it.copy(session = finalSession) }
        syncStateToClients()

        // Persist to local Room database
        viewModelScope.launch {
            val me = _uiState.value.myPlayer
            val isWin = me.role == winner
            db.matchHistoryDao().insertMatch(
                MatchHistoryEntity(
                    joinCode = finalSession.joinCode,
                    rolePlayed = me.role.name,
                    isWin = isWin,
                    durationSeconds = (finalSession.durationMinutes * 60) - finalSession.remainingSeconds,
                    catchesCount = finalSession.totalCatches
                )
            )
        }
    }

    private fun syncStateToClients() {
        val s = _uiState.value.session ?: return
        p2p.broadcastToClients(P2PPacket.SyncState(session = s, players = _uiState.value.players))
    }

    private fun handleIncomingPacket(packet: P2PPacket) {
        when (packet) {
            is P2PPacket.JoinRequest -> {
                if (_uiState.value.isOrganizer) {
                    val current = _uiState.value.players.toMutableList()
                    if (current.none { it.id == packet.player.id }) {
                        val catchCode = if (packet.player.role == Role.HIDER) RandomCode.generateNumericCode(4) else ""
                        current.add(packet.player.copy(catchCode = catchCode))
                        _uiState.update { it.copy(players = current) }
                        syncStateToClients()
                    }
                }
            }
            is P2PPacket.SyncState -> {
                val myId = _uiState.value.myPlayer.id
                val myUpdatedPlayer = packet.players.find { it.id == myId } ?: _uiState.value.myPlayer
                _uiState.update {
                    it.copy(
                        session = packet.session,
                        players = packet.players,
                        myPlayer = myUpdatedPlayer
                    )
                }
                recalculateDistancesAndProximity()
            }
            is P2PPacket.LocationUpdate -> {
                val updated = _uiState.value.players.map {
                    if (it.id == packet.playerId) {
                        it.copy(
                            latitude = packet.latitude,
                            longitude = packet.longitude,
                            isOutOfZone = packet.isOutOfZone,
                            lastUpdatedEpochMs = System.currentTimeMillis()
                        )
                    } else it
                }
                _uiState.update { it.copy(players = updated) }
                recalculateDistancesAndProximity()
            }
            is P2PPacket.CatchAttempt -> {
                if (_uiState.value.isOrganizer) {
                    processCatchAttempt(packet)
                }
            }
            is P2PPacket.CatchResult -> {
                _uiState.update { it.copy(toastMessage = packet.message) }
            }
            is P2PPacket.PauseToggle -> {
                _uiState.update {
                    it.copy(
                        session = it.session?.copy(
                            isPaused = packet.isPaused,
                            status = if (packet.isPaused) GameStatus.PAUSED else GameStatus.RUNNING
                        )
                    )
                }
            }
            else -> {}
        }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun leaveGame() {
        timerJob?.cancel()
        p2p.stop()
        compass.stopListening()
        _uiState.update {
            it.copy(
                session = null,
                players = emptyList(),
                isOrganizer = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        compass.stopListening()
        p2p.stop()
    }
}

object RandomCode {
    private val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun generateCode(length: Int): String {
        return (1..length).map { CHARS.random() }.joinToString("")
    }

    fun generateNumericCode(length: Int): String {
        return (1..length).map { (0..9).random() }.joinToString("")
    }
}
