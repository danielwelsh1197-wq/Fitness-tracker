package com.example.fitnessdashboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Decode a Google-encoded polyline string into (lat, lng) points. */
fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        out.add(lat / 1e5 to lng / 1e5)
    }
    return out
}

/**
 * Draws every route overlaid at low opacity, so frequently-run paths build up
 * brighter — a route "heatmap" with no map basemap (and no external dependency).
 */
@Composable
fun RouteHeatmap(
    routes: List<List<Pair<Double, Double>>>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 280.dp,
) {
    val tracks = routes.filter { it.size >= 2 }
    if (tracks.isEmpty()) {
        Text("No route data yet", style = MaterialTheme.typography.bodySmall)
        return
    }
    // Keep only the dominant cluster: a far-away trip would stretch the fixed
    // view across continents and squash the routes you actually run at home.
    val starts = tracks.map { it.first() }
    val medLat = median(starts.map { it.first })
    val medLng = median(starts.map { it.second })
    val shown = tracks.filter {
        val s = it.first()
        abs(s.first - medLat) <= LOCAL_DEGREES && abs(s.second - medLng) <= LOCAL_DEGREES
    }.ifEmpty { tracks }
    val excluded = tracks.size - shown.size

    val all = shown.flatten()
    val minLat = all.minOf { it.first }
    val maxLat = all.maxOf { it.first }
    val minLng = all.minOf { it.second }
    val maxLng = all.maxOf { it.second }
    val lngScale = cos(((minLat + maxLat) / 2.0) * PI / 180.0)   // equirectangular correction
    val spanX = ((maxLng - minLng) * lngScale).coerceAtLeast(1e-9)
    val spanY = (maxLat - minLat).coerceAtLeast(1e-9)
    val color = MaterialTheme.colorScheme.primary

    Column {
        Canvas(modifier.fillMaxWidth().height(height)) {
            val pad = 12f
            val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
            val drawW = (spanX * scale).toFloat()
            val drawH = (spanY * scale).toFloat()
            val offX = (size.width - drawW) / 2f
            val offY = (size.height - drawH) / 2f
            fun project(lat: Double, lng: Double) = Offset(
                offX + (((lng - minLng) * lngScale) * scale).toFloat(),
                offY + drawH - ((lat - minLat) * scale).toFloat(),   // north points up
            )
            shown.forEach { track ->
                val path = Path()
                track.forEachIndexed { i, (lat, lng) ->
                    val o = project(lat, lng)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, color = color.copy(alpha = 0.22f), style = Stroke(width = 2f))
            }
        }
        if (excluded > 0) {
            Text(
                "$excluded run${if (excluded == 1) "" else "s"} in other areas not shown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val LOCAL_DEGREES = 1.0   // ~110 km — same metro area, not another city

private fun median(xs: List<Double>): Double {
    val sorted = xs.sorted()
    return sorted[sorted.size / 2]
}
