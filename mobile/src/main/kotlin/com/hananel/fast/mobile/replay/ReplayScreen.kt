package com.hananel.fast.mobile.replay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hananel.fast.core.data.SessionInsight
import com.hananel.fast.core.model.TrackPoint
import com.hananel.fast.core.tracking.Effort
import com.hananel.fast.core.tracking.EffortKind
import com.hananel.fast.core.tracking.SpeedZones
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.data.PhoneData
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.HeartRate
import com.hananel.fast.mobile.ui.LeftToRight
import com.hananel.fast.mobile.ui.TopBar
import com.hananel.fast.mobile.ui.color
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatDateTime
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.label
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Composable
fun ReplayRoute(id: String, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val insight by produceState<SessionInsight?>(initialValue = null, id) { value = PhoneData.insight(context, id) }
    insight?.let { ReplayScreen(it, onBack) }
}

private enum class ReplayMode { TRACK, HEAT, SPRINTS }

/**
 * The session played back like a film: the track drawn to scale, a dot moving
 * along it in fast-forward, its trail coloured by speed.
 *
 * There is no map underneath — nothing is fetched, so nothing about where the
 * player trains leaves the phone. A grid and a scale bar give the size.
 */
@Composable
fun ReplayScreen(insight: SessionInsight, onBack: () -> Unit) {
    val analysis = insight.analysis
    // Colours on the track mean the same as everywhere else in this session.
    val zones = insight.profile.zones
    val track = remember(insight) { Track.of(insight.points, zones) }
    val efforts = analysis?.efforts.orEmpty()
    val totalMs = track.times.lastOrNull()?.toFloat() ?: 0f

    var positionMs by rememberSaveable { mutableFloatStateOf(0f) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var factor by rememberSaveable { mutableIntStateOf(DEFAULT_FACTOR) }
    var mode by rememberSaveable { mutableStateOf(ReplayMode.TRACK) }

    LaunchedEffect(playing, factor) {
        if (!playing) return@LaunchedEffect
        if (positionMs >= totalMs) positionMs = 0f
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            positionMs = min(totalMs, positionMs + (now - last) / 1_000_000f * factor)
            last = now
            if (positionMs >= totalMs) {
                playing = false
                break
            }
        }
    }

    val index = track.indexAt(positionMs.toLong())
    val current = track.points.getOrNull(index)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(title = stringResource(R.string.replay_title), subtitle = formatDateTime(insight.session.header.startedUtcMs), onBack = onBack)

        Readout(positionMs.toLong(), current, zones)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(FastColors.Pitch),
        ) {
            TrackCanvas(track, efforts, index, mode)
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = { playing = !playing }) {
                    if (playing) PauseIcon() else Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.replay_play))
                }
                // Time runs left to right, so the scrubber does too.
                Box(Modifier.weight(1f)) {
                    LeftToRight {
                        Slider(
                            value = positionMs,
                            onValueChange = { positionMs = it },
                            valueRange = 0f..max(totalMs, 1f),
                        )
                    }
                }
                Text(formatClock(totalMs.toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in FACTORS) {
                    // "×30" has no letters to give it a direction, so it is told: the sign comes first.
                    FilterChip(selected = factor == option, onClick = { factor = option }, label = { LeftToRight { Text("×$option") } })
                }
                Spacer(Modifier.weight(1f))
                val next = efforts.firstOrNull { it.kind == EffortKind.SPRINT && it.startMs > positionMs + SPRINT_LEAD_IN_MS }
                if (next != null) {
                    TextButton(onClick = { positionMs = max(0f, next.startMs - SPRINT_LEAD_IN_MS.toFloat()) }) {
                        Text(stringResource(R.string.replay_next_sprint))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = mode == ReplayMode.TRACK, onClick = { mode = ReplayMode.TRACK }, label = { Text(stringResource(R.string.replay_mode_track)) })
                FilterChip(selected = mode == ReplayMode.HEAT, onClick = { mode = ReplayMode.HEAT }, label = { Text(stringResource(R.string.replay_mode_heat)) })
                if (efforts.isNotEmpty()) {
                    FilterChip(selected = mode == ReplayMode.SPRINTS, onClick = { mode = ReplayMode.SPRINTS }, label = { Text(stringResource(R.string.replay_mode_sprints)) })
                }
            }
        }
    }
}

