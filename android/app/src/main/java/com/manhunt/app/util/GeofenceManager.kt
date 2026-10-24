package com.manhunt.app.util

enum class GeofenceStatus {
    INSIDE,         // Safe inside the play zone
    BOUNDARY_ALERT, // Within 25m of perimeter boundary
    OUT_OF_BOUNDS,  // Outside circular zone; return grace timer active
    BREACH_PENALTY  // Grace timer expired outside boundary
}

data class GeofenceEvaluation(
    val status: GeofenceStatus,
    val distanceFromCenterMeters: Double,
    val distanceToBoundaryMeters: Double,
    val isOutside: Boolean,
    val warningMessage: String? = null
)

object GeofenceManager {

    const val BOUNDARY_BUFFER_METERS = 25.0
    const val DEFAULT_GRACE_PERIOD_SECONDS = 30

    /**
     * Evaluates high-accuracy geofence boundary coordinates.
     */
    fun evaluate(
        playerLat: Double,
        playerLng: Double,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double
    ): GeofenceEvaluation {
        if (radiusMeters <= 0.0 || centerLat == 0.0) {
            return GeofenceEvaluation(
                status = GeofenceStatus.INSIDE,
                distanceFromCenterMeters = 0.0,
                distanceToBoundaryMeters = 0.0,
                isOutside = false
            )
        }

        val distFromCenter = GeoUtils.distanceMeters(playerLat, playerLng, centerLat, centerLng)
        val distToEdge = radiusMeters - distFromCenter

        return when {
            distFromCenter > radiusMeters -> {
                GeofenceEvaluation(
                    status = GeofenceStatus.OUT_OF_BOUNDS,
                    distanceFromCenterMeters = distFromCenter,
                    distanceToBoundaryMeters = distFromCenter - radiusMeters,
                    isOutside = true,
                    warningMessage = "OUT OF BOUNDS: Return to play zone immediately!"
                )
            }
            distToEdge <= BOUNDARY_BUFFER_METERS -> {
                GeofenceEvaluation(
                    status = GeofenceStatus.BOUNDARY_ALERT,
                    distanceFromCenterMeters = distFromCenter,
                    distanceToBoundaryMeters = distToEdge,
                    isOutside = false,
                    warningMessage = "Approaching boundary (${distToEdge.toInt()}m to edge)"
                )
            }
            else -> {
                GeofenceEvaluation(
                    status = GeofenceStatus.INSIDE,
                    distanceFromCenterMeters = distFromCenter,
                    distanceToBoundaryMeters = distToEdge,
                    isOutside = false
                )
            }
        }
    }
}
