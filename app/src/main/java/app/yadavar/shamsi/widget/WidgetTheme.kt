package app.yadavar.shamsi.widget

import androidx.compose.ui.graphics.Color

/** Colors used by the home-screen widget for one background choice. */
data class WidgetPalette(
    val bg: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val line: Color,
    val selected: Color,
    val dark: Boolean,
)

data class WidgetBgOption(val id: String, val label: String, val swatch: Color)

val WidgetBackgrounds = listOf(
    WidgetBgOption("lavender", "بنفش روشن", Color(0xFFD3C0F2)),
    WidgetBgOption("white", "سفید", Color(0xFFFFFFFF)),
    WidgetBgOption("mint", "نعنایی", Color(0xFFB4E3C4)),
    WidgetBgOption("sky", "آبی روشن", Color(0xFFB1D5F5)),
    WidgetBgOption("peach", "هلویی", Color(0xFFF9C6A3)),
    WidgetBgOption("rose", "صورتی", Color(0xFFF5B2C7)),
    WidgetBgOption("lemon", "لیمویی", Color(0xFFF6E58A)),
    WidgetBgOption("ice", "یخی", Color(0xFF98DCE6)),
    WidgetBgOption("lilac", "یاسی", Color(0xFFC9B3F2)),
    WidgetBgOption("dark", "تیره", Color(0xFF1F1B29)),
    WidgetBgOption("navy", "سرمه‌ای", Color(0xFF14213D)),
    WidgetBgOption("plum", "آلویی", Color(0xFF2E1A3C)),
    WidgetBgOption("graphite", "گرافیتی", Color(0xFF26282C)),
)

private val LightText = Color(0xFF2A2133)
private val LightMuted = Color(0xFF6B6178)
private val DarkPurple = Color(0xFF4A148C)
private val LightLine = Color(0x594A148C)

private fun light(bg: Long, selected: Long) = WidgetPalette(
    bg = Color(bg),
    text = LightText,
    muted = LightMuted,
    accent = DarkPurple,
    line = LightLine,
    selected = Color(selected),
    dark = false,
)

private fun darkPalette(bg: Long, selected: Long, accent: Long) = WidgetPalette(
    bg = Color(bg),
    text = Color(0xFFF1ECF8),
    muted = Color(0xFFA79FB5),
    accent = Color(accent),
    line = Color(accent).copy(alpha = 0.4f),
    selected = Color(selected),
    dark = true,
)

fun widgetPalette(id: String): WidgetPalette = when (id) {
    "white" -> light(0xFFFFFFFF, 0xFFE4DAF5)
    "mint" -> light(0xFFB4E3C4, 0xFF94D3AA)
    "sky" -> light(0xFFB1D5F5, 0xFF8FC0EC)
    "peach" -> light(0xFFF9C6A3, 0xFFF2AC7C)
    "rose" -> light(0xFFF5B2C7, 0xFFEC93B0)
    "lemon" -> light(0xFFF6E58A, 0xFFEBD461)
    "ice" -> light(0xFF98DCE6, 0xFF72CCD9)
    "lilac" -> light(0xFFC9B3F2, 0xFFB095E8)
    "navy" -> darkPalette(0xFF14213D, 0xFF2A3C66, 0xFFB8C7F0)
    "plum" -> darkPalette(0xFF2E1A3C, 0xFF47295E, 0xFFE0B8F5)
    "graphite" -> darkPalette(0xFF26282C, 0xFF3A3D43, 0xFFD1B3F5)
    "dark" -> WidgetPalette(
        bg = Color(0xFF1F1B29),
        text = Color(0xFFF1ECF8),
        muted = Color(0xFFA79FB5),
        accent = Color(0xFFD1B3F5),
        line = Color(0x66D1B3F5),
        selected = Color(0xFF3B3050),
        dark = true,
    )
    else -> light(0xFFD3C0F2, 0xFFBBA2E8)
}

/** The palette of [id] with the person's own text color ([textId] "auto" keeps the automatic one). */
fun widgetPaletteWithText(id: String, textId: String): WidgetPalette {
    val base = widgetPalette(id)
    val custom = app.yadavar.shamsi.ui.chosenTextColor(textId) ?: return base
    return base.copy(text = custom, muted = custom.copy(alpha = 0.78f))
}
