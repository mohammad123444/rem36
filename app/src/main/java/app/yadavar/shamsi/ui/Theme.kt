package app.yadavar.shamsi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AccentOption(val id: String, val label: String, val color: Color)

/** Soft pastel color themes of the app (the first one is the default). The color is also the page background. */
val AccentOptions = listOf(
    AccentOption("purple", "بنفش", Color(0xFFB08CEB)),
    AccentOption("indigo", "نیلی", Color(0xFF8FA3EA)),
    AccentOption("blue", "آبی", Color(0xFF7DB6F2)),
    AccentOption("teal", "سبزآبی", Color(0xFF6CCBBF)),
    AccentOption("green", "سبز", Color(0xFF8FD398)),
    AccentOption("amber", "کهربایی", Color(0xFFEFCB5E)),
    AccentOption("orange", "نارنجی", Color(0xFFF5A56B)),
    AccentOption("rose", "صورتی", Color(0xFFF08DB0)),
    AccentOption("brown", "قهوه‌ای", Color(0xFFC4A090)),
    AccentOption("slate", "خاکستری", Color(0xFF9FB1BF)),
)

/** Text colors the person can choose for the texts of the app (and of the widget); "auto" follows the background. */
data class TextColorOption(val id: String, val label: String, val color: Color?)

val TextColorOptions = listOf(
    TextColorOption("auto", "خودکار", null),
    TextColorOption("black", "مشکی", Color(0xFF15131A)),
    TextColorOption("graphite", "گرافیتی", Color(0xFF3A3F47)),
    TextColorOption("white", "سفید", Color(0xFFFFFFFF)),
    TextColorOption("purple", "بنفش", Color(0xFF4A148C)),
    TextColorOption("indigo", "نیلی", Color(0xFF1A237E)),
    TextColorOption("blue", "آبی", Color(0xFF0D47A1)),
    TextColorOption("sky", "آبی آسمانی", Color(0xFF0288D1)),
    TextColorOption("teal", "سبزآبی", Color(0xFF00695C)),
    TextColorOption("green", "سبز", Color(0xFF1B5E20)),
    TextColorOption("lime", "سبز روشن", Color(0xFF7CB342)),
    TextColorOption("gold", "طلایی", Color(0xFFB8860B)),
    TextColorOption("orange", "نارنجی", Color(0xFFE65100)),
    TextColorOption("red", "قرمز", Color(0xFFB71C1C)),
    TextColorOption("rose", "صورتی", Color(0xFFAD1457)),
    TextColorOption("brown", "قهوه‌ای", Color(0xFF5D4037)),
)

/** The chosen text color, or null for "auto". */
fun chosenTextColor(id: String): Color? = TextColorOptions.firstOrNull { it.id == id }?.color

fun accentColor(name: String): Color =
    AccentOptions.firstOrNull { it.id == name }?.color ?: AccentOptions.first().color

/** Size of the texts inside the app (the widget has its own size setting). */
val AppFontOptions = listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ", "xl" to "خیلی بزرگ")

fun appTextScale(id: String): Float = when (id) {
    "sm" -> 0.9f
    "lg" -> 1.15f
    "xl" -> 1.3f
    else -> 1f
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Slightly larger text with roomier lines, for easier reading.
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
)

/** Near-black on light colors, white on dark colors, so text stays readable on any background. */
fun contrastOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF211F26) else Color.White

@Composable
fun YadavarTheme(
    theme: String,
    accent: String,
    textScale: Float = 1f,
    textColorId: String = "auto",
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val seed = accentColor(accent)
    val scheme = if (dark) {
        val background = lerp(Color(0xFF121214), seed, 0.34f)
        val surface = lerp(background, seed, 0.14f)
        val onBackground = contrastOn(background)
        val primary = lerp(seed, Color.White, 0.10f)
        darkColorScheme(
            primary = primary,
            onPrimary = contrastOn(primary),
            primaryContainer = lerp(background, seed, 0.45f),
            onPrimaryContainer = contrastOn(lerp(background, seed, 0.45f)),
            secondaryContainer = lerp(background, seed, 0.40f),
            onSecondaryContainer = contrastOn(lerp(background, seed, 0.40f)),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = contrastOn(surface),
            surfaceVariant = lerp(background, seed, 0.18f),
            onSurfaceVariant = contrastOn(surface).copy(alpha = 0.72f),
            surfaceContainer = lerp(background, seed, 0.16f),
            outline = lerp(seed, background, 0.45f),
        )
    } else {
        val background = lerp(Color.White, seed, 0.80f)
        val surface = lerp(Color.White, seed, 0.30f)
        val primary = lerp(seed, Color.Black, 0.42f)
        lightColorScheme(
            primary = primary,
            onPrimary = contrastOn(primary),
            primaryContainer = seed,
            onPrimaryContainer = contrastOn(seed),
            secondaryContainer = seed,
            onSecondaryContainer = contrastOn(seed),
            background = background,
            onBackground = contrastOn(background),
            surface = surface,
            onSurface = contrastOn(surface),
            surfaceVariant = lerp(Color.White, seed, 0.55f),
            onSurfaceVariant = contrastOn(surface).copy(alpha = 0.72f),
            surfaceContainer = lerp(Color.White, seed, 0.60f),
            outline = lerp(seed, Color.Black, 0.35f),
        )
    }
    val custom = chosenTextColor(textColorId)
    val finalScheme = if (custom == null) {
        scheme
    } else {
        scheme.copy(
            onBackground = custom,
            onSurface = custom,
            onSurfaceVariant = custom.copy(alpha = 0.78f),
        )
    }
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * textScale)) {
        MaterialTheme(colorScheme = finalScheme, shapes = AppShapes, typography = AppTypography, content = content)
    }
}
