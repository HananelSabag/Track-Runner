package com.hananel.fast.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material3.MaterialTheme
import com.hananel.fast.core.data.ActivityKind
import com.hananel.fast.core.tracking.sprint.SprintTestOutcome
import com.hananel.fast.wear.R
import com.hananel.fast.wear.theme.FastColors

fun ActivityKind.titleRes(): Int = when (this) {
    ActivityKind.RUN -> R.string.mode_run
    ActivityKind.FOOTBALL -> R.string.mode_football
    ActivityKind.SPRINT_TEST -> R.string.home_sprint_title
}

/** One colour and one pictogram per activity, the same on the home screen and in the history. */
fun ActivityKind.color(): Color = when (this) {
    ActivityKind.FOOTBALL -> FastColors.Lilac
    ActivityKind.RUN -> FastColors.Sky
    ActivityKind.SPRINT_TEST -> FastColors.Cyan
}

fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.FOOTBALL -> FastIcons.Ball
    ActivityKind.RUN -> FastIcons.Runner
    ActivityKind.SPRINT_TEST -> FastIcons.Bolt
}

/** Green when a speed test can be trusted, the ordinary accent when it mostly can, red when it cannot. */
@Composable
fun confidenceColor(confidence: Int?): Color = when {
    confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
    confidence >= SprintTestOutcome.HIGH_CONFIDENCE -> FastColors.Mint
    confidence >= SprintTestOutcome.MEDIUM_CONFIDENCE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}
