package com.hananel.fast.mobile.reference

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.HeroNumber
import com.hananel.fast.mobile.ui.SectionTitle
import com.hananel.fast.mobile.ui.StatTile
import com.hananel.fast.mobile.ui.TopBar
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatDateTime
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.label
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone as a second measuring instrument: start here, start a run on the
 * watch, and the same sprint is recorded twice.
 */
@Composable
fun ReferenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val state by ReferenceRecorder.state.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) ReferenceService.start(context) else denied = true
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ReferenceRecorder.refresh(context) } }

    fun start() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            ReferenceService.start(context)
        } else {
            // The notification permission only makes the "measuring" notice visible; location is what matters.
            permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(title = stringResource(R.string.reference_title), onBack = onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val current = state) {
                is ReferenceState.Recording -> RecordingBody(current, onStop = { ReferenceRecorder.stop(context) })
                is ReferenceState.Idle -> IdleBody(current, denied, onStart = ::start)
            }
        }
    }
}

@Composable
private fun IdleBody(state: ReferenceState.Idle, denied: Boolean, onStart: () -> Unit) {
    Card {
        Text(stringResource(R.string.reference_what_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.reference_what_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        for (step in listOf(R.string.reference_step_1, R.string.reference_step_2, R.string.reference_step_3, R.string.reference_step_4)) {
            Text(stringResource(step), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (denied) {
        Text(
            text = stringResource(R.string.reference_permission_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reference_start)) }

    state.last?.let { last ->
        Card {
            Text(stringResource(R.string.reference_saved_title), style = MaterialTheme.typography.titleMedium, color = FastColors.Mint)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.reference_duration), formatClock(last.durationMs))
                StatTile(stringResource(R.string.reference_fixes), last.fixes.toString())
                StatTile(stringResource(R.string.reference_rate), String.format(Locale.US, "%.1f", last.fixesPerSecond), stringResource(R.string.reference_per_second))
            }
        }
    }
    if (state.entries.isNotEmpty()) {
        SectionTitle(stringResource(R.string.reference_list_title), Modifier.padding(top = 8.dp))
    }
    for (entry in state.entries) {
        EntryBlock(entry)
    }
}

/** One stored measurement: when, how long, how dense — and the watch beside it, when the watch was recording too. */
@Composable
private fun EntryBlock(entry: ReferenceEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDateTime(entry.startedUtcMs) + " · " + formatClock(entry.durationMs),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.reference_entry_fixes, entry.fixes, String.format(Locale.US, "%.1f", entry.fixesPerSecond)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!entry.endedCleanly) {
            Text(
                text = stringResource(R.string.reference_cut_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        val comparison = entry.comparison
        val kind = entry.watchKind
        if (comparison != null && kind != null) {
            ComparisonCard(comparison, title = stringResource(R.string.reference_against, kind.label()))
        } else {
            Card {
                Text(
                    text = stringResource(R.string.compare_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordingBody(state: ReferenceState.Recording, onStop: () -> Unit) {
    Card {
        Text(stringResource(R.string.reference_recording), style = MaterialTheme.typography.labelLarge, color = FastColors.Mint)
        HeroNumber(formatClock(state.elapsedMs), fontSize = 56)
        Text(
            text = state.speed?.let { stringResource(R.string.value_kmh, it.formatKmh()) } ?: stringResource(R.string.reference_waiting),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.reference_fixes), state.fixes.toString())
        // The number this tool exists to find out: how often this receiver really delivers a fix.
        StatTile(
            label = stringResource(R.string.reference_rate),
            value = String.format(Locale.US, "%.1f", state.fixesPerSecond),
            unit = stringResource(R.string.reference_per_second),
            valueColor = FastColors.Sky,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            label = stringResource(R.string.reference_speed_accuracy),
            value = state.speedAccuracy?.let { plusMinus(it.formatKmh()) } ?: "—",
            unit = stringResource(R.string.unit_kmh),
        )
        StatTile(
            label = stringResource(R.string.reference_position_accuracy),
            value = state.positionAccuracyMeters?.let { plusMinus(String.format(Locale.US, "%.0f", it)) } ?: "—",
            unit = stringResource(R.string.value_meters_text),
        )
        StatTile(
            label = stringResource(R.string.reference_satellites),
            value = state.satellites?.let { "${it.usedInFix}" } ?: "—",
        )
    }
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = FastColors.RoseDeep, contentColor = FastColors.Ice),
    ) { Text(stringResource(R.string.reference_stop)) }
}

/** "±0.3", held left to right so the sign stays in front of the number in a right-to-left screen. */
private fun plusMinus(number: String): String = "\u2066±$number\u2069"
