package com.hananel.fast.mobile.reference

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.formatClock
import com.hananel.fast.mobile.ui.formatKmh

/**
 * The phone as a measuring instrument, on the home screen: start or stop a
 * measurement, and the switch that makes the phone follow the watch (D43).
 */
@Composable
fun PhoneMeasureCard(onOpenMeasurements: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val state by ReferenceRecorder.state.collectAsStateWithLifecycle()
    val armed by ReferenceRecorder.armed.collectAsStateWithLifecycle()
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) afterPermission?.invoke()
    }

    fun withLocation(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            afterPermission = action
            permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reference_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenMeasurements) { Text(stringResource(R.string.measure_saved)) }
        }
        when (val current = state) {
            is ReferenceState.Recording -> Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiveDot()
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reference_recording) + " · " + formatClock(current.elapsedMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = FastColors.Mint,
                    )
                    Text(
                        text = current.speed?.let { stringResource(R.string.value_kmh, it.formatKmh()) } ?: stringResource(R.string.reference_waiting),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Button(
                    onClick = { ReferenceService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = FastColors.RoseDeep, contentColor = FastColors.Ice),
                ) { Text(stringResource(R.string.reference_stop)) }
            }
            is ReferenceState.Idle -> Button(
                onClick = { withLocation { ReferenceService.start(context) } },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text(stringResource(R.string.measure_start)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.link_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(if (armed) R.string.link_waiting else R.string.link_off),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (armed) FastColors.Mint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = armed,
                onCheckedChange = { on -> if (on) withLocation { ReferenceService.arm(context) } else ReferenceService.disarm(context) },
            )
        }
    }
}

/** A dot that breathes while something is being recorded. */
@Composable
private fun LiveDot() {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "live dot")
    Box(Modifier.size(12.dp).alpha(alpha).background(FastColors.Mint, CircleShape))
}
