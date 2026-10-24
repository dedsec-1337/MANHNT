package com.manhunt.app.data.model

enum class Role {
    SEEKER,
    HIDER
}

enum class GameStatus {
    LOBBY,
    RUNNING,
    PAUSED,
    FINISHED
}

data class Player(
    val id: String,
    val name: String,
    val avatarEmoji: String = "🏃",
    var role: Role = Role.SEEKER,
    var isCaught: Boolean = false,
    val catchCode: String = "", // 4-digit code shown on Hider phone
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var isOutOfZone: Boolean = false,
    var lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

data class GameSession(
    val roomId: String,
    val roomName: String,
    val joinCode: String,
    val hostPlayerId: String,
    val status: GameStatus = GameStatus.LOBBY,
    val zoneCenterLat: Double = 0.0,
    val zoneCenterLng: Double = 0.0,
    val zoneRadiusMeters: Double = 500.0,
    val durationMinutes: Int = 30,
    val remainingSeconds: Int = 1800,
    val isPaused: Boolean = false,
    val winnerRole: Role? = null,
    val totalCatches: Int = 0
)

data class DiscoveredRoom(
    val joinCode: String,
    val roomName: String,
    val hostIp: String,
    val hostPort: Int,
    val playerCount: Int,
    val zoneRadiusMeters: Double
)

// P2P Network Packets (JSON serializable)
sealed class P2PPacket {
    data class DiscoveryBroadcast(
        val type: String = "DISCOVERY",
        val joinCode: String,
        val roomName: String,
        val hostPort: Int = 9876,
        val playerCount: Int,
        val zoneRadiusMeters: Double
    ) : P2PPacket()

    data class JoinRequest(
        val type: String = "JOIN_REQUEST",
        val player: Player
    ) : P2PPacket()

    data class SyncState(
        val type: String = "SYNC_STATE",
        val session: GameSession,
        val players: List<Player>
    ) : P2PPacket()

    data class LocationUpdate(
        val type: String = "LOCATION_UPDATE",
        val playerId: String,
        val latitude: Double,
        val longitude: Double,
        val isOutOfZone: Boolean
    ) : P2PPacket()

    data class CatchAttempt(
        val type: String = "CATCH_ATTEMPT",
        val seekerId: String,
        val targetHiderId: String,
        val enteredCode: String
    ) : P2PPacket()

    data class CatchResult(
        val type: String = "CATCH_RESULT",
        val success: Boolean,
        val targetHiderId: String,
        val message: String
    ) : P2PPacket()

    data class PauseToggle(
        val type: String = "PAUSE_TOGGLE",
        val isPaused: Boolean
    ) : P2PPacket()
}
