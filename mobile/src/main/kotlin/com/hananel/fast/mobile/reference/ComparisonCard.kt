package com.hananel.fast.mobile.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.DeviceComparison
import com.hananel.fast.core.data.ReceiverFigures
import com.hananel.fast.core.model.Speed
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatKmh
import java.util.Locale

/**
 * The watch and the phone side by side over the stretch both measured: what each
 * reported, what each one's positions imply, and what each claims about itself.
 *
 * Reported speed and position-implied speed are both shown on purpose. The watch's
 * reported speed is smoothed by its receiver (Q17); the positions are the check.
 */
@Composable
fun ComparisonCard(comparison: DeviceComparison, modifier: Modifier = Modifier, title: String = stringResource(R.string.compare_title)) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.compare_shared, formatClock(comparison.sharedMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderRow()
            val watch = comparison.watch
            val phone = comparison.phone
            FigureRow(R.string.compare_best_reported, watch.best3sReported.formatKmh(), phone.best3sReported.formatKmh(), emphasize = true)
            FigureRow(R.string.compare_best_positions, watch.best3sFromPositions.orDash(), phone.best3sFromPositions.orDash())
            FigureRow(R.string.compare_distance_speeds, meters(watch.distanceFromSpeedsMeters), meters(phone.distanceFromSpeedsMeters))
            FigureRow(R.string.compare_distance_positions, watch.distanceFromPositionsMeters.metersOrDash(), phone.distanceFromPositionsMeters.metersOrDash())
            FigureRow(R.string.compare_rate, watch.rate(), phone.rate())
            FigureRow(R.string.compare_speed_accuracy, watch.claimedSpeed(), phone.claimedSpeed())
            FigureRow(R.string.compare_position_accuracy, watch.claimedPosition(), phone.claimedPosition())
        }
        comparison.meanSeparationMeters?.let {
            Text(
                text = stringResource(R.string.compare_separation, String.format(Locale.US, "%.0f", it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        ColumnHead(stringResource(R.string.compare_watch), FastColors.Lilac)
        ColumnHead(stringResource(R.string.compare_phone), FastColors.Cyan)
    }
}

@Composable
private fun ColumnHead(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(VALUE_WIDTH),
    )
}

@Composable
private fun FigureRow(label: Int, watch: String, phone: String, emphasize: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        for (value in listOf(watch, phone)) {
            Text(
                // Numbers keep their own direction inside the Hebrew row.
                text = "⁦$value⁩",
                style = if (emphasize) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(VALUE_WIDTH),
            )
        }
    }
}

@Composable
private fun meters(value: Double): String = stringResource(R.string.value_meters, value.toInt())

@Composable
private fun Double?.metersOrDash(): String = this?.let { meters(it) } ?: "—"

private fun Speed?.orDash(): String = this?.formatKmh() ?: "—"

private fun ReceiverFigures.rate(): String = String.format(Locale.US, "%.1f", fixesPerSecond)

/** "±1.0", or a dash for a receiver that claims nothing believable — the watch fills in a placeholder. */
private fun ReceiverFigures.claimedSpeed(): String = speedAccuracy?.let { "±" + it.formatKmh() } ?: "—"

private fun ReceiverFigures.claimedPosition(): String = positionAccuracyMeters?.let { "±" + String.format(Locale.US, "%.0f", it) } ?: "—"

private val VALUE_WIDTH = 72.dp
