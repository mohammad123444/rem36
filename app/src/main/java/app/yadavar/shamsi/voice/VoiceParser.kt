package app.yadavar.shamsi.voice

import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.MONTH_NAMES
import app.yadavar.shamsi.jalali.addDays
import app.yadavar.shamsi.jalali.addMonths
import app.yadavar.shamsi.jalali.compareJ
import app.yadavar.shamsi.jalali.dateEpoch
import app.yadavar.shamsi.jalali.monthLength
import app.yadavar.shamsi.jalali.nextWeekday
import app.yadavar.shamsi.jalali.toJalali
import java.util.Calendar
import java.util.Date

data class ParsedSpeech(
    val title: String,
    val jy: Int?,
    val jm: Int?,
    val jd: Int?,
    val hour: Int?,
    val minute: Int?,
    val repeatKind: String? = null,
)

private val ones = listOf(
    "نوزده" to 19, "هجده" to 18, "هفده" to 17, "شانزده" to 16, "پونزده" to 15,
    "پانزده" to 15, "چهارده" to 14, "سیزده" to 13, "دوازده" to 12, "یازده" to 11,
    "ده" to 10, "نه" to 9, "هشت" to 8, "هفت" to 7, "شیش" to 6, "شش" to 6,
    "پنج" to 5, "چهار" to 4, "سه" to 3, "دو" to 2, "یک" to 1, "یه" to 1, "صفر" to 0,
)

private val tens = listOf(
    "نود" to 90, "هشتاد" to 80, "هفتاد" to 70, "شصت" to 60,
    "پنجاه" to 50, "چهل" to 40, "سی" to 30, "بیست" to 20,
)

private val weekdays = listOf(
    "چهارشنبه" to 4, "پنجشنبه" to 5, "سه شنبه" to 3, "سهشنبه" to 3,
    "دوشنبه" to 2, "یکشنبه" to 1, "یک شنبه" to 1, "شنبه" to 0, "جمعه" to 6,
)

private fun isFa(ch: Char?) = ch != null && ch in '\u0600'..'\u06FF'

