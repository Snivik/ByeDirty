package dev.snivik.byedirty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.R
import dev.snivik.byedirty.data.FilterEstimate
import kotlin.math.roundToInt
import dev.snivik.byedirty.data.ShowerStore
import dev.snivik.byedirty.data.ShowerStats
import dev.snivik.byedirty.ui.theme.ColdMark
import dev.snivik.byedirty.ui.theme.FilterGood
import dev.snivik.byedirty.ui.theme.FilterLow
import dev.snivik.byedirty.ui.theme.HotMark

/** Common skin-care guidance: 5-10 minute showers, and keep very hot water to a few minutes. */
private const val TARGET_MIN_MINUTES = 5
private const val TARGET_MAX_MINUTES = 10
private const val TARGET_HOT_SECONDS = 5 * 60

@Composable
fun HomeScreen(store: ShowerStore, padding: PaddingValues) {
    val estimate = store.estimate
    var window by rememberSaveable { mutableIntStateOf(7) }
    ScreenColumn(padding) {
        when {
            store.reading == null -> EmptyState()
            estimate == null -> Hint("Couldn't find filter data on the last scan. Try syncing again.")
            else -> FilterLifeCard(estimate, store.lastSyncMillis, store.showersPerWeek)
        }
        val stats = ShowerStats.compute(store.showers, window) ?: return@ScreenColumn
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shower insights", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                listOf(7, 30).forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = window == n,
                        onClick = { window = n },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = {},
                    ) { Text("Last $n") }
                }
            }
        }
        if (stats.count < window) Hint("Only ${stats.count} showers logged so far.")
        AverageShowerCard(stats)
        HotWaterCard(stats)
        ColdWaterCard(stats)
    }
}

@Composable
private fun EmptyState() {
    BrandCard {
        Icon(painterResource(R.drawable.ic_nfc), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        Text("Connect your shower head", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap Sync, then hold the back of your phone against the shower head handle until it vibrates. Works with Hello Klean smart shower heads (model HKSSH001).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterLifeCard(estimate: FilterEstimate, lastSync: Long?, showersPerWeek: Int) {
    BrandCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Filter life", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TintChip(estimate.status.label, estimate.status.color)
        }
        Spacer(Modifier.height(8.dp))
        Text("${estimate.percent}%", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { estimate.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = estimate.status.color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBlock("Litres left", "%,d L".format(estimate.litresLeft), Modifier.weight(1f))
            StatBlock("Days left", estimate.daysLeft?.toString() ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                estimate.refillDate?.let {
                    append("Replace around ${it.pretty()}")
                    append(if (estimate.basis == FilterEstimate.Basis.SHOWERS_PER_WEEK) " at $showersPerWeek showers a week. " else ". ")
                }
                lastSync?.let { append("Synced ${relativeTime(it)}.") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightCard(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    chip: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    BrandCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            chip?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun AverageShowerCard(stats: ShowerStats) {
    val long = stats.averageSeconds > TARGET_MAX_MINUTES * 60
    InsightCard(
        R.drawable.ic_timer, "Average shower", formatShowerTime(stats.averageSeconds),
        chip = { TintChip(if (long) "On the long side" else "Right on time", if (long) FilterLow else FilterGood) },
    ) {
        DistributionBar(stats.hotShare.toFloat(), stats.coldShare.toFloat())
        Spacer(Modifier.height(8.dp))
        Row {
            Text("${percent(stats.hotShare)} hot", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${percent(stats.coldShare)} cold", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBlock("Target", "$TARGET_MIN_MINUTES–$TARGET_MAX_MINUTES min", Modifier.weight(1f))
            StatBlock("Water each", "%.0f L".format(stats.averageLitres), Modifier.weight(1f))
        }
    }
    Hint(
        if (long) "Trimming a few minutes saves water, energy and filter life, and long hot soaks can leave skin dry."
        else "Short and sweet. Your skin, your bills and your filter all approve."
    )
}

@Composable
private fun HotWaterCard(stats: ShowerStats) {
    val tooHot = stats.averageHotSeconds > TARGET_HOT_SECONDS
    InsightCard(
        R.drawable.ic_thermostat, "Hot water", formatShowerTime(stats.averageHotSeconds),
        chip = { TintChip(if (tooHot) "Very hot" else "Gentle", if (tooHot) HotMark else FilterGood) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBlock("Showers with hot", "${stats.showersWithHot} of ${stats.count}", Modifier.weight(1f))
            StatBlock("Target", "under 5 min", Modifier.weight(1f))
        }
    }
    Hint(
        when {
            stats.showersWithHot == 0 -> "Nothing above 42 °C in these showers. Nice and gentle."
            tooHot -> "Lots of time above 42 °C. Turning it down a notch is easier on skin and the boiler."
            else -> "Average time per shower with water above 42 °C."
        }
    )
}

@Composable
private fun ColdWaterCard(stats: ShowerStats) {
    InsightCard(
        R.drawable.ic_drop, "Cold water", formatShowerTime(stats.averageColdSeconds),
        chip = if (stats.coldStreak >= 3) ({ TintChip("On a streak", ColdMark) }) else null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBlock("Cold streak", "${stats.coldStreak} showers", Modifier.weight(1f))
            StatBlock("With cold", "${stats.showersWithCold} of ${stats.count}", Modifier.weight(1f))
        }
    }
    Hint("Average time per shower with water below 25 °C. The streak counts showers in a row with at least a splash of cold.")
}

/** "15 sec" under a minute, otherwise rounded minutes. Same rounding as the official app, so numbers agree. */
fun formatShowerTime(seconds: Double): String =
    if (seconds < 60) "${seconds.toInt()} sec" else "${(seconds / 60).roundToInt()} min"

/** Whole percent, rounded down ("1%" for 1.7%), again to agree with the official app. */
private fun percent(share: Double): String = "${(share * 100).toInt()}%"
