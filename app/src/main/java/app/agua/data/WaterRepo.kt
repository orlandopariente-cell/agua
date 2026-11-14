package app.agua.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.agua.widget.WaterWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime

private class WaterDb(ctx: Context) : SQLiteOpenHelper(ctx, "agua.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE intake (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "day TEXT NOT NULL, " +
                "ml INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_intake_day ON intake(day)")
        // Snapshot of goal/weight per day so history stays correct when settings change.
        db.execSQL(
            "CREATE TABLE day_meta (" +
                "day TEXT PRIMARY KEY, " +
                "goal_ml INTEGER NOT NULL, " +
                "weight_kg REAL NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * Single source of truth for water intake. Used by the app and the widget
 * (both run in the same process). Every write bumps [changes] and refreshes
 * the home-screen widget.
 */
object WaterRepo {
    @Volatile
    private var helper: WaterDb? = null

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun db(ctx: Context): SQLiteDatabase {
        val h = helper ?: synchronized(this) {
            helper ?: WaterDb(ctx.applicationContext).also { helper = it }
        }
        return h.writableDatabase
    }

    fun today(ctx: Context): LocalDate = DayMath.logicalDay(LocalDateTime.now(), Prefs.load(ctx))

    fun add(ctx: Context, ml: Int) {
        if (ml <= 0) return
        val s = Prefs.load(ctx)
        val day = DayMath.logicalDay(LocalDateTime.now(), s).toString()
        val values = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("day", day)
            put("ml", ml)
        }
        db(ctx).insert("intake", null, values)
        saveMeta(ctx, day, s)
        changed(ctx)
    }

    /** Removes the most recent entry of today. Returns its amount, or null if there was nothing. */
    fun undoLast(ctx: Context): Int? {
        val day = today(ctx).toString()
        val db = db(ctx)
        val last = db.rawQuery(
            "SELECT id, ml FROM intake WHERE day = ? ORDER BY id DESC LIMIT 1",
            arrayOf(day)
        ).use { c -> if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else null } ?: return null
        db.delete("intake", "id = ?", arrayOf(last.first.toString()))
        changed(ctx)
        return last.second
    }

    fun delete(ctx: Context, id: Long) {
        db(ctx).delete("intake", "id = ?", arrayOf(id.toString()))
        changed(ctx)
    }

    fun entriesFor(ctx: Context, day: LocalDate): List<Entry> =
        db(ctx).rawQuery(
            "SELECT id, ts, ml FROM intake WHERE day = ? ORDER BY id DESC",
            arrayOf(day.toString())
        ).use { c ->
            val out = ArrayList<Entry>()
            while (c.moveToNext()) out += Entry(c.getLong(0), c.getLong(1), c.getInt(2))
            out
        }

    fun totalFor(ctx: Context, day: LocalDate): Int =
        db(ctx).rawQuery(
            "SELECT COALESCE(SUM(ml), 0) FROM intake WHERE day = ?",
            arrayOf(day.toString())
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun status(ctx: Context, now: LocalDateTime = LocalDateTime.now()): Status {
        val s = Prefs.load(ctx)
        val day = DayMath.logicalDay(now, s)
        return Status(
            day = day,
            consumedMl = totalFor(ctx, day),
            expectedMl = DayMath.expectedMl(now, s),
            goalMl = s.goalMl,
            weightKg = s.weightKg,
        )
    }

    /** Daily totals in ascending order, from the first record (max [maxDays] back) up to today. */
    fun history(ctx: Context, maxDays: Int): List<DaySummary> {
        val s = Prefs.load(ctx)
        val today = DayMath.logicalDay(LocalDateTime.now(), s)
        val db = db(ctx)

        val first = db.rawQuery("SELECT MIN(day) FROM intake", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) LocalDate.parse(c.getString(0)) else today
        }
        var from = today.minusDays((maxDays - 1).toLong())
        if (first.isAfter(from)) from = first
        if (from.isAfter(today)) from = today
        val fromStr = from.toString()

        val totals = HashMap<String, Int>()
        db.rawQuery(
            "SELECT day, SUM(ml) FROM intake WHERE day >= ? GROUP BY day",
            arrayOf(fromStr)
        ).use { c -> while (c.moveToNext()) totals[c.getString(0)] = c.getInt(1) }

        val metas = HashMap<String, Pair<Int, Double>>()
        db.rawQuery(
            "SELECT day, goal_ml, weight_kg FROM day_meta WHERE day >= ?",
            arrayOf(fromStr)
        ).use { c -> while (c.moveToNext()) metas[c.getString(0)] = c.getInt(1) to c.getDouble(2) }

        val out = ArrayList<DaySummary>()
        var d = from
        while (!d.isAfter(today)) {
            val key = d.toString()
            val meta = metas[key]
            out += DaySummary(
                day = d,
                totalMl = totals[key] ?: 0,
                goalMl = meta?.first ?: s.goalMl,
                weightKg = meta?.second ?: s.weightKg,
            )
            d = d.plusDays(1)
        }
        return out
    }

    fun saveSettings(ctx: Context, s: Settings) {
        Prefs.save(ctx, s)
        saveMeta(ctx, DayMath.logicalDay(LocalDateTime.now(), s).toString(), s)
        changed(ctx)
    }

    private fun saveMeta(ctx: Context, day: String, s: Settings) {
        val values = ContentValues().apply {
            put("day", day)
            put("goal_ml", s.goalMl)
            put("weight_kg", s.weightKg)
        }
        db(ctx).insertWithOnConflict("day_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun changed(ctx: Context) {
        _changes.update { it + 1 }
        WaterWidget.updateAll(ctx)
    }
}
