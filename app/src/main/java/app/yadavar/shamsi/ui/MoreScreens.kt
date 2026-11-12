package app.yadavar.shamsi.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateContentSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.yadavar.shamsi.widget.ClockAlignOptions
import app.yadavar.shamsi.widget.ClockSizeOptions
import app.yadavar.shamsi.model.faNum
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import java.util.Date
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.formatTime
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.alarm.AlarmSound
import app.yadavar.shamsi.widget.WidgetBackgrounds
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf
import kotlinx.coroutines.delay

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(padding: PaddingValues, settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    val exact = AlarmScheduler.canExact(context)
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.6"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .animateContentSize(softTween()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text("تنظیمات", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())

        // Appearance and color of the app: one section.
        SectionTitle("ظاهر و رنگ برنامه")
        ChipFlow {
            listOf("system" to "خودکار", "light" to "روشن", "dark" to "تیره").forEach { (id, label) ->
                FilterChip(selected = settings.theme == id, onClick = { vm.setTheme(id) }, label = { Text(label) })
            }
        }
        ChipFlow {
            AccentOptions.forEach { option ->
                ColorChip(option.label, option.color, settings.accent == option.id) { vm.setAccent(option.id) }
            }
        }
        SoftDivider()

        SectionTitle("رنگ نوشته‌های برنامه")
        ChipFlow {
            TextColorOptions.forEach { option ->
                ColorChip(option.label, option.color, settings.appText == option.id) { vm.setAppText(option.id) }
            }
        }
        SoftDivider()

        SectionTitle("اندازهٔ نوشته‌های برنامه")
        ChipFlow {
            AppFontOptions.forEach { (id, label) ->
                FilterChip(selected = settings.appFont == id, onClick = { vm.setAppFont(id) }, label = { Text(label) })
            }
        }
        SoftDivider()

        SectionTitle("ویجت اصلی: اندازهٔ نوشته")
        ChipFlow {
            listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ").forEach { (id, label) ->
                FilterChip(selected = settings.widgetFont == id, onClick = { vm.setFont(id) }, label = { Text(label) })
            }
        }
        SectionTitle("ویجت اصلی: رنگ پس‌زمینه")
        ChipFlow {
            WidgetBackgrounds.forEach { option ->
                ColorChip(option.label, app.yadavar.shamsi.widget.widgetPalette(option.id).bg, settings.widgetBg == option.id) { vm.setWidgetBg(option.id) }
            }
        }
        SectionTitle("ویجت اصلی: رنگ نوشته‌ها")
        ChipFlow {
            TextColorOptions.forEach { option ->
                ColorChip(option.label, option.color, settings.widgetText == option.id) { vm.setWidgetText(option.id) }
            }
        }
        SoftDivider()

        SectionTitle("ویجت تاریخ و ساعت (بدون پس‌زمینه)")
        Text(
            "ویجت دوم را از فهرست ویجت‌های گوشی با نام «ساعت و تاریخ شمسی» اضافه کن.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("اندازهٔ نوشته", style = MaterialTheme.typography.labelLarge)
        ChipFlow {
            ClockSizeOptions.forEach { (id, label) ->
                FilterChip(selected = settings.clockSize == id, onClick = { vm.setClockSize(id) }, label = { Text(label) })
            }
        }
        Text("محل قرارگیری در کادر", style = MaterialTheme.typography.labelLarge)
        ChipFlow {
            ClockAlignOptions.forEach { (id, label) ->
                FilterChip(selected = settings.clockAlign == id, onClick = { vm.setClockAlign(id) }, label = { Text(label) })
            }
        }
        Text("رنگ نوشته", style = MaterialTheme.typography.labelLarge)
        ChipFlow {
            TextColorOptions.filter { it.color != null }.forEach { option ->
                ColorChip(option.label, option.color, settings.clockColor == option.id) { vm.setClockColor(option.id) }
            }
        }
        SoftDivider()

        AlarmModeSection(settings, vm)
        SoftDivider()
        AlarmSoundSection(settings, vm)
        SoftDivider()
        SecuritySection(settings, vm)
        SoftDivider()
        UpdateSection(vm)
        SoftDivider()
        BackupSection(settings, vm)
        SoftDivider()
        BatterySection()
        SoftDivider()
        Text(if (exact) "زنگ دقیق روشن است." else "برای زنگ سر وقت دقیق، اجازهٔ هشدار دقیق را بده.", modifier = Modifier.fillMaxWidth())
        ChipFlow {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 31) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }
            }) { Text("اجازهٔ زنگ دقیق") }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                }
            }) { Text("اعلان‌ها") }
        }
        SoftDivider()
        Text(
            "یادآورها روی همین گوشی می‌مانند. زنگ با AlarmManager است و بعد از خاموش و روشن شدن و بعد از به‌روزرسانی برنامه دوباره تنظیم می‌شود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        SoftDivider()
        // Last items of the page, centered.
        Text(
            "برنامه نویسان: محمد یگانه - مصطفی ابراهیمی",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "ورژن " + faNum(versionName),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Battery: the app must be "Unrestricted" so alarms and the widget run on time. */
@Composable
private fun BatterySection() {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(BatterySettings.isUnrestricted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = BatterySettings.isUnrestricted(context) }
    SectionTitle("بهینه‌سازی باتری")
    Text(
        if (unrestricted) "باتری روی «بدون محدودیت» است؛ زنگ‌ها بدون تأخیر کار می‌کنند ✓"
        else "برای زنگ بدون تأخیر، باتری برنامه را روی «بدون محدودیت» (Unrestricted) بگذار.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().animateContentSize(softTween()),
        textAlign = TextAlign.Center,
    )
    ChipFlow {
        if (!unrestricted) {
            Button(onClick = { BatterySettings.requestUnrestricted(context) }) { Text("اجازهٔ اجرای بدون محدودیت") }
        }
        OutlinedButton(onClick = { BatterySettings.openAppBattery(context) }) { Text("تنظیمات باتری برنامه") }
    }
}

private fun fileLabel(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name ?: "فایل پشتیبان"
}

private fun backupTime(millis: Long): String {
    val moment = Date(millis)
    val cal = Calendar.getInstance()
    cal.time = moment
    return formatLongDate(toJalali(moment)) + " ساعت " + formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

private const val DRIVE_PACKAGE = "com.google.android.apps.docs"

private fun isDriveFile(uri: String): Boolean = uri.contains(DRIVE_PACKAGE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupSection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    val chosen = settings.backupUri.isNotBlank()
    val onDrive = chosen && isDriveFile(settings.backupUri)
    val driveInstalled = remember {
        runCatching { context.packageManager.getLaunchIntentForPackage(DRIVE_PACKAGE) != null }.getOrDefault(false)
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        vm.setBackupFile(uri.toString())
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        vm.restoreFrom(uri)
    }
    val pickFile: () -> Unit = {
        create.launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "yadavar-backup.json")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            },
        )
    }

    SectionTitle("پشتیبان‌گیری خودکار")

    // Google Drive link card.
    SectionBox {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (onDrive) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("گوگل درایو", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            when {
                onDrive -> "متصل است ✓  هر تغییر چند ثانیه بعد در درایو ذخیره می‌شود."
                chosen -> "فایل انتخاب‌شده روی گوگل درایو نیست. برای اتصال به درایو دوباره انتخاب کن و در پنجره، «Drive» را بزن."
                else -> "هنوز به گوگل درایو وصل نشده است."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.animateContentSize(softTween()),
        )
        if (!onDrive) {
            Text(
                "۱) دکمهٔ «اتصال به گوگل درایو» را بزن.\n۲) در پنجره، از منو «Drive» (درایو) را انتخاب کن و حساب گوگل را بزن.\n۳) یک پوشه انتخاب و «ذخیره» را بزن؛ همین. از آن به بعد خودکار ذخیره می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chosen) {
            Text("فایل: " + fileLabel(context, Uri.parse(settings.backupUri)), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (settings.backupAt > 0L) "آخرین پشتیبان: " + backupTime(settings.backupAt) else "هنوز پشتیبان گرفته نشده است.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ChipFlow {
            Button(onClick = pickFile) { Text(if (onDrive) "تغییر پوشهٔ درایو" else "اتصال به گوگل درایو") }
            if (chosen) {
                OutlinedButton(onClick = { vm.backupNow() }) { Text("پشتیبان‌گیری هم‌اکنون") }
                TextButton(onClick = { vm.unlinkBackup() }) { Text("قطع اتصال") }
            }
            if (!driveInstalled) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$DRIVE_PACKAGE")))
                    }.onFailure {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$DRIVE_PACKAGE")))
                        }
                    }
                }) { Text("نصب برنامهٔ گوگل درایو") }
            }
        }
    }
    Text(
        "فایل پشتیبان بیرون از برنامه است و با حذف برنامه از بین نمی‌رود. جدا از آن، یک نسخهٔ خودکار هم همیشه در حافظهٔ داخلی گوشی می‌ماند: پوشهٔ Documents/YadavarShamsi (فایل yadavar-backup.json).",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(onClick = {
        restore.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
        )
    }) { Text("بازیابی از فایل پشتیبان") }
}

