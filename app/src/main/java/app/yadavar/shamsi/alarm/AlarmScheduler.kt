package app.yadavar.shamsi.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.model.nextTrigger

object AlarmScheduler {
    const val ACTION_FIRE = "app.yadavar.shamsi.FIRE"
    const val ACTION_PRE = "app.yadavar.shamsi.PRE"
    const val ACTION_SNOOZE = "app.yadavar.shamsi.SNOOZE"
    const val ACTION_DONE = "app.yadavar.shamsi.DONE"
    const val ACTION_DISMISS = "app.yadavar.shamsi.DISMISS"
    const val ACTION_ESCALATE = "app.yadavar.shamsi.ESCALATE"

    /** Most extra (louder) rings after the first one. */
    const val MAX_ESCALATIONS = 3

    /** The "ring again, louder" alarm of a reminder; [level] is 1 for the first extra ring. */
    fun pendingEscalate(context: Context, id: String, level: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ESCALATE
            putExtra("id", id)
            putExtra("level", level)
        }
        return PendingIntent.getBroadcast(
            context,
            "esc:$id".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun scheduleEscalation(context: Context, id: String, level: Int, minutes: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        set(manager, at, pendingEscalate(context, id, level))
    }

    fun cancelEscalation(context: Context, id: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingEscalate(context, id, 1))
    }

    fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The early-notice alarm of a reminder (separate request code from the main alarm). */
    fun pendingPre(context: Context, id: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_PRE
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(
            context,
            "pre:$id".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val FIRED_PREFS = "yadavar_fired"

    /** The ring key ("at:..." / "snooze:...") of the occurrence of [id] that already rang, or null. */
    fun firedKey(context: Context, id: String): String? =
        context.getSharedPreferences(FIRED_PREFS, Context.MODE_PRIVATE).getString(id, null)

    /**
     * Remembers that this occurrence has rung. Without it, every later reschedule (finishing any reminder,
     * opening the app, a widget tap, a restart of the app process) treated a reminder that rang within the
     * last hours as "missed" and made it ring again a moment later - the "done button rings again" bug.
     */
    fun markFired(context: Context, id: String, key: String?) {
        if (key == null) return
        context.getSharedPreferences(FIRED_PREFS, Context.MODE_PRIVATE).edit().putString(id, key).apply()
    }

    private fun set(manager: AlarmManager, whenMs: Long, intent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, intent)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, intent)
        }
    }

    suspend fun reschedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        for (reminder in Graph.repo.all()) {
            val intent = pending(context, reminder.id)
            val preIntent = pendingPre(context, reminder.id)
            manager.cancel(intent)
            manager.cancel(preIntent)
            val whenMs = nextTrigger(reminder, now, firedKey(context, reminder.id)) ?: continue
            set(manager, whenMs, intent)
            // Early notice: only before the real due time (not for a snoozed ring).
            val lead = reminder.leadMinutes
            if (lead != null && lead > 0 && reminder.snoozeUntil == null) {
                val preAt = whenMs - lead * 60_000L
                if (preAt > now) set(manager, preAt, preIntent)
            }
        }
    }

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }
}
