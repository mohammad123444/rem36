package app.yadavar.shamsi.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yadavar.shamsi.model.KIND_SHARED
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.shared.Api
import app.yadavar.shamsi.shared.ApiException
import app.yadavar.shamsi.shared.Crypto
import app.yadavar.shamsi.shared.OfflineException
import app.yadavar.shamsi.shared.SharedGraph
import app.yadavar.shamsi.shared.SharedItem
import app.yadavar.shamsi.shared.SharedPrefs
import app.yadavar.shamsi.shared.SharedSpace
import app.yadavar.shamsi.shared.SharedStore
import app.yadavar.shamsi.shared.SyncStatus
import kotlinx.coroutines.launch

private fun errText(e: Throwable): String = when (e) {
    is OfflineException -> "به سرور وصل نشد: اینترنت را بررسی کن یا آدرس سرور اشتباه است."
    is ApiException -> e.message ?: "خطا"
    else -> "خطا: ${e.javaClass.simpleName}"
}

@Composable
private fun Page(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

/** The shared-reminders area: server setup → login → groups → one group. */
@Composable
fun SharedHome(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { SharedGraph.init(context); true }
    val prefs = SharedGraph.prefs
    var configured by remember { mutableStateOf(prefs.configured) }
    var signedIn by remember { mutableStateOf(prefs.signedIn) }
    var openSpace by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    when {
        !configured || settingsOpen -> ServerSetup(
            onDone = { configured = true; settingsOpen = false },
            onBack = { if (configured && settingsOpen) settingsOpen = false else onBack() },
        )
        !signedIn -> AuthPage(onDone = { signedIn = true }, onBack = onBack, onServer = { settingsOpen = true })
        openSpace != null -> SpacePage(openSpace!!, onBack = { openSpace = null })
        else -> SpacesPage(
            onBack = onBack,
            onOpen = { openSpace = it },
            onServer = { settingsOpen = true },
            onSignedOut = { signedIn = false },
        )
    }
}

@Composable
private fun ServerSetup(onDone: () -> Unit, onBack: () -> Unit) {
    val prefs = SharedGraph.prefs
    var url by remember { mutableStateOf(prefs.url) }
    var key by remember { mutableStateOf(prefs.anonKey) }
    var error by remember { mutableStateOf<String?>(null) }
    Page("تنظیم سرور", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "بخش اشتراکی به یک پروژهٔ Supabase (رایگان) نیاز دارد که خودت می‌سازی. داده‌ها قبل از ارسال روی گوشی رمز می‌شوند. راهنمای ساخت در فایل supabase/SETUP-FA.md پروژه هست.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(url, { url = it }, label = { Text("Project URL (https://….supabase.co)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(key, { key = it }, label = { Text("کلید anon public") }, modifier = Modifier.fillMaxWidth())
            Text("کلید service_role را هرگز اینجا نگذار؛ برنامه آن را رد می‌کند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val u = url.trim().trimEnd('/')
                    val problem = SharedPrefs.keyProblem(key)
                    error = when {
                        !u.startsWith("https://") || !u.contains(".") -> "آدرس باید با https:// شروع شود."
                        problem != null -> problem
                        else -> null
                    }
                    if (error == null) {
                        if (u != prefs.url || key.trim() != prefs.anonKey) prefs.signOut()
                        prefs.url = u
                        prefs.anonKey = key.trim()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره") }
        }
    }
}

@Composable
private fun AuthPage(onDone: () -> Unit, onBack: () -> Unit, onServer: () -> Unit) {
    val prefs = SharedGraph.prefs
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(prefs.email) }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(prefs.displayName) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun run(register: Boolean) {
        if (email.isBlank() || password.length < 6 || name.isBlank()) {
            message = "نام، ایمیل و رمز عبور (حداقل ۶ نویسه) را پر کن."
            return
        }
        busy = true
        message = null
        scope.launch {
            try {
                prefs.displayName = name.trim()
                if (register) SharedGraph.api.signUp(email.trim(), password) else SharedGraph.api.signIn(email.trim(), password)
                SharedGraph.schedulePeriodic(context)
                onDone()
            } catch (e: Exception) {
                message = errText(e)
            }
            busy = false
        }
    }

    Page("ورود به بخش اشتراکی", onBack, actions = { TextButton(onClick = onServer) { Text("سرور") } }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("برای کار در گروه‌ها یک حساب لازم است. یادآورهای شخصی تو هیچ‌وقت به این حساب ارسال نمی‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("نامی که اعضای گروه می‌بینند") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("ایمیل") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text("رمز عبور") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { run(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "صبر کن…" else "ورود") }
            OutlinedButton(onClick = { run(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ثبت‌نام (حساب جدید)") }
        }
    }
}

@Composable
private fun StatusLine(status: SyncStatus) {
    val color = when (status.kind) {
        SyncStatus.Kind.Error -> MaterialTheme.colorScheme.error
        SyncStatus.Kind.Offline -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(status.text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun SpacesPage(onBack: () -> Unit, onOpen: (String) -> Unit, onServer: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spaces by SharedGraph.store.spaces.collectAsState()
    val status by SharedGraph.status.collectAsState()
    var createOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var signOutOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        SharedGraph.schedulePeriodic(context)
        SharedGraph.syncAll()
    }

    Page(
        "یادآورهای اشتراکی", onBack,
        actions = {
            IconButton(onClick = { scope.launch { SharedGraph.syncAll() } }) { Icon(Icons.Filled.Sync, contentDescription = "همگام‌سازی") }
            TextButton(onClick = onServer) { Text("سرور") }
            TextButton(onClick = { signOutOpen = true }) { Text("خروج") }
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusLine(status)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { createOpen = true }) { Text("ساخت گروه") }
                OutlinedButton(onClick = { joinOpen = true }) { Text("پیوستن با کد") }
            }
            if (spaces.isEmpty()) {
                Text("هنوز در هیچ گروهی نیستی. یک گروه بساز یا با کد دعوت بپیوند.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(spaces, key = { it.id }) { space ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(space.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(space.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (space.role == "owner") "مالک" else "عضو", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        var name by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) createOpen = false },
            title = { Text("ساخت گروه") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("نام گروه") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val id = SharedGraph.api.createSpace(name.trim(), SharedGraph.prefs.displayName)
                                val key = Crypto.newGroupKey()
                                SharedGraph.store.upsertSpace(SharedSpace(id, name.trim(), "owner", Crypto.protect(key), 0))
                                message = null
                                createOpen = false
                                onOpen(id)
                            } catch (e: Exception) {
                                message = errText(e)
                                createOpen = false
                            }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "…" else "بساز") }
            },
            dismissButton = { TextButton(onClick = { createOpen = false }, enabled = !busy) { Text("لغو") } },
        )
    }

    if (joinOpen) {
        var code by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) joinOpen = false },
            title = { Text("پیوستن با کد دعوت") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("متن دعوت را که برایت فرستاده‌اند اینجا بچسبان (با ys1 شروع می‌شود).", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(code, { code = it }, label = { Text("کد دعوت") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = code.isNotBlank() && !busy,
                    onClick = {
                        val parts = code.trim().split(".")
                        val key = if (parts.size == 3 && parts[0] == "ys1") runCatching { Crypto.unb64(parts[2]) }.getOrNull() else null
                        if (key == null || key.size != 32) {
                            message = "کد دعوت درست نیست؛ باید کامل و با ys1 شروع شود."
                            joinOpen = false
                        } else {
                            busy = true
                            scope.launch {
                                try {
                                    val (id, name) = SharedGraph.api.joinSpace(parts[1], SharedGraph.prefs.displayName)
                                    SharedGraph.store.upsertSpace(SharedSpace(id, name, "member", Crypto.protect(key), 0))
                                    message = null
                                    joinOpen = false
                                    SharedGraph.syncAll()
                                    onOpen(id)
                                } catch (e: Exception) {
                                    message = errText(e)
                                    joinOpen = false
                                }
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) "…" else "بپیوند") }
            },
            dismissButton = { TextButton(onClick = { joinOpen = false }, enabled = !busy) { Text("لغو") } },
        )
    }

    if (signOutOpen) {
        AlertDialog(
            onDismissRequest = { signOutOpen = false },
            title = { Text("خروج از حساب؟") },
            text = { Text("نسخهٔ محلی گروه‌ها و کلیدهای رمز از این گوشی پاک می‌شود. برای دسترسی دوباره باید دوباره وارد شوی و با کد دعوت بپیوندی. یادآورهای شخصی دست نمی‌خورند.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        SharedGraph.store.clearAll()
                        SharedGraph.prefs.signOut()
                        SharedGraph.cancelPeriodic(context)
                        signOutOpen = false
                        onSignedOut()
                    }
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { signOutOpen = false }) { Text("لغو") } },
        )
    }
}

private fun dueText(r: Reminder): String {
    val d = if (r.jy != null && r.jm != null && r.jd != null) "${r.jy}/${r.jm}/${r.jd}" else ""
    val t = if (r.hour != null && r.minute != null) String.format("%02d:%02d", r.hour, r.minute) else ""
    return listOf(d, t).filter { it.isNotEmpty() }.joinToString("  ")
}

@Composable
private fun SpacePage(spaceId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val spaces by SharedGraph.store.spaces.collectAsState()
    val allItems by SharedGraph.store.items.collectAsState()
    val status by SharedGraph.status.collectAsState()
    val space = spaces.firstOrNull { it.id == spaceId }
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    if (space == null) {
        // The group disappeared (left, deleted or removed by the owner).
        LaunchedEffect(Unit) { onBack() }
        return
    }

    fun write(r: Reminder, deleted: Boolean) {
        scope.launch {
            val now = System.currentTimeMillis()
            SharedGraph.store.saveItem(SharedItem(r.id, space.id, SharedStore.toJson(r), now, deleted, true))
            SharedGraph.syncAll()
        }
    }

    editing?.let { current ->
        EditorScreen(
            initial = current,
            onClose = { editing = null },
            onSave = { saved -> write(saved.copy(kind = KIND_SHARED, spaceId = space.id), false); editing = null },
            onDelete = if (editingIsNew) null else ({ write(current, true); editing = null }),
        )
        return
    }

    val reminders = allItems
        .filter { it.spaceId == space.id && !it.deleted }
        .mapNotNull { SharedStore.fromJson(it.id, it.spaceId, it.json, it.updatedAt) }
        .sortedWith(compareBy<Reminder> { it.completed }.thenBy { it.jy ?: 9999 }.thenBy { it.jm ?: 99 }.thenBy { it.jd ?: 99 }.thenBy { it.hour ?: 99 }.thenBy { it.minute ?: 99 })

    Page(
        space.name, onBack,
        actions = { IconButton(onClick = { scope.launch { SharedGraph.syncAll() } }) { Icon(Icons.Filled.Sync, contentDescription = "همگام‌سازی") } },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("یادآورها") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("اعضا و دعوت") })
                }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { StatusLine(status) }
                if (tab == 0) {
                    if (reminders.isEmpty()) {
                        Text("هنوز یادآوری‌ای در این گروه نیست. با دکمهٔ + یکی اضافه کن.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                    }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reminders, key = { it.id }) { r ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { editingIsNew = false; editing = r },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = r.completed,
                                        onCheckedChange = { done ->
                                            write(r.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null), false)
                                        },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title.ifBlank { "(بدون عنوان)" }, style = MaterialTheme.typography.titleSmall)
                                        val due = dueText(r)
                                        if (due.isNotEmpty()) Text(due, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(90.dp)) }
                    }
                } else {
                    MembersTab(space, onLeft = onBack)
                }
            }
            if (tab == 0) {
                FloatingActionButton(
                    onClick = { editingIsNew = true; editing = blankReminder("personal").copy(kind = KIND_SHARED, spaceId = space.id, alert = false) },
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "افزودن") }
            }
        }
    }
}

