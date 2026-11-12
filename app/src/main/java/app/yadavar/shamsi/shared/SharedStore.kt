package app.yadavar.shamsi.shared

import android.content.Context
import app.yadavar.shamsi.model.CheckItem
import app.yadavar.shamsi.model.KIND_SHARED
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class SharedSpace(val id: String, val name: String, val role: String, val keyProtected: String, val rev: Long)

/** [json] is the plain reminder (only ever on this phone); what goes to the server is its encrypted form. */
data class SharedItem(val id: String, val spaceId: String, val json: String, val updatedAt: Long, val deleted: Boolean, val dirty: Boolean)

/** Local copy of the shared groups, kept in a private file so the screens work offline. Personal reminders are never stored here. */
class SharedStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "shared_store.json")
    private val lock = Mutex()
    private val _spaces = MutableStateFlow<List<SharedSpace>>(emptyList())
    private val _items = MutableStateFlow<List<SharedItem>>(emptyList())
    val spaces: StateFlow<List<SharedSpace>> = _spaces
    val items: StateFlow<List<SharedItem>> = _items

    init {
        runCatching {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                _spaces.value = root.getJSONArray("spaces").let { a ->
                    (0 until a.length()).map { a.getJSONObject(it).let { o -> SharedSpace(o.getString("id"), o.getString("name"), o.getString("role"), o.getString("key"), o.optLong("rev", 0)) } }
                }
                _items.value = root.getJSONArray("items").let { a ->
                    (0 until a.length()).map {
                        a.getJSONObject(it).let { o -> SharedItem(o.getString("id"), o.getString("space"), o.getString("json"), o.getLong("at"), o.getBoolean("del"), o.getBoolean("dirty")) }
                    }
                }
            }
        }
    }

    private fun persist() {
        val root = JSONObject()
        root.put("spaces", JSONArray().also { a ->
            _spaces.value.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("role", it.role).put("key", it.keyProtected).put("rev", it.rev)) }
        })
        root.put("items", JSONArray().also { a ->
            _items.value.forEach { a.put(JSONObject().put("id", it.id).put("space", it.spaceId).put("json", it.json).put("at", it.updatedAt).put("del", it.deleted).put("dirty", it.dirty)) }
        })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    suspend fun mutate(block: (spaces: List<SharedSpace>, items: List<SharedItem>) -> Pair<List<SharedSpace>, List<SharedItem>>) {
        lock.withLock {
            val (s, i) = block(_spaces.value, _items.value)
            _spaces.value = s
            _items.value = i
            persist()
        }
    }

    suspend fun upsertSpace(space: SharedSpace) = mutate { s, i -> (s.filter { it.id != space.id } + space) to i }

    suspend fun removeSpace(id: String) = mutate { s, i -> s.filter { it.id != id } to i.filter { it.spaceId != id } }

    suspend fun clearAll() = mutate { _, _ -> emptyList<SharedSpace>() to emptyList() }

    suspend fun saveItem(item: SharedItem) = mutate { s, i -> s to (i.filter { it.id != item.id } + item) }

    fun key(space: SharedSpace): ByteArray? = Crypto.unprotect(space.keyProtected)

    companion object {
        fun toJson(r: Reminder): String = JSONObject().apply {
            put("title", r.title)
            put("items", JSONArray().also { a -> r.items.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("d", it.done)) } })
            r.jy?.let { put("jy", it) }; r.jm?.let { put("jm", it) }; r.jd?.let { put("jd", it) }
            r.hour?.let { put("h", it) }; r.minute?.let { put("mi", it) }
            put("loc", r.location)
            put("star", r.starred)
            put("done", r.completed)
            r.completedAt?.let { put("doneAt", it) }
            put("rk", r.repeat.kindName)
            put("re", r.repeat.everyCount)
            r.leadMinutes?.let { put("lead", it) }
            put("created", r.createdAt)
        }.toString()

        fun fromJson(id: String, spaceId: String, json: String, updatedAt: Long): Reminder? = runCatching {
            val o = JSONObject(json)
            val arr = o.optJSONArray("items") ?: JSONArray()
            Reminder(
                id = id,
                title = o.optString("title"),
                items = (0 until arr.length()).map { arr.getJSONObject(it).let { c -> CheckItem(c.getString("id"), c.getString("t"), c.getBoolean("d")) } },
                jy = if (o.has("jy")) o.getInt("jy") else null,
                jm = if (o.has("jm")) o.getInt("jm") else null,
                jd = if (o.has("jd")) o.getInt("jd") else null,
                hour = if (o.has("h")) o.getInt("h") else null,
                minute = if (o.has("mi")) o.getInt("mi") else null,
                location = o.optString("loc"),
                groupId = "personal",
                starred = o.optBoolean("star"),
                alert = false,
                repeat = Repeat.of(o.optString("rk", "none"), o.optInt("re", 1)),
                completed = o.optBoolean("done"),
                completedAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
                expanded = true,
                snoozeUntil = null,
                dismissedKey = null,
                createdAt = o.optLong("created", updatedAt),
                updatedAt = updatedAt,
                leadMinutes = if (o.has("lead")) o.getInt("lead") else null,
                kind = KIND_SHARED,
                spaceId = spaceId,
            )
        }.getOrNull()
    }
}
