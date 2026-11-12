package app.yadavar.shamsi.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.yadavar.shamsi.CHANNEL_ALARM
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays the chosen alarm sound for the configured number of seconds, with a gentle volume fade-in. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var rampJob: Job? = null
    private var savedAlarmVolume: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id")
        if (id == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        playingId = id
        val level = intent.getIntExtra("level", 0)
        startAsForeground(id.hashCode(), AlarmNotifier.placeholder(this))
        job?.cancel()
        job = Graph.appScope.launch {
            val reminder = Graph.repo.get(id)
            if (reminder == null) {
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }
            val settings = Graph.settings.current()
            runCatching {
                NotificationManagerCompat.from(applicationContext)
                    .notify(id.hashCode(), AlarmNotifier.build(applicationContext, reminder, CHANNEL_ALARM, ongoing = true))
            }
            val mode = settings.alarmMode
            // "silent": notification and alarm screen only. "vibrate": vibration only. "ring": sound + vibration.
            val prepared = if (mode == "ring") {
                withContext(Dispatchers.IO) { AlarmSound.create(applicationContext, settings.alarmSound) }
            } else {
                null
            }
            withContext(Dispatchers.Main) { startSound(prepared, mode, level) }
            delay(settings.alarmSeconds.coerceIn(5, 120) * 1000L)
            withContext(Dispatchers.Main) { finish(reminder) }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(notificationId: Int, notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, notificationId, notification, type)
    }

    private fun startSound(created: MediaPlayer?, mode: String, level: Int) {
        stopSound()
        if (created != null) {
            player = created
            // Escalating rings (level 1, 2, 3) start louder and also raise the phone's alarm volume.
            val startVolume = if (level <= 0) 0.2f else minOf(1f, 0.5f + 0.25f * level)
            if (level > 0) raiseAlarmVolume(level)
            runCatching { created.setVolume(startVolume, startVolume) }
            runCatching { created.start() }
            rampJob = Graph.appScope.launch(Dispatchers.Main) {
                var volume = startVolume
                while (volume < 1f) {
                    delay(300)
                    volume = minOf(1f, volume + 0.1f)
                    runCatching { created.setVolume(volume, volume) }
                }
            }
        }
        if (mode != "silent") vibrate()
    }

    /** Level 1 -> 70 %, level 2 -> 85 %, level 3 -> 100 % of the alarm volume; the old volume is restored afterwards. */
    private fun raiseAlarmVolume(level: Int) {
        runCatching {
            val audio = getSystemService(android.media.AudioManager::class.java) ?: return
            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            val now = audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            if (savedAlarmVolume == null) savedAlarmVolume = now
            val fraction = when (level) {
                1 -> 0.7f
                2 -> 0.85f
                else -> 1f
            }
            val target = maxOf(now, (max * fraction).toInt())
            audio.setStreamVolume(android.media.AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreAlarmVolume() {
        val old = savedAlarmVolume ?: return
        savedAlarmVolume = null
        runCatching {
            getSystemService(android.media.AudioManager::class.java)
                ?.setStreamVolume(android.media.AudioManager.STREAM_ALARM, old, 0)
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 500, 900)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopSound() {
        rampJob?.cancel()
        rampJob = null
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        runCatching { getSystemService(Vibrator::class.java)?.cancel() }
        restoreAlarmVolume()
    }

    /** Time is up: silence everything but keep a normal notification so the reminder is not lost. */
    private fun finish(reminder: Reminder) {
        stopSound()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(reminder.id.hashCode(), AlarmNotifier.build(applicationContext, reminder, CHANNEL_ALARM, ongoing = false))
        }
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        stopSound()
        playingId = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, id: String, level: Int = 0): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).putExtra("id", id).putExtra("level", level),
            )
            true
        }.getOrDefault(false)

        @Volatile
        var playingId: String? = null

        /** Stops the alarm sound; with an [id] only when that reminder's alarm is the one playing. */
        fun stop(context: Context, id: String? = null) {
            if (id != null && id != playingId) return
            runCatching { context.stopService(Intent(context, AlarmService::class.java)) }
        }
    }
}
