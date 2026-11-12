package app.yadavar.shamsi.widget

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.yadavar.shamsi.ui.BiometricLock
import app.yadavar.shamsi.ui.LockScreen
import kotlinx.coroutines.runBlocking
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.ui.EditorScreen
import app.yadavar.shamsi.ui.YadavarTheme
import app.yadavar.shamsi.ui.appTextScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opened by the "+" button of the widget: the reminder form without the main screen. */
class QuickAddActivity : FragmentActivity() {
    private var saving = false

    /** True while the fingerprint lock hides the form (the lock also guards the widget's "+" button). */
    private val locked = mutableStateOf(false)

    private fun promptUnlock() {
        BiometricLock.authenticate(this, onSuccess = { locked.value = false }, onFail = { })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Graph.isReady()) {
            finish()
            return
        }
        locked.value = runCatching { runBlocking { Graph.settings.current().biometricLock } }.getOrDefault(false) &&
            BiometricLock.canUse(this)
        enableEdgeToEdge()
        val draft = blankReminder("personal")
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent, appTextScale(settings.appFont), settings.appText) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (locked.value) {
                        LockScreen(onUnlock = ::promptUnlock)
                    } else EditorScreen(
                        initial = draft,
                        onClose = { finish() },
                        onSave = { reminder ->
                            if (!saving) {
                                saving = true
                                Graph.appScope.launch {
                                    Actions.save(reminder)
                                    withContext(Dispatchers.Main) { finish() }
                                }
                            }
                        },
                        onDelete = null,
                    )
                }
            }
        }
    }
}
