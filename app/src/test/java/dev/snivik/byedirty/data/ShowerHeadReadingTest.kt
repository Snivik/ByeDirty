package dev.snivik.byedirty.data

import dev.snivik.byedirty.ui.formatShowerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the real scans in research/: export.txt (first dump, 1 KB) and the labelled full scans
 * from the experiment (base -> hot -> cold -> warm -> filter reset). Expected values come from
 * the HelloKlean app's screens and its Devices.json export.
 */
class ShowerHeadReadingTest {

    private companion object {
        const val DAY = 86_400_000L
        fun load(name: String) = ShowerHeadReading.decode(
            TagDump.parseText(ShowerHeadReadingTest::class.java.getResource("/$name")!!.readText())
        )
    }

    private val first = load("export.txt")
    private val base = load("scan-base.txt")
    private val warm = load("scan-warm.txt")
    private val reset = load("scan-reset.txt")

    @Test
    fun decodesIdentityAndConfig() {
        assertEquals("https://helloklean.com", first.ndefUrl)
        assertEquals("HKSSH001", first.model)
        assertEquals("TW26030001", first.serial)
        assertTrue(first.isSupportedShowerHead)
        assertEquals(5, first.resetHoldSeconds)
        assertEquals(10, first.datalogCombinedInterval)
        assertEquals(8000, first.filterCapacityLitres)
        assertEquals(6400, first.filterReminderLitres)
        assertEquals(45.0, first.hotWarningC!!, 0.0)
        assertEquals(42.0, first.hotThresholdC!!, 0.0)
        assertEquals(25.0, first.coldThresholdC!!, 0.0)
    }

    @Test
    fun firstDumpHasNoLog() {
        // The original 1 KB dump stops before the log.
        assertTrue(first.records.isEmpty())
        assertEquals(8000, first.filterRemainingLitres)
    }

    @Test
    fun liveCountersTrackWaterAndReset() {
        assertEquals(11, warm.filterUsedLitres)
        assertEquals(7989, warm.filterRemainingLitres)
        assertEquals(126L, warm.filterFlowSeconds)
        assertEquals(155_765L, warm.lifetimeFlowSeconds)
        assertEquals(1, warm.filterNumber)

        assertEquals(0, reset.filterUsedLitres)
        assertEquals(8000, reset.filterRemainingLitres)
        assertEquals(2, reset.filterNumber)
        assertEquals(155_801L, reset.lifetimeFlowSeconds)
        assertEquals(2800, reset.supercapMillivolts)
    }

    @Test
    fun logIsReadOldestFirstWithValidChecksums() {
        // 228 slots used; two hold all-zero or checksum-failing data and are skipped.
        assertEquals(226, base.records.size)
        assertEquals(0 to 1, base.records.first().let { it.filter to it.logNumber })
        // Newest is the marker the head wrote when filter 1 was fitted.
        assertTrue(base.records.last().isFilterMarker)
        assertEquals(1, base.records.last().filter)
    }

    @Test
    fun hotColdAndWarmRunsUpdateOneSessionInPlace() {
        val session = warm.records.last()
        assertEquals(1 to 2, session.filter to session.logNumber)
        assertEquals(141, session.durationSeconds)
        assertEquals(12.3, session.litres, 0.001)
        assertEquals(17, session.hotSeconds)
        assertEquals(26, session.coldSeconds)

        val marker = reset.records.last()
        assertTrue(marker.isFilterMarker)
        assertEquals(2, marker.filter)
    }

    @Test
    fun lastSevenShowersMatchTheHelloKleanApp() {
        val stats = ShowerStats.compute(base.records.filter { it.isShower }, 7)!!
        assertEquals("16 min", formatShowerTime(stats.averageSeconds))
        assertEquals("15 sec", formatShowerTime(stats.averageHotSeconds))
        assertEquals("6 sec", formatShowerTime(stats.averageColdSeconds))
        assertEquals(1, (stats.hotShare * 100).toInt())
        assertEquals(0, (stats.coldShare * 100).toInt())
        assertEquals(26, stats.coldStreak)
    }

    @Test
    fun daysLeftFromShowersPerWeekIsCloseToTheApp() {
        // The app said 74 days at 10 showers a week with a fresh filter.
        val e = FilterEstimate.compute(first, base.records.filter { it.isShower }, emptyList(), showersPerWeek = 10)!!
        assertEquals(100, e.percent)
        assertEquals(FilterEstimate.Basis.SHOWERS_PER_WEEK, e.basis)
        assertTrue("got ${e.daysLeft}", e.daysLeft!! in 73..76)
    }

    @Test
    fun recentRecordBlocksCoverTheNewestRecords() {
        val dump = TagDump.parseText(javaClass.getResource("/scan-base.txt")!!.readText())
        val blocks = ShowerHeadReading.recentRecordBlocks(dump, 2)
        // Newest record (filter 1 marker) sits at byte 7380 = blocks 0x735-0x73B; the one before at 0x72E.
        assertEquals((0x72E..0x73B).toList(), blocks)
    }

    @Test
    fun measuredRateWinsOverShowersPerWeek() {
        // 200 L over 4 days -> 50 L/day; 4000 L left -> 80 days.
        val history = listOf(SyncPoint(0, 4200), SyncPoint(4 * DAY, 4000))
        val e = FilterEstimate.compute(first.copy(filterRemainingLitres = 4000), emptyList(), history, 10, now = 4 * DAY)!!
        assertEquals(50, e.percent)
        assertEquals(80, e.daysLeft)
        assertEquals(FilterEstimate.Basis.MEASURED, e.basis)
    }

    @Test
    fun resetOnShowerHeadStartsANewRate() {
        val history = listOf(
            SyncPoint(0, 3000), SyncPoint(5 * DAY, 500),
            SyncPoint(6 * DAY, 8000), SyncPoint(10 * DAY, 7600),
        )
        assertEquals(100.0, FilterEstimate.usagePerDay(history)!!, 0.001)
    }

    @Test
    fun statusFollowsTheHeadsOwnWarningLevel() {
        val fresh = FilterEstimate.compute(first, emptyList(), emptyList(), 10)!!
        assertNull(fresh.daysLeft)
        assertEquals(FilterStatus.GOOD, fresh.status)
        // Warns from 6400 L used, i.e. 1600 left.
        assertEquals(FilterStatus.LOW, FilterEstimate.compute(first.copy(filterRemainingLitres = 1600), emptyList(), emptyList(), 10)!!.status)
        assertEquals(FilterStatus.REPLACE, FilterEstimate.compute(first.copy(filterRemainingLitres = 0), emptyList(), emptyList(), 10)!!.status)
    }
}
