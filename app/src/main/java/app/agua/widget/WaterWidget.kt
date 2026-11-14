package app.agua.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import app.agua.R
import app.agua.data.Fmt
import app.agua.data.WaterRepo
import app.agua.ui.MainActivity

class WaterWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = buildViews(ctx)
        ids.forEach { mgr.updateAppWidget(it, views) }
        scheduleTick(ctx)
    }

    override fun onEnabled(ctx: Context) = scheduleTick(ctx)

    override fun onDisabled(ctx: Context) = cancelTick(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ADD -> WaterRepo.add(ctx, intent.getIntExtra(EXTRA_ML, 0))
            ACTION_UNDO -> {
                val removed = WaterRepo.undoLast(ctx)
                val msg = if (removed != null) "Desfeito: -$removed mL" else "Nada para desfazer hoje"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
            ACTION_TICK -> updateAll(ctx)
            else -> super.onReceive(ctx, intent)
        }
    }

    companion object {
        private const val ACTION_ADD = "app.agua.action.ADD"
        private const val ACTION_UNDO = "app.agua.action.UNDO"
        private const val ACTION_TICK = "app.agua.action.TICK"
        private const val EXTRA_ML = "ml"

        private const val RC_UNDO = 1
        private const val RC_TICK = 2
        private const val RC_OPEN = 3

        private val QUICK_BUTTONS = listOf(
            R.id.btn_100 to 100,
            R.id.btn_180 to 180,
            R.id.btn_500 to 500,
            R.id.btn_700 to 700,
        )

        /** Redraws every placed widget with the current numbers. */
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WaterWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, buildViews(ctx))
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val st = WaterRepo.status(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_water)

            v.setTextViewText(R.id.status, Fmt.signedMl(st.diffMl))
            v.setTextColor(
                R.id.status,
                ctx.getColor(if (st.diffMl >= 0) R.color.status_ok else R.color.status_low)
            )
            v.setTextViewText(R.id.consumed, "${st.consumedMl} / ${st.goalMl} mL")
            val detail = buildString {
                append("Esperado agora: ${st.expectedMl} mL")
                st.mlPerKg?.let { append("  ·  ").append(Fmt.mlPerKg(it)) }
            }
            v.setTextViewText(R.id.detail, detail)
            v.setProgressBar(R.id.progress, 1000, (st.fraction * 1000).toInt().coerceIn(0, 1000), false)

            QUICK_BUTTONS.forEach { (id, ml) ->
                val i = Intent(ctx, WaterWidget::class.java)
                    .setAction(ACTION_ADD)
                    .putExtra(EXTRA_ML, ml)
                // requestCode = ml keeps each button's PendingIntent distinct
                v.setOnClickPendingIntent(id, broadcast(ctx, ml, i))
            }
            v.setOnClickPendingIntent(
                R.id.btn_undo,
                broadcast(ctx, RC_UNDO, Intent(ctx, WaterWidget::class.java).setAction(ACTION_UNDO))
            )

            val open = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            v.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    ctx, RC_OPEN, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return v
        }

        private fun broadcast(ctx: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun tickIntent(ctx: Context) =
            broadcast(ctx, RC_TICK, Intent(ctx, WaterWidget::class.java).setAction(ACTION_TICK))

        /**
         * The expected amount grows during the day, so refresh roughly every
         * 15 minutes. Non-wakeup alarm: no battery cost while the phone sleeps;
         * it fires as soon as the screen is turned on.
         */
        fun scheduleTick(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            am.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                tickIntent(ctx)
            )
        }

        private fun cancelTick(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx))
        }
    }
}
