package app.yadavar.shamsi.widget

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.ui.BiometricLock
import app.yadavar.shamsi.ui.YadavarTheme
import app.yadavar.shamsi.ui.appTextScale
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Opened by the circle of a reminder in the widget. With the fingerprint lock on, the fingerprint is the
 * confirmation; otherwise a small "مطمئنی؟" question is shown. Only then is the reminder finished.
 */
class ConfirmCompleteActivity : FragmentActivity() {
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra("id")
        if (id == null || !Graph.isReady()) {
            finish()
            return
        }
        val useFingerprint = runCatching { runBlocking { Graph.settings.current().biometricLock } }
            .getOrDefault(false) && BiometricLock.canUse(this)
        if (useFingerprint) {
            setContent { }
            BiometricLock.authenticate(this, onSuccess = { complete(id) }, onFail = { finish() })
            return
        }
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent, appTextScale(settings.appFont), settings.appText) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("مطمئنی؟") },
                        text = { Text("این یادآور انجام شد و از ویجت برداشته می‌شود.") },
                        confirmButton = { TextButton(onClick = { complete(id) }) { Text("بله، انجام شد") } },
                        dismissButton = { TextButton(onClick = { finish() }) { Text("نه") } },
                    )
                }
            }
        }
    }

    private fun complete(id: String) {
        if (finished) return
        finished = true
        val appContext = applicationContext
        Graph.appScope.launch { completeFromWidget(appContext, id) }
        finish()
    }
}
