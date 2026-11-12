package app.yadavar.shamsi.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.R
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.CheckItem
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.Section
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.TimeFilter
import app.yadavar.shamsi.model.dateOf
import app.yadavar.shamsi.model.filterReminders
import app.yadavar.shamsi.model.formatDayMonth
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.formatTime
import app.yadavar.shamsi.model.repeatLabel
import app.yadavar.shamsi.model.sectionOf
import app.yadavar.shamsi.model.weekdayName
import app.yadavar.shamsi.ui.MainActivity
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Reminders whose circle was just tapped: the widget shows a green check for a moment before they disappear. */
object WidgetState {
    val completing = MutableStateFlow<Set<String>>(emptySet())
}

object WidgetKeys {
    val id = ActionParameters.Key<String>("id")
    val item = ActionParameters.Key<String>("item")
    val filter = ActionParameters.Key<String>("filter")
}

sealed interface WidgetRow {
    val key: Long

    data class Main(val reminder: Reminder) : WidgetRow {
        override val key: Long get() = reminder.id.hashCode().toLong()
    }

    data class Sub(val reminder: Reminder, val item: CheckItem) : WidgetRow {
        override val key: Long get() = (reminder.id + item.id).hashCode().toLong()
    }

    /** Thin line drawn after the last reminder of one date, before the next date starts. */
    data class Divider(val index: Int) : WidgetRow {
        override val key: Long get() = 1_000_000_000L + index
    }
}

/** Title and meta text sizes (sp) for the three widget font levels. */
private fun widgetSizes(font: String): Pair<Int, Int> = when (font) {
    "sm" -> 12 to 10
    "lg" -> 18 to 14
    else -> 15 to 12
}

private fun emptyText(filter: TimeFilter): String = when (filter) {
    TimeFilter.All -> "یادآوری فعالی نیست"
    TimeFilter.Past -> "یادآوری گذشته‌ای نیست"
    TimeFilter.Today -> "برای امروز کاری نیست"
    TimeFilter.Week -> "برای این هفته کاری نیست"
    TimeFilter.Month -> "برای این ماه کاری نیست"
}

/** Weekday, day + month, time and repeat of one reminder (the weekday comes before the date). */
private fun widgetMeta(reminder: Reminder): String {
    val parts = mutableListOf<String>()
    val date = dateOf(reminder)
    if (date != null) parts += weekdayName(date) + " " + formatDayMonth(date)
    if (reminder.hour != null && reminder.minute != null) parts += formatTime(reminder.hour, reminder.minute)
    if (reminder.repeat !is Repeat.None) parts += repeatLabel(reminder.repeat)
    return parts.joinToString(" - ")
}

/** Opens the app straight on the edit screen of this reminder. */
private fun openReminderIntent(context: Context, id: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        // A distinct data URI keeps every row's PendingIntent separate.
        data = Uri.parse("yadavar://reminder/$id")
        putExtra(MainActivity.EXTRA_OPEN_ID, id)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/** Opens the confirmation (message or fingerprint) that must be passed before a reminder is finished from the widget. */
private fun confirmCompleteIntent(context: Context, id: String): Intent =
    Intent(context, ConfirmCompleteActivity::class.java).apply {
        data = Uri.parse("yadavar://complete/$id")
        putExtra("id", id)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/** The completion of a reminder from the widget: a green check first, then the row disappears. */
suspend fun completeFromWidget(context: Context, id: String) {
    WidgetState.completing.update { it + id }
    YadavarWidget().updateAll(context)
    delay(650)
    Actions.complete(id)
    WidgetState.completing.update { it - id }
    YadavarWidget().updateAll(context)
}

class YadavarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val firstReminders = Graph.repo.all()
        val firstSettings = Graph.settings.current()
        provideContent {
            // Live state: the widget redraws as soon as the database or settings change.
            val reminders by Graph.repo.reminders.collectAsState(initial = firstReminders)
            val settings by Graph.settings.flow.collectAsState(initial = firstSettings)
            val completing by WidgetState.completing.collectAsState()
            WidgetContent(context, reminders, settings, completing)
        }
    }
}

