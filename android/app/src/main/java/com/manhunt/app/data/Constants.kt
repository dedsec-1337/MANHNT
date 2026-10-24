package com.manhunt.app.data

/**
 * Global application constants.
 * Configure your Firebase Realtime Database REST URL here.
 */
object Constants {
    /**
     * Firebase Realtime Database Base URL for Long-Range Cellular Sync.
     * Ensure the URL ends with a trailing slash ("/").
     *
     * Example: "https://your-project-default-rtdb.firebaseio.com/"
     */
    const val FIREBASE_DATABASE_URL = "https://manhnt-ba00c-default-rtdb.asia-southeast1.firebasedatabase.app/"

    // Location update intervals
    const val LOCATION_UPDATE_INTERVAL_MS = 5000L
    const val FASTEST_LOCATION_INTERVAL_MS = 3000L

    // Cloud polling interval for room state & player positions
    const val CLOUD_SYNC_POLL_INTERVAL_MS = 2500L

    // Catch proximity threshold in meters
    const val CATCH_PROXIMITY_METERS = 10.0
}
