package app.yadavar.shamsi.alarm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.dateEpoch
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.faNum
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.formatTime
import app.yadavar.shamsi.ui.MonthGrid
import app.yadavar.shamsi.ui.YadavarTheme
import app.yadavar.shamsi.ui.appTextScale
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val id = intent.getStringExtra("id")
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        val openSnooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        val openCustom = intent.getBooleanExtra(EXTRA_CUSTOM, false)
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent, appTextScale(settings.appFont), settings.appText) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlarmScreen(id = id, startWithSnooze = openSnooze, startWithCustom = openCustom, onFinish = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_SNOOZE = "snooze"
        const val EXTRA_CUSTOM = "custom_time"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmScreen(id: String, startWithSnooze: Boolean, startWithCustom: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reminder by remember { mutableStateOf<Reminder?>(null) }
    var snoozeOpen by remember { mutableStateOf(startWithSnooze || startWithCustom) }
    var custom by remember { mutableStateOf(startWithCustom) }
    // Default for "another time": half an hour from now.
    val suggested = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 30) } }
    val suggestedDate = remember { toJalali(suggested.time) }
    var date by remember { mutableStateOf(suggestedDate) }
    var visible by remember { mutableStateOf(JDate(suggestedDate.jy, suggestedDate.jm, 1)) }
    var hour by remember { mutableStateOf(suggested.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(suggested.get(Calendar.MINUTE)) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) {
        reminder = Graph.repo.get(id)
        if (reminder == null) onFinish()
    }
    val item = reminder ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Keep clear of the status bar (its icons used to overlap the title), the camera cutout and the navigation bar.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        app.yadavar.shamsi.ui.BellAnimation(Modifier.height(120.dp).fillMaxWidth())
        Text("یادآور", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(item.title.ifBlank { "بدون عنوان" }, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    Actions.complete(id)
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("انجام شد") }
        OutlinedButton(
            onClick = {
                snoozeOpen = !snoozeOpen
                if (!snoozeOpen) custom = false
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("تعویق") }
        if (snoozeOpen) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "۵ دقیقه", 10 to "۱۰ دقیقه", 15 to "۱۵ دقیقه", 30 to "۳۰ دقیقه", 60 to "۱ ساعت").forEach { (minutes, label) ->
                    OutlinedButton(onClick = {
                        scope.launch {
                            Actions.snooze(id, minutes)
                            onFinish()
                        }
                    }) { Text(label) }
                }
                OutlinedButton(onClick = { custom = !custom }) { Text("زمان دیگر") }
            }
        }
        if (custom) {
            Text(formatLongDate(date), style = MaterialTheme.typography.titleSmall)
            MonthGrid(visible = visible, selected = date, onSelect = { date = it }, onMonth = { visible = JDate(it.jy, it.jm, 1) })
            Text("ساعت: ${formatTime(hour, minute)}", style = MaterialTheme.typography.titleSmall)
            app.yadavar.shamsi.ui.WheelTimePicker(hour = hour, minute = minute, onChange = { h, m -> hour = h; minute = m })
            val problem = error
            if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val target = dateEpoch(date.jy, date.jm, date.jd, hour, minute)
                    if (target <= System.currentTimeMillis()) {
                        error = "این زمان گذشته است؛ تاریخ یا ساعت بعدی را انتخاب کن."
                    } else {
                        error = null
                        scope.launch {
                            Actions.moveTo(id, date, hour, minute)
                            onFinish()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیرهٔ زمان تازه") }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    Actions.dismiss(id)
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("بستن") }
    }
}
