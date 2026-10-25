package com.manhunt.app.util

import com.manhunt.app.data.Constants
import kotlin.math.*

object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Returns whether a GPS coordinate is present and within geographic bounds.
     * (0, 0) remains the app's existing "no location yet" sentinel.
     */
    fun hasValidCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)

    /** Returns true only while a reported GPS fix is recent and not implausibly future-dated. */
    fun isLocationRecent(
        lastUpdatedEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean =
        lastUpdatedEpochMs > 0L &&
            lastUpdatedEpochMs <= nowEpochMs + Constants.MAX_LOCATION_FUTURE_SKEW_MS &&
            nowEpochMs - lastUpdatedEpochMs <= Constants.MAX_LOCATION_AGE_MS

    /**
     * Calculates distance in meters between two lat/lng coordinates using the Haversine formula.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2.0) +
                sin(dLon / 2.0).pow(2.0) * cos(rLat1) * cos(rLat2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates bearing in degrees (0 - 360) from (lat1, lon1) towards (lat2, lon2).
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360.0) % 360.0).toFloat()
    }

    /**
     * Checks if coordinates are outside the circular play boundary.
     */
    fun isOutsideZone(
        playerLat: Double,
        playerLng: Double,
        zoneCenterLat: Double,
        zoneCenterLng: Double,
        radiusMeters: Double
    ): Boolean {
        val dist = distanceMeters(playerLat, playerLng, zoneCenterLat, zoneCenterLng)
        return dist > radiusMeters
    }

    /**
     * Normalizes an angle difference to [-180, 180] for smooth compass needles.
     */
    fun normalizeAngleDiff(angle: Float): Float {
        var diff = (angle % 360f + 360f) % 360f
        if (diff > 180f) {
            diff -= 360f
        }
        return diff
    }
}
