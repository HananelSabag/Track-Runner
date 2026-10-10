package com.hananel.fast.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hananel.fast.mobile.R
import com.hananel.fast.mobile.theme.FastColors

val CardShape = RoundedCornerShape(22.dp)

val BrandGradient: Brush
    get() = Brush.horizontalGradient(listOf(FastColors.Cyan, FastColors.Electric, FastColors.Violet))

/** The surface every block of a screen sits on. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** The number a screen exists to show: heavy, slanted, in the brand gradient. */
@Composable
fun HeroNumber(text: String, fontSize: Int, modifier: Modifier = Modifier, gradient: Boolean = true, color: Color = Color.Unspecified) {
    val style = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.05f).sp,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
    )
    Text(
        text = text,
        style = if (gradient) style.copy(brush = BrandGradient) else style,
        color = if (gradient) Color.Unspecified else color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** One figure with its name: the building block of every summary. */
@Composable
fun RowScope.StatTile(label: String, value: String, unit: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1,
                softWrap = false,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/** Charts, timelines and number lines run left to right in every language. */
@Composable
fun LeftToRight(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** A heart and a number: heart rate, without spending a word on it. */
@Composable
fun HeartRate(bpm: Int, style: TextStyle, color: Color = FastColors.Rose) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            val heart = Path().apply {
                moveTo(w / 2, h * 0.95f)
                cubicTo(-w * 0.35f, h * 0.45f, w * 0.15f, -h * 0.25f, w / 2, h * 0.25f)
                cubicTo(w * 0.85f, -h * 0.25f, w * 1.35f, h * 0.45f, w / 2, h * 0.95f)
                close()
            }
            drawPath(heart, color)
        }
        Text(bpm.toString(), style = style, color = color, maxLines = 1)
    }
}
