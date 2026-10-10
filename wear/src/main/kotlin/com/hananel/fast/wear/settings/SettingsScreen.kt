package com.hananel.fast.wear.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.hananel.fast.wear.R
import com.hananel.fast.wear.data.WearData
import com.hananel.fast.wear.record.AgeScreen
import com.hananel.fast.wear.record.DEFAULT_AGE
import com.hananel.fast.wear.sync.SyncEngine
import com.hananel.fast.wear.theme.FastColors
import com.hananel.fast.wear.ui.edgeMorphing
import com.hananel.fast.wear.ui.formatKmh
import com.hananel.fast.wear.ui.withSideMargins

@Composable
fun SettingsRoute(onEditAge: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val settings = remember { WearData.settings(context) }
    val pending by SyncEngine.pendingCount.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { SyncEngine.syncPending(context) }

    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val age = settings.ageYears
    val maxBpm = settings.maxBpm
    val topSpeed = settings.topSpeed

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding.withSideMargins()) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(R.string.home_settings)) }
            }
            item {
                FilledTonalButton(
                    onClick = onEditAge,
                    label = { Text(stringResource(R.string.settings_age)) },
                    secondaryLabel = {
                        Text(
                            if (age != null && maxBpm != null) {
                                stringResource(R.string.settings_age_value, age, maxBpm)
                            } else {
                                stringResource(R.string.settings_age_unset)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
            item {
                // The speed that decides where "sprint" begins. Shown so the bands are not a mystery.
                Text(
                    text = topSpeed?.let { stringResource(R.string.settings_top_speed, it.formatKmh()) }
                        ?: stringResource(R.string.settings_top_speed_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec).padding(vertical = 4.dp),
                )
            }
            item {
                Text(
                    text = if (pending == 0) {
                        stringResource(R.string.settings_sync_done)
                    } else {
                        pluralStringResource(R.plurals.settings_sync_pending, pending, pending)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pending == 0) FastColors.Mint else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = edgeMorphing(spec).padding(top = 4.dp),
                )
            }
            if (pending > 0) {
                item(key = "sync") {
                    FilledTonalButton(
                        onClick = { SyncEngine.syncPending(context) },
                        label = { Text(stringResource(R.string.settings_sync_now), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }
}

@Composable
fun EditAgeRoute(onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val settings = remember { WearData.settings(context) }
    AgeScreen(
        initialAge = settings.ageYears ?: DEFAULT_AGE,
        onConfirm = { age ->
            settings.ageYears = age
            settings.ageAsked = true
            onDone()
        },
        onSkip = null,
    )
}
