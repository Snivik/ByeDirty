package dev.snivik.byedirty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.R
import dev.snivik.byedirty.data.FilterEstimate
import dev.snivik.byedirty.data.ShowerStore

/** A standard 500 ml water bottle, for the "bottles' worth" comparison. */
private const val BOTTLE_LITRES = 0.5

@Composable
fun FilterScreen(store: ShowerStore, padding: PaddingValues, actions: AppActions) {
    val estimate = store.estimate
    ScreenColumn(padding) {
        BrandCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DropGauge(
                    estimate?.percent ?: 0,
                    estimate?.status?.color ?: MaterialTheme.colorScheme.outline,
                    170.dp,
                    Modifier.padding(vertical = 12.dp),
                )
            }
            Text(
                estimate?.status?.label ?: "Not synced yet",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Last synced ${store.lastSyncMillis?.let { relativeTime(it) } ?: "never"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("Last sync", store.lastSyncMillis?.toLocalDate()?.pretty() ?: "—", alignment = Alignment.CenterHorizontally)
                StatBlock("Est. refill", estimate?.refillDate?.pretty() ?: "—", alignment = Alignment.CenterHorizontally)
                StatBlock("Days left", estimate?.daysLeft?.toString() ?: "—", alignment = Alignment.CenterHorizontally)
            }
        }

        if (estimate != null) WaterUsageCard(estimate, store.reading?.filterCapacityLitres)

        SectionHeader("Estimate")
        BrandCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Showers per week", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everyone in the house, combined",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton({ actions.setShowersPerWeek(store.showersPerWeek - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(
                    "${store.showersPerWeek}",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(48.dp),
                )
                FilledTonalIconButton({ actions.setShowersPerWeek(store.showersPerWeek + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Hint(
            if (estimate?.basis == FilterEstimate.Basis.MEASURED) "Days left are based on how fast the filter has been used between your syncs."
            else "Days left use your average shower (${store.showers.size} logged) times this number. After a few days of syncing, the app switches to your real usage."
        )
        Hint("After fitting a new filter, hold the reset button on the shower head for 5 seconds, then sync.")
    }
}

@Composable
private fun WaterUsageCard(estimate: FilterEstimate, capacity: Int?) {
    val litres = estimate.litresUsed
    BrandCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_drop), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("Water through this filter", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text("%,d L".format(litres), style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            capacity?.let { StatBlock("Filter capacity", "%,d L".format(it), Modifier.weight(1f)) }
            StatBlock("Bottles' worth", "%,d".format((litres / BOTTLE_LITRES).toInt()), Modifier.weight(1f))
        }
    }
}
