package app.agua.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Time math for the "drinking day".
 *
 * The logical day does not roll over at midnight but in the middle of the
 * rest period (between end and start). With 07:00–23:00 the day rolls over
 * at 03:00, so a glass at 00:30 still counts for the previous day. This also
 * works when the day ends after midnight (e.g. 09:00–01:00).
 */
object DayMath {
    private const val DAY_MIN = 24 * 60

    private fun mod(a: Int, m: Int) = ((a % m) + m) % m

    /** Minutes are relative to the rollover point of the logical day. */
    data class Window(val rolloverMin: Int, val startOffset: Int, val endOffset: Int)

    fun window(s: Settings): Window {
        if (s.startMin == s.endMin) return Window(s.startMin, 0, DAY_MIN)
        val rest = mod(s.startMin - s.endMin, DAY_MIN)
        val roll = mod(s.endMin + rest / 2, DAY_MIN)
        val start = mod(s.startMin - roll, DAY_MIN)
        val end = mod(s.endMin - roll, DAY_MIN).let { if (it == 0) DAY_MIN else it }
        return Window(roll, start, end)
    }

    fun logicalDay(t: LocalDateTime, s: Settings): LocalDate =
        t.minusMinutes(window(s).rolloverMin.toLong()).toLocalDate()

    /** Fraction (0..1) of the daily goal that should have been drunk by [t]. */
    fun expectedFraction(t: LocalDateTime, s: Settings): Double {
        val w = window(s)
        val sec = t.toLocalTime().toSecondOfDay()
        val minutesIntoDay = mod(sec - w.rolloverMin * 60, DAY_MIN * 60) / 60.0
        val f = (minutesIntoDay - w.startOffset) / (w.endOffset - w.startOffset).toDouble()
        return f.coerceIn(0.0, 1.0)
    }

    fun expectedMl(t: LocalDateTime, s: Settings): Int =
        (s.goalMl * expectedFraction(t, s)).roundToInt()
}
