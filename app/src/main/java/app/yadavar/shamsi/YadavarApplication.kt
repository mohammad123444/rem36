package app.yadavar.shamsi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.data.AppDatabase
import app.yadavar.shamsi.data.BackupManager
import app.yadavar.shamsi.data.ReminderRepository
import app.yadavar.shamsi.data.SettingsStore
import app.yadavar.shamsi.widget.ClockDateWidget
import app.yadavar.shamsi.widget.RescheduleWorker
import app.yadavar.shamsi.widget.WidgetRefreshReceiver
import app.yadavar.shamsi.widget.YadavarWidget
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Graph {
    lateinit var repo: ReminderRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var appContext: Context
        private set
    private val handler = CoroutineExceptionHandler { _, error ->
        if (::appContext.isInitialized) CrashLog.record(appContext, error)
    }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    fun isReady(): Boolean = ::repo.isInitialized && ::settings.isInitialized

    @OptIn(FlowPreview::class)
    fun init(app: Application) {
        if (::repo.isInitialized) return
        appContext = app
        repo = ReminderRepository(AppDatabase.open(app).dao())
        settings = SettingsStore(app)
        appScope.launch {
            runCatching { AlarmScheduler.reschedule(app) }.onFailure { CrashLog.record(app, it) }
            runCatching { YadavarWidget().updateAll(app) }.onFailure { CrashLog.record(app, it) }
            runCatching { ClockDateWidget().updateAll(app) }.onFailure { CrashLog.record(app, it) }
            runCatching { WidgetRefreshReceiver.scheduleNext(app) }.onFailure { CrashLog.record(app, it) }
        }
        appScope.launch {
            // Automatic backup: a few seconds after any change, if a backup file was chosen.
            repo.reminders.debounce(3_000).collect {
                runCatching { BackupManager.autoBackup(app) }
                runCatching { android.app.backup.BackupManager.dataChanged(app.packageName) }
            }
        }
    }

    suspend fun refreshWidget() {
        YadavarWidget().updateAll(appContext)
        runCatching { ClockDateWidget().updateAll(appContext) }
    }

    suspend fun refresh() {
        AlarmScheduler.reschedule(appContext)
        YadavarWidget().updateAll(appContext)
        runCatching { ClockDateWidget().updateAll(appContext) }
    }
}

class YadavarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        runCatching { ensureChannel(this) }.onFailure { CrashLog.record(this, it) }
        runCatching { Graph.init(this) }.onFailure { CrashLog.record(this, it) }
        runCatching {
            val request = PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "yadavar-reschedule",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }.onFailure { CrashLog.record(this, it) }
    }
}

const val CHANNEL_ID = "reminders"
const val CHANNEL_ALARM = "alarm_playing"

private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.channel_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.channel_desc)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)

    val playing = NotificationChannel(
        CHANNEL_ALARM,
        "هشدار در حال پخش",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "اعلان همراه صدای هشدار"
        setSound(null, null)
        enableVibration(false)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(playing)
}
