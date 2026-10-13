package com.hananel.fast.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's own pictograms: one per activity. Kept identical, by hand, to the
 * watch's copy (wear/.../ui/Icons.kt), so an activity looks the same on both.
 */
object FastIcons {

    /** Speed test. */
    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            filled {
                moveTo(13.5f, 2f)
                lineTo(4.5f, 13.5f)
                horizontalLineTo(11f)
                lineTo(10f, 22f)
                lineTo(19.5f, 10f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    /** Football: a ball, its centre panel and the seams running out from it. */
    val Ball: ImageVector by lazy {
        icon("Ball") {
            stroked(width = 1.7f) { circle(12f, 12f, 9f) }
            stroked(width = 1.5f) {
                moveTo(12f, 8.4f)
                lineTo(15.42f, 10.89f)
                lineTo(14.12f, 14.91f)
                lineTo(9.88f, 14.91f)
                lineTo(8.58f, 10.89f)
                close()
                moveTo(12f, 8.4f); lineTo(12f, 3f)
                moveTo(15.42f, 10.89f); lineTo(20.56f, 9.22f)
                moveTo(14.12f, 14.91f); lineTo(17.29f, 19.28f)
                moveTo(9.88f, 14.91f); lineTo(6.71f, 19.28f)
                moveTo(8.58f, 10.89f); lineTo(3.44f, 9.22f)
            }
        }
    }

    /** Run: a figure in mid-stride, facing the way the language reads. */
    val Runner: ImageVector by lazy {
        icon("Runner", autoMirror = true) {
            filled { circle(15f, 4.3f, 2.1f) }
            stroked(width = 2f) {
                // Torso
                moveTo(13.2f, 8.6f); lineTo(10.6f, 14f)
                // Leading arm, trailing arm
                moveTo(13.2f, 8.8f); lineTo(16.6f, 10.9f); lineTo(19f, 9.4f)
                moveTo(13f, 8.8f); lineTo(9.4f, 9.6f); lineTo(7.8f, 12.4f)
                // Leading leg, trailing leg
                moveTo(10.6f, 14f); lineTo(14.6f, 16.2f); lineTo(13.6f, 21.2f)
                moveTo(10.6f, 14f); lineTo(8.2f, 17.6f); lineTo(4.2f, 18.4f)
            }
        }
    }
}

private fun icon(name: String, autoMirror: Boolean = false, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).apply(content).build()

// The colour is a placeholder: an icon is tinted where it is shown.
private fun ImageVector.Builder.filled(block: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero, pathBuilder = block)
}

private fun ImageVector.Builder.stroked(width: Float = 1.9f, block: PathBuilder.() -> Unit) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}
