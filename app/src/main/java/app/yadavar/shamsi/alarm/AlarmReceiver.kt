package app.yadavar.shamsi.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.model.ringKey
import app.yadavar.shamsi.widget.WidgetRefreshReceiver
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_SNOOZE -> Actions.snooze(id, intent.getIntExtra("minutes", 5))
                    AlarmScheduler.ACTION_DONE -> Actions.complete(id)
                    AlarmScheduler.ACTION_DISMISS -> Actions.dismiss(id)
                    AlarmScheduler.ACTION_PRE -> showEarlyNotice(context.applicationContext, id)
                    AlarmScheduler.ACTION_ESCALATE -> escalate(context.applicationContext, id, intent.getIntExtra("level", 1))
                    else -> show(context.applicationContext, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showEarlyNotice(context: Context, id: String) {
        val reminder = Graph.repo.get(id) ?: return
        if (reminder.completed) return
        AlarmNotifier.postEarlyNotice(context, reminder)
    }

    private suspend fun show(context: Context, id: String) {
        val reminder = Graph.repo.get(id) ?: return
        // A stale alarm (the reminder was finished, closed or already rang) must stay silent.
        val key = ringKey(reminder, System.currentTimeMillis()) ?: return
        if (AlarmScheduler.firedKey(context, id) == key) return
        AlarmScheduler.markFired(context, id, key)
        ring(context, reminder, level = 0)
        // Escalating alarm: if nobody answers, ring again after a few minutes, louder.
        val settings = Graph.settings.current()
        if (settings.escalate && settings.alarmMode != "silent") {
            AlarmScheduler.scheduleEscalation(context, id, 1, settings.escalateMinutes)
        }
    }

    /** An extra, louder ring of a reminder that was not finished, snoozed or closed. */
    private suspend fun escalate(context: Context, id: String, level: Int) {
        val reminder = Graph.repo.get(id) ?: return
        val key = ringKey(reminder, System.currentTimeMillis()) ?: return
        // The reminder changed (new time) since the ring that started this chain: stop here.
        if (AlarmScheduler.firedKey(context, id) != key) return
        val settings = Graph.settings.current()
        if (!settings.escalate) return
        ring(context, reminder, level)
        if (level < AlarmScheduler.MAX_ESCALATIONS) {
            AlarmScheduler.scheduleEscalation(context, id, level + 1, settings.escalateMinutes)
        }
    }

    private fun ring(context: Context, reminder: app.yadavar.shamsi.model.Reminder, level: Int) {
        if (!AlarmService.start(context, reminder.id, level)) {
            AlarmNotifier.postFallback(context, reminder)
        }
        runCatching {
            context.startActivity(
                Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("id", reminder.id)
                },
            )
        }
    }
}

/**
 * Re-registers every alarm after a restart, a time/timezone change and - important - after the app itself is
 * updated: Android removes all alarms of a package when it is replaced (MY_PACKAGE_REPLACED).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                if (!Graph.isReady()) {
                    // The application object normally initializes Graph; this is only a safety net.
                    (app as? android.app.Application)?.let { Graph.init(it) }
                }
                AlarmScheduler.reschedule(app)
                runCatching { Graph.refreshWidget() }
                runCatching { WidgetRefreshReceiver.scheduleNext(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
