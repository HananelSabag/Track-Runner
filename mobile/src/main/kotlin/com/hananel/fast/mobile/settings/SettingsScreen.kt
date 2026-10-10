package com.hananel.fast.mobile.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.data.SessionInsights
import com.hananel.fast.core.tracking.HeartRateZones
import com.hananel.fast.core.tracking.SpeedZone
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.data.Library
import com.hananel.fast.mobile.data.PhoneData
import com.hananel.fast.mobile.demo.DemoSessions
import com.hananel.fast.mobile.sync.PhoneSync
import com.hananel.fast.mobile.sync.WatchStatus
import com.hananel.fast.mobile.theme.FastColors
import com.hananel.fast.mobile.ui.Card
import com.hananel.fast.mobile.ui.Dot
import com.hananel.fast.mobile.ui.LeftToRight
import com.hananel.fast.mobile.ui.TopBar
import com.hananel.fast.mobile.ui.color
import com.hananel.fast.mobile.ui.formatKmh
import com.hananel.fast.mobile.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(library: Library?, watch: WatchStatus, onBack: () -> Unit, onDeviceCheck: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val settings = remember { PhoneData.settings(context) }
    val scope = rememberCoroutineScope()
    var age by remember { mutableStateOf(settings.ageYears) }
    var asked by remember { mutableStateOf(false) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }

    fun setAge(value: Int) {
        age = value
        settings.ageYears = value
        // Heart-rate zones of sessions that carry no maximum of their own change with it.
        PhoneData.refresh(context)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Title(stringResource(R.string.settings_speed_zones))
                val topSpeed = library?.topSpeed
                Body(
                    topSpeed?.let { stringResource(R.string.settings_zones_personal, it.formatKmh()) }
                        ?: stringResource(R.string.settings_zones_standard),
                )
                // The bands football sessions are read with right now.
                val zones = SessionInsights.profileFor(ActivityKind.FOOTBALL, topSpeed).zones
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (zone in SpeedZone.entries.reversed()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(zone.color())
                            Text(zone.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                // The slowest band has no lower edge worth stating; its upper one says more.
                                text = if (zone == SpeedZone.WALK) {
                                    stringResource(R.string.settings_zone_up_to, zones.lowerBound(SpeedZone.JOG).formatKmh())
                                } else {
                                    stringResource(R.string.settings_zone_from, zones.lowerBound(zone).formatKmh())
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Card {
                Title(stringResource(R.string.settings_heart_rate))
                Body(stringResource(R.string.settings_age_explain))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_age), style = MaterialTheme.typography.bodyLarge)
                        age?.let {
                            Text(
                                text = stringResource(R.string.settings_max_bpm, HeartRateZones.estimateMaxBpm(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Less on the left, more on the right, in every language.
                    LeftToRight {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalIconButton(onClick = { setAge((age?.minus(1) ?: DEFAULT_AGE).coerceAtLeast(MIN_AGE)) }) { Text("−") }
                            Text(
                                text = age?.toString() ?: "—",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            FilledTonalIconButton(onClick = { setAge((age?.plus(1) ?: DEFAULT_AGE).coerceAtMost(MAX_AGE)) }) { Text("+") }
                        }
                    }
                }
            }

            Card {
                Title(stringResource(R.string.settings_watch))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(if (watch.connectedName != null) FastColors.Mint else FastColors.Slate)
                    Text(
                        text = watch.connectedName?.let { stringResource(R.string.watch_connected, it) } ?: stringResource(R.string.watch_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Body(stringResource(R.string.settings_resync_explain), Modifier.padding(top = 8.dp))
                FilledTonalButton(
                    onClick = {
                        PhoneSync.requestEverythingAgain(context)
                        asked = true
                    },
                    enabled = !asked,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) { Text(stringResource(if (asked) R.string.settings_resync_sent else R.string.settings_resync)) }
            }

            Card(Modifier.clickable(onClick = onDeviceCheck)) {
                Title(stringResource(R.string.device_check_title))
                Body(stringResource(R.string.device_check_subtitle))
            }

            if (DemoSessions.AVAILABLE) {
                Card {
                    Title(stringResource(R.string.demo_title))
                    Body(stringResource(R.string.demo_explain))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { scope.launch { withContext(Dispatchers.IO) { DemoSessions.add(context) } } },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.demo_load)) }
                        FilledTonalButton(
                            onClick = { scope.launch { withContext(Dispatchers.IO) { DemoSessions.remove(context) } } },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.demo_remove)) }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_version, version),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

private const val DEFAULT_AGE = 30
private const val MIN_AGE = 10
private const val MAX_AGE = 90
