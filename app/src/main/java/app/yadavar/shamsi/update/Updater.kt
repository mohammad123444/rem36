package app.yadavar.shamsi.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app update. The new APK is the file behind [APK_URL]. The file has no version number online, so the
 * app remembers the "signature" (ETag + size + date) of that file; when the signature changes a newer file
 * was uploaded. The downloaded APK is checked (same package, higher versionCode) before it is installed.
 */
object Updater {
    const val APK_URL = "https://github.com/most1404/sinerehmain/releases/download/ff/app-release.apk"

    /** Signature of the online file, or null when it cannot be reached. */
    suspend fun remoteSignature(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(APK_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                // Only the first byte: enough to read the headers without downloading the file.
                setRequestProperty("Range", "bytes=0-0")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return@runCatching null
                val range = conn.getHeaderField("Content-Range") // "bytes 0-0/12345"
                val total = range?.substringAfter('/', "")?.trim()?.takeIf { it.isNotEmpty() && it != "*" }
                    ?: conn.getHeaderField("Content-Length") ?: ""
                listOf(conn.getHeaderField("ETag") ?: "", total, conn.getHeaderField("Last-Modified") ?: "")
                    .joinToString("|")
                    .takeIf { it.replace("|", "").isNotBlank() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun updateFile(context: Context): File {
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        return File(dir, "app-release.apk")
    }

    /** Downloads the APK. Returns the file or null on failure. [onProgress] gets 0..1 (or -1 when the size is unknown). */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val target = updateFile(context)
        val temp = File(target.parentFile, "app-release.part")
        runCatching {
            val conn = (URL(APK_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (target.exists()) target.delete()
            if (temp.renameTo(target)) target else null
        }.getOrNull().also { if (it == null) runCatching { temp.delete() } }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    /** True when [apk] is this app and has a higher versionCode than the installed one. */
    fun isNewer(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, 0) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return false
        return versionCodeOf(archive) > versionCodeOf(installed)
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the "allow installing from this app" screen. */
    fun openInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Hands the downloaded APK to the system installer. */
    fun install(context: Context) {
        val file = updateFile(context)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
