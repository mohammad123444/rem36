package app.yadavar.shamsi.shared

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The server cannot be reached (no internet, wrong address, timeout). */
class OfflineException(cause: Throwable) : IOException(cause)

/** The server answered with an error. [message] is meant for the person (Persian). */
class ApiException(val code: Int, val raw: String, message: String) : Exception(message)

/** Server address, public (anon) key and login tokens. The anon key is public by design; the data is protected by the RLS rules. */
class SharedPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("shared_prefs", Context.MODE_PRIVATE)

    var url: String
        get() = p.getString("url", "") ?: ""
        set(v) = p.edit().putString("url", v.trim().trimEnd('/')).apply()
    var anonKey: String
        get() = p.getString("anon", "") ?: ""
        set(v) = p.edit().putString("anon", v.trim()).apply()
    var access: String
        get() = p.getString("access", "") ?: ""
        set(v) = p.edit().putString("access", v).apply()
    var refresh: String
        get() = p.getString("refresh", "") ?: ""
        set(v) = p.edit().putString("refresh", v).apply()
    var userId: String
        get() = p.getString("uid", "") ?: ""
        set(v) = p.edit().putString("uid", v).apply()
    var email: String
        get() = p.getString("email", "") ?: ""
        set(v) = p.edit().putString("email", v).apply()
    var displayName: String
        get() = p.getString("dn", "") ?: ""
        set(v) = p.edit().putString("dn", v).apply()

    val configured get() = url.startsWith("https://") && anonKey.length > 20
    val signedIn get() = refresh.isNotEmpty() && userId.isNotEmpty()

    fun signOut() {
        p.edit().remove("access").remove("refresh").remove("uid").remove("email").apply()
    }

    companion object {
        /** Refuses a "service_role" key: it gives full control of the database and must never be put in an app. */
        fun keyProblem(key: String): String? {
            val parts = key.trim().split(".")
            if (parts.size != 3) return "این شبیه کلید anon نیست (باید سه بخش با نقطه داشته باشد)."
            val role = runCatching {
                val json = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE), Charsets.UTF_8)
                JSONObject(json).optString("role")
            }.getOrDefault("")
            return when (role) {
                "anon" -> null
                "service_role" -> "این کلید service_role است و نباید در برنامه گذاشته شود! کلید anon public را بگذار."
                else -> "نقش کلید «$role» است؛ کلید anon public لازم است."
            }
        }
    }
}

/** Minimal Supabase client (Auth + PostgREST + RPC) over plain HTTPS. */
class Api(private val prefs: SharedPrefs) {
    private class Resp(val code: Int, val body: String)

