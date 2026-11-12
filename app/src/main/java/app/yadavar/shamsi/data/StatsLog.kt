package app.yadavar.shamsi.data

import android.content.Context

/**
 * Small log of the moments a repeating reminder was finished. A repeating reminder never goes to the
 * "done" list (it just moves to its next date), so the statistics page needs this log to count it.
 */
object StatsLog {
    private const val PREFS = "yadavar_stats"
    private const val KEY = "repeatDone"
    private const val MAX = 600

    fun addRepeatDone(context: Context, at: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = (read(context) + at).takeLast(MAX)
        prefs.edit().putString(KEY, all.joinToString(",")).apply()
    }

    fun read(context: Context): List<Long> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }
    }
}
