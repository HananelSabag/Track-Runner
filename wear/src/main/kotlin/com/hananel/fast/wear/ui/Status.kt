package com.hananel.fast.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.hananel.fast.wear.R
import com.hananel.fast.wear.sync.PhoneFollow
import com.hananel.fast.wear.sync.PhoneLink
import com.hananel.fast.wear.theme.FastColors

/**
 * One colour for one meaning, the same on the watch and the phone (D45):
 *
 * - waiting, working on it, nothing to do: grey
 * - open, ready: blue
 * - live, measuring, saved: green
 * - blocked, needs the user: red, with a lock
 *
 * Rose without a lock stays what it was: a sprint, a peak, a doubtful result.
 */
object StatusColors {
    val Waiting = FastColors.Mist
    val Ready = FastColors.Sky
    val Live = FastColors.Mint
    val Blocked = FastColors.RoseDeep
}

/**
 * What the phone said about the measurement just started, in one line: green
 * when it measures along, red with a lock when it wanted to and could not. Says
 * nothing when the phone said nothing — out of reach, or "measure together" off.
 */
@Composable
fun PhoneFollowLine(modifier: Modifier = Modifier) {
    val phone by PhoneLink.phone.collectAsStateWithLifecycle()
    when (phone) {
        PhoneFollow.UNKNOWN -> Unit
        PhoneFollow.FOLLOWING -> StatusLine(text = stringResource(R.string.phone_following), color = StatusColors.Live, blocked = false, modifier = modifier)
        PhoneFollow.NOT_FOLLOWING -> StatusLine(text = stringResource(R.string.phone_not_following), color = StatusColors.Blocked, blocked = true, modifier = modifier)
    }
}

@Composable
private fun StatusLine(text: String, color: Color, blocked: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        if (blocked) Icon(FastIcons.Lock, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.Center)
    }
}
