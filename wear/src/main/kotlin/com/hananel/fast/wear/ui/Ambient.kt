package com.hananel.fast.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the watch is in its dim always-on mode right now.
 *
 * The activity sets it; the screens that measure read it and swap their picture
 * for a plain one. While something is being measured the app stays in front in
 * that mode, so lowering the wrist leaves the speed on the watch instead of the
 * watch face.
 */
object Ambient {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(active: Boolean) {
        _active.value = active
    }
}

/**
 * What a measuring screen shows while the watch is dimmed: the one number, and
 * a line or two around it. Nothing filled, nothing coloured, nothing to touch —
 * lit pixels cost power in this mode, and a picture that sits still for an hour
 * must not burn into the display.
 *
 * @param above a short line over the number, such as the clock of the session.
 * @param below a short line under it, such as the fastest so far.
 */
@Composable
fun AmbientReadout(number: String, above: String?, below: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (above != null) Text(above, style = MaterialTheme.typography.labelMedium, color = AMBIENT_DIM)
        HeroNumber(number, fontSize = 62, color = AMBIENT_BRIGHT)
        if (below != null) Text(below, style = MaterialTheme.typography.labelMedium, color = AMBIENT_DIM)
    }
}

// Greys, not white: the mode exists to save power.
private val AMBIENT_BRIGHT = Color(0xFFD0D5DD)
private val AMBIENT_DIM = Color(0xFF8A93A3)
