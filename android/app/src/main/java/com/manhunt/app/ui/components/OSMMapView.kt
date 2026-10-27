package com.manhunt.app.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.manhunt.app.data.model.LocationRevealSnapshot
import com.manhunt.app.data.model.Player
import com.manhunt.app.data.model.Role
import com.manhunt.app.data.model.SafeZone
import com.manhunt.app.util.GeoUtils
import com.manhunt.app.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private data class MapOverlayContent(
    val playerLat: Double,
    val playerLng: Double,
    val userRole: Role,
    val zoneCenterLat: Double,
    val zoneCenterLng: Double,
    val zoneRadiusMeters: Double,
    val isUnlimitedZone: Boolean,
    val safeZones: List<SafeZone>,
    val isAutoPingActive: Boolean,
    val otherPlayers: List<Player>,
    val jailLat: Double,
    val jailLng: Double,
    val jailRadiusMeters: Double,
    val jailEnabled: Boolean,
    val hasHeading: Boolean,
    val playerLocationUpdatedAtEpochMs: Long,
    val playerLocationIsRecent: Boolean,
    val otherPlayerLocationFreshness: List<Pair<String, Boolean>>,
    val visibleOtherPlayerIds: Set<String>,
    val showOtherPlayerLocations: Boolean
)

private class MapRenderState {
    var overlayContent: MapOverlayContent? = null
    var playerMarker: Marker? = null
    var headingDegrees: Float = Float.NaN
}

