package app.yadavar.shamsi.jalali

import java.util.Calendar
import java.util.Date

data class JDate(val jy: Int, val jm: Int, val jd: Int)

val MONTH_NAMES = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

val WEEKDAY_NAMES = listOf(
    "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه",
)

val WEEKDAY_NARROW = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

private fun div(a: Int, b: Int) = a / b

private fun mod(a: Int, b: Int) = a - (a / b) * b

private data class JalCal(val leap: Int, val gy: Int, val march: Int)

private fun jalCal(jy: Int): JalCal {
    val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )
    val bl = breaks.size
    val gy = jy + 621
    var leapJ = -14
    var jp = breaks[0]
    require(jy >= jp && jy < breaks[bl - 1]) { "Invalid Jalaali year $jy" }
    var jump = 0
    for (i in 1 until bl) {
        val jm = breaks[i]
        jump = jm - jp
        if (jy < jm) break
        leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
        jp = jm
    }
    var n = jy - jp
    leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
    if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
    val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
    val march = 20 + leapJ - leapG
    if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
    var leap = mod(mod(n + 1, 33) - 1, 4)
    if (leap == -1) leap = 4
    return JalCal(leap, gy, march)
}

private fun g2d(gy: Int, gm: Int, gd: Int): Int {
    var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
        div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
    d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
    return d
}

data class GDate(val gy: Int, val gm: Int, val gd: Int)

private fun d2g(jdn: Int): GDate {
    var j = 4 * jdn + 139361631
    j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
    val i = div(mod(j, 1461), 4) * 5 + 308
    val gd = div(mod(i, 153), 5) + 1
    val gm = mod(div(i, 153), 12) + 1
    val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
    return GDate(gy, gm, gd)
}

fun j2d(jy: Int, jm: Int, jd: Int): Int {
    val r = jalCal(jy)
    return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
}

fun d2j(jdn: Int): JDate {
    val gy = d2g(jdn).gy
    var jy = gy - 621
    val r = jalCal(jy)
    val jdn1f = g2d(gy, 3, r.march)
    var k = jdn - jdn1f
    if (k >= 0) {
        if (k <= 185) {
            return JDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
        }
        k -= 186
    } else {
        jy -= 1
        k += 179
        if (r.leap == 1) k += 1
    }
    return JDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
}

fun toJalali(date: Date): JDate {
    val cal = Calendar.getInstance().apply { time = date }
    return d2j(g2d(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)))
}

fun toGregorian(jy: Int, jm: Int, jd: Int): GDate = d2g(j2d(jy, jm, jd))

fun isLeapJalaaliYear(jy: Int) = jalCal(jy).leap == 0

fun monthLength(jy: Int, jm: Int): Int {
    if (jm <= 6) return 31
    if (jm <= 11) return 30
    return if (isLeapJalaaliYear(jy)) 30 else 29
}

fun compareJ(a: JDate, b: JDate): Int {
    if (a.jy != b.jy) return a.jy - b.jy
    if (a.jm != b.jm) return a.jm - b.jm
    return a.jd - b.jd
}

fun addDays(j: JDate, days: Int) = d2j(j2d(j.jy, j.jm, j.jd) + days)

fun addMonths(j: JDate, months: Int): JDate {
    val total = j.jy * 12 + (j.jm - 1) + months
    val jy = Math.floorDiv(total, 12)
    val jm = Math.floorMod(total, 12) + 1
    return JDate(jy, jm, minOf(j.jd, monthLength(jy, jm)))
}

fun iranWeekday(j: JDate): Int {
    val g = toGregorian(j.jy, j.jm, j.jd)
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, g.gy)
        set(Calendar.MONTH, g.gm - 1)
        set(Calendar.DAY_OF_MONTH, g.gd)
        set(Calendar.HOUR_OF_DAY, 12)
    }
    return (cal.get(Calendar.DAY_OF_WEEK) % 7)
}

fun nextWeekday(from: JDate, target: Int): JDate {
    val delta = (target - iranWeekday(from) + 7) % 7
    return addDays(from, delta)
}

fun startOfIranWeek(j: JDate) = addDays(j, -iranWeekday(j))

fun dateEpoch(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int): Long {
    val g = toGregorian(jy, jm, jd)
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, g.gy)
        set(Calendar.MONTH, g.gm - 1)
        set(Calendar.DAY_OF_MONTH, g.gd)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
