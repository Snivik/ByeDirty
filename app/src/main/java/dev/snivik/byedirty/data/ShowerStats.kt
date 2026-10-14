package dev.snivik.byedirty.data

/**
 * Shower insights over the last [count] showers, computed the way the official Hello Klean app
 * does (checked against its screens): plain averages over showers of a minute or more, across
 * filter changes.
 */
data class ShowerStats(
    val count: Int,
    val averageSeconds: Double,
    val averageHotSeconds: Double,
    val averageColdSeconds: Double,
    /** Share of all shower time with hot / cold water, 0-1. */
    val hotShare: Double,
    val coldShare: Double,
    val showersWithHot: Int,
    val showersWithCold: Int,
    /** Consecutive most-recent showers that included some cold water (the official app's "streaks"). */
    val coldStreak: Int,
    val averageLitres: Double,
) {
    companion object {
        /** @param showers real showers, oldest first (see [ShowerRecord.isShower]). */
        fun compute(showers: List<ShowerRecord>, window: Int): ShowerStats? {
            val all = showers
            val recent = all.takeLast(window)
            if (recent.isEmpty()) return null
            val total = recent.sumOf { it.durationSeconds }.toDouble()
            val hot = recent.sumOf { it.hotSeconds }.toDouble()
            val cold = recent.sumOf { it.coldSeconds }.toDouble()
            val n = recent.size
            return ShowerStats(
                count = n,
                averageSeconds = total / n,
                averageHotSeconds = hot / n,
                averageColdSeconds = cold / n,
                hotShare = if (total > 0) hot / total else 0.0,
                coldShare = if (total > 0) cold / total else 0.0,
                showersWithHot = recent.count { it.hotSeconds > 0 },
                showersWithCold = recent.count { it.coldSeconds > 0 },
                coldStreak = all.asReversed().takeWhile { it.coldSeconds > 0 }.size,
                averageLitres = recent.sumOf { it.litres } / n,
            )
        }
    }
}
