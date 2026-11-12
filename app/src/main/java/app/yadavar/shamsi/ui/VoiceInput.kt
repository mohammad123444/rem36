package app.yadavar.shamsi.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Offline speech-to-text for one text box. Returns the function that starts listening; the dialog
 * (model download, listening, system-recognizer fallback) is drawn by this composable itself.
 * [onText] gets the recognized sentence.
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var showVoice by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) onText(said)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showVoice = true else denied = true
    }
    if (showVoice) {
        VoiceCaptureDialog(
            onText = { said ->
                showVoice = false
                onText(said)
            },
            onDismiss = { showVoice = false },
            onUseSystemRecognizer = {
                showVoice = false
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                }
                runCatching { system.launch(intent) }
            },
        )
    }
    if (denied) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { denied = false },
            text = { androidx.compose.material3.Text("برای گفتن، اجازهٔ میکروفون لازم است.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { denied = false }) { androidx.compose.material3.Text("باشه") }
            },
        )
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            showVoice = true
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** The small microphone button shown at the end of every text box. */
@Composable
fun MicButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Mic, contentDescription = "گفتن با میکروفون")
    }
}

/** Joins a spoken sentence to what is already typed. */
fun appendSpoken(old: String, said: String): String {
    val clean = said.trim()
    if (clean.isEmpty()) return old
    return if (old.isBlank()) clean else old.trimEnd() + " " + clean
}
