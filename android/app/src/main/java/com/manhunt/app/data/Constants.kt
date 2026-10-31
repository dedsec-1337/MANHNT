package com.manhunt.app.data

object Constants {
    const val FIREBASE_DATABASE_URL = "https://manhnt-ba00c-default-rtdb.asia-southeast1.firebasedatabase.app/"

    const val LOCATION_UPDATE_INTERVAL_MS = 5000L
    const val FASTEST_LOCATION_INTERVAL_MS = 3000L
    const val CLOUD_SYNC_POLL_INTERVAL_MS = 2500L
    // Allow a bit more slack: write every 5s + poll every 2.5s + phone GPS jitter.
    const val MAX_LOCATION_AGE_MS = 30_000L
    const val MAX_LOCATION_FUTURE_SKEW_MS = 5_000L
    const val GAME_SIGNAL_MAX_AGE_MS = 30_000L
    const val GAME_SIGNAL_MAX_FUTURE_SKEW_MS = 5_000L
    const val GAME_SIGNAL_SEND_COOLDOWN_MS = 1_500L

    // Catch range (reference only — tag is code/QR proof, not distance-gated).
    const val CATCH_PROXIMITY_METERS = 50.0

    const val HEARTBEAT_FAR_METERS = 80.0
    const val HEARTBEAT_MID_METERS = 50.0
    const val HEARTBEAT_NEAR_METERS = 25.0
    const val HEARTBEAT_CRITICAL_METERS = 10.0

    const val JAIL_RADIUS_METERS = 25.0
    const val JAILBREAK_HOLD_SECONDS = 5
    const val JAIL_HUNTER_REVEAL_METERS = 35.0
    const val POST_JAILBREAK_IMMUNITY_SECONDS = 30

    const val STEALTH_DURATION_SECONDS = 45
    const val STEALTH_COOLDOWN_SECONDS = 180

    const val MAX_DISPUTES_PER_GAME = 2
    const val CATCH_CONFIRM_TIMEOUT_SECONDS = 12

    // Manhunt reveal: snapshot once per ping window, then freeze pins locally.
    const val REVEAL_PIN_FROZEN_UNTIL_NEXT_PING = true
    const val REVEAL_MIN_ACCURACY_METERS = 50.0
    const val CATCH_OPTIMISTIC_UI = true
    const val CATCH_FIREBASE_CONFIRM_TIMEOUT_MS = 8_000L
}