/** What is happening at the moment being shown. */
@Composable
private fun Readout(positionMs: Long, point: TrackPoint?, zones: SpeedZones) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(formatClock(positionMs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (point != null) {
            val zone = zones.zoneOf(point.speed)
            Text(
                text = stringResource(R.string.value_kmh, point.speed.formatKmh()),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            )
            Text(zone.label(), style = MaterialTheme.typography.titleMedium, color = zone.color())
            Spacer(Modifier.weight(1f))
            point.heartRateBpm?.let { HeartRate(it, MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun TrackCanvas(track: Track, efforts: List<Effort>, index: Int, mode: ReplayMode) {
    val measurer = rememberTextMeasurer()
    val gridColor = Color.White.copy(alpha = 0.06f)
    val labelStyle = TextStyle(fontSize = 11.sp, color = FastColors.Mist)
    val effortLabel = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    val scaleLabel = stringResource(R.string.value_meters_text)
    val heat = remember(track) { HeatMap.of(track) }

    // Drawn left to right whatever the language: east stays on the right.
    LeftToRight {
        Canvas(Modifier.fillMaxSize()) {
            if (track.size < 2) return@Canvas
            val padding = 28.dp.toPx()
            // With no map underneath, north has no claim to be up. The track is turned
            // on its side when that lets it fill the view: a pitch is longer than it is wide,
            // and so is a phone.
            val turned = (track.widthMeters > track.heightMeters) != (size.width > size.height)
            val across = if (turned) track.heightMeters else track.widthMeters
            val along = if (turned) track.widthMeters else track.heightMeters
            val scale = min((size.width - 2 * padding) / across, (size.height - 2 * padding) / along)
            val originX = (size.width - across * scale) / 2
            val originY = (size.height + along * scale) / 2
            fun at(i: Int) = if (turned) {
                Offset(originX + track.ys[i] * scale, originY - track.xs[i] * scale)
            } else {
                Offset(originX + track.xs[i] * scale, originY - track.ys[i] * scale)
            }

            // A grid in round metres, so distances can be judged by eye.
            val gridMeters = GRID_STEPS.firstOrNull { it * scale >= 22.dp.toPx() } ?: GRID_STEPS.last()
            var gx = originX % (gridMeters * scale)
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height))
                gx += gridMeters * scale
            }
            var gy = originY % (gridMeters * scale)
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy))
                gy += gridMeters * scale
            }

            when (mode) {
                ReplayMode.TRACK -> {
                    // Where the player has yet to go, barely there; where they have been, dimmed;
                    // and the last minute and a half bright behind the dot, like the tail of a comet.
                    drawTrack(track, index, track.size - 1, ::at, width = 2.dp.toPx()) { 0.10f }
                    val nowMs = track.times[index]
                    drawTrack(track, 0, index, ::at, width = 3.dp.toPx()) { i ->
                        val age = nowMs - track.times[i]
                        if (age >= TRAIL_MS) PAST_ALPHA else PAST_ALPHA + (1f - PAST_ALPHA) * (1f - age.toFloat() / TRAIL_MS)
                    }
                }
                ReplayMode.HEAT -> heat.draw(this, originX, originY, scale, turned)
                ReplayMode.SPRINTS -> {
                    drawTrack(track, 0, track.size - 1, ::at, width = 2.dp.toPx()) { 0.14f }
                    var number = 0
                    for (effort in efforts) {
                        val from = track.indexAt(effort.startMs)
                        val to = track.indexAt(effort.endMs)
                        if (to <= from) continue
                        val color = if (effort.kind == EffortKind.SPRINT) FastColors.Rose else FastColors.Zones[4]
                        for (i in from until to) {
                            if (track.times[i + 1] - track.times[i] > GAP_MS) continue
                            drawLine(color, at(i), at(i + 1), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        }
                        if (effort.kind == EffortKind.SPRINT) {
                            number++
                            drawCircle(FastColors.RoseDeep, radius = 9.dp.toPx(), center = at(from))
                            val text = measurer.measure(number.toString(), effortLabel)
                            drawText(text, topLeft = at(from) - Offset(text.size.width / 2f, text.size.height / 2f))
                        }
                    }
                }
            }

            if (mode != ReplayMode.HEAT) {
                val here = at(index)
                drawCircle(track.colors[index].copy(alpha = 0.3f), radius = 13.dp.toPx(), center = here)
                drawCircle(Color.White, radius = 5.5.dp.toPx(), center = here)
                drawCircle(track.colors[index], radius = 3.5.dp.toPx(), center = here)
            }

            // Scale bar: the grid's own step, labelled.
            val barY = size.height - 14.dp.toPx()
            val barX = 14.dp.toPx()
            drawLine(FastColors.Mist, Offset(barX, barY), Offset(barX + gridMeters * scale, barY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawText(
                measurer.measure("$gridMeters $scaleLabel", labelStyle),
                topLeft = Offset(barX + gridMeters * scale + 6.dp.toPx(), barY - 8.dp.toPx()),
            )
        }
    }
}

/**
 * Draws the track from [from] to [to], each step in the colour of its speed and as
 * opaque as [alpha] says for that step. A signal gap is left open.
 */
private fun DrawScope.drawTrack(track: Track, from: Int, to: Int, at: (Int) -> Offset, width: Float, alpha: (Int) -> Float) {
    for (i in from until to) {
        if (track.times[i + 1] - track.times[i] > GAP_MS) continue
        drawLine(track.colors[i + 1].copy(alpha = alpha(i + 1)), at(i), at(i + 1), strokeWidth = width, cap = StrokeCap.Round)
    }
}

@Composable
private fun PauseIcon() {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(18.dp)) {
        val bar = Size(size.width * 0.28f, size.height)
        drawRect(color, topLeft = Offset(size.width * 0.12f, 0f), size = bar)
        drawRect(color, topLeft = Offset(size.width * 0.60f, 0f), size = bar)
    }
}

/** The positions of a session in metres on a flat plane, east and north of its south-west corner. */
private class Track(
    /** The points that have a position, in time order; every array below runs parallel to it. */
    val points: List<TrackPoint>,
    val times: LongArray,
    val xs: FloatArray,
    val ys: FloatArray,
    val colors: List<Color>,
    val widthMeters: Float,
    val heightMeters: Float,
) {
    val size: Int get() = times.size

    /** The last point at or before [timeMs]. */
    fun indexAt(timeMs: Long): Int {
        if (size == 0) return 0
        var low = 0
        var high = size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (times[middle] <= timeMs) low = middle else high = middle - 1
        }
        return low
    }

    companion object {
        fun of(points: List<TrackPoint>, zones: SpeedZones): Track {
            val located = points.filter { it.position != null }
            if (located.isEmpty()) return Track(emptyList(), LongArray(0), FloatArray(0), FloatArray(0), emptyList(), 1f, 1f)
            val south = located.minOf { it.position!!.latitude }
            val west = located.minOf { it.position!!.longitude }
            val metersPerDegreeLongitude = METERS_PER_DEGREE * cos(Math.toRadians(south))
            val xs = FloatArray(located.size) { ((located[it].position!!.longitude - west) * metersPerDegreeLongitude).toFloat() }
            val ys = FloatArray(located.size) { ((located[it].position!!.latitude - south) * METERS_PER_DEGREE).toFloat() }
            return Track(
                points = located,
                times = LongArray(located.size) { located[it].timeMs },
                xs = xs,
                ys = ys,
                colors = located.map { zones.zoneOf(it.speed).color() },
                // A session spent standing still must not divide by zero.
                widthMeters = xs.max().coerceAtLeast(MIN_EXTENT_METERS),
                heightMeters = ys.max().coerceAtLeast(MIN_EXTENT_METERS),
            )
        }
    }
}

