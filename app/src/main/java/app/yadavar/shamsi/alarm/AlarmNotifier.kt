package app.yadavar.shamsi.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.yadavar.shamsi.CHANNEL_ALARM
import app.yadavar.shamsi.CHANNEL_ID
import app.yadavar.shamsi.R
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.formatTime
import app.yadavar.shamsi.model.leadLabel
import app.yadavar.shamsi.ui.MainActivity
import app.yadavar.shamsi.model.rowMeta

object AlarmNotifier {
    fun placeholder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("یادآور")
            .setContentText("وقت یادآور است")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

    fun build(context: Context, reminder: Reminder, channelId: String, ongoing: Boolean): Notification {
        val id = reminder.id
        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("id", id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val other = PendingIntent.getActivity(
            context,
            "other:$id".hashCode(),
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("id", id)
                putExtra(AlarmActivity.EXTRA_SNOOZE, true)
                putExtra(AlarmActivity.EXTRA_CUSTOM, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = broadcast(context, id, AlarmScheduler.ACTION_DONE, "done")
        val title = reminder.title.ifBlank { "یادآور" }
        val time = if (reminder.hour != null && reminder.minute != null) formatTime(reminder.hour, reminder.minute) else ""
        val text = listOf(time, rowMeta(reminder, null, null)).filter { it.isNotBlank() }.joinToString(" · ")
        val summary = text.ifBlank { "وقت یادآور است" }

        // Expanded notification: all six answers without opening the app.
        val big = RemoteViews(context.packageName, R.layout.notification_alarm_big)
        big.setTextViewText(R.id.notif_title, title)
        big.setTextViewText(R.id.notif_text, summary)
        big.setOnClickPendingIntent(R.id.btn_done, done)
        big.setOnClickPendingIntent(R.id.btn_10, broadcast(context, id, AlarmScheduler.ACTION_SNOOZE, "snooze10", 10))
        big.setOnClickPendingIntent(R.id.btn_15, broadcast(context, id, AlarmScheduler.ACTION_SNOOZE, "snooze15", 15))
        big.setOnClickPendingIntent(R.id.btn_30, broadcast(context, id, AlarmScheduler.ACTION_SNOOZE, "snooze30", 30))
        big.setOnClickPendingIntent(R.id.btn_60, broadcast(context, id, AlarmScheduler.ACTION_SNOOZE, "snooze60", 60))
        big.setOnClickPendingIntent(R.id.btn_other, other)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(big)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(ongoing)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "انجام شد", done)
            .addAction(0, "۱۰ دقیقه", broadcast(context, id, AlarmScheduler.ACTION_SNOOZE, "snooze10", 10))
            .addAction(0, "زمان دیگر", other)
            .build()
    }

    /** "N minutes left" heads-up shown before the due time; tapping it opens the reminder. */
    fun postEarlyNotice(context: Context, reminder: Reminder) {
        val lead = reminder.leadMinutes ?: return
        val open = PendingIntent.getActivity(
            context,
            "preopen:${reminder.id}".hashCode(),
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("yadavar://reminder/${reminder.id}")
                putExtra(MainActivity.EXTRA_OPEN_ID, reminder.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = if (reminder.hour != null && reminder.minute != null) " · ساعت " + formatTime(reminder.hour, reminder.minute) else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(reminder.title.ifBlank { "یادآور" })
            .setContentText("تا " + leadLabel(lead) + " دیگر" + time)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify("pre:${reminder.id}".hashCode(), notification) }
    }

    /** Used only when the alarm service cannot start: the channel's own sound plays instead. */
    fun postFallback(context: Context, reminder: Reminder) {
        val notification = build(context, reminder, CHANNEL_ID, ongoing = false)
        NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun broadcast(context: Context, id: String, action: String, salt: String, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra("id", id)
            if (action == AlarmScheduler.ACTION_SNOOZE) putExtra("minutes", if (minutes > 0) minutes else 5)
        }
        return PendingIntent.getBroadcast(
            context,
            "$salt:$id".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
