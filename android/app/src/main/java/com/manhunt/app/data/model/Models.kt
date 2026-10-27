package com.manhunt.app.data.model

import com.google.gson.annotations.SerializedName

enum class Role { SEEKER, HIDER }
enum class GameStatus { LOBBY, RUNNING, PAUSED, FINISHED }
enum class NetworkSyncStatus { ONLINE, SYNCING, OFFLINE }

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
    @Transient val catchCode: String = "",
    @Transient val latitude: Double = 0.0,
    @Transient val longitude: Double = 0.0,
    @Transient val isOutOfZone: Boolean = false,
    val isInSafeZone: Boolean = false,
    val safeZoneName: String? = null,
    @Transient val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
    val isInJail: Boolean = false,
    val immunityUntilEpochMs: Long = 0L,
    val isStealthActive: Boolean = false,
    val stealthUntilEpochMs: Long = 0L,
    val stealthActivationRequestedAtEpochMs: Long = 0L,
    val stealthCooldownUntilEpochMs: Long = 0L,
    val disputesRemaining: Int = 2,
    // A hider writes this request to their own player record; only the room host advances the jailbreak.
    val jailbreakHoldStartedAtEpochMs: Long = 0L
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
    val zoneSizePreset: String = "1000m",
    val isUnlimitedZone: Boolean = false,
    val headStartMinutes: Int = 0,
    val headStartRemainingSeconds: Int = 0,
    val isShrinkingZoneEnabled: Boolean = false,
    val currentDynamicZoneRadiusMeters: Double = 1000.0,
    val autoPingIntervalMinutes: Int = 0,
    val isAutoPingActive: Boolean = false,
    val autoPingRemainingSeconds: Int = 0,
    val safeZones: List<SafeZone> = emptyList(),
    val jailEnabled: Boolean = true,
    val jailLat: Double = 0.0,
    val jailLng: Double = 0.0,
    val jailRadiusMeters: Double = 25.0,
    val isJailbreakInProgress: Boolean = false,
    val jailbreakProgressSeconds: Int = 0
)

/** Frozen map clue published at an auto-ping / reveal boundary. */
data class LocationRevealSnapshot(
    val playerId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val capturedAtEpochMs: Long = 0L,
    val accuracyMeters: Double = 0.0
)

data class LocationPayload(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isOutOfZone: Boolean = false,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis()
)

// Safe lookup metadata only; never include exact coordinates in this public endpoint.
data class RoomJoinInfo(
    val roomName: String = "",
    val joinCode: String = "",
    val hostPlayerId: String = ""
)

data class RoomNetworkModel(
    val session: GameSession = GameSession(),
    @SerializedName("publicPlayers") val players: Map<String, Player> = emptyMap(),
    val lastCatchAttempt: CatchAttemptPayload? = null,
    val pendingCatchConfirm: CatchConfirmPayload? = null,
    val catchAck: CatchAckPayload? = null
)

data class CatchAttemptPayload(
    val seekerId: String = "",
    val targetHiderId: String = "",
    val enteredCode: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CatchConfirmPayload(
    val seekerId: String = "",
    val targetHiderId: String = "",
    val seekerName: String = "",
    val distanceMeters: Double = 0.0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long = 0L,
    val status: String = "PENDING"
)

/**
 * Host-written acknowledgement of a processed catch attempt.
 * `lastCatchAttempt` is host-readable only, so a non-host Seeker cannot observe the outcome there;
 * the host mirrors the result here under the seeker's own UID for that seeker to read back.
 */
data class CatchAckPayload(
    val seekerId: String = "",
    val targetHiderId: String = "",
    val targetName: String = "",
    val accepted: Boolean = false,
    val timestamp: Long = 0L
)

data class GameSignalPayload(
    val code: String = "",
    val timestampEpochMs: Long = 0L
)

object GameSignalCode {
    const val REACTION_LAUGH = "R_LAUGH"
    const val REACTION_EYES = "R_EYES"
    const val REACTION_CLAP = "R_CLAP"
    const val REACTION_FIRE = "R_FIRE"
    const val PHRASE_CATCH_ME = "P_CATCH_ME"
    const val PHRASE_CLOSE_CALL = "P_CLOSE_CALL"
    const val PHRASE_WARMER = "P_WARMER"
    const val PHRASE_NEED_RESCUE = "P_NEED_RESCUE"
    const val PHRASE_GG = "P_GG"

    val allowedCodes = setOf(
        REACTION_LAUGH, REACTION_EYES, REACTION_CLAP, REACTION_FIRE,
        PHRASE_CATCH_ME, PHRASE_CLOSE_CALL, PHRASE_WARMER, PHRASE_NEED_RESCUE, PHRASE_GG
    )
}