    private suspend fun call(method: String, path: String, body: String?, bearer: String?, extra: Map<String, String> = emptyMap()): Resp =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(prefs.url + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("apikey", prefs.anonKey)
                conn.setRequestProperty("Authorization", "Bearer " + (bearer ?: prefs.anonKey))
                conn.setRequestProperty("Content-Type", "application/json")
                extra.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()
                Resp(code, text)
            } catch (e: IOException) {
                throw OfflineException(e)
            }
        }

    private fun fail(r: Resp): Nothing {
        val msg = runCatching {
            val j = JSONObject(r.body)
            listOf("msg", "message", "error_description", "error").map { j.optString(it) }.firstOrNull { it.isNotEmpty() } ?: ""
        }.getOrDefault("")
        throw ApiException(r.code, r.raw(), explain(r.code, msg))
    }

    private fun Resp.raw() = body.take(300)

    private fun explain(code: Int, m: String): String = when {
        m.contains("invite_invalid") -> "کد دعوت نامعتبر، منقضی، لغوشده یا پر شده است."
        m.contains("forbidden") -> "اجازهٔ این کار را نداری."
        m.contains("owner_must_transfer_or_delete") -> "تو مالک گروه هستی و هنوز عضو دیگری هست؛ اول گروه را حذف کن یا اعضا را بیرون کن."
        m.contains("Invalid login") -> "ایمیل یا رمز عبور اشتباه است."
        m.contains("already registered") -> "این ایمیل قبلاً ثبت‌نام کرده؛ «ورود» را بزن."
        m.contains("Email not confirmed") -> "ایمیل هنوز تأیید نشده؛ لینک تأیید در صندوق ایمیل است (یا تأیید ایمیل را در Supabase خاموش کن)."
        m.contains("Password should be") -> "رمز عبور کوتاه است (حداقل ۶ نویسه)."
        code == 401 -> "نشست ورود منقضی شده؛ دوباره وارد شو."
        code == 404 -> "آدرس سرور یا ساختار پایگاه‌داده (فایل schema.sql) درست نیست."
        else -> "خطای سرور ($code)" + if (m.isNotEmpty()) ": $m" else ""
    }

    // ─── Auth ───
    private fun saveSession(j: JSONObject) {
        prefs.access = j.getString("access_token")
        prefs.refresh = j.getString("refresh_token")
        prefs.userId = j.getJSONObject("user").getString("id")
    }

    suspend fun signUp(email: String, password: String) {
        val b = JSONObject().put("email", email).put("password", password).toString()
        val r = call("POST", "/auth/v1/signup", b, null)
        if (r.code !in 200..299) fail(r)
        val j = JSONObject(r.body)
        if (!j.has("access_token")) throw ApiException(200, "", "ثبت‌نام انجام شد؛ ایمیل تأیید فرستاده شد. بعد از تأیید، «ورود» را بزن.")
        saveSession(j)
        prefs.email = email
    }

    suspend fun signIn(email: String, password: String) {
        val b = JSONObject().put("email", email).put("password", password).toString()
        val r = call("POST", "/auth/v1/token?grant_type=password", b, null)
        if (r.code !in 200..299) fail(r)
        saveSession(JSONObject(r.body))
        prefs.email = email
    }

    private suspend fun refreshSession(): Boolean {
        if (prefs.refresh.isEmpty()) return false
        val b = JSONObject().put("refresh_token", prefs.refresh).toString()
        val r = call("POST", "/auth/v1/token?grant_type=refresh_token", b, null)
        if (r.code !in 200..299) {
            if (r.code in 400..401) prefs.signOut()
            return false
        }
        saveSession(JSONObject(r.body))
        return true
    }

    /** Calls with the login token; on "expired" refreshes it once and tries again. */
    private suspend fun authed(method: String, path: String, body: String?, extra: Map<String, String> = emptyMap()): String {
        var r = call(method, path, body, prefs.access, extra)
        if (r.code == 401 && refreshSession()) r = call(method, path, body, prefs.access, extra)
        if (r.code !in 200..299) fail(r)
        return r.body
    }

    // ─── Groups ───
    suspend fun createSpace(name: String, display: String): String {
        val b = JSONObject().put("p_name", name).put("p_display", display).toString()
        return authed("POST", "/rest/v1/rpc/create_space", b).trim().trim('"')
    }

    suspend fun createInvite(space: String, hours: Int, maxUses: Int): String {
        val b = JSONObject().put("p_space", space).put("p_hours", hours).put("p_max_uses", maxUses).toString()
        return authed("POST", "/rest/v1/rpc/create_invite", b).trim().trim('"')
    }

    suspend fun revokeInvite(code: String) {
        authed("POST", "/rest/v1/rpc/revoke_invite", JSONObject().put("p_code", code).toString())
    }

    /** Returns (spaceId, name). */
    suspend fun joinSpace(code: String, display: String): Pair<String, String> {
        val b = JSONObject().put("p_code", code).put("p_display", display).toString()
        val arr = JSONArray(authed("POST", "/rest/v1/rpc/join_space", b))
        val o = arr.getJSONObject(0)
        return o.getString("id") to o.getString("name")
    }

    suspend fun leaveSpace(space: String) {
        authed("POST", "/rest/v1/rpc/leave_space", JSONObject().put("p_space", space).toString())
    }

    suspend fun deleteSpace(space: String) {
        authed("POST", "/rest/v1/rpc/delete_space", JSONObject().put("p_space", space).toString())
    }

    suspend fun removeMember(space: String, user: String) {
        authed("POST", "/rest/v1/rpc/remove_member", JSONObject().put("p_space", space).put("p_user", user).toString())
    }

    class Member(val userId: String, val role: String, val name: String)
    class InviteRow(val code: String, val expiresAt: String, val uses: Int, val maxUses: Int, val revoked: Boolean)

    suspend fun members(space: String): List<Member> {
        val arr = JSONArray(authed("GET", "/rest/v1/space_members?space_id=eq.${enc(space)}&select=user_id,role,display_name", null))
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Member(o.getString("user_id"), o.getString("role"), o.optString("display_name")) } }
    }

    suspend fun invites(space: String): List<InviteRow> {
        val arr = JSONArray(authed("GET", "/rest/v1/invites?space_id=eq.${enc(space)}&select=code,expires_at,uses,max_uses,revoked&order=created_at.desc", null))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            InviteRow(o.getString("code"), o.getString("expires_at"), o.getInt("uses"), o.getInt("max_uses"), o.getBoolean("revoked"))
        }
    }

    /** Spaces this user belongs to: (id, name, role). */
    suspend fun mySpaces(): List<Triple<String, String, String>> {
        val spaces = JSONArray(authed("GET", "/rest/v1/spaces?select=id,name", null))
        val mem = JSONArray(authed("GET", "/rest/v1/space_members?user_id=eq.${enc(prefs.userId)}&select=space_id,role", null))
        val roles = (0 until mem.length()).associate { mem.getJSONObject(it).let { o -> o.getString("space_id") to o.getString("role") } }
        return (0 until spaces.length()).map {
            val o = spaces.getJSONObject(it)
            Triple(o.getString("id"), o.getString("name"), roles[o.getString("id")] ?: "member")
        }
    }

    // ─── Items ───
    class RemoteItem(val id: String, val payload: String, val updatedAt: Long, val deleted: Boolean, val rev: Long)

    suspend fun pull(space: String, afterRev: Long): List<RemoteItem> {
        val arr = JSONArray(
            authed("GET", "/rest/v1/shared_items?space_id=eq.${enc(space)}&rev=gt.$afterRev&select=id,payload,updated_at,deleted,rev&order=rev.asc&limit=500", null),
        )
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            RemoteItem(o.getString("id"), o.getString("payload"), o.getLong("updated_at"), o.getBoolean("deleted"), o.getLong("rev"))
        }
    }

    /** True when the server accepted the change (false = the server already had a newer edit). */
    suspend fun push(space: String, id: String, payload: String, updatedAt: Long, deleted: Boolean): Boolean {
        val b = JSONObject().put("p_id", id).put("p_space", space).put("p_payload", payload)
            .put("p_updated_at", updatedAt).put("p_deleted", deleted).toString()
        return authed("POST", "/rest/v1/rpc/upsert_item", b).trim() == "true"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
