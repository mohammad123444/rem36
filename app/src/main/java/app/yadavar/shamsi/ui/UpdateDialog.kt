package app.yadavar.shamsi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.yadavar.shamsi.model.faNum
import app.yadavar.shamsi.update.UpdateController
import app.yadavar.shamsi.update.UpdateState

/** The dialogs of the in-app update: new version found, downloading, install permission, answers. */
@Composable
fun UpdateDialogs(controller: UpdateController) {
    when (val state = controller.state) {
        UpdateState.Idle, UpdateState.Checking -> Unit
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("نسخهٔ جدید آماده است") },
            text = { Text("یک نسخهٔ تازهٔ برنامه پیدا شد. دانلود و نصب شود؟ یادآورهای تو سر جایشان می‌مانند.") },
            confirmButton = { TextButton(onClick = { controller.download(state.signature) }) { Text("دانلود و نصب") } },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("بعداً") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("در حال دانلود…") },
            text = {
                Column {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${faNum((state.progress * 100).toInt())}٪")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
        )
        UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("اجازهٔ نصب") },
            text = { Text("برای نصب نسخهٔ جدید، در صفحهٔ باز شده اجازه بده این برنامه نصب کند؛ بعد برگرد و «نصب» را بزن.") },
            confirmButton = { TextButton(onClick = controller::install) { Text("نصب") } },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("بستن") } },
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = controller::dismiss,
            text = { Text("برنامه به‌روز است.") },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text("باشه") } },
        )
        UpdateState.Failed -> AlertDialog(
            onDismissRequest = controller::dismiss,
            text = { Text("به‌روزرسانی انجام نشد. اینترنت را بررسی کن و دوباره امتحان کن.") },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text("باشه") } },
        )
    }
}
