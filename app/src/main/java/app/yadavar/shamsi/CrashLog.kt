package app.yadavar.shamsi

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/** Saves the last crash to a file so the next launch can show it on screen. */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, error: Throwable) {
        runCatching {
            val header = "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n"
            File(context.applicationContext.filesDir, FILE_NAME).writeText(header + Log.getStackTraceString(error))
        }
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }
}
