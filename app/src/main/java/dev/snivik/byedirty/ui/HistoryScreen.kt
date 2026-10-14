package dev.snivik.byedirty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.data.ShowerStore
import dev.snivik.byedirty.data.ShowerRecord
import kotlin.math.ceil

private const val CHART_SHOWERS = 30
private const val OPTIMAL_MAX_MINUTES = 10
/** A typical full bath, for the "bathtubs" fun fact. */
private const val BATHTUB_LITRES = 80.0

/**
 * Per-shower history from the head's log. The head's clock resets whenever its supercapacitor
 * runs flat, so everything here is ordered by shower rather than by calendar date.
 */
@Composable
fun HistoryScreen(store: ShowerStore, padding: PaddingValues) {
    val showers = store.showers
    ScreenColumn(padding) {
        if (showers.isEmpty()) {
            Hint("Sync with your shower head to see your shower history.")
            return@ScreenColumn
        }
        FunFacts(showers)
        RecentShowersCard(showers.takeLast(CHART_SHOWERS), totalCount = showers.size)
        LengthHistogramCard(showers)
        PressureCard(showers)
        Hint("The shower head keeps its last 256 sessions, and this app keeps every one it has seen. Showers under a minute aren't counted.")
    }
}

@Composable
private fun FunFacts(showers: List<ShowerRecord>) {
    val litres = showers.sumOf { it.litres }
    val seconds = showers.sumOf { it.durationSeconds.toLong() }
    val longest = showers.maxOf { it.durationSeconds }
    val hotMinutes = showers.sumOf { it.hotSeconds } / 60
    val facts = listOf(
        "Showers" to "%,d".format(showers.size),
        "Water used" to "%,.0f L".format(litres),
        "In bathtubs" to "%,.0f".format(litres / BATHTUB_LITRES),
        "Time showering" to if (seconds >= 3600) "${seconds / 3600} h ${seconds % 3600 / 60} m" else "${seconds / 60} min",
        "Longest shower" to formatShowerTime(longest.toDouble()),
        "Hot water" to "$hotMinutes min",
    )
    facts.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (label, value) ->
                BrandCard(Modifier.weight(1f)) { StatBlock(label, value) }
            }
        }
    }
}

