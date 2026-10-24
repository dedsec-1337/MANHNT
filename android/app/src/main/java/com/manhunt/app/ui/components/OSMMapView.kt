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

            // 1. Draw Play Zone Circle Boundary
            if (zoneRadiusMeters > 0 && zoneCenterLat != 0.0) {
                val circlePolygon = Polygon(mapView).apply {
                    points = Polygon.pointsAsCircle(
                        GeoPoint(zoneCenterLat, zoneCenterLng),
                        zoneRadiusMeters
                    )
                    fillPaint.color = AndroidColor.argb(35, 0, 122, 255)
                    fillPaint.style = Paint.Style.FILL
                    outlinePaint.color = AndroidColor.argb(180, 0, 122, 255)
                    outlinePaint.strokeWidth = 5f
                    outlinePaint.style = Paint.Style.STROKE
                }
                mapView.overlays.add(circlePolygon)
            }

            // 2. Draw Current Player Marker (Blue dot)
            if (playerLat != 0.0 && playerLng != 0.0) {
                val selfMarker = Marker(mapView).apply {
                    position = GeoPoint(playerLat, playerLng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "You"
                    // Standard OSMDroid icon or colored marker
                    icon = context.getDrawable(android.R.drawable.presence_online)
                }
                mapView.overlays.add(selfMarker)
            }

            // 3. For Seekers: show Hiders (orange markers)
            // For Hiders: do NOT show seekers on map (rule requirement: hiders see only their own position + compass)
            if (userRole == Role.SEEKER) {
                val hiders = otherPlayers.filter { it.role == Role.HIDER && !it.isCaught }
                for (hider in hiders) {
                    if (hider.latitude != 0.0 && hider.longitude != 0.0) {
                        val hiderMarker = Marker(mapView).apply {
                            position = GeoPoint(hider.latitude, hider.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "${hider.name} (Hider)"
                            icon = context.getDrawable(android.R.drawable.presence_away)
                        }
                        mapView.overlays.add(hiderMarker)
                    }
                }
            }

            mapView.invalidate()
        }
    )
}
