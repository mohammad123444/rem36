package app.yadavar.shamsi.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import app.yadavar.shamsi.Graph
import java.util.Calendar
import kotlinx.coroutines.launch

/** Refreshes the widget just after midnight so the date and the "today" lists change on time. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                runCatching { YadavarWidget().updateAll(app) }
                runCatching { ClockDateWidget().updateAll(app) }
                scheduleNext(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun scheduleNext(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
            val intent = PendingIntent.getBroadcast(
                context,
                9917,
                Intent(context, WidgetRefreshReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC, next.timeInMillis, intent) }
        }
    }
}
