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

enum class NetworkSyncStatus {
    ONLINE,    // Successfully syncing with Firebase REST over cellular data
    SYNCING,   // Request in flight
    OFFLINE    // Network error or offline
}

/**
 * Safe Zone where Hiders cannot be caught (e.g. Base, Pavilion, Library)
 */
data class SafeZone(
    val id: String = "",
    val name: String = "Safe Zone",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 40.0
)

data class Player(
    val id: String = "",
    val name: String = "",
    val avatarEmoji: String = "🏃",
    val role: Role = Role.SEEKER,
    val isCaught: Boolean = false,
    val catchCode: String = "", // 4-digit PIN shown on Hider screen
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isOutOfZone: Boolean = false,
    val isInSafeZone: Boolean = false,
    val safeZoneName: String? = null,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

data class GameSession(
    val roomId: String = "",
    val roomName: String = "",
    val joinCode: String = "",
    val hostPlayerId: String = "",
    val status: GameStatus = GameStatus.LOBBY,
    val zoneCenterLat: Double = 0.0,
    val zoneCenterLng: Double = 0.0,
    val zoneRadiusMeters: Double = 1000.0,
    val durationMinutes: Int = 30,
    val remainingSeconds: Int = 1800,
    val isPaused: Boolean = false,
    val winnerRole: Role? = null,
    val totalCatches: Int = 0,

    // Advanced Lobby & Game Settings
    val zoneSizePreset: String = "1000m", // "1000m", "2000m", "UNLIMITED"
    val isUnlimitedZone: Boolean = false,
    val headStartMinutes: Int = 0, // 0, 1, 2, 3, 4, 5, 10, 15 min
    val headStartRemainingSeconds: Int = 0,
    val isShrinkingZoneEnabled: Boolean = false,
    val currentDynamicZoneRadiusMeters: Double = 1000.0,
    val autoPingIntervalMinutes: Int = 0, // 0 (Off), 2, 5 min
    val isAutoPingActive: Boolean = false, // 10s reveal active
    val autoPingRemainingSeconds: Int = 0,
    val safeZones: List<SafeZone> = emptyList()
)

/**
 * Root JSON structure stored under /rooms/{joinCode}.json in Firebase Realtime Database
 */
data class RoomNetworkModel(
    val session: GameSession = GameSession(),
    val players: Map<String, Player> = emptyMap(),
    val lastCatchAttempt: CatchAttemptPayload? = null
)

data class CatchAttemptPayload(
    val seekerId: String = "",
    val targetHiderId: String = "",
    val enteredCode: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