private fun displayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name ?: "آهنگ انتخاب‌شده"
}

@Composable
private fun AlarmSoundSection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun stopPreview() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        playing = false
    }

    DisposableEffect(Unit) { onDispose { stopPreview() } }
    LaunchedEffect(playing) {
        if (playing) {
            delay(8_000)
            stopPreview()
        }
    }

    val pickRingtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        stopPreview()
        if (uri == null) {
            vm.setAlarmSound("", "")
        } else {
            val title = runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrNull()
            vm.setAlarmSound(uri.toString(), title ?: "زنگ انتخاب‌شده")
        }
    }
    val pickSong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        stopPreview()
        vm.setAlarmSound(uri.toString(), displayName(context, uri))
    }

    SectionTitle("صدای هشدار")
    Text(
        settings.alarmSoundName.ifBlank { "پیش‌فرض (آلارم گوشی)" },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChipFlow {
        OutlinedButton(onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "صدای هشدار")
                if (settings.alarmSound.isNotBlank()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(settings.alarmSound))
                }
            }
            runCatching { pickRingtone.launch(intent) }
        }) { Text("زنگ‌های گوشی") }
        OutlinedButton(onClick = { runCatching { pickSong.launch(arrayOf("audio/*")) } }) { Text("آهنگ از حافظه") }
    }
    ChipFlow {
        OutlinedButton(onClick = {
            if (playing) {
                stopPreview()
            } else {
                val created = AlarmSound.create(context, settings.alarmSound)
                if (created != null) {
                    runCatching { created.setVolume(1f, 1f) }
                    runCatching { created.start() }
                    player = created
                    playing = true
                }
            }
        }) { Text(if (playing) "توقف" else "پخش آزمایشی") }
        if (settings.alarmSound.isNotBlank()) {
            OutlinedButton(onClick = {
                stopPreview()
                vm.setAlarmSound("", "")
            }) { Text("بازگشت به پیش‌فرض") }
        }
    }
    Text("مدت پخش", style = MaterialTheme.typography.labelLarge)
    ChipFlow {
        listOf(10 to "۱۰ ثانیه", 30 to "۳۰ ثانیه", 60 to "۶۰ ثانیه").forEach { (seconds, label) ->
            FilterChip(
                selected = settings.alarmSeconds == seconds,
                onClick = { vm.setAlarmSeconds(seconds) },
                label = { Text(label) },
            )
        }
    }
    Text(
        "صدا ملایم شروع می‌شود و تا پایان این مدت پخش می‌شود. با «انجام شد»، «به تعویق» یا «بستن» زودتر قطع می‌شود.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

/** Alarm style (silent / vibrate / ring) and the escalating alarm. */
@Composable
private fun AlarmModeSection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    SectionTitle("حالت آلارم")
    ChipFlow {
        listOf("ring" to "زنگ", "vibrate" to "ویبره", "silent" to "سایلنت").forEach { (id, label) ->
            FilterChip(selected = settings.alarmMode == id, onClick = { vm.setAlarmMode(id) }, label = { Text(label) })
        }
    }
    Text(
        when (settings.alarmMode) {
            "vibrate" -> "فقط ویبره؛ صدا پخش نمی‌شود."
            "silent" -> "بدون صدا و ویبره؛ فقط اعلان و صفحهٔ هشدار."
            else -> "صدا و ویبره."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text("آلارم افزایشی", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = settings.escalate,
            enabled = settings.alarmMode != "silent",
            onCheckedChange = { vm.setEscalate(it) },
        )
    }
    Text(
        "اگر «انجام شد»، «تعویق» یا «بستن» را نزنی، بعد از چند دقیقه دوباره (تا ۳ بار) و هر بار با صدای بلندتر زنگ می‌زند.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (settings.escalate && settings.alarmMode != "silent") {
        Text("فاصله تا زنگ بعدی", style = MaterialTheme.typography.labelLarge)
        ChipFlow {
            listOf(2 to "۲ دقیقه", 5 to "۵ دقیقه", 10 to "۱۰ دقیقه", 15 to "۱۵ دقیقه", 30 to "۳۰ دقیقه").forEach { (minutes, label) ->
                FilterChip(
                    selected = settings.escalateMinutes == minutes,
                    onClick = { vm.setEscalateMinutes(minutes) },
                    label = { Text(label) },
                )
            }
        }
    }
}

/** Fingerprint lock of the app. */
@Composable
private fun SecuritySection(settings: app.yadavar.shamsi.model.Settings, vm: HomeViewModel) {
    val context = LocalContext.current
    val available = remember { BiometricLock.canUse(context) }
    SectionTitle("قفل برنامه")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text("قفل با اثر انگشت", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = settings.biometricLock && available,
            enabled = available,
            onCheckedChange = { want ->
                if (!want) {
                    vm.setBiometricLock(false)
                } else if (context is androidx.fragment.app.FragmentActivity) {
                    // Turn it on only after the person proves the fingerprint works.
                    BiometricLock.authenticate(context, onSuccess = { vm.setBiometricLock(true) }, onFail = { })
                }
            },
        )
    }
    Text(
        if (available) "با روشن بودن این گزینه، برنامه موقع باز شدن اثر انگشت می‌خواهد."
        else "روی این گوشی اثر انگشت (یا قفل صفحه) تنظیم نشده است؛ اول آن را در تنظیمات گوشی فعال کن.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Manual check for a new version of the app. */
@Composable
private fun UpdateSection(vm: HomeViewModel) {
    SectionTitle("به‌روزرسانی برنامه")
    Text(
        "هر بار که برنامه باز می‌شود، خودکار نسخهٔ جدید بررسی می‌شود.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    OutlinedButton(onClick = { vm.updater.check(manual = true) }) { Text("بررسی نسخهٔ جدید") }
}
