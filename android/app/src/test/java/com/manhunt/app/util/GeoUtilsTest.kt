package com.manhunt.app.util

import com.manhunt.app.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    // One degree of arc on a 6,371,000 m sphere: 6_371_000 * PI / 180.
    private val metersPerDegree = 111_194.93

    // ---- distanceMeters ----

    @Test
    fun distance_isZeroForIdenticalPoints() {
        assertEquals(0.0, GeoUtils.distanceMeters(22.5726, 88.3639, 22.5726, 88.3639), 1e-6)
    }

    @Test
    fun distance_oneDegreeOfLatitudeIsAboutOneHundredElevenKm() {
        assertEquals(metersPerDegree, GeoUtils.distanceMeters(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun distance_oneDegreeOfLongitudeAtEquatorMatchesLatitudeDegree() {
        assertEquals(metersPerDegree, GeoUtils.distanceMeters(0.0, 0.0, 0.0, 1.0), 1.0)
    }

    @Test
    fun distance_longitudeDegreeShrinksWithLatitude() {
        val atSixty = GeoUtils.distanceMeters(60.0, 10.0, 60.0, 11.0)
        assertEquals(metersPerDegree / 2.0, atSixty, 100.0)
    }

    @Test
    fun distance_isSymmetric() {
        val ab = GeoUtils.distanceMeters(12.97, 77.59, 13.08, 80.27)
        val ba = GeoUtils.distanceMeters(13.08, 80.27, 12.97, 77.59)
        assertEquals(ab, ba, 1e-6)
    }

    @Test
    fun distance_crossesTheAntimeridianTheShortWay() {
        assertEquals(2 * metersPerDegree, GeoUtils.distanceMeters(0.0, 179.0, 0.0, -179.0), 2.0)
    }

    // ---- bearingDegrees ----

    @Test
    fun bearing_cardinalDirections() {
        assertEquals(0f, GeoUtils.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.01f)
        assertEquals(90f, GeoUtils.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.01f)
        assertEquals(180f, GeoUtils.bearingDegrees(0.0, 0.0, -1.0, 0.0), 0.01f)
        assertEquals(270f, GeoUtils.bearingDegrees(0.0, 0.0, 0.0, -1.0), 0.01f)
    }

    @Test
    fun bearing_alwaysWithinZeroToThreeSixty() {
        val points = listOf(-80.0, -30.0, 0.0, 30.0, 80.0)
        for (la1 in points) for (lo1 in points) for (la2 in points) for (lo2 in points) {
            if (la1 == la2 && lo1 == lo2) continue
            val b = GeoUtils.bearingDegrees(la1, lo1, la2, lo2)
            assertTrue("bearing $b out of range", b >= 0f && b < 360f)
        }
    }

    // ---- isOutsideZone ----

    @Test
    fun zone_insideAndOutside() {
        // ~111 m north of the centre.
        val lat = 0.001
        assertTrue(GeoUtils.isOutsideZone(lat, 0.0, 0.0, 0.0, 50.0))
        assertFalse(GeoUtils.isOutsideZone(lat, 0.0, 0.0, 0.0, 150.0))
    }

    @Test
    fun zone_pointExactlyOnBoundaryIsInside() {
        val d = GeoUtils.distanceMeters(0.001, 0.0, 0.0, 0.0)
        assertFalse(GeoUtils.isOutsideZone(0.001, 0.0, 0.0, 0.0, d))
    }

    // ---- normalizeAngleDiff ----

    @Test
    fun normalize_wrapsIntoMinusOneEightyToOneEighty() {
        assertEquals(0f, GeoUtils.normalizeAngleDiff(0f), 1e-4f)
        assertEquals(-170f, GeoUtils.normalizeAngleDiff(190f), 1e-4f)
        assertEquals(170f, GeoUtils.normalizeAngleDiff(-190f), 1e-4f)
        assertEquals(0f, GeoUtils.normalizeAngleDiff(360f), 1e-4f)
        assertEquals(180f, GeoUtils.normalizeAngleDiff(540f), 1e-4f)
        assertEquals(-90f, GeoUtils.normalizeAngleDiff(-90f), 1e-4f)
        assertEquals(5f, GeoUtils.normalizeAngleDiff(725f), 1e-3f)
    }

    // ---- hasValidCoordinates ----

    @Test
    fun coordinates_originIsTheNoFixSentinel() {
        assertFalse(GeoUtils.hasValidCoordinates(0.0, 0.0))
    }

    @Test
    fun coordinates_acceptsBoundsAndRejectsOutOfRange() {
        assertTrue(GeoUtils.hasValidCoordinates(0.0, 1.0))
        assertTrue(GeoUtils.hasValidCoordinates(90.0, 180.0))
        assertTrue(GeoUtils.hasValidCoordinates(-90.0, -180.0))
        assertFalse(GeoUtils.hasValidCoordinates(90.0001, 0.0))
        assertFalse(GeoUtils.hasValidCoordinates(0.0, 180.0001))
    }

    @Test
    fun coordinates_rejectsNonFinite() {
        assertFalse(GeoUtils.hasValidCoordinates(Double.NaN, 10.0))
        assertFalse(GeoUtils.hasValidCoordinates(10.0, Double.POSITIVE_INFINITY))
    }

    // ---- isLocationRecent ----

    @Test
    fun recent_freshFixIsRecent() {
        val now = 1_000_000_000L
        assertTrue(GeoUtils.isLocationRecent(now, now))
    }

    @Test
    fun recent_ageBoundaryIsInclusive() {
        val now = 1_000_000_000L
        assertTrue(GeoUtils.isLocationRecent(now - Constants.MAX_LOCATION_AGE_MS, now))
        assertFalse(GeoUtils.isLocationRecent(now - Constants.MAX_LOCATION_AGE_MS - 1, now))
    }

    @Test
    fun recent_futureSkewBoundaryIsInclusive() {
        val now = 1_000_000_000L
        assertTrue(GeoUtils.isLocationRecent(now + Constants.MAX_LOCATION_FUTURE_SKEW_MS, now))
        assertFalse(GeoUtils.isLocationRecent(now + Constants.MAX_LOCATION_FUTURE_SKEW_MS + 1, now))
    }

    @Test
    fun recent_missingTimestampIsNeverRecent() {
        assertFalse(GeoUtils.isLocationRecent(0L, 1_000_000_000L))
        assertFalse(GeoUtils.isLocationRecent(-5L, 1_000_000_000L))
    }
}
