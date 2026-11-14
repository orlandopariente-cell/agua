package app.agua.data

import android.content.Context

/**
 * User settings.
 * @param goalMl   daily goal in mL
 * @param startMin start of the drinking day, minutes after midnight (e.g. 07:00 = 420)
 * @param endMin   end of the drinking day, minutes after midnight (may be after midnight, e.g. 01:00)
 * @param weightKg body weight in kg, 0 = not set
 */
data class Settings(
    val goalMl: Int = 2000,
    val startMin: Int = 7 * 60,
    val endMin: Int = 23 * 60,
    val weightKg: Double = 0.0,
)

object Prefs {
    private const val FILE = "agua_prefs"

    fun load(ctx: Context): Settings {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            goalMl = p.getInt("goal_ml", d.goalMl),
            startMin = p.getInt("start_min", d.startMin),
            endMin = p.getInt("end_min", d.endMin),
            weightKg = p.getFloat("weight_kg", d.weightKg.toFloat()).toDouble(),
        )
    }

    fun save(ctx: Context, s: Settings) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("goal_ml", s.goalMl)
            .putInt("start_min", s.startMin)
            .putInt("end_min", s.endMin)
            .putFloat("weight_kg", s.weightKg.toFloat())
            .apply()
    }
}