@Composable
private fun MembersTab(space: SharedSpace, onLeft: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<Api.Member>>(emptyList()) }
    var invites by remember { mutableStateOf<List<Api.InviteRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var inviteOpen by remember { mutableStateOf(false) }
    var newInvite by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) } // "leave" | "delete"
    val isOwner = space.role == "owner"
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(space.id, tick) {
        try {
            members = SharedGraph.api.members(space.id)
            invites = if (isOwner) SharedGraph.api.invites(space.id) else emptyList()
            message = null
        } catch (e: Exception) {
            message = errText(e)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("اعضا", style = MaterialTheme.typography.titleMedium)
        members.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((m.name.ifBlank { "بدون نام" }) + if (m.role == "owner") " (مالک)" else "", modifier = Modifier.weight(1f))
                if (isOwner && m.userId != SharedGraph.prefs.userId) {
                    TextButton(onClick = {
                        scope.launch {
                            try { SharedGraph.api.removeMember(space.id, m.userId); tick++ } catch (e: Exception) { message = errText(e) }
                        }
                    }) { Text("بیرون‌کردن") }
                }
            }
        }
        if (isOwner) {
            Text("دعوت‌ها", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { inviteOpen = true }) { Text("ساخت لینک/کد دعوت") }
            invites.forEach { inv ->
                val state = when {
                    inv.revoked -> "لغوشده"
                    inv.uses >= inv.maxUses -> "پر شده"
                    else -> "فعال تا ${inv.expiresAt.take(16).replace('T', ' ')} (UTC)"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("…${inv.code.takeLast(4)} — $state", style = MaterialTheme.typography.bodyMedium)
                        Text("استفاده: ${inv.uses} از ${inv.maxUses}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!inv.revoked) {
                        TextButton(onClick = {
                            scope.launch {
                                try { SharedGraph.api.revokeInvite(inv.code); tick++ } catch (e: Exception) { message = errText(e) }
                            }
                        }) { Text("لغو") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { confirm = "leave" }, modifier = Modifier.fillMaxWidth()) { Text("ترک گروه") }
        if (isOwner) {
            OutlinedButton(onClick = { confirm = "delete" }, modifier = Modifier.fillMaxWidth()) { Text("حذف کامل گروه برای همه") }
        }
    }

    if (inviteOpen) {
        var hours by remember { mutableStateOf(24) }
        var uses by remember { mutableStateOf(1) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) inviteOpen = false },
            title = { Text("دعوت جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اعتبار:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(24 to "۱ روز", 72 to "۳ روز", 168 to "۷ روز").forEach { (h, label) ->
                            if (hours == h) Button(onClick = { hours = h }) { Text(label) } else OutlinedButton(onClick = { hours = h }) { Text(label) }
                        }
                    }
                    Text("تعداد دفعات استفاده:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 5, 20).forEach { n ->
                            if (uses == n) Button(onClick = { uses = n }) { Text("$n") } else OutlinedButton(onClick = { uses = n }) { Text("$n") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val code = SharedGraph.api.createInvite(space.id, hours, uses)
                                val key = SharedGraph.store.key(space)
                                if (key == null) message = "کلید گروه روی این گوشی خوانده نشد." else newInvite = "ys1.$code.${Crypto.b64(key)}"
                                tick++
                            } catch (e: Exception) {
                                message = errText(e)
                            }
                            inviteOpen = false
                            busy = false
                        }
                    },
                ) { Text(if (busy) "…" else "بساز") }
            },
            dismissButton = { TextButton(onClick = { inviteOpen = false }, enabled = !busy) { Text("لغو") } },
        )
    }

    newInvite?.let { text ->
        val shareText = "دعوت به گروه «${space.name}» در یادآور شمسی.\nدر برنامه: یادآورهای اشتراکی ← پیوستن با کد، و این متن را بچسبان:\n$text"
        AlertDialog(
            onDismissRequest = { newInvite = null },
            title = { Text("دعوت آماده است") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text, style = MaterialTheme.typography.bodySmall)
                    Text("هر کس این متن را داشته باشد می‌تواند عضو شود و یادآورها را بخواند. فقط برای افراد مورد اعتماد بفرست؛ هر زمان خواستی از لیست دعوت‌ها «لغو» کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
                    context.startActivity(Intent.createChooser(send, "ارسال دعوت"))
                }) { Text("ارسال") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(shareText)) }) { Text("کپی") }
                    TextButton(onClick = { newInvite = null }) { Text("بستن") }
                }
            },
        )
    }

    confirm?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (kind == "leave") "ترک گروه؟" else "حذف گروه برای همه؟") },
            text = {
                Text(
                    if (kind == "leave") "یادآورهای این گروه از این گوشی پاک می‌شوند. برای برگشتن به دعوت جدید نیاز داری."
                    else "همهٔ یادآورهای گروه برای همهٔ اعضا از سرور پاک می‌شود و برگشت ندارد.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            if (kind == "leave") SharedGraph.api.leaveSpace(space.id) else SharedGraph.api.deleteSpace(space.id)
                            SharedGraph.store.removeSpace(space.id)
                            confirm = null
                            onLeft()
                        } catch (e: Exception) {
                            message = errText(e)
                            confirm = null
                        }
                    }
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("لغو") } },
        )
    }
}
