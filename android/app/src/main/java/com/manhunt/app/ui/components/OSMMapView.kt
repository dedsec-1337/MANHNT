package com.manhunt.app.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.manhunt.app.data.model.Player
import com.manhunt.app.data.model.Role
import com.manhunt.app.data.model.SafeZone
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(17.0)
                if (playerLat != 0.0 && playerLng != 0.0) {
                    controller.setCenter(GeoPoint(playerLat, playerLng))
                }
                mapViewRef = this
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            // 1. Draw Play Zone Geofence Circle Boundary & Warning Buffer (Skipped if Unlimited)
            if (!isUnlimitedZone && zoneRadiusMeters > 0 && zoneCenterLat != 0.0) {
                val circleCenter = GeoPoint(zoneCenterLat, zoneCenterLng)

                // Main Geofence Boundary
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

                // 25m Warning Buffer Ring (inner perimeter)
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

            // 2. Draw Safe Zones (Green Circles with Shielded Bases)
            for (safeZone in safeZones) {
                if (safeZone.latitude != 0.0 && safeZone.longitude != 0.0) {
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

            // 3. Draw Current Player Marker (Blue dot)
            if (playerLat != 0.0 && playerLng != 0.0) {
                val selfMarker = Marker(mapView).apply {
                    position = GeoPoint(playerLat, playerLng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "You"
                    icon = context.getDrawable(android.R.drawable.presence_online)
                }
                mapView.overlays.add(selfMarker)
            }

            // 4. For Seekers (or during Auto-Ping reveal): show Hiders
            // For Hiders: Stealth map (Seekers hidden from map, only rotary compass points to nearest)
            if (userRole == Role.SEEKER || isAutoPingActive) {
                val hiders = otherPlayers.filter { it.role == Role.HIDER && !it.isCaught }
                for (hider in hiders) {
                    if (hider.latitude != 0.0 && hider.longitude != 0.0) {
                        val hiderMarker = Marker(mapView).apply {
                            position = GeoPoint(hider.latitude, hider.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = if (hider.isInSafeZone) "${hider.name} 🛡️ (Safe in ${hider.safeZoneName ?: "Base"})" else "${hider.name} (Hider)"
                            icon = context.getDrawable(if (hider.isInSafeZone) android.R.drawable.presence_invisible else android.R.drawable.presence_away)
                        }
                        mapView.overlays.add(hiderMarker)
                    }
                }
            }

            mapView.invalidate()
        }
    )
}
