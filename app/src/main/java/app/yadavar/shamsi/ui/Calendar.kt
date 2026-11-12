package app.yadavar.shamsi.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.yadavar.shamsi.jalali.toJalali
import java.util.Date
import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.MONTH_NAMES
import app.yadavar.shamsi.jalali.WEEKDAY_NARROW
import app.yadavar.shamsi.jalali.addMonths
import app.yadavar.shamsi.jalali.iranWeekday
import app.yadavar.shamsi.jalali.monthLength
import app.yadavar.shamsi.model.faNum

/** Ring and tint that mark today in the calendar. */
private val TodayHighlight = Color(0xFF2FA36B)

@Composable
fun MonthGrid(
    visible: JDate,
    selected: JDate?,
    onSelect: (JDate) -> Unit,
    onMonth: (JDate) -> Unit,
) {
    val todayDate = remember { toJalali(Date()) }
    val length = monthLength(visible.jy, visible.jm)
    val lead = iranWeekday(JDate(visible.jy, visible.jm, 1))
    val cells = List(lead) { null as Int? } + (1..length).map { it as Int? }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${MONTH_NAMES[visible.jm - 1]} ${faNum(visible.jy)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onMonth(shift(visible, -1)) }) { Text("قبل") }
            TextButton(onClick = { onMonth(shift(visible, 1)) }) { Text("بعد") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_NARROW.forEach { name ->
                Text(name, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day == null) {
                            Box(Modifier.size(36.dp))
                        } else {
                            val date = JDate(visible.jy, visible.jm, day)
                            val on = selected?.jy == date.jy && selected.jm == date.jm && selected.jd == date.jd
                            val isToday = date.jy == todayDate.jy && date.jm == todayDate.jm && date.jd == todayDate.jd
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            on -> MaterialTheme.colorScheme.primary
                                            isToday -> TodayHighlight.copy(alpha = 0.28f)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .then(if (isToday) Modifier.border(2.dp, TodayHighlight, CircleShape) else Modifier)
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    faNum(day),
                                    fontWeight = if (isToday) FontWeight.Bold else null,
                                    color = when {
                                        on -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private fun shift(date: JDate, delta: Int): JDate {
    val moved = addMonths(JDate(date.jy, date.jm, 1), delta)
    val day = minOf(date.jd, monthLength(moved.jy, moved.jm))
    return JDate(moved.jy, moved.jm, day)
}
