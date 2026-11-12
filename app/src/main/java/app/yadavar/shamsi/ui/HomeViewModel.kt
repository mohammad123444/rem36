package app.yadavar.shamsi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.data.BackupManager
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.voice.ParsedSpeech
import app.yadavar.shamsi.widget.ClockDateWidget
import app.yadavar.shamsi.widget.YadavarWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { Home, Stats, Settings }

private const val DEFAULT_GROUP = "personal"

class HomeViewModel : ViewModel() {
    val reminders = Graph.repo.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = Graph.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.yadavar.shamsi.model.Settings())

    var editing by mutableStateOf<Reminder?>(null)
    var spoken by mutableStateOf("")
    var tab by mutableStateOf(Tab.Home)
    var filter by mutableStateOf("all")
    var notice by mutableStateOf<String?>(null)

    /** In-app update (checked when the app opens; the Settings page can ask again). */
    val updater = app.yadavar.shamsi.update.UpdateController(viewModelScope)

    /** Moments at which a repeating reminder was finished (needed for the statistics page). */
    fun repeatDoneLog(): List<Long> = app.yadavar.shamsi.data.StatsLog.read(Graph.appContext)

    fun deleteReminder(id: String) = run { Actions.delete(id) }

    fun setBiometricLock(value: Boolean) = run { Graph.settings.setBiometricLock(value) }
    fun setAlarmMode(value: String) = run { Graph.settings.setAlarmMode(value) }
    fun setEscalate(value: Boolean) = run { Graph.settings.setEscalate(value) }
    fun setEscalateMinutes(value: Int) = run { Graph.settings.setEscalateMinutes(value) }

    fun openNew() {
        spoken = ""
        editing = blankReminder(DEFAULT_GROUP)
    }

    fun open(reminder: Reminder) {
        spoken = ""
        editing = reminder
    }

    fun closeEditor() {
        editing = null
        spoken = ""
    }

    /** Opens the editor already filled from the spoken sentence, so nothing has to be typed again. */
    fun openParsed(parsed: ParsedSpeech, sentence: String) {
        spoken = sentence
        val base = blankReminder(DEFAULT_GROUP)
        editing = base.copy(
            title = parsed.title.ifBlank { sentence.trim() },
            jy = parsed.jy,
            jm = parsed.jm,
            jd = parsed.jd,
            hour = parsed.hour,
            minute = parsed.minute,
            alert = true,
            repeat = Repeat.of(parsed.repeatKind ?: "none", 1),
            anchorDay = parsed.jd,
        )
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { notice = "انجام نشد" }
        }
    }

    fun save(reminder: Reminder) = run {
        Actions.save(reminder)
        closeEditor()
    }

    fun setTheme(value: String) = run { Graph.settings.setTheme(value) }
    fun setAccent(value: String) = run { Graph.settings.setAccent(value) }
    fun setFont(value: String) = run {
        Graph.settings.setFont(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun setBackupFile(uri: String) = run {
        Graph.settings.setBackupUri(uri)
        val ok = BackupManager.autoBackup(Graph.appContext, force = true)
        notice = if (ok) "فایل پشتیبان آماده شد" else "نوشتن در فایل پشتیبان انجام نشد"
    }

    fun backupNow() = run {
        val ok = BackupManager.autoBackup(Graph.appContext, force = true)
        notice = if (ok) "پشتیبان‌گیری انجام شد" else "پشتیبان‌گیری نشد؛ فایل پشتیبان را دوباره انتخاب کن"
    }

    fun restoreFrom(uri: android.net.Uri) = run {
        val count = runCatching { BackupManager.importFrom(Graph.appContext, uri) }.getOrDefault(-1)
        notice = if (count >= 0) "${app.yadavar.shamsi.model.faNum(count)} یادآور بازیابی شد" else "فایل پشتیبان معتبر نیست"
    }

    fun setAppFont(value: String) = run { Graph.settings.setAppFont(value) }
    fun setAppText(value: String) = run { Graph.settings.setAppText(value) }

    fun setWidgetText(value: String) = run {
        Graph.settings.setWidgetText(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun setClockSize(value: String) = run {
        Graph.settings.setClockSize(value)
        ClockDateWidget().updateAll(Graph.appContext)
    }

    fun setClockAlign(value: String) = run {
        Graph.settings.setClockAlign(value)
        ClockDateWidget().updateAll(Graph.appContext)
    }

    fun setClockColor(value: String) = run {
        Graph.settings.setClockColor(value)
        ClockDateWidget().updateAll(Graph.appContext)
    }

    fun markBatteryAsked() = run { Graph.settings.setBatteryAsked(true) }

    /** Unlinks the backup file (Google Drive or any other); the copy in the phone's storage stays automatic. */
    fun unlinkBackup() = run {
        Graph.settings.setBackupUri("")
        notice = "اتصال پشتیبان‌گیری قطع شد"
    }

    fun setWidgetBg(value: String) = run {
        Graph.settings.setWidgetBg(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun setAlarmSound(uri: String, name: String) = run { Graph.settings.setAlarmSound(uri, name) }
    fun setAlarmSeconds(value: Int) = run { Graph.settings.setAlarmSeconds(value) }
}
