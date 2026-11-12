package app.yadavar.shamsi.shared

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the screen shows about synchronisation (Persian text in [text]). */
data class SyncStatus(val kind: Kind, val text: String) {
    enum class Kind { Idle, Syncing, Ok, Offline, Error }
}

/** The shared-groups services: one place that holds the settings, the local store and the sync. */
object SharedGraph {
    private var _prefs: SharedPrefs? = null
    private var _store: SharedStore? = null
    private var _api: Api? = null
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus(SyncStatus.Kind.Idle, "هنوز همگام‌سازی نشده"))
    val status: StateFlow<SyncStatus> = _status

    fun init(context: Context) {
        if (_prefs != null) return
        val app = context.applicationContext
        _prefs = SharedPrefs(app)
        _store = SharedStore(app)
        _api = Api(_prefs!!)
    }

    val prefs get() = _prefs!!
    val store get() = _store!!
    val api get() = _api!!

    private fun fmtTime(): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())

    /** Sends local edits and fetches others' edits for every group. Safe to call often; never throws. */
    suspend fun syncAll(): SyncStatus = mutex.withLock {
        if (!prefs.configured || !prefs.signedIn) return@withLock SyncStatus(SyncStatus.Kind.Idle, "وارد نشده‌ای")
        _status.value = SyncStatus(SyncStatus.Kind.Syncing, "در حال همگام‌سازی…")
        val result = try {
            // Refresh the group list first (new groups appear, removed ones disappear).
            val remote = api.mySpaces()
            val local = store.spaces.value
            local.filter { l -> remote.none { it.first == l.id } }.forEach { store.removeSpace(it.id) }
            remote.forEach { (id, name, role) ->
                val old = store.spaces.value.firstOrNull { it.id == id }
                if (old != null) store.upsertSpace(old.copy(name = name, role = role))
            }
            store.spaces.value.forEach { syncSpace(it) }
            SyncStatus(SyncStatus.Kind.Ok, "همگام شد (${fmtTime()})")
        } catch (e: OfflineException) {
            val pending = store.items.value.count { it.dirty }
            SyncStatus(SyncStatus.Kind.Offline, if (pending > 0) "آفلاین هستی؛ $pending تغییر بعداً ارسال می‌شود" else "آفلاین هستی")
        } catch (e: ApiException) {
            SyncStatus(SyncStatus.Kind.Error, e.message ?: "خطا")
        } catch (e: Exception) {
            SyncStatus(SyncStatus.Kind.Error, "خطای ناشناخته: ${e.javaClass.simpleName}")
        }
        _status.value = result
        result
    }

    private suspend fun syncSpace(space: SharedSpace) {
        val key = store.key(space) ?: throw ApiException(0, "", "کلید رمز گروه «${space.name}» روی این گوشی خوانده نشد؛ دوباره با کد دعوت بپیوند.")
        // 1) push local edits. The server accepts an edit only if it is newer than what it has (last edit wins).
        var refetchAll = false
        for (item in store.items.value.filter { it.spaceId == space.id && it.dirty }) {
            val accepted = api.push(space.id, item.id, Crypto.encrypt(key, item.json), item.updatedAt, item.deleted)
            // Either way the local edit is no longer "waiting": if it was rejected, the newer remote version arrives in step 2.
            store.mutate { s, i -> s to i.map { if (it.id == item.id && it.updatedAt == item.updatedAt) it.copy(dirty = false) else it } }
            if (!accepted) refetchAll = true // the server has a newer edit: fetch everything again so it replaces ours
        }
        // 2) pull everything that changed on the server since the last time.
        var rev = if (refetchAll) 0L else (store.spaces.value.firstOrNull { it.id == space.id }?.rev ?: 0L)
        while (true) {
            val batch = api.pull(space.id, rev)
            if (batch.isEmpty()) break
            for (r in batch) {
                val plain = Crypto.decrypt(key, r.payload)
                val local = store.items.value.firstOrNull { it.id == r.id }
                // Keep a newer local edit that has not been sent yet; otherwise the server version wins.
                val keepLocal = local != null && local.dirty && local.updatedAt > r.updatedAt
                if (plain != null && !keepLocal) {
                    store.saveItem(SharedItem(r.id, space.id, plain, r.updatedAt, r.deleted, false))
                }
                rev = maxOf(rev, r.rev)
            }
            store.upsertSpace(store.spaces.value.first { it.id == space.id }.copy(rev = rev))
            if (batch.size < 500) break
        }
        // Old tombstones (deleted > 60 days ago and already synced) are dropped from the local copy.
        val cutoff = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        store.mutate { s, i -> s to i.filterNot { it.deleted && !it.dirty && it.updatedAt < cutoff } }
    }

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SharedSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork("shared_sync", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("shared_sync")
    }
}

/** Background sync (about every 15 minutes while the phone has internet; Android may delay it to save battery). */
class SharedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SharedGraph.init(applicationContext)
        return when (SharedGraph.syncAll().kind) {
            SyncStatus.Kind.Offline -> Result.retry()
            else -> Result.success()
        }
    }
}
