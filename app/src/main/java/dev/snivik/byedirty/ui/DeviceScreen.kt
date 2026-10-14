package dev.snivik.byedirty.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.R
import dev.snivik.byedirty.data.ShowerHeadReading
import dev.snivik.byedirty.data.ShowerStore
import dev.snivik.byedirty.data.TagDump
import dev.snivik.byedirty.data.toHex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shower head details plus the tools for capturing labelled scans to decode the rest of its memory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(store: ShowerStore, onBack: () -> Unit, actions: AppActions) {
    val dump = store.lastDump
    val r = store.reading
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shower head") },
                navigationIcon = { IconButton(onBack) { Icon(painterResource(R.drawable.ic_back), "Back") } },
            )
        },
    ) { padding ->
        ScreenColumn(padding) {
            if (dump != null && r != null) AboutCard(dump, r)

            SectionHeader("Collect data", "Scan before and after using the shower so changes can be decoded")
            CollectCard(store, actions)

            if (dump != null) {
                MemoryCard(dump, store.previousDump?.takeIf { it.uid.contentEquals(dump.uid) })
                if (r != null) DeviceDataCard(r, store.showers.size)
            }
            AboutAppCard()
        }
    }
}

@Composable
private fun AboutCard(dump: TagDump, r: ShowerHeadReading) {
    BrandCard {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Field("Model", r.model)
        Field("Serial", r.serial)
        Field("Link on tag", r.ndefUrl)
        Field("Tag UID", dump.uidDisplay, mono = true)
        Field("Chip maker", dump.manufacturer)
        Field("Last scan read", "${dump.readCount} of ${dump.blockCount} blocks")
    }
}

@Composable
private fun CollectCard(store: ShowerStore, actions: AppActions) {
    var confirmClear by remember { mutableStateOf(false) }
    BrandCard {
        OutlinedTextField(
            value = store.nextScanLabel,
            onValueChange = { store.nextScanLabel = it },
            label = { Text("Label for next scan") },
            placeholder = { Text("e.g. after 60 s hot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button({ actions.sync(true) }, Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_nfc), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Full scan")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(actions.shareDumps, Modifier.weight(1f), enabled = store.archivedCount > 0) {
                Icon(painterResource(R.drawable.ic_share), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share ${store.archivedCount} scans")
            }
            TextButton({ confirmClear = true }, enabled = store.archivedCount > 0) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Full scan reads the whole 8 KB chip. Normal syncs only read the first 1 KB. Every scan is saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete saved scans?") },
            text = { Text("This removes all ${store.archivedCount} saved scan files from this phone.") },
            confirmButton = { TextButton({ store.clearArchive(); confirmClear = false }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemoryCard(dump: TagDump, previous: TagDump?) {
    val changed = previous?.let { prev ->
        dump.blocks.indices.filter { i -> i < prev.blockCount && prev.block(i) != null && dump.block(i) != null && !prev.block(i)!!.contentEquals(dump.block(i)!!) }.toSet()
    }.orEmpty()
    val highlight = MaterialTheme.colorScheme.tertiary
    BrandCard {
        Text("Memory", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                previous == null -> "Non-empty blocks. Scan again to see what changes."
                changed.isEmpty() -> "Nothing changed since the previous scan."
                else -> "${changed.size} block${if (changed.size == 1) "" else "s"} changed since the previous scan (old → new)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        val text = buildAnnotatedString {
            dump.blocks.forEachIndexed { i, b ->
                when {
                    i in changed -> withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
                        append("%03X  %s → %s\n".format(i, previous!!.block(i)!!.toHex(" "), b!!.toHex(" ")))
                    }
                    b == null -> append("%03X  -- locked --\n".format(i))
                    b.any { it != 0.toByte() } -> append("%03X  %s  %s\n".format(i, b.toHex(" "), b.ascii()))
                }
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun AboutAppCard() {
    BrandCard {
        Text("About ByeDirty", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "An independent, unofficial companion app for Hello Klean smart shower heads, made by an owner " +
                "for owners who were promised an Android app. Not affiliated with, endorsed by or supported by " +
                "Hello Klean; the name is used only to say which shower heads this works with.\n\n" +
                "ByeDirty only reads the shower head over NFC. It never writes to it, and your data stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceDataCard(r: ShowerHeadReading, showerCount: Int) {
    var open by rememberSaveable { mutableStateOf(false) }
    BrandCard(onClick = { open = !open }) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Device data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Settings and counters stored on the shower head",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (open) "Hide" else "Show", color = MaterialTheme.colorScheme.secondary)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Field("Filter capacity", r.filterCapacityLitres?.let { "%,d L".format(it) })
            Field("Filter warning at", r.filterReminderLitres?.let { "%,d L used".format(it) })
            Field("Used on this filter", r.filterUsedLitres?.let { "%,d L".format(it) })
            Field("Water time on this filter", r.filterFlowSeconds?.let(::formatDuration))
            Field("Water time, lifetime", r.lifetimeFlowSeconds?.let(::formatDuration))
            Field("Filters replaced", r.filterNumber?.toString())
            Field("Hot water above", r.hotThresholdC?.let { "${it.oneDecimal()} °C" })
            Field("Cold water below", r.coldThresholdC?.let { "${it.oneDecimal()} °C" })
            Field("Too-hot warning light", r.hotWarningC?.let { "${it.oneDecimal()} °C" })
            Field("Reset button hold", r.resetHoldSeconds?.let { "$it s" })
            Field("Log records written", r.recordsWritten?.toString())
            Field("Showers in app history", showerCount.toString())
            Field("Supercapacitor", r.supercapMillivolts?.let { "%.2f V".format(it / 1000.0) })
            Field("Device clock", r.lastEventClock?.let(::formatEpoch))
            Field("Hardware / firmware", "${r.hardwareVersion} / ${r.firmwareVersion}")
            Field("Device ID", r.deviceId, mono = true)
        }
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
    else -> "%,d h %d min".format(seconds / 3600, seconds % 3600 / 60)
}

@Composable
private fun Field(label: String, value: String?, mono: Boolean = false) {
    if (value == null) return
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun ByteArray.ascii(): String = map { c -> if (c in 0x20..0x7E) c.toInt().toChar() else '.' }.joinToString("")

private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss")

private fun formatEpoch(seconds: Long): String =
    Instant.ofEpochSecond(seconds).atZone(ZoneId.of("UTC")).format(DATE_TIME) + " UTC"
