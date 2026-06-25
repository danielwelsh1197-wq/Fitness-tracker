package com.example.fitnessdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Icon + friendly label for a sport bucket. */
data class SportVisual(val label: String, val icon: ImageVector)

fun sportVisual(sport: String): SportVisual = when (sport.lowercase()) {
    "run" -> SportVisual("Running", Icons.Filled.DirectionsRun)
    "swim" -> SportVisual("Swimming", Icons.Filled.Pool)
    "lift" -> SportVisual("Lifting", Icons.Filled.FitnessCenter)
    else -> SportVisual(sport.replaceFirstChar { it.uppercase() }, Icons.Filled.Sports)
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lightweight bar chart: list of (label, value). No external chart dependency. */
@Composable
fun BarChart(
    bars: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (bars.isEmpty()) {
        Text("No data yet", style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = (bars.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bars.forEach { (_, value) ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction = (value / max).coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bars.forEach { (label, _) ->
                Text(
                    label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One line for [LineChart]. Set `asPoints` to draw a scatter instead of a line. */
data class LineSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Float, Float>>,
    val asPoints: Boolean = false,
)

/**
 * Lightweight multi-line chart drawn with Canvas (no external dependency).
 * `xMax` fixes the x-axis. The y-axis runs `yMin`..`yMax` (yMax null => a round
 * max above the data); `yLabel` formats the axis ticks (e.g. m:ss for pace).
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    xMax: Float = 366f,
    xTicks: List<Pair<Float, String>> = emptyList(),
    height: androidx.compose.ui.unit.Dp = 200.dp,
    yMin: Float = 0f,
    yMax: Float? = null,
    yLabel: (Float) -> String = { it.roundToInt().toString() },
) {
    val points = series.flatMap { it.points }
    if (points.size < 2) {
        Text("No data yet", style = MaterialTheme.typography.bodySmall)
        return
    }
    val top = (yMax ?: niceCeil(points.maxOf { it.second })).coerceAtLeast(yMin + 1f)
    val bottom = yMin
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val labelPx = with(LocalDensity.current) { 10.sp.toPx() }

    Canvas(modifier.fillMaxWidth().height(height)) {
        val leftPad = 48f
        val bottomPad = if (xTicks.isEmpty()) 8f else 26f
        val topPad = 10f
        val rightPad = 10f
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        fun px(x: Float) = leftPad + (if (xMax == 0f) 0f else x / xMax) * chartW
        fun py(y: Float) = topPad + chartH - ((y - bottom) / (top - bottom)) * chartH

        val textPaint = android.graphics.Paint().apply {
            color = labelArgb
            textSize = labelPx
            isAntiAlias = true
        }
        val steps = 4
        for (i in 0..steps) {
            val v = bottom + (top - bottom) * i / steps
            val y = py(v)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(yLabel(v), 4f, y + labelPx / 3f, textPaint)
        }
        val xPaint = android.graphics.Paint(textPaint).apply {
            textAlign = android.graphics.Paint.Align.CENTER
        }
        xTicks.forEach { (xv, label) ->
            drawContext.canvas.nativeCanvas.drawText(label, px(xv), size.height - 6f, xPaint)
        }
        series.forEach { s ->
            val pts = s.points.sortedBy { it.first }
            if (s.asPoints) {
                pts.forEach { (x, y) -> drawCircle(s.color, radius = 3.5f, center = Offset(px(x), py(y))) }
            } else if (pts.size >= 2) {
                val path = Path()
                pts.forEachIndexed { i, (x, y) ->
                    if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
                }
                drawPath(path, color = s.color, style = Stroke(width = 4f))
            }
        }
    }
}

/** Round up to a "nice" axis maximum (1/2/5 × 10ⁿ). */
private fun niceCeil(v: Float): Float {
    if (v <= 0f) return 1f
    val mag = 10.0.pow(floor(log10(v.toDouble()))).toFloat()
    val n = v / mag
    val nice = when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 5f -> 5f
        else -> 10f
    }
    return nice * mag
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}
