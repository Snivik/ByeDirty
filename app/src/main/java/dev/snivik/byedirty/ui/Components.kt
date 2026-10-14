package dev.snivik.byedirty.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.data.FilterStatus
import dev.snivik.byedirty.ui.theme.ColdMark
import dev.snivik.byedirty.ui.theme.FilterGood
import dev.snivik.byedirty.ui.theme.HotMark
import dev.snivik.byedirty.ui.theme.FilterLow
import dev.snivik.byedirty.ui.theme.FilterReplace
import dev.snivik.byedirty.ui.theme.Overline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

val FilterStatus.color: Color
    get() = when (this) {
        FilterStatus.GOOD -> FilterGood
        FilterStatus.LOW -> FilterLow
        FilterStatus.REPLACE -> FilterReplace
    }

val FilterStatus.label: String
    get() = when (this) {
        FilterStatus.GOOD -> "Good"
        FilterStatus.LOW -> "Replace soon"
        FilterStatus.REPLACE -> "Replace now"
    }

/** White card on the cream background (dark grey in dark mode). */
@Composable
fun BrandCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val inner: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(20.dp), content = content) }
    if (onClick != null) {
        Card(onClick, modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = colors, content = inner)
    } else {
        Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = colors, content = inner)
    }
}

/** Tinted capsule such as "Good" or "Safe". */
@Composable
fun TintChip(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.18f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(label.uppercase(), style = Overline, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * Share of shower time with hot (left, orange) and cold (right, blue) water. Tiny shares still
 * get a sliver so they're visible; callers label both ends so colour isn't the only cue.
 */
@Composable
fun DistributionBar(hot: Float, cold: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = CornerRadius(r))
        val minW = size.height * 1.5f
        if (hot > 0f) {
            val w = maxOf(minW, size.width * hot)
            drawRoundRect(HotMark, size = size.copy(width = w), cornerRadius = CornerRadius(r))
        }
        if (cold > 0f) {
            val w = maxOf(minW, size.width * cold)
            drawRoundRect(ColdMark, topLeft = Offset(size.width - w, 0f), size = size.copy(width = w), cornerRadius = CornerRadius(r))
        }
    }
}

/**
 * ByeDirty's filter gauge: a water drop that drains as the filter is used, with a slowly
 * moving waterline. The percentage sits in the middle, in body ink so it reads on any level.
 */
@Composable
fun DropGauge(percent: Int, color: Color, width: Dp, modifier: Modifier = Modifier) {
    val wave by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "phase",
    )
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(width, width * 1.3f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val drop = Path().apply {
                moveTo(w / 2, 0f)
                cubicTo(w * 0.62f, h * 0.22f, w, h * 0.42f, w, h * 0.64f)
                cubicTo(w, h * 0.84f, w * 0.79f, h, w / 2, h)
                cubicTo(w * 0.21f, h, 0f, h * 0.84f, 0f, h * 0.64f)
                cubicTo(0f, h * 0.42f, w * 0.38f, h * 0.22f, w / 2, 0f)
                close()
            }
            drawPath(drop, empty)
            // Level is by height rather than volume, so it tracks the number at a glance.
            val level = h * (1 - percent.coerceIn(0, 100) / 100f)
            val amplitude = if (percent in 1..99) 4.dp.toPx() else 0f
            val water = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    lineTo(x, level + amplitude * sin(x / w * 2 * PI.toFloat() * 1.5f + wave))
                    x += 4f
                }
                lineTo(w, h)
                close()
            }
            clipPath(drop) { drawPath(water, color.copy(alpha = 0.45f)) }
            drawPath(drop, color, style = Stroke(width = 3.dp.toPx()))
        }
        Text("$percent%", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = width * 0.3f))
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

fun LocalDate.pretty(): String = format(DATE)

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val mins = (now - millis) / 60_000
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        mins < 1 -> "just now"
        mins < 60 -> plural(mins, "min")
        mins < 60 * 24 -> plural(mins / 60, "hour")
        else -> plural(mins / (60 * 24), "day")
    }
}

fun Double.oneDecimal(): String =
    if (this % 1.0 == 0.0) "%.0f".format(Locale.getDefault(), this) else "%.1f".format(Locale.getDefault(), this)