@Composable
private fun DateLabel(context: Context, text: String, color: Color, sizeSp: Int) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(color),
            fontSize = sizeSp.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = GlanceModifier
            .padding(horizontal = 6.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    )
}

@Composable
private fun PlusButton(context: Context, palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier
            .size(36.dp)
            .background(ColorProvider(palette.accent))
            .cornerRadius(18.dp)
            .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = ColorProvider(if (palette.dark) Color(0xFF1F1B29) else Color.White),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun ClockView(context: Context, palette: WidgetPalette, custom: Color?) {
    val views = RemoteViews(context.packageName, if (palette.dark) R.layout.widget_clock_dark else R.layout.widget_clock)
    // The person's own text color also colors the live clock.
    if (custom != null) views.setTextColor(R.id.clock_text, custom.toArgb())
    AndroidRemoteViews(
        views,
        GlanceModifier.width(82.dp).height(36.dp),
    )
}

@Composable
private fun WidgetContent(context: Context, reminders: List<Reminder>, settings: Settings, completing: Set<String>) {
    val now = Date()
    val palette = widgetPaletteWithText(settings.widgetBg, settings.widgetText)
    val customText = app.yadavar.shamsi.ui.chosenTextColor(settings.widgetText)
    val filter = TimeFilter.of(settings.widgetFilter)
    val (titleSp, metaSp) = widgetSizes(settings.widgetFont)
    val visible = filterReminders(reminders, filter, now)
    val rows = buildList {
        visible.forEachIndexed { index, reminder ->
            add(WidgetRow.Main(reminder))
            if (reminder.expanded) reminder.items.forEach { add(WidgetRow.Sub(reminder, it)) }
            val next = visible.getOrNull(index + 1)
            if (next != null && dateOf(next) != dateOf(reminder)) add(WidgetRow.Divider(index))
        }
    }
    val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    val dateText = formatLongDate(toJalali(now))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(palette.bg))
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        // Always: Jalali date on the right (next to "+"), the live clock on the left.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Visually (left to right): "+", clock, free space, date. The "+" sits after the clock, on the far left.
            if (rtl) {
                DateLabel(context, dateText, customText ?: palette.accent, titleSp + 1)
                Spacer(modifier = GlanceModifier.defaultWeight())
                ClockView(context, palette, customText)
                PlusButton(context, palette)
            } else {
                PlusButton(context, palette)
                ClockView(context, palette, customText)
                Spacer(modifier = GlanceModifier.defaultWeight())
                DateLabel(context, dateText, customText ?: palette.accent, titleSp + 1)
            }
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(3.dp).background(ColorProvider(palette.accent)),
        ) {}
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeFilter.entries.forEachIndexed { index, item ->
                val selected = item == filter
                // Every tab always sets its own background (transparent when inactive),
                // so a color from an earlier state can never stay behind.
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(vertical = 6.dp)
                        .background(ColorProvider(if (selected) palette.selected else Color.Transparent))
                        .cornerRadius(12.dp)
                        .clickable(actionRunCallback<SetFilterAction>(actionParametersOf(WidgetKeys.filter to item.id))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(if (selected) palette.accent else palette.muted),
                            fontSize = (metaSp - 1).sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
                if (index < TimeFilter.entries.size - 1) {
                    Box(
                        modifier = GlanceModifier.width(1.dp).height(16.dp).background(ColorProvider(palette.line)),
                    ) {}
                }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(palette.line)),
        ) {}
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (rows.isEmpty()) {
            Text(
                text = emptyText(filter),
                style = TextStyle(color = ColorProvider(palette.muted), fontSize = metaSp.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.key }) { row ->
                    when (row) {
                        is WidgetRow.Main -> MainRow(context, row.reminder, palette, titleSp, metaSp, row.reminder.id in completing)
                        is WidgetRow.Sub -> SubRow(context, row.reminder, row.item, palette, titleSp)
                        is WidgetRow.Divider -> DateDivider(palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateDivider(palette: WidgetPalette) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(palette.line)),
        ) {}
    }
}