/** One bar per shower (minutes), dashed line at the 10-minute guideline; tap a bar for details. */
@Composable
private fun RecentShowersCard(showers: List<ShowerRecord>, totalCount: Int) {
    var selected by remember(showers.size) { mutableIntStateOf(showers.lastIndex) }
    val barColor = MaterialTheme.colorScheme.secondary
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val maxMinutes = maxOf(OPTIMAL_MAX_MINUTES + 5, ceil(showers.maxOf { it.durationSeconds } / 60.0 / 10).toInt() * 10)

    BrandCard {
        Text("Last ${showers.size} showers", style = MaterialTheme.typography.titleMedium)
        Text("Minutes per shower, oldest to newest. Tap a bar.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = "Bar chart of the last ${showers.size} shower lengths in minutes" }
                .pointerInput(showers) {
                    detectTapGestures { pos ->
                        val axis = 28.dp.toPx()
                        val slot = (size.width - axis) / showers.size
                        selected = ((pos.x - axis) / slot).toInt().coerceIn(0, showers.lastIndex)
                    }
                },
        ) {
            val axis = 28.dp.toPx()
            val bottom = size.height - 2.dp.toPx()
            val plotH = bottom - 8.dp.toPx()
            fun y(minutes: Double) = bottom - (minutes / maxMinutes * plotH).toFloat()

            // Recessive grid every 10 minutes, labelled on the left.
            for (m in 0..maxMinutes step 10) {
                drawLine(grid, Offset(axis, y(m.toDouble())), Offset(size.width, y(m.toDouble())), strokeWidth = 1.dp.toPx())
                val label = measurer.measure("$m", labelStyle)
                drawText(label, topLeft = Offset(axis - label.size.width - 6.dp.toPx(), y(m.toDouble()) - label.size.height / 2))
            }

            val slot = (size.width - axis) / showers.size
            val gap = 2.dp.toPx()
            val radius = minOf(4.dp.toPx(), (slot - gap) / 2)
            showers.forEachIndexed { i, s ->
                val top = y(s.durationSeconds / 60.0)
                val left = axis + i * slot + gap / 2
                val path = Path().apply {
                    // Rounded data end, square at the baseline.
                    addRoundRect(
                        RoundRect(
                            left, top, left + slot - gap, bottom,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                        )
                    )
                }
                drawPath(path, if (i == selected) selectedColor else barColor)
            }

            // The 10-minute guideline sits on top of the bars so it stays visible.
            drawLine(
                guide, Offset(axis, y(OPTIMAL_MAX_MINUTES.toDouble())), Offset(size.width, y(OPTIMAL_MAX_MINUTES.toDouble())),
                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("- - -  $OPTIMAL_MAX_MINUTES-minute guideline", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        showers.getOrNull(selected)?.let { s ->
            Spacer(Modifier.height(16.dp))
            Text(
                "Shower ${totalCount - showers.size + selected + 1} of $totalCount" +
                    if (selected == showers.lastIndex) " (latest)" else "",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBlock("Length", formatShowerTime(s.durationSeconds.toDouble()), Modifier.weight(1f))
                StatBlock("Water", "%.0f L".format(s.litres), Modifier.weight(1f))
                StatBlock("Hot", "${s.hotSeconds} s", Modifier.weight(1f))
                StatBlock("Cold", "${s.coldSeconds} s", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LengthHistogramCard(showers: List<ShowerRecord>) {
    val buckets = listOf(
        "Under 5 min" to (0 until 5 * 60),
        "5–10 min" to (5 * 60 until 10 * 60),
        "10–15 min" to (10 * 60 until 15 * 60),
        "15–20 min" to (15 * 60 until 20 * 60),
        "20 min +" to (20 * 60..Int.MAX_VALUE),
    ).map { (label, range) -> label to showers.count { it.durationSeconds in range } }
    val most = buckets.maxOf { it.second }.coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.secondary

    BrandCard {
        Text("Shower lengths", style = MaterialTheme.typography.titleMedium)
        Text("All ${showers.size} logged showers. 5–10 minutes is the sweet spot.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        buckets.forEach { (label, count) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
                Box(Modifier.weight(1f)) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(count.toFloat() / most)
                                .height(18.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(bar)
                        )
                    }
                }
                Text("$count", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp).padding(start = 8.dp))
            }
        }
    }
}

/** Share of water time at each pressure band the head distinguishes (only low and medium seen in use). */
@Composable
private fun PressureCard(showers: List<ShowerRecord>) {
    val names = listOf("Low", "Medium", "High", "Very high")
    val seconds = List(4) { band -> showers.sumOf { it.bandSeconds[band].toLong() } }
    val total = seconds.sum().takeIf { it > 0 } ?: return
    val used = seconds.indices.filter { seconds[it] > 0 }
    // Ordinal bands: one hue, light to dark.
    val base = MaterialTheme.colorScheme.secondary
    val shades = listOf(0.35f, 0.65f, 0.85f, 1f).map { base.copy(alpha = it) }
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest

    BrandCard {
        Text("Water pressure", style = MaterialTheme.typography.titleMedium)
        Text("Share of shower time at each pressure the head measures.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            var x = 0f
            val gap = 2.dp.toPx()
            used.forEach { band ->
                val w = size.width * seconds[band] / total
                drawRoundRect(shades[band], topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                x += w
                // Surface-coloured gap between segments.
                if (band != used.last()) drawRect(surface, topLeft = Offset(x - gap / 2, 0f), size = Size(gap, size.height))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            used.forEach { band ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(shades[band])
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${names[band]} ${(100 * seconds[band] / total)}%", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
