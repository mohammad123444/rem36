package app.yadavar.shamsi.model

import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.addDays
import app.yadavar.shamsi.jalali.compareJ
import app.yadavar.shamsi.jalali.monthLength
import app.yadavar.shamsi.jalali.startOfIranWeek
import app.yadavar.shamsi.jalali.toJalali
import java.util.Date

/** Time filters shared by the home screen and the widget. */
enum class TimeFilter(val id: String, val label: String) {
    All("all", "همه"),
    Past("past", "گذشته"),
    Today("today", "امروز"),
    Week("week", "این هفته"),
    Month("month", "این ماه");

    companion object {
        fun of(id: String): TimeFilter = entries.firstOrNull { it.id == id } ?: All
    }
}

/**
 * Active (not completed) reminders that match [filter], sorted by time.
 * The Iranian week runs Saturday..Friday; the month is the current Jalali month.
 */
fun filterReminders(all: List<Reminder>, filter: TimeFilter, now: Date = Date()): List<Reminder> {
    val today = toJalali(now)
    val weekStart = startOfIranWeek(today)
    val weekEnd = addDays(weekStart, 6)
    val monthStart = JDate(today.jy, today.jm, 1)
    val monthEnd = JDate(today.jy, today.jm, monthLength(today.jy, today.jm))

    fun inRange(date: JDate?, from: JDate, to: JDate): Boolean =
        date != null && compareJ(date, from) >= 0 && compareJ(date, to) <= 0

    return all
        .filter { !it.completed }
        .filter { reminder ->
            when (filter) {
                TimeFilter.All -> true
                TimeFilter.Past -> sectionOf(reminder, now) == Section.Overdue
                TimeFilter.Today -> dateOf(reminder) == today
                TimeFilter.Week -> inRange(dateOf(reminder), weekStart, weekEnd)
                TimeFilter.Month -> inRange(dateOf(reminder), monthStart, monthEnd)
            }
        }
        .sortedWith(smartOrder)
}
