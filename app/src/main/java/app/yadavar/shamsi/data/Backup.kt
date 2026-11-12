package app.yadavar.shamsi.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.yadavar.shamsi.Graph
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves every reminder (active and completed) to one JSON file the user chose with the system file picker.
 * That file lives outside the app, so it survives uninstalling and can be restored later.
 */
object BackupManager {
    private const val FORMAT = 1

    private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)

    private fun ReminderEntity.toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("items", itemsJson)
        obj.put("jy", jy ?: JSONObject.NULL)
        obj.put("jm", jm ?: JSONObject.NULL)
        obj.put("jd", jd ?: JSONObject.NULL)
        obj.put("hour", hour ?: JSONObject.NULL)
        obj.put("minute", minute ?: JSONObject.NULL)
        obj.put("location", location)
        obj.put("groupId", groupId)
        obj.put("starred", starred)
        obj.put("alert", alert)
        obj.put("repeatKind", repeatKind)
        obj.put("repeatEvery", repeatEvery)
        obj.put("completed", completed)
        obj.put("completedAt", completedAt ?: JSONObject.NULL)
        obj.put("expanded", expanded)
        obj.put("snoozeUntil", snoozeUntil ?: JSONObject.NULL)
        obj.put("dismissedKey", dismissedKey ?: JSONObject.NULL)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        obj.put("anchorDay", anchorDay ?: JSONObject.NULL)
        obj.put("leadMinutes", leadMinutes ?: JSONObject.NULL)
        return obj
    }

    private fun JSONObject.toEntity(): ReminderEntity {
        val now = System.currentTimeMillis()
        return ReminderEntity(
            id = getString("id"),
            title = optString("title", ""),
            itemsJson = optString("items", "[]"),
            jy = intOrNull("jy"),
            jm = intOrNull("jm"),
            jd = intOrNull("jd"),
            hour = intOrNull("hour"),
            minute = intOrNull("minute"),
            location = optString("location", ""),
            groupId = optString("groupId", "personal"),
            starred = optBoolean("starred", false),
            alert = optBoolean("alert", true),
            repeatKind = optString("repeatKind", "none"),
            repeatEvery = optInt("repeatEvery", 1),
            completed = optBoolean("completed", false),
            completedAt = longOrNull("completedAt"),
            expanded = optBoolean("expanded", false),
            snoozeUntil = longOrNull("snoozeUntil"),
            dismissedKey = stringOrNull("dismissedKey"),
            createdAt = optLong("createdAt", now),
            updatedAt = optLong("updatedAt", now),
            anchorDay = intOrNull("anchorDay"),
            leadMinutes = intOrNull("leadMinutes"),
        )
    }

    private suspend fun buildJson(): String {
        val reminders = Graph.repo.all()
        val array = JSONArray()
        reminders.forEach { array.put(it.toEntity().toJson()) }
        val root = JSONObject()
        root.put("app", "yadavar-shamsi")
        root.put("format", FORMAT)
        root.put("savedAt", System.currentTimeMillis())
        root.put("reminders", array)
        return root.toString(2)
    }

    suspend fun writeTo(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildJson().toByteArray(Charsets.UTF_8)
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
            out.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    private const val INTERNAL_NAME = "yadavar-backup.json"
    private const val INTERNAL_DIR = "Documents/YadavarShamsi"

    /**
     * Keeps a second, automatic copy in the phone's internal storage: Documents/YadavarShamsi/yadavar-backup.json
     * (Android 10+ through MediaStore, no permission needed; older phones use the app's own storage folder).
     */
    suspend fun writeInternalCopy(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildJson().toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                var uri: Uri? = null
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                    arrayOf(INTERNAL_NAME, "$INTERNAL_DIR%"),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
                if (uri == null) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, INTERNAL_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, INTERNAL_DIR)
                    }
                    uri = resolver.insert(collection, values)
                }
                val target = uri ?: return@runCatching false
                val out = resolver.openOutputStream(target, "wt") ?: return@runCatching false
                out.use { it.write(bytes) }
            } else {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                File(dir, INTERNAL_NAME).writeBytes(bytes)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Writes the chosen backup file. The automatic runs skip an empty list, so a fresh install
     * can never wipe an existing backup before it has been restored.
     */
    suspend fun autoBackup(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && Graph.repo.all().isEmpty()) return@withContext false
        // Always keep the copy in the phone's internal storage, with or without a chosen file.
        writeInternalCopy(context)
        val target = Graph.settings.current().backupUri
        if (target.isBlank()) return@withContext false
        val ok = writeTo(context, Uri.parse(target))
        if (ok) Graph.settings.setBackupAt(System.currentTimeMillis())
        ok
    }

    /** Restores reminders from a backup file; a reminder that is newer in the app is kept. Returns the count, or -1. */
    suspend fun importFrom(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@withContext -1
        val array = JSONObject(text).getJSONArray("reminders")
        var restored = 0
        for (index in 0 until array.length()) {
            val reminder = array.getJSONObject(index).toEntity().toModel()
            val existing = Graph.repo.get(reminder.id)
            if (existing == null || existing.updatedAt <= reminder.updatedAt) {
                Graph.repo.save(reminder)
                restored++
            }
        }
        Graph.refresh()
        restored
    }
}