@Composable
fun OSMMapView(
    playerLat: Double,
    playerLng: Double,
    userRole: Role,
    zoneCenterLat: Double,
    zoneCenterLng: Double,
    zoneRadiusMeters: Double,
    isUnlimitedZone: Boolean = false,
    safeZones: List<SafeZone> = emptyList(),
    isAutoPingActive: Boolean = false,
    otherPlayers: List<Player>,
    jailLat: Double = 0.0,
    jailLng: Double = 0.0,
    jailRadiusMeters: Double = 25.0,
    jailEnabled: Boolean = false,
    headingDegrees: Float = 0f,
    hasHeading: Boolean = false,
    playerLocationUpdatedAtEpochMs: Long = 0L,
    centerOnPlayer: Boolean = true,
    showOtherPlayerLocations: Boolean = true,
    frozenRevealLocations: Map<String, LocationRevealSnapshot> = emptyMap(),
    initialZoom: Double = 17.0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val hasCenteredOnPlayer = remember(zoneCenterLat, zoneCenterLng) { booleanArrayOf(false) }
    val lastCenteredLocationTimestamp = remember(zoneCenterLat, zoneCenterLng) {
        longArrayOf(playerLocationUpdatedAtEpochMs)
    }
    val mapRenderState = remember { MapRenderState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onPause()
            mapViewRef?.onDetach()
            mapViewRef = null
        }
    }

    val overlayContent = remember(
        playerLat, playerLng, userRole, zoneCenterLat, zoneCenterLng, zoneRadiusMeters,
        isUnlimitedZone, safeZones, isAutoPingActive, otherPlayers, jailLat, jailLng,
        jailRadiusMeters, jailEnabled, hasHeading, playerLocationUpdatedAtEpochMs,
        showOtherPlayerLocations, frozenRevealLocations
    ) {
        MapOverlayContent(
            playerLat = playerLat,
            playerLng = playerLng,
            userRole = userRole,
            zoneCenterLat = zoneCenterLat,
            zoneCenterLng = zoneCenterLng,
            zoneRadiusMeters = zoneRadiusMeters,
            isUnlimitedZone = isUnlimitedZone,
            safeZones = safeZones,
            isAutoPingActive = isAutoPingActive,
            otherPlayers = otherPlayers,
            jailLat = jailLat,
            jailLng = jailLng,
            jailRadiusMeters = jailRadiusMeters,
            jailEnabled = jailEnabled,
            hasHeading = hasHeading,
            playerLocationUpdatedAtEpochMs = playerLocationUpdatedAtEpochMs,
            playerLocationIsRecent = GeoUtils.isLocationRecent(playerLocationUpdatedAtEpochMs),
            otherPlayerLocationFreshness = otherPlayers.map {
                it.id to GeoUtils.isLocationRecent(it.lastUpdatedEpochMs)
            },
            visibleOtherPlayerIds = if (frozenRevealLocations.isNotEmpty()) {
                frozenRevealLocations.keys
            } else {
                otherPlayers.asSequence()
                    .filter { it.role == Role.HIDER && !it.isCaught }
                    .filterNot { it.isStealthActive && System.currentTimeMillis() < it.stealthUntilEpochMs }
                    .map { it.id }
                    .toSet()
            },
            showOtherPlayerLocations = showOtherPlayerLocations
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(initialZoom)
                if (GeoUtils.hasValidCoordinates(zoneCenterLat, zoneCenterLng)) {
                    controller.setCenter(GeoPoint(zoneCenterLat, zoneCenterLng))
                }
                mapViewRef = this
            }
        },
        update = { mapView ->
            val previous = mapRenderState.overlayContent
            val overlaysChanged = previous != overlayContent
            val headingChanged = mapRenderState.headingDegrees != headingDegrees

            if (centerOnPlayer && GeoUtils.hasValidCoordinates(playerLat, playerLng)) {
                val movedEnough = kotlin.math.abs(playerLocationUpdatedAtEpochMs - lastCenteredLocationTimestamp[0]) > 2000L
                if (!hasCenteredOnPlayer[0] || movedEnough) {
                    mapView.controller.animateTo(GeoPoint(playerLat, playerLng))
                    hasCenteredOnPlayer[0] = true
                    lastCenteredLocationTimestamp[0] = playerLocationUpdatedAtEpochMs
                }
            }

            if (overlaysChanged) {
                mapView.overlays.clear()

                if (!isUnlimitedZone && zoneRadiusMeters > 0 && GeoUtils.hasValidCoordinates(zoneCenterLat, zoneCenterLng)) {
                    val zone = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(zoneCenterLat, zoneCenterLng), zoneRadiusMeters)
                        fillPaint.color = AndroidColor.argb(40, 0, 122, 255)
                        outlinePaint.color = AndroidColor.argb(180, 0, 122, 255)
                        outlinePaint.strokeWidth = 4f
                        outlinePaint.style = Paint.Style.STROKE
                    }
                    mapView.overlays.add(zone)
                }

                for (safe in safeZones) {
                    if (GeoUtils.hasValidCoordinates(safe.latitude, safe.longitude) && safe.radiusMeters > 0) {
                        val poly = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(GeoPoint(safe.latitude, safe.longitude), safe.radiusMeters)
                            fillPaint.color = AndroidColor.argb(50, 52, 199, 89)
                            outlinePaint.color = AndroidColor.argb(200, 52, 199, 89)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.style = Paint.Style.STROKE
                            title = safe.name
                        }
                        mapView.overlays.add(poly)
                    }
                }

                if (jailEnabled && GeoUtils.hasValidCoordinates(jailLat, jailLng)) {
                    val jail = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(jailLat, jailLng), jailRadiusMeters)
                        fillPaint.color = AndroidColor.argb(50, 255, 59, 48)
                        outlinePaint.color = AndroidColor.argb(200, 255, 59, 48)
                        outlinePaint.strokeWidth = 3f
                        outlinePaint.style = Paint.Style.STROKE
                        title = "Jail"
                    }
                    mapView.overlays.add(jail)
                }

                if (GeoUtils.hasValidCoordinates(playerLat, playerLng)) {
                    val selfMarker = Marker(mapView).apply {
                        position = GeoPoint(playerLat, playerLng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "You"
                        icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)
                        if (hasHeading) setRotation(-headingDegrees)
                    }
                    mapView.overlays.add(selfMarker)
                    mapRenderState.playerMarker = selfMarker
                }

                if (showOtherPlayerLocations && (userRole == Role.SEEKER || isAutoPingActive || frozenRevealLocations.isNotEmpty())) {
                    for (hider in otherPlayers.filter { it.id in overlayContent.visibleOtherPlayerIds }) {
                        val frozen = frozenRevealLocations[hider.id]
                        val lat = frozen?.latitude ?: hider.latitude
                        val lng = frozen?.longitude ?: hider.longitude
                        if (GeoUtils.hasValidCoordinates(lat, lng)) {
                            val hiderMarker = Marker(mapView).apply {
                                position = GeoPoint(lat, lng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = when {
                                    hider.isInSafeZone -> "${hider.name} (Safe)"
                                    frozen != null -> "${hider.name} (Reveal)"
                                    else -> "${hider.name} (Hider)"
                                }
                                icon = context.getDrawable(
                                    if (hider.isInSafeZone) android.R.drawable.presence_invisible
                                    else android.R.drawable.presence_away
                                )
                            }
                            mapView.overlays.add(hiderMarker)
                        }
                    }
                    val jailed = otherPlayers.filter {
                        it.role == Role.HIDER && it.isInJail
                    }
                    for (j in jailed) {
                        if (GeoUtils.hasValidCoordinates(j.latitude, j.longitude)) {
                            val m = Marker(mapView).apply {
                                position = GeoPoint(j.latitude, j.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "${j.name} Jail"
                                icon = context.getDrawable(android.R.drawable.ic_lock_lock)
                            }
                            mapView.overlays.add(m)
                        }
                    }
                }

                mapRenderState.overlayContent = overlayContent
            }

            if (overlaysChanged || headingChanged) {
                if (hasHeading) mapRenderState.playerMarker?.setRotation(-headingDegrees)
                mapRenderState.headingDegrees = headingDegrees
            }

            if (overlaysChanged || headingChanged) mapView.invalidate()
        }
    )
}
