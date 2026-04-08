package com.subwayalert.presentation.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.subwayalert.domain.model.LocationPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMapView(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                
                // Default center if no points
                controller.setCenter(GeoPoint(31.2304, 121.4737)) // Shanghai
            }
        },
        update = { mapView ->
            // Clear existing overlays except tile layer
            mapView.overlays.clear()
            
            if (points.isNotEmpty()) {
                // Calculate center of all points
                val avgLat = points.map { it.latitude }.average()
                val avgLng = points.map { it.longitude }.average()
                val center = GeoPoint(avgLat, avgLng)
                
                // Draw polyline for the track
                val polyline = Polyline().apply {
                    outlinePaint.color = Color.parseColor("#2196F3") // Blue
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.style = Paint.Style.STROKE
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    
                    points.forEach { point ->
                        addPoint(GeoPoint(point.latitude, point.longitude))
                    }
                }
                mapView.overlays.add(polyline)
                
                // Add start marker (green)
                val startPoint = points.first()
                val startMarker = Marker(mapView).apply {
                    position = GeoPoint(startPoint.latitude, startPoint.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "起点 ${startPoint.getFormattedTime()}"
                }
                mapView.overlays.add(startMarker)
                
                // Add end marker (red)
                val endPoint = points.last()
                val endMarker = Marker(mapView).apply {
                    position = GeoPoint(endPoint.latitude, endPoint.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "终点 ${endPoint.getFormattedTime()}"
                }
                mapView.overlays.add(endMarker)
                
                // Add intermediate markers (if more than 2 points)
                if (points.size > 2) {
                    points.drop(1).dropLast(1).forEachIndexed { index, point ->
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(point.latitude, point.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = "#${index + 2} ${point.getFormattedTime()}"
                        }
                        mapView.overlays.add(marker)
                    }
                }
                
                // Zoom to fit the track
                mapView.controller.setCenter(center)
                mapView.controller.setZoom(16.0)
            }
            
            mapView.invalidate()
        }
    )
}
