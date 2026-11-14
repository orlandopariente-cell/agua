package app.agua.data

import java.time.LocalDate
import java.util.Locale

data class Entry(val id: Long, val timeMillis: Long, val ml: Int)

data class DaySummary(
    val day: LocalDate,
    val totalMl: Int,
    val goalMl: Int,
    val weightKg: Double,
) {
    val fraction: Double get() = if (goalMl > 0) totalMl / goalMl.toDouble() else 0.0
    val mlPerKg: Double? get() = if (weightKg > 0) totalMl / weightKg else null
    val reachedGoal: Boolean get() = goalMl > 0 && totalMl >= goalMl
}

data class Status(
    val day: LocalDate,
    val consumedMl: Int,
    val expectedMl: Int,
    val goalMl: Int,
    val weightKg: Double,
) {
    /** Positive = ahead of schedule, negative = behind. */
    val diffMl: Int get() = consumedMl - expectedMl
    val fraction: Double get() = if (goalMl > 0) consumedMl / goalMl.toDouble() else 0.0
    val expectedFraction: Double get() = if (goalMl > 0) expectedMl / goalMl.toDouble() else 0.0
    val mlPerKg: Double? get() = if (weightKg > 0) consumedMl / weightKg else null
}

object Fmt {
    fun signedMl(v: Int): String = when {
        v > 0 -> "+$v mL"
        v < 0 -> "-${-v} mL"
        else -> "0 mL"
    }

    fun mlPerKg(v: Double): String = String.format(Locale.getDefault(), "%.1f mL/kg", v)

    fun time(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

    fun kg(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString()
        else String.format(Locale.getDefault(), "%.1f", v)
}