fun normalizeSpeech(input: String): String {
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    val ar = "٠١٢٣٤٥٦٧٨٩"
    return input
        .replace("\u200c", " ")
        .replace("\u200d", " ")
        .replace('\u064a', '\u06cc')
        .replace('\u0643', '\u06a9')
        .map { ch ->
            val fi = fa.indexOf(ch)
            if (fi >= 0) return@map ('0' + fi)
            val ai = ar.indexOf(ch)
            if (ai >= 0) '0' + ai else ch
        }
        .joinToString("")
        .replace(Regex("[،,؛;]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private data class Read(val value: Int, val consumed: Int)

private fun readNumber(source: String): Read? {
    val digit = Regex("^(\\d{1,4})").find(source)
    if (digit != null) return Read(digit.groupValues[1].toInt(), digit.groupValues[1].length)
    for ((word, value) in tens) {
        if (!source.startsWith(word)) continue
        val rest = source.substring(word.length)
        val and = Regex("^\\s+و\\s+").find(rest)
        if (and != null) {
            val after = rest.substring(and.value.length)
            for ((onesWord, onesValue) in ones) {
                if (onesValue >= 10) continue
                if (after.startsWith(onesWord) && !isFa(after.getOrNull(onesWord.length))) {
                    return Read(value + onesValue, word.length + and.value.length + onesWord.length)
                }
            }
        }
        if (!isFa(source.getOrNull(word.length))) return Read(value, word.length)
    }
    for ((word, value) in ones) {
        if (source.startsWith(word) && !isFa(source.getOrNull(word.length))) {
            return Read(value, word.length)
        }
    }
    return null
}

private fun applyPeriod(hour: Int, minute: Int, period: String?): Pair<Int, Int>? {
    if (minute > 59 || hour > 24) return null
    if (period == null) return if (hour > 23) null else hour to minute
    val p = period.replace(" ", "")
    if (p == "صبح" || p == "بامداد") {
        if (hour == 12) return 0 to minute
        if (hour > 23) return null
        return hour to minute
    }
    if (p == "ظهر") {
        if (hour == 0 || hour == 12) return 12 to minute
        if (hour < 12) return hour + 12 to minute
        if (hour > 23) return null
        return hour to minute
    }
    if (hour == 12 && p == "شب") return 0 to minute
    if (hour in 13..23) return hour to minute
    if (hour == 12) return 12 to minute
    if (hour > 12) return null
    return hour + 12 to minute
}

private data class Clock(val hour: Int, val minute: Int, val consumed: Int, val explicit: Boolean)

/** A time found in a sentence. [explicit] is false for an ambiguous 12-hour number such as "ساعت ۵". */
private data class TimeHit(val hour: Int, val minute: Int, val span: Span, val explicit: Boolean)

private fun parseClock(source: String): Clock? {
    val n = readNumber(source) ?: return null
    var i = n.consumed
    var minute = 0
    if (source.getOrNull(i) == ':' || source.getOrNull(i) == '.') {
        val mm = Regex("^[:.](\\d{2})").find(source.substring(i)) ?: return null
        minute = mm.groupValues[1].toInt()
        i += mm.value.length
    } else {
        val and = Regex("^\\s+و\\s+").find(source.substring(i))
        if (and != null) {
            val after = source.substring(i + and.value.length)
            when {
                after.startsWith("نیم") && !isFa(after.getOrNull(3)) -> {
                    minute = 30
                    i += and.value.length + 3
                }
                after.startsWith("ربع") && !isFa(after.getOrNull(3)) -> {
                    minute = 15
                    i += and.value.length + 3
                }
                else -> {
                    val second = readNumber(after)
                    if (second != null && second.value <= 59) {
                        minute = second.value
                        i += and.value.length + second.consumed
                    }
                }
            }
        }
    }
    var period: String? = null
    val periodMatch = Regex("^\\s+(صبح|بامداد|ظهر|بعد از ظهر|بعدازظهر|عصر|غروب|شب)").find(source.substring(i))
    if (periodMatch != null) {
        period = periodMatch.groupValues[1]
        i += periodMatch.value.length
    }
    val applied = applyPeriod(n.value, minute, period) ?: return null
    val explicit = period != null || n.value !in 1..12
    return Clock(applied.first, applied.second, i, explicit)
}

private data class Span(val start: Int, val end: Int)

private val periodWords = "صبح|بامداد|ظهر|بعد از ظهر|بعدازظهر|عصر|غروب|شب"

private fun extractTime(text: String): TimeHit? {
    var from = 0
    while (from < text.length) {
        val at = text.indexOf("ساعت", from)
        if (at < 0) break
        val prev = if (at > 0) text[at - 1] else ' '
        if (prev == ' ' || at == 0) {
            var i = at + 4
            if (text.getOrNull(i) == ' ') i += 1
            val parsed = parseClock(text.substring(i))
            if (parsed != null) {
                return TimeHit(parsed.hour, parsed.minute, Span(at, i + parsed.consumed), parsed.explicit)
            }
        }
        from = at + 4
    }
    val clock = Regex("(^|\\s)(\\d{1,2}):(\\d{2})(?=\\s|$)").find(text)
    if (clock != null) {
        val hour = clock.groupValues[2].toInt()
        val minute = clock.groupValues[3].toInt()
        if (hour <= 23 && minute <= 59) {
            val start = clock.range.first + clock.groupValues[1].length
            return TimeHit(hour, minute, Span(start, clock.range.last + 1), hour !in 1..12)
        }
    }
    // "۵ عصر", "۸:۳۰ شب" (without the word "ساعت").
    val withPeriod = Regex("(^|\\s)(\\d{1,2})(?::(\\d{2}))?\\s+($periodWords)(?=\\s|$)").find(text)
    if (withPeriod != null) {
        val hour = withPeriod.groupValues[2].toInt()
        val minute = withPeriod.groupValues[3].ifEmpty { "0" }.toInt()
        val applied = applyPeriod(hour, minute, withPeriod.groupValues[4])
        if (applied != null) {
            val start = withPeriod.range.first + withPeriod.groupValues[1].length
            return TimeHit(applied.first, applied.second, Span(start, withPeriod.range.last + 1), true)
        }
    }
    return null
}

/**
 * "ساعت ۵" without morning/evening is ambiguous. Nobody means 5 a.m. for an errand, so 1..5 is taken as
 * afternoon; 6..11 stays in the morning unless that time has already passed today and the evening one has not.
 */
private fun guessHour(hour: Int, minute: Int, date: JDate, today: JDate, now: Date, tonight: Boolean): Int {
    if (hour !in 1..11) return hour
    if (tonight || hour <= 5) return hour + 12
    if (compareJ(date, today) == 0) {
        val literal = dateEpoch(date.jy, date.jm, date.jd, hour, minute)
        val evening = dateEpoch(date.jy, date.jm, date.jd, hour + 12, minute)
        if (literal <= now.time && evening > now.time) return hour + 12
    }
    return hour
}

private fun findBounded(text: String, word: String): Int {
    var from = 0
    while (from < text.length) {
        val index = text.indexOf(word, from)
        if (index < 0) return -1
        val prev = if (index > 0) text[index - 1] else null
        val next = text.getOrNull(index + word.length)
        if (!isFa(prev) && !isFa(next)) return index
        from = index + word.length
    }
    return -1
}

private fun trailingNumber(before: String): Pair<Int, Int>? {
    val digits = Regex("(\\d{1,2})$").find(before)
    if (digits != null) {
        val prev = if (digits.range.first > 0) before[digits.range.first - 1] else ' '
        if (digits.range.first == 0 || prev == ' ') return digits.groupValues[1].toInt() to digits.range.first
    }
    val max = minOf(18, before.length)
    for (len in max downTo 1) {
        val start = before.length - len
        val prev = if (start > 0) before[start - 1] else ' '
        if (start > 0 && prev != ' ') continue
        val parsed = readNumber(before.substring(start))
        if (parsed != null && parsed.consumed == len) return parsed.value to start
    }
    return null
}

private fun resolveMonthDay(today: JDate, jm: Int, jdWant: Int): JDate {
    fun clamp(jy: Int) = JDate(jy, jm, minOf(maxOf(1, jdWant), monthLength(jy, jm)))
    val current = clamp(today.jy)
    if (compareJ(current, today) >= 0) return current
    return clamp(today.jy + 1)
}

private data class DateHit(val date: JDate, val span: Span)

private fun extractDate(text: String, today: JDate): DateHit? {
    val hits = mutableListOf<DateHit>()
    val relative = listOf("پس فردا" to 2, "پسفردا" to 2, "فردا" to 1, "امروز" to 0, "امشب" to 0, "دیروز" to -1)
    for ((word, delta) in relative) {
        val index = findBounded(text, word)
        if (index >= 0) hits += DateHit(addDays(today, delta), Span(index, index + word.length))
    }
    for (word in listOf("هفته آینده", "هفته بعد")) {
        val index = findBounded(text, word)
        if (index >= 0) hits += DateHit(addDays(today, 7), Span(index, index + word.length))
    }
    val monthAhead = findBounded(text, "ماه آینده")
    if (monthAhead >= 0) {
        hits += DateHit(addMonths(today, 1), Span(monthAhead, monthAhead + "ماه آینده".length))
    }
    for ((word, weekday) in weekdays) {
        val index = findBounded(text, word)
        if (index >= 0) hits += DateHit(nextWeekday(today, weekday), Span(index, index + word.length))
    }
    MONTH_NAMES.forEachIndexed { monthIndex, name ->
        val jm = monthIndex + 1
        val index = findBounded(text, name)
        if (index < 0) return@forEachIndexed
        val before = text.substring(0, index).replace(Regex("\\s+$"), "")
        val lead = trailingNumber(before)
        if (lead != null && lead.first in 1..31) {
            hits += DateHit(resolveMonthDay(today, jm, lead.first), Span(lead.second, index + name.length))
            return@forEachIndexed
        }
        val after = text.substring(index + name.length)
        val space = Regex("^\\s+").find(after) ?: return@forEachIndexed
        val parsed = readNumber(after.substring(space.value.length)) ?: return@forEachIndexed
        if (parsed.value !in 1..31) return@forEachIndexed
        if (isFa(after.getOrNull(space.value.length + parsed.consumed))) return@forEachIndexed
        hits += DateHit(
            resolveMonthDay(today, jm, parsed.value),
            Span(index, index + name.length + space.value.length + parsed.consumed),
        )
    }
    return hits.minWithOrNull(compareBy({ it.span.start }, { it.span.end }))
}

private fun cut(text: String, span: Span?): String {
    if (span == null) return text
    return "${text.substring(0, span.start)} ${text.substring(span.end)}".replace(Regex("\\s+"), " ").trim()
}

private fun cleanupTitle(text: String, hadCue: Boolean): String {
    var next = " $text "
    val cues = listOf(
        "یادآوری کنید", "یادآوری کن", "یاد اوری کن", "یادم بنداز", "یادم بیار",
        "یادآور بساز", "یه یادآور", "یک یادآور", "یادآوری", "یادآور",
    )
    for (cue in cues) next = next.replace(cue, " ")
    if (hadCue) next = next.replace("برای", " ")
    next = next.replace(Regex("\\s+"), " ").trim()
    for (ignored in 0 until 4) {
        val stripped = next.replace(Regex("^(و|که|را|رو|یه|یک|این|ساعت)\\s+"), "")
        if (stripped == next) break
        next = stripped
    }
    return next.replace(Regex("^[\\s.\\-]+|[\\s.\\-]+$"), "").trim()
}

private val repeatPattern = Regex("(?<!\\S)هر\\s+(روز|هفته|ماه|سال)(?!\\S)")
private val monthEndPattern = Regex("(?<!\\S)(هر\\s+)?آخر(?:\\s+این)?\\s+ماه(?!\\S)")
private val relativePattern = Regex(
    "(?<!\\S)(?:(\\d{1,3})|(نیم)|(یک|یه|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده))\\s*(دقیقه|ساعت)\\s*(?:دیگر|بعد)(?!\\S)",
)
private val relativeDayPattern = Regex(
    "(?<!\\S)(?:(\\d{1,3})|(یک|یه|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده))\\s*(روز|هفته|ماه)\\s*(?:دیگر|بعد)(?!\\S)",
)
private val smallNumbers = mapOf(
    "یک" to 1, "یه" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
    "شش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10,
)

private fun collapse(text: String) = text.replace(Regex("\\s+"), " ").trim()

/** "نیم ساعت دیگر", "۱۰ دقیقه دیگر", "دو ساعت دیگر" -> minutes from now (null if not usable). */
private fun relativeMinutes(match: MatchResult): Int? {
    val unit = match.groupValues[4]
    val amount = when {
        match.groupValues[2].isNotEmpty() -> return if (unit == "ساعت") 30 else null
        match.groupValues[1].isNotEmpty() -> match.groupValues[1].toIntOrNull() ?: return null
        else -> smallNumbers[match.groupValues[3]] ?: return null
    }
    val minutes = if (unit == "ساعت") amount * 60 else amount
    return if (minutes in 1..(60 * 24 * 7)) minutes else null
}

fun parseReminderSpeech(input: String, now: Date = Date()): ParsedSpeech {
    var original = normalizeSpeech(input)
    val hadCue = Regex("یادآوری|یادآور|یادم").containsMatchIn(original)
    val today = toJalali(now)

    var repeatKind: String? = null
    var fixedDate: JDate? = null
    var fixedHour: Int? = null
    var fixedMinute: Int? = null

    val monthEnd = monthEndPattern.find(original)
    if (monthEnd != null) {
        fixedDate = JDate(today.jy, today.jm, monthLength(today.jy, today.jm))
        if (monthEnd.groupValues[1].isNotBlank()) repeatKind = "monthly"
        original = collapse(original.removeRange(monthEnd.range))
    }

    val repeatMatch = repeatPattern.find(original)
    if (repeatMatch != null) {
        repeatKind = when (repeatMatch.groupValues[1]) {
            "روز" -> "daily"
            "هفته" -> "weekly"
            "ماه" -> "monthly"
            else -> "yearly"
        }
        original = collapse(original.removeRange(repeatMatch.range))
    }

    val relativeMatch = relativePattern.find(original)
    val minutesAhead = if (relativeMatch != null) relativeMinutes(relativeMatch) else null
    if (relativeMatch != null && minutesAhead != null) {
        val target = Date(now.time + minutesAhead * 60_000L)
        val cal = Calendar.getInstance()
        cal.time = target
        fixedDate = toJalali(target)
        fixedHour = cal.get(Calendar.HOUR_OF_DAY)
        fixedMinute = cal.get(Calendar.MINUTE)
        original = collapse(original.removeRange(relativeMatch.range))
    }

    // "۳ روز دیگر", "دو هفته بعد", "یک ماه دیگر"
    if (fixedDate == null) {
        val dayMatch = relativeDayPattern.find(original)
        if (dayMatch != null) {
            val amount = dayMatch.groupValues[1].toIntOrNull() ?: smallNumbers[dayMatch.groupValues[2]]
            if (amount != null && amount in 1..400) {
                fixedDate = when (dayMatch.groupValues[3]) {
                    "روز" -> addDays(today, amount)
                    "هفته" -> addDays(today, 7 * amount)
                    else -> addMonths(today, amount)
                }
                original = collapse(original.removeRange(dayMatch.range))
            }
        }
    }

    val time = if (fixedHour == null) extractTime(original) else null
    var rest = cut(original, time?.span)
    val date = if (fixedDate == null) extractDate(rest, today) else null
    rest = cut(rest, date?.span)
    val title = cleanupTitle(rest, hadCue).ifBlank {
        cleanupTitle(cut(cut(original, time?.span), date?.span), false)
    }

    var hour = fixedHour ?: time?.hour
    val minute = fixedMinute ?: time?.minute
    var resultDate = fixedDate ?: date?.date
    if (fixedHour == null && time != null && !time.explicit && hour != null && minute != null) {
        val tonight = findBounded(original, "امشب") >= 0
        hour = guessHour(hour, minute, resultDate ?: today, today, now, tonight)
    }
    // A time without a date means "the next time it is that hour": today if still ahead, otherwise tomorrow.
    if (resultDate == null && hour != null && minute != null) {
        val todayAt = dateEpoch(today.jy, today.jm, today.jd, hour, minute)
        resultDate = if (todayAt > now.time) today else addDays(today, 1)
    }
    return ParsedSpeech(
        title = title,
        jy = resultDate?.jy,
        jm = resultDate?.jm,
        jd = resultDate?.jd,
        hour = hour,
        minute = minute,
        repeatKind = repeatKind,
    )
}