/** Where the player spent their time: seconds per cell, smoothed so it reads as areas rather than squares. */
private class HeatMap(val columns: Int, val rows: Int, val cellMeters: Float, val weights: FloatArray) {

    /** [turned] matches the track drawn over the same view: east up instead of north up. */
    fun draw(scope: DrawScope, originX: Float, originY: Float, scale: Float, turned: Boolean) = with(scope) {
        val cell = cellMeters * scale
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val weight = weights[row * columns + column]
                if (weight < 0.02f) continue
                // Cool to hot, in the same hues as the speed zones.
                val color = if (weight < 0.5f) lerp(FastColors.Electric, FastColors.Violet, weight * 2) else lerp(FastColors.Violet, FastColors.Rose, (weight - 0.5f) * 2)
                drawRect(
                    color = color.copy(alpha = 0.15f + 0.8f * weight),
                    topLeft = if (turned) {
                        Offset(originX + (row - MARGIN_CELLS) * cell, originY - (column - MARGIN_CELLS + 1) * cell)
                    } else {
                        Offset(originX + (column - MARGIN_CELLS) * cell, originY - (row - MARGIN_CELLS + 1) * cell)
                    },
                    size = Size(cell + 0.5f, cell + 0.5f),
                )
            }
        }
    }

    companion object {
        private const val MARGIN_CELLS = 2

        fun of(track: Track): HeatMap {
            val cellMeters = max(1.5f, max(track.widthMeters, track.heightMeters) / 60f)
            val columns = (track.widthMeters / cellMeters).toInt() + 1 + 2 * MARGIN_CELLS
            val rows = (track.heightMeters / cellMeters).toInt() + 1 + 2 * MARGIN_CELLS
            var grid = FloatArray(columns * rows)
            for (i in 1 until track.size) {
                // Each point stands for the time since the one before it; a signal gap counts for nothing.
                val seconds = (track.times[i] - track.times[i - 1]) / 1000f
                if (seconds <= 0f || seconds > GAP_MS / 1000f) continue
                val column = (track.xs[i] / cellMeters).toInt() + MARGIN_CELLS
                val row = (track.ys[i] / cellMeters).toInt() + MARGIN_CELLS
                grid[row * columns + column] += seconds
            }
            repeat(2) { grid = blur(grid, columns, rows) }
            // A corner the player stood in for minutes would otherwise wash everything else out.
            val visited = grid.filter { it > 0f }.sorted()
            val top = visited.getOrNull((visited.size * 0.97f).toInt().coerceAtMost(visited.lastIndex))?.coerceAtLeast(0.001f) ?: 1f
            return HeatMap(columns, rows, cellMeters, FloatArray(grid.size) { min(1f, grid[it] / top).pow(0.6f) })
        }

        private fun blur(grid: FloatArray, columns: Int, rows: Int): FloatArray {
            val out = FloatArray(grid.size)
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    var sum = 0f
                    var count = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val r = row + dy
                            val c = column + dx
                            if (r in 0 until rows && c in 0 until columns) {
                                sum += grid[r * columns + c]
                                count++
                            }
                        }
                    }
                    out[row * columns + column] = sum / count
                }
            }
            return out
        }
    }
}

private const val METERS_PER_DEGREE = 111_320.0
private const val MIN_EXTENT_METERS = 10f
private const val GAP_MS = 5_000L

/** How far before a sprint the "next sprint" jump lands, so the run-up is seen too. */
private const val SPRINT_LEAD_IN_MS = 3_000L
private const val DEFAULT_FACTOR = 30

/** How much of the session, in its own time, glows behind the moving dot. */
private const val TRAIL_MS = 90_000L
private const val PAST_ALPHA = 0.3f
private val FACTORS = listOf(10, 30, 60, 120)
private val GRID_STEPS = listOf(5, 10, 20, 50, 100, 200, 500, 1000)
