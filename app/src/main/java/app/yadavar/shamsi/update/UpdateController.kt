package app.yadavar.shamsi.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.yadavar.shamsi.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val signature: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object NeedsPermission : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
}

/** Checks for a new version when the app opens (and on demand) and drives the update dialog. */
class UpdateController(private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private var checked = false

    /** [manual] = the person pressed the button: always answer, even when there is nothing new. */
    fun check(manual: Boolean = false) {
        if (!manual && checked) return
        checked = true
        if (state is UpdateState.Downloading || state is UpdateState.Checking) return
        scope.launch {
            if (manual) state = UpdateState.Checking
            val signature = Updater.remoteSignature()
            if (signature == null) {
                state = if (manual) UpdateState.Failed else UpdateState.Idle
                return@launch
            }
            val seen = Graph.settings.current().updateSeen
            when {
                seen.isBlank() && !manual -> {
                    // First run of this version: the online file is taken as the current one.
                    Graph.settings.setUpdateSeen(signature)
                    state = UpdateState.Idle
                }
                signature != seen || manual -> {
                    if (signature == seen) {
                        state = UpdateState.UpToDate
                    } else {
                        state = UpdateState.Available(signature)
                    }
                }
                else -> state = UpdateState.Idle
            }
        }
    }

    fun download(signature: String) {
        val context = Graph.appContext
        scope.launch {
            state = UpdateState.Downloading(-1f)
            val file = Updater.download(context) { state = UpdateState.Downloading(it) }
            if (file == null) {
                state = UpdateState.Failed
                return@launch
            }
            Graph.settings.setUpdateSeen(signature)
            if (!Updater.isNewer(context, file)) {
                state = UpdateState.UpToDate
                return@launch
            }
            install()
        }
    }

    fun install() {
        val context = Graph.appContext
        if (!Updater.canInstall(context)) {
            state = UpdateState.NeedsPermission
            Updater.openInstallPermission(context)
            return
        }
        Updater.install(context)
        state = UpdateState.Idle
    }

    fun dismiss() {
        state = UpdateState.Idle
    }
}
