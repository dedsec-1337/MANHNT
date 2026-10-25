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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(initialZoom)
                val now = System.currentTimeMillis()
                val hasFreshPlayerFix = centerOnPlayer &&
                    GeoUtils.hasValidCoordinates(playerLat, playerLng) &&
                    playerLocationUpdatedAtEpochMs > 0L &&
                    now - playerLocationUpdatedAtEpochMs in 0L..15_000L
                val initialCenter = when {
                    hasFreshPlayerFix -> GeoPoint(playerLat, playerLng)
                    GeoUtils.hasValidCoordinates(zoneCenterLat, zoneCenterLng) -> GeoPoint(zoneCenterLat, zoneCenterLng)
                    else -> GeoPoint(0.0, 0.0)
                }
                controller.setCenter(initialCenter)
                hasCenteredOnPlayer[0] = hasFreshPlayerFix
                onResume()
                mapViewRef = this
            }
        },
        update = { mapView ->
            if (centerOnPlayer && !hasCenteredOnPlayer[0] &&
                playerLocationUpdatedAtEpochMs > lastCenteredLocationTimestamp[0] &&
                GeoUtils.hasValidCoordinates(playerLat, playerLng)
            ) {
                mapView.controller.setCenter(GeoPoint(playerLat, playerLng))
                hasCenteredOnPlayer[0] = true
            }
            lastCenteredLocationTimestamp[0] = maxOf(
                lastCenteredLocationTimestamp[0],
                playerLocationUpdatedAtEpochMs
            )

            val overlayContent = MapOverlayContent(
                playerLat = playerLat,
                playerLng = playerLng,
                userRole = userRole,
                zoneCenterLat = zoneCenterLat,
                zoneCenterLng = zoneCenterLng,
                zoneRadiusMeters = zoneRadiusMeters,
                isUnlimitedZone = isUnlimitedZone,
                safeZones = safeZones.toList(),
                isAutoPingActive = isAutoPingActive,
                otherPlayers = otherPlayers.toList(),
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
                visibleOtherPlayerIds = otherPlayers.asSequence()
                    .filter { it.role == Role.HIDER && !it.isCaught }
                    .filter { GeoUtils.isLocationRecent(it.lastUpdatedEpochMs) }
                    .filterNot { it.isStealthActive && System.currentTimeMillis() < it.stealthUntilEpochMs }
                    .map { it.id }
                    .toSet(),
                showOtherPlayerLocations = showOtherPlayerLocations
            )
            val overlaysChanged = mapRenderState.overlayContent != overlayContent
            val headingChanged = mapRenderState.headingDegrees != headingDegrees

            if (overlaysChanged) {
                mapView.overlays.clear()
                mapRenderState.playerMarker = null

                if (!isUnlimitedZone && zoneRadiusMeters > 0 &&
                    GeoUtils.hasValidCoordinates(zoneCenterLat, zoneCenterLng)
                ) {
                    val circleCenter = GeoPoint(zoneCenterLat, zoneCenterLng)
                    val circlePolygon = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(circleCenter, zoneRadiusMeters)
                        fillPaint.color = AndroidColor.argb(25, 0, 122, 255)
                        fillPaint.style = Paint.Style.FILL
                        outlinePaint.color = AndroidColor.argb(200, 0, 122, 255)
                        outlinePaint.strokeWidth = 6f
                        outlinePaint.style = Paint.Style.STROKE
                        title = "Play Zone Perimeter (${zoneRadiusMeters.toInt()}m)"
                    }
                    mapView.overlays.add(circlePolygon)
                    if (zoneRadiusMeters > 50) {
                        val bufferPolygon = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(circleCenter, zoneRadiusMeters - 25.0)
                            fillPaint.color = AndroidColor.TRANSPARENT
                            outlinePaint.color = AndroidColor.argb(120, 255, 149, 0)
                            outlinePaint.strokeWidth = 3f
                            outlinePaint.style = Paint.Style.STROKE
                        }
                        mapView.overlays.add(bufferPolygon)
                    }
                }

                for (safeZone in safeZones) {
                    if (GeoUtils.hasValidCoordinates(safeZone.latitude, safeZone.longitude)) {
                        val safeCenter = GeoPoint(safeZone.latitude, safeZone.longitude)
                        val safePolygon = Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(safeCenter, safeZone.radiusMeters)
                            fillPaint.color = AndroidColor.argb(45, 52, 199, 89)
                            fillPaint.style = Paint.Style.FILL
                            outlinePaint.color = AndroidColor.argb(220, 52, 199, 89)
                            outlinePaint.strokeWidth = 4f
                            outlinePaint.style = Paint.Style.STROKE
                            title = "🛡️ ${safeZone.name} (Safe Zone)"
                        }
                        mapView.overlays.add(safePolygon)
                    }
                }

                if (jailEnabled && GeoUtils.hasValidCoordinates(jailLat, jailLng)) {
                    val jailCenter = GeoPoint(jailLat, jailLng)
                    val jailPolygon = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(jailCenter, jailRadiusMeters)
                        fillPaint.color = AndroidColor.argb(55, 255, 59, 48)
                        fillPaint.style = Paint.Style.FILL
                        outlinePaint.color = AndroidColor.argb(230, 255, 59, 48)
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.style = Paint.Style.STROKE
                        title = "🔒 Jail"
                    }
                    mapView.overlays.add(jailPolygon)
                }

                if (GeoUtils.hasValidCoordinates(playerLat, playerLng) &&
                    overlayContent.playerLocationIsRecent
                ) {
                    val selfMarker = Marker(mapView).apply {
                        position = GeoPoint(playerLat, playerLng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "You"
                        icon = context.getDrawable(
                            if (hasHeading) R.drawable.ic_player_heading else android.R.drawable.presence_online
                        )
                    }
                    mapView.overlays.add(selfMarker)
                    mapRenderState.playerMarker = selfMarker
                }

                if (showOtherPlayerLocations && (userRole == Role.SEEKER || isAutoPingActive)) {
                    for (hider in otherPlayers.filter { it.id in overlayContent.visibleOtherPlayerIds }) {
                        if (GeoUtils.hasValidCoordinates(hider.latitude, hider.longitude)) {
                            val hiderMarker = Marker(mapView).apply {
                                position = GeoPoint(hider.latitude, hider.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = when {
                                    hider.isInSafeZone -> "${hider.name} 🛡️ (Safe)"
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
                        it.role == Role.HIDER && it.isInJail && GeoUtils.isLocationRecent(it.lastUpdatedEpochMs)
                    }
                    for (j in jailed) {
                        if (GeoUtils.hasValidCoordinates(j.latitude, j.longitude)) {
                            val m = Marker(mapView).apply {
                                position = GeoPoint(j.latitude, j.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "${j.name} 🔒 Jail"
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
