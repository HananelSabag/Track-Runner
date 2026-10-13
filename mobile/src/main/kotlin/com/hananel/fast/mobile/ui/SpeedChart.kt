package com.hananel.fast.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hananel.fast.core.model.TrackPoint
import com.hananel.fast.core.tracking.Effort
import com.hananel.fast.core.tracking.EffortKind
import com.hananel.fast.core.tracking.Segment
import com.hananel.fast.core.tracking.SegmentKind
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Speed over the whole session, with the rests shaded and the sprints marked.
 * Dragging a finger across it reads out any moment.
 *
 * Time always runs left to right, whatever the language.
 *
 * @param segments work and rest, to shade the rests. Empty for a chart without them.
 * @param efforts fast efforts to mark on the line.
 */
@Composable
fun SpeedChart(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    segments: List<Segment> = emptyList(),
    efforts: List<Effort> = emptyList(),
    peakAtMs: Long? = null,
) {
    if (points.size < 2) return
    // Point times are measured from the start of the session, and so is the chart: a
    // stretch before the first fix shows as the empty stretch it was.
    val startMs = 0L
    val spanMs = points.last().timeMs.coerceAtLeast(1)
    val topKmh = remember(points) { (ceil(points.maxOf { it.speed.kmh } / 5.0) * 5.0).coerceAtLeast(10.0) }
    var selected by remember(points) { mutableStateOf<TrackPoint?>(null) }
    val reveal = rememberEntrance(durationMs = 900)

    val line = FastColors.Sky
    val sprint = FastColors.Rose
    val fast = FastColors.Zones[4]
    val restShade = MaterialTheme.colorScheme.surfaceContainerHigh
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val cursor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()

    Column(modifier) {
        // What the finger is pointing at, or a hint that it can point.
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = selected
            if (shown == null) {
                Text(
                    text = stringResource(R.string.chart_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Separate texts, so each number stays next to its own unit in a right-to-left sentence.
                val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                Text(formatClock(shown.timeMs - startMs), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.value_kmh, shown.speed.formatKmh()),
                    style = style,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                shown.heartRateBpm?.let { HeartRate(it, style) }
            }
        }

        LeftToRight {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .pointerInput(points) {
                        fun pick(x: Float) {
                            val left = AXIS_WIDTH.dp.toPx()
                            val t = startMs + ((x - left) / (size.width - left)).coerceIn(0f, 1f) * spanMs
                            selected = points.minByOrNull { abs(it.timeMs - t) }
                        }
                        detectTapGestures(onPress = { pick(it.x); tryAwaitRelease() })
                    }
                    .pointerInput(points) {
                        val left = AXIS_WIDTH.dp.toPx()
                        detectHorizontalDragGestures { change, _ ->
                            val t = startMs + ((change.position.x - left) / (size.width - left)).coerceIn(0f, 1f) * spanMs
                            selected = points.minByOrNull { abs(it.timeMs - t) }
                        }
                    },
            ) {
                val left = AXIS_WIDTH.dp.toPx()
                val bottom = size.height - TIME_AXIS_HEIGHT.dp.toPx()
                val plotWidth = size.width - left
                fun x(timeMs: Long) = left + (timeMs - startMs).toFloat() / spanMs * plotWidth
                fun y(kmh: Double) = bottom - (kmh / topKmh).toFloat() * bottom

                // Rests, shaded behind everything.
                for (segment in segments) {
                    if (segment.kind != SegmentKind.REST) continue
                    val from = x(segment.startMs.coerceAtLeast(startMs))
                    drawRect(restShade, topLeft = Offset(from, 0f), size = Size(x(segment.endMs) - from, bottom))
                }

                // Speed lines, labelled on the left.
                val step = if (topKmh > 20) 10 else 5
                var kmh = 0
                while (kmh <= topKmh) {
                    val lineY = y(kmh.toDouble())
                    drawLine(grid, Offset(left, lineY), Offset(size.width, lineY), strokeWidth = 1f)
                    val label = measurer.measure(kmh.toString(), labelStyle)
                    drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), lineY - label.size.height / 2))
                    kmh += step
                }

                // Time marks along the bottom.
                val tickMs = timeTick(spanMs)
                var tick = 0L
                while (tick <= spanMs) {
                    val label = measurer.measure(formatClock(tick), labelStyle)
                    val labelX = (x(startMs + tick) - label.size.width / 2).coerceIn(left, size.width - label.size.width)
                    drawText(label, topLeft = Offset(labelX, bottom + 4.dp.toPx()))
                    tick += tickMs
                }

                // The line draws itself from the start when the chart first appears.
                drawContext.canvas.save()
                drawContext.canvas.clipRect(0f, -24.dp.toPx(), left + plotWidth * reveal, size.height)

                // The speed itself. A gap in the signal breaks the line instead of drawing through it.
                val path = Path()
                val fill = Path()
                var penDown = false
                var runStartX = 0f
                var lastX = 0f
                for (i in points.indices) {
                    val point = points[i]
                    val px = x(point.timeMs)
                    val py = y(point.speed.kmh)
                    val gap = i > 0 && point.timeMs - points[i - 1].timeMs > GAP_MS
                    if (!penDown || gap) {
                        if (penDown) {
                            fill.lineTo(lastX, bottom)
                            fill.lineTo(runStartX, bottom)
                            fill.close()
                        }
                        path.moveTo(px, py)
                        fill.moveTo(px, py)
                        runStartX = px
                        penDown = true
                    } else {
                        path.lineTo(px, py)
                        fill.lineTo(px, py)
                    }
                    lastX = px
                }
                if (penDown) {
                    fill.lineTo(lastX, bottom)
                    fill.lineTo(runStartX, bottom)
                    fill.close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.02f)), endY = bottom))
                drawPath(path, line, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Fast efforts, drawn over the line in their own colour.
                for (effort in efforts) {
                    val effortPath = Path()
                    var started = false
                    for (point in points) {
                        if (point.timeMs < effort.startMs || point.timeMs > effort.endMs) continue
                        if (!started) effortPath.moveTo(x(point.timeMs), y(point.speed.kmh)) else effortPath.lineTo(x(point.timeMs), y(point.speed.kmh))
                        started = true
                    }
                    drawPath(
                        effortPath,
                        if (effort.kind == EffortKind.SPRINT) sprint else fast,
                        style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                // The fastest moment.
                points.firstOrNull { it.timeMs == peakAtMs }?.let { peak ->
                    val center = Offset(x(peak.timeMs), y(peak.speed.kmh))
                    drawCircle(sprint, radius = 4.5.dp.toPx(), center = center)
                    drawCircle(Color.White, radius = 1.8.dp.toPx(), center = center)
                }
                drawContext.canvas.restore()

                selected?.let { point ->
                    val px = x(point.timeMs)
                    drawLine(cursor.copy(alpha = 0.6f), Offset(px, 0f), Offset(px, bottom), strokeWidth = 1.dp.toPx())
                    drawCircle(cursor, radius = 4.dp.toPx(), center = Offset(px, y(point.speed.kmh)))
                }
            }
        }
    }
}

/** A round interval that puts four to seven marks across the chart. */
private fun timeTick(spanMs: Long): Long {
    val candidates = longArrayOf(10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000, 3_600_000)
    return candidates.firstOrNull { spanMs / it <= 6 } ?: candidates.last()
}

private const val AXIS_WIDTH = 26
private const val TIME_AXIS_HEIGHT = 20

/** Samples further apart than this are a signal gap, not continuous movement. */
private const val GAP_MS = 5_000L