@Composable
private fun MainRow(context: Context, reminder: Reminder, palette: WidgetPalette, titleSp: Int, metaSp: Int, finishing: Boolean) {
    val meta = widgetMeta(reminder)
    val open = actionStartActivity(openReminderIntent(context, reminder.id))
    val status = sectionOf(reminder)
    val red = if (palette.dark) Color(0xFFFF7A7A) else Color(0xFFD32F2F)
    val green = if (palette.dark) Color(0xFF5FD38D) else Color(0xFF2E7D32)
    val titleColor = when (status) {
        Section.Overdue -> red
        Section.Today -> green
        else -> palette.text
    }
    val metaColor = when (status) {
        Section.Overdue -> red
        Section.Today -> green
        else -> palette.text
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only this circle completes the reminder.
        Image(
            provider = ImageProvider(if (finishing) R.drawable.ic_check_circle else R.drawable.ic_circle),
            contentDescription = "تمام شد",
            colorFilter = ColorFilter.tint(ColorProvider(if (finishing) Color(0xFF45B27A) else palette.accent)),
            modifier = GlanceModifier
                .size(30.dp)
                .padding(3.dp)
                // Asks "مطمئنی؟" (or the fingerprint) before the reminder is finished.
                .clickable(actionStartActivity(confirmCompleteIntent(context, reminder.id))),
        )
        // Tapping the text opens this reminder's edit screen.
        Column(
            modifier = GlanceModifier.defaultWeight().padding(start = 8.dp).clickable(open),
        ) {
            Text(
                text = reminder.title.ifBlank { "بدون عنوان" },
                style = if (finishing) {
                    TextStyle(
                        color = ColorProvider(palette.muted),
                        fontSize = titleSp.sp,
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    TextStyle(color = ColorProvider(titleColor), fontSize = titleSp.sp)
                },
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = TextStyle(color = ColorProvider(metaColor), fontSize = metaSp.sp),
                )
            }
        }
        if (reminder.items.isNotEmpty()) {
            Text(
                text = if (reminder.expanded) "▲" else "▼",
                style = TextStyle(color = ColorProvider(palette.accent), fontSize = titleSp.sp),
                modifier = GlanceModifier
                    .padding(8.dp)
                    .clickable(actionRunCallback<ToggleExpandAction>(actionParametersOf(WidgetKeys.id to reminder.id))),
            )
        }
    }
}

@Composable
private fun SubRow(context: Context, reminder: Reminder, item: CheckItem, palette: WidgetPalette, titleSp: Int) {
    val style = if (item.done) {
        TextStyle(
            color = ColorProvider(palette.muted),
            fontSize = (titleSp - 1).sp,
            textDecoration = TextDecoration.LineThrough,
        )
    } else {
        TextStyle(color = ColorProvider(palette.text), fontSize = (titleSp - 1).sp)
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 28.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(if (item.done) R.drawable.ic_check_circle else R.drawable.ic_circle),
            contentDescription = "مورد",
            colorFilter = ColorFilter.tint(ColorProvider(if (item.done) palette.muted else palette.accent)),
            modifier = GlanceModifier
                .size(26.dp)
                .padding(3.dp)
                .clickable(
                    actionRunCallback<ToggleItemAction>(
                        actionParametersOf(WidgetKeys.id to reminder.id, WidgetKeys.item to item.id),
                    ),
                ),
        )
        Text(
            text = item.title,
            style = style,
            modifier = GlanceModifier
                .defaultWeight()
                .padding(start = 8.dp)
                .clickable(actionStartActivity(openReminderIntent(context, reminder.id))),
        )
    }
}

class SetFilterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val value = parameters[WidgetKeys.filter] ?: return
        Graph.settings.setWidgetFilter(value)
        YadavarWidget().updateAll(context)
    }
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        completeFromWidget(context, id)
    }
}

class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        val item = parameters[WidgetKeys.item] ?: return
        Actions.toggleItem(id, item)
        YadavarWidget().updateAll(context)
    }
}

class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        Actions.toggleExpanded(id)
        YadavarWidget().updateAll(context)
    }
}

class YadavarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YadavarWidget()
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AlarmScheduler.reschedule(applicationContext)
        YadavarWidget().updateAll(applicationContext)
        ClockDateWidget().updateAll(applicationContext)
        WidgetRefreshReceiver.scheduleNext(applicationContext)
        return Result.success()
    }
}
