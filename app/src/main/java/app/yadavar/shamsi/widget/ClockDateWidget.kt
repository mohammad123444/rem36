package app.yadavar.shamsi.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.R
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.ui.MainActivity
import app.yadavar.shamsi.ui.chosenTextColor
import java.util.Date

/** Text size choices of the date-and-time widget. */
val ClockSizeOptions = listOf(
    "xs" to "خیلی کوچک",
    "sm" to "کوچک",
    "md" to "متوسط",
    "lg" to "بزرگ",
    "xl" to "خیلی بزرگ",
)

/** Where the texts sit inside the widget frame (left / center / right of the screen). */
val ClockAlignOptions = listOf(
    "left" to "چپ‌چین",
    "center" to "وسط‌چین",
    "right" to "راست‌چین",
)

private fun clockTimeSp(id: String): Int = when (id) {
    "xs" -> 26
    "sm" -> 34
    "lg" -> 52
    "xl" -> 66
    else -> 42
}

/** Second widget: only the Jalali date and the live clock, with no background of its own. */
class ClockDateWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = Graph.settings.current()
        provideContent {
            val settings by Graph.settings.flow.collectAsState(initial = first)
            ClockDateContent(context, settings)
        }
    }
}

@Composable
private fun ClockDateContent(context: Context, settings: Settings) {
    val timeSp = clockTimeSp(settings.clockSize)
    val dateSp = maxOf(12, (timeSp * 0.42f).toInt())
    val color: Color = chosenTextColor(settings.clockColor) ?: Color.White
    val textAlign = when (settings.clockAlign) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }
    val layout = when (settings.clockAlign) {
        "left" -> R.layout.widget_time_left
        "right" -> R.layout.widget_time_right
        else -> R.layout.widget_time_center
    }
    val views = RemoteViews(context.packageName, layout)
    views.setTextColor(R.id.time_text, color.toArgb())
    views.setTextViewTextSize(R.id.time_text, TypedValue.COMPLEX_UNIT_SP, timeSp.toFloat())
    val dateText = formatLongDate(toJalali(Date()))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dateText,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = dateSp.sp,
                fontWeight = FontWeight.Medium,
                textAlign = textAlign,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        AndroidRemoteViews(views, GlanceModifier.fillMaxWidth())
    }
}

class ClockDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockDateWidget()
}
