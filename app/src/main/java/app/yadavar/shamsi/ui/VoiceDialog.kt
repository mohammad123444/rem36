package app.yadavar.shamsi.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yadavar.shamsi.voice.MicrophoneUnavailable
import app.yadavar.shamsi.voice.OfflineSpeech
import java.util.concurrent.atomic.AtomicBoolean

private enum class VoicePhase { AskDownload, Downloading, Loading, Listening, Processing, Failed, Silent }

/**
 * The microphone dialog: downloads the offline model once (with the person's OK), then listens and recognizes
 * Persian speech completely offline. [onText] gets the recognized sentence.
 */
@Composable
fun VoiceCaptureDialog(onText: (String) -> Unit, onDismiss: () -> Unit, onUseSystemRecognizer: () -> Unit) {
    val context = LocalContext.current
    val ready = remember { OfflineSpeech.isReady(context) }
    var phase by remember { mutableStateOf(if (ready) VoicePhase.Loading else VoicePhase.AskDownload) }
    var progress by remember { mutableFloatStateOf(0f) }
    var level by remember { mutableFloatStateOf(0f) }
    // Counters (not the phase) start the two jobs, so a job is never cancelled by its own phase changes.
    var downloadRun by remember { mutableStateOf(0) }
    var listenRun by remember { mutableStateOf(if (ready) 1 else 0) }
    val stopFlag = remember { AtomicBoolean(false) }

    // Download, once the person agreed.
    LaunchedEffect(downloadRun) {
        if (downloadRun == 0) return@LaunchedEffect
        phase = VoicePhase.Downloading
        val ok = OfflineSpeech.download(context) { progress = it }
        if (ok) {
            phase = VoicePhase.Loading
            listenRun += 1
        } else {
            phase = VoicePhase.Failed
        }
    }
    // Listen and recognize.
    LaunchedEffect(listenRun) {
        if (listenRun == 0) return@LaunchedEffect
        stopFlag.set(false)
        phase = VoicePhase.Loading
        val result = runCatching {
            phase = VoicePhase.Listening
            OfflineSpeech.listen(
                context = context,
                stop = { stopFlag.get() },
                onLevel = { level = it },
                onProcessing = { phase = VoicePhase.Processing },
            )
        }
        val text = result.getOrNull()
        when {
            result.isFailure -> {
                // Damaged model files: remove them so the next try downloads again.
                val problem = result.exceptionOrNull()
                if (problem !is kotlinx.coroutines.CancellationException) {
                    if (problem is Exception && problem !is MicrophoneUnavailable) OfflineSpeech.reset(context)
                    phase = VoicePhase.Failed
                }
            }
            text.isNullOrBlank() -> phase = VoicePhase.Silent
            else -> onText(text)
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathing by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "breathing",
    )
    val loud by animateFloatAsState(level, tween(120), label = "level")

    AlertDialog(
        onDismissRequest = {
            stopFlag.set(true)
            onDismiss()
        },
        title = {
            Text(
                when (phase) {
                    VoicePhase.AskDownload -> "دانلود مدل گفتار فارسی"
                    VoicePhase.Downloading -> "در حال دانلود مدل…"
                    VoicePhase.Loading -> "آماده‌سازی…"
                    VoicePhase.Listening -> "بگو…"
                    VoicePhase.Processing -> "در حال تبدیل گفتار به متن…"
                    VoicePhase.Failed -> "انجام نشد"
                    VoicePhase.Silent -> "چیزی نشنیدم"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (phase) {
                    VoicePhase.AskDownload -> Text(
                        "برای تشخیص گفتار فارسی کاملاً آفلاین (مدل Shenava)، یک بار حدود ۱۲۰ مگابایت دانلود می‌شود. بهتر است به وای‌فای وصل باشی. بعد از آن بدون اینترنت کار می‌کند.",
                        textAlign = TextAlign.Center,
                    )
                    VoicePhase.Downloading -> {
                        if (progress in 0f..1f && progress > 0f) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("${app.yadavar.shamsi.model.faNum((progress * 100).toInt())}٪")
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    VoicePhase.Listening -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(110.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .scale(breathing + loud * 0.35f)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Text("وقتی حرفت تمام شد، خودکار تشخیص می‌دهد.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    VoicePhase.Loading, VoicePhase.Processing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    VoicePhase.Failed -> Text(
                        "مدل آفلاین آماده نشد (اینترنت یا اجازهٔ میکروفون را بررسی کن). می‌توانی از تشخیص گفتار خود گوشی استفاده کنی.",
                        textAlign = TextAlign.Center,
                    )
                    VoicePhase.Silent -> Text("دوباره امتحان کن و کمی نزدیک‌تر به میکروفون بگو.", textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            when (phase) {
                VoicePhase.AskDownload -> TextButton(onClick = { downloadRun += 1 }) { Text("دانلود") }
                VoicePhase.Listening -> TextButton(onClick = { stopFlag.set(true) }) { Text("تمام") }
                VoicePhase.Failed -> TextButton(onClick = onUseSystemRecognizer) { Text("تشخیص گفتار گوشی") }
                VoicePhase.Silent -> TextButton(onClick = { listenRun += 1 }) { Text("دوباره") }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = {
                stopFlag.set(true)
                onDismiss()
            }) { Text("انصراف") }
        },
    )
}
