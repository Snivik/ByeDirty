package dev.snivik.byedirty.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class FilterStatus { GOOD, LOW, REPLACE }

/** One past sync: when it happened and how many litres the shower head said were left. */
data class SyncPoint(val millis: Long, val litresLeft: Int)

/**
 * Filter life as reported by the shower head, plus a "days left" projection.
 *
 * The head's clock isn't trustworthy, so the projection needs a usage rate from outside it:
 * measured between syncs when there's enough history, otherwise showers per week times the
 * average litres per logged shower. That second method is what the HelloKlean app does; it
 * gave 75 days where the app said 74.
 */
data class FilterEstimate(
    val percent: Int,
    val litresLeft: Int,
    val litresUsed: Int,
    val daysLeft: Int?,
    val refillDate: LocalDate?,
    val status: FilterStatus,
    val basis: Basis?,
) {
    enum class Basis { MEASURED, SHOWERS_PER_WEEK }

    companion object {
        private const val DAY_MS = 86_400_000.0
        /** Need at least this much history before trusting a measured rate. */
        private const val MIN_SPAN_DAYS = 3.0

        fun compute(
            reading: ShowerHeadReading,
            showers: List<ShowerRecord>,
            history: List<SyncPoint>,
            showersPerWeek: Int,
            now: Long = System.currentTimeMillis(),
        ): FilterEstimate? {
            val capacity = reading.filterCapacityLitres ?: return null
            val left = reading.filterRemainingLitres?.coerceIn(0, capacity) ?: return null
            val percent = (100.0 * left / capacity).roundToInt()

            val measured = usagePerDay(history)
            val fromHabits = showers.takeIf { it.isNotEmpty() }
                ?.let { s -> s.sumOf { it.litres } / s.size * showersPerWeek / 7.0 }
                ?.takeIf { it > 0 }
            val (perDay, basis) = when {
                measured != null -> measured to Basis.MEASURED
                fromHabits != null -> fromHabits to Basis.SHOWERS_PER_WEEK
                else -> null to null
            }
            val daysLeft = perDay?.let { (left / it).toInt() }
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

            // The head starts warning at filterElementReminder (6400 of 8000 L).
            val warnAt = reading.filterReminderLitres ?: (capacity * 0.8).toInt()
            val status = when {
                left <= 0 -> FilterStatus.REPLACE
                capacity - left >= warnAt || (daysLeft != null && daysLeft <= 7) -> FilterStatus.LOW
                else -> FilterStatus.GOOD
            }
            return FilterEstimate(
                percent, left, capacity - left, daysLeft, daysLeft?.let { today.plusDays(it.toLong()) }, status, basis,
            )
        }

        /** Litres per day since the filter was last reset (a jump up in litres left marks a reset). */
        fun usagePerDay(history: List<SyncPoint>): Double? {
            val sorted = history.sortedBy { it.millis }
            val resetAt = sorted.indices.lastOrNull { i -> i > 0 && sorted[i].litresLeft > sorted[i - 1].litresLeft } ?: 0
            val current = sorted.drop(resetAt)
            val first = current.firstOrNull() ?: return null
            val last = current.last()
            val days = (last.millis - first.millis) / DAY_MS
            val used = first.litresLeft - last.litresLeft
            if (days < MIN_SPAN_DAYS || used <= 0) return null
            return used / days
        }
    }
}
