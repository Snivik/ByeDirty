package dev.snivik.byedirty.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Everything the app remembers between launches. Backed by SharedPreferences, observable from Compose.
 * The shower head owns all filter state (it's reset with a button on the head), so this only caches
 * what was read plus a short history used to estimate how fast the filter is being used.
 */
class ShowerStore(context: Context) {
    private val prefs = context.getSharedPreferences("byedirty", Context.MODE_PRIVATE)

    /** Every scan is archived here as a text dump, for reverse-engineering. */
    val dumpDir = File(context.filesDir, "dumps")

    var lastDump by mutableStateOf(loadDump(KEY_DUMP))
        private set
    /** The scan before [lastDump], so the memory view can highlight what changed. */
    var previousDump by mutableStateOf(loadDump(KEY_PREV_DUMP))
        private set
    var lastSyncMillis by mutableStateOf(prefs.getLong(KEY_SYNC, 0L).takeIf { it > 0 })
        private set
    var history by mutableStateOf(loadHistory())
        private set
    var remindersEnabled by mutableStateOf(prefs.getBoolean(KEY_REMINDERS, false))
        private set
    /** Used for the days-left estimate, as in the HelloKlean app (which defaults to asking). */
    var showersPerWeek by mutableIntStateOf(prefs.getInt(KEY_SHOWERS_PER_WEEK, 10))
        private set
    /** Every log record seen across syncs, oldest first; the chip itself only keeps the last 256. */
    var records by mutableStateOf(loadRecords())
        private set
    var archivedCount by mutableIntStateOf(dumpDir.listFiles()?.size ?: 0)
        private set

    /** Optional note attached to the next archived scan, e.g. "after 60 s hot". */
    var nextScanLabel by mutableStateOf("")

    val reading: ShowerHeadReading? get() = lastDump?.let(ShowerHeadReading::decode)

    /** Real showers (a minute or longer), oldest first. */
    val showers: List<ShowerRecord> get() = records.filter { it.isShower }

    val estimate: FilterEstimate? get() = reading?.let { FilterEstimate.compute(it, showers, history, showersPerWeek) }

    fun onSynced(dump: TagDump) {
        previousDump = lastDump
        lastDump = dump
        val decoded = ShowerHeadReading.decode(dump)
        // Later reads win: the newest record is rewritten in place while the water runs.
        val byKey = LinkedHashMap<Long, ShowerRecord>()
        (records + decoded.records).forEach { byKey[it.key] = it }
        records = byKey.values.sortedBy { it.key }
        lastSyncMillis = dump.readAtMillis
        decoded.filterRemainingLitres?.let {
            history = (history + SyncPoint(dump.readAtMillis, it)).takeLast(MAX_HISTORY)
        }
        archive(dump)
        save()
    }

    fun changeShowersPerWeek(count: Int) {
        showersPerWeek = count.coerceIn(1, 70)
        save()
    }

    fun changeReminders(enabled: Boolean) {
        remindersEnabled = enabled
        save()
    }

    fun archivedDumps(): List<File> = dumpDir.listFiles()?.sortedBy { it.name }.orEmpty()

    fun clearArchive() {
        dumpDir.listFiles()?.forEach { it.delete() }
        archivedCount = 0
    }

    private fun archive(dump: TagDump) {
        dumpDir.mkdirs()
        val label = nextScanLabel.trim()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val slug = label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
        val name = "byedirty-$stamp${if (slug.isEmpty()) "" else "-$slug"}.txt"
        File(dumpDir, name).writeText(dump.toText(label.ifEmpty { null }))
        archivedCount = dumpDir.listFiles()?.size ?: 0
        nextScanLabel = ""
    }

    private fun loadDump(key: String): TagDump? =
        prefs.getString(key, null)?.let { runCatching { TagDump.fromJson(it) }.getOrNull() }

    private fun loadRecords(): List<ShowerRecord> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_RECORDS, "[]"))
        List(arr.length()) { arr.getString(it) }.mapNotNull { ShowerRecord.parse(it.hexToBytes()) }
    }.getOrDefault(emptyList())

    private fun loadHistory(): List<SyncPoint> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        List(arr.length()) { i -> arr.getJSONObject(i).let { SyncPoint(it.getLong("t"), it.getInt("left")) } }
    }.getOrDefault(emptyList())

    private fun save() = prefs.edit {
        putString(KEY_DUMP, lastDump?.toJson())
        putString(KEY_PREV_DUMP, previousDump?.toJson())
        putLong(KEY_SYNC, lastSyncMillis ?: 0L)
        putString(KEY_HISTORY, JSONArray(history.map { JSONObject().put("t", it.millis).put("left", it.litresLeft) }).toString())
        putBoolean(KEY_REMINDERS, remindersEnabled)
        putInt(KEY_SHOWERS_PER_WEEK, showersPerWeek)
        putString(KEY_RECORDS, JSONArray(records.map { it.hex }).toString())
    }

    private companion object {
        const val KEY_DUMP = "last_dump"
        const val KEY_PREV_DUMP = "prev_dump"
        const val KEY_SYNC = "last_sync"
        const val KEY_HISTORY = "history"
        const val KEY_REMINDERS = "reminders"
        const val KEY_SHOWERS_PER_WEEK = "showers_per_week"
        const val KEY_RECORDS = "records"
        const val MAX_HISTORY = 200
    }
}
