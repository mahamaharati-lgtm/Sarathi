package com.k.sarathi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.k.sarathi.R
import com.k.sarathi.data.ThemeChoice
import java.time.LocalTime

@Immutable
data class Palette(
    val id: ThemeChoice,
    val isDark: Boolean,
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
) {
    val accentSoft get() = accent.copy(alpha = if (isDark) 0.14f else 0.12f)
    val glow get() = accent.copy(alpha = if (isDark) 0.35f else 0.0f)
}

object Palettes {
    val Arc = Palette(
        ThemeChoice.ARC, true,
        bg = Color(0xFF070B11), bg2 = Color(0xFF0B1422), surface = Color(0xFF0E1622), surfaceHi = Color(0xFF15213A),
        line = Color(0xFF1F2D42), text = Color(0xFFE6F1FA), textDim = Color(0xFF97A8BC), textFaint = Color(0xFF66778C),
        accent = Color(0xFF4FD8FF), accent2 = Color(0xFF8CA2FF), onAccent = Color(0xFF00131C),
        good = Color(0xFF4ADE9A), warn = Color(0xFFFFB547), bad = Color(0xFFFF6B6B),
    )
    val Saffron = Palette(
        ThemeChoice.SAFFRON, true,
        bg = Color(0xFF0D0906), bg2 = Color(0xFF181009), surface = Color(0xFF1A120B), surfaceHi = Color(0xFF261A10),
        line = Color(0xFF35271B), text = Color(0xFFFBEFE3), textDim = Color(0xFFC9AE95), textFaint = Color(0xFF8C7560),
        accent = Color(0xFFFF9933), accent2 = Color(0xFFFFD166), onAccent = Color(0xFF1F0E00),
        good = Color(0xFF7BD88F), warn = Color(0xFFFFD166), bad = Color(0xFFFF6B57),
    )
    val Ember = Palette(
        ThemeChoice.EMBER, true,
        bg = Color(0xFF0E0707), bg2 = Color(0xFF190C0B), surface = Color(0xFF1B0F0E), surfaceHi = Color(0xFF281614),
        line = Color(0xFF38211E), text = Color(0xFFFBEAE7), textDim = Color(0xFFCCA39C), textFaint = Color(0xFF8E6760),
        accent = Color(0xFFFF6A47), accent2 = Color(0xFFFFA36B), onAccent = Color(0xFF1F0500),
        good = Color(0xFF7BD88F), warn = Color(0xFFFFC15E), bad = Color(0xFFFF4D6D),
    )
    val Nebula = Palette(
        ThemeChoice.NEBULA, true,
        bg = Color(0xFF09070F), bg2 = Color(0xFF120E1F), surface = Color(0xFF151024), surfaceHi = Color(0xFF1F1832),
        line = Color(0xFF2C2443), text = Color(0xFFF0EBFF), textDim = Color(0xFFAEA1CC), textFaint = Color(0xFF756A96),
        accent = Color(0xFFB57BFF), accent2 = Color(0xFFFF7BD5), onAccent = Color(0xFF14002B),
        good = Color(0xFF6EE7B7), warn = Color(0xFFFCD34D), bad = Color(0xFFFB7185),
    )
    /** Light theme for daylight readability (contrast-checked to WCAG AA for text). */
    val Day = Palette(
        ThemeChoice.DAY, false,
        bg = Color(0xFFF6F3EE), bg2 = Color(0xFFEDE6DB), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFF8F4EE),
        line = Color(0xFFE2D9CC), text = Color(0xFF1E1A16), textDim = Color(0xFF5C5248), textFaint = Color(0xFF857A6F),
        accent = Color(0xFFB85700), accent2 = Color(0xFF0E7383), onAccent = Color(0xFFFFFFFF),
        good = Color(0xFF1B7F50), warn = Color(0xFFA86A12), bad = Color(0xFFC0302F),
    )

    fun of(choice: ThemeChoice) = when (choice) {
        ThemeChoice.ARC -> Arc
        ThemeChoice.SAFFRON -> Saffron
        ThemeChoice.EMBER -> Ember
        ThemeChoice.NEBULA -> Nebula
        ThemeChoice.DAY -> Day
        ThemeChoice.AUTO -> Arc
    }

    /** Saffron at dawn · Arc (or Day) by day · Ember at dusk · Nebula at night. */
    fun auto(t: LocalTime, dayInDaylight: Boolean): Palette = when (t.hour) {
        in 5..8 -> Saffron
        in 9..16 -> if (dayInDaylight) Day else Arc
        in 17..19 -> Ember
        else -> Nebula
    }

    fun resolve(choice: ThemeChoice, t: LocalTime, dayInDaylight: Boolean) =
        if (choice == ThemeChoice.AUTO) auto(t, dayInDaylight) else of(choice)
}

private fun variable(res: Int, weight: Int) = Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

object Fonts {
    val Orbitron = FontFamily(variable(R.font.orbitron, 500), variable(R.font.orbitron, 700), variable(R.font.orbitron, 900))
    val Rajdhani = FontFamily(Font(R.font.rajdhani_semibold, FontWeight.SemiBold), Font(R.font.rajdhani_bold, FontWeight.Bold))
    val Inter = FontFamily(variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600), variable(R.font.inter, 700))
    val Mono = FontFamily(variable(R.font.jbmono, 400), variable(R.font.jbmono, 500), variable(R.font.jbmono, 700))
}

@Immutable
data class SarType(
    val clock: TextStyle = TextStyle(fontFamily = Fonts.Orbitron, fontWeight = FontWeight.Bold, fontSize = 56.sp, letterSpacing = 2.sp),
    val display: TextStyle = TextStyle(fontFamily = Fonts.Orbitron, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 1.sp),
    val headline: TextStyle = TextStyle(fontFamily = Fonts.Rajdhani, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    val title: TextStyle = TextStyle(fontFamily = Fonts.Rajdhani, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp),
    val label: TextStyle = TextStyle(fontFamily = Fonts.Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.6.sp),
    val body: TextStyle = TextStyle(fontFamily = Fonts.Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = Fonts.Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    val small: TextStyle = TextStyle(fontFamily = Fonts.Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    val num: TextStyle = TextStyle(fontFamily = Fonts.Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    val bigNum: TextStyle = TextStyle(fontFamily = Fonts.Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
)

val LocalPalette = staticCompositionLocalOf { Palettes.Arc }
val LocalSarType = staticCompositionLocalOf { SarType() }

object Sar {
    val c: Palette @Composable get() = LocalPalette.current
    val t: SarType @Composable get() = LocalSarType.current
}

private fun scheme(p: Palette): ColorScheme = if (p.isDark) darkColorScheme(
    primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.surfaceHi, onPrimaryContainer = p.text,
    secondary = p.accent2, onSecondary = p.onAccent, background = p.bg, onBackground = p.text,
    surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHi, onSurfaceVariant = p.textDim,
    surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHi, surfaceContainerHighest = p.surfaceHi,
    surfaceContainerLow = p.bg2, surfaceContainerLowest = p.bg,
    outline = p.line, outlineVariant = p.line, error = p.bad, onError = p.onAccent,
    inverseSurface = p.text, inverseOnSurface = p.bg, inversePrimary = p.accent,
) else lightColorScheme(
    primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.surfaceHi, onPrimaryContainer = p.text,
    secondary = p.accent2, onSecondary = p.onAccent, background = p.bg, onBackground = p.text,
    surface = p.surface, onSurface = p.text, surfaceVariant = p.surfaceHi, onSurfaceVariant = p.textDim,
    surfaceContainer = p.surface, surfaceContainerHigh = p.surfaceHi, surfaceContainerHighest = p.bg2,
    surfaceContainerLow = p.bg, surfaceContainerLowest = p.surface,
    outline = p.line, outlineVariant = p.line, error = p.bad, onError = p.onAccent,
    inverseSurface = p.text, inverseOnSurface = p.bg, inversePrimary = p.accent,
)

@Composable
fun SarathiTheme(palette: Palette, content: @Composable () -> Unit) {
    val type = SarType()
    val m3Type = Typography(
        displayLarge = type.display, headlineMedium = type.headline, titleLarge = type.title,
        titleMedium = type.bodyStrong, bodyLarge = type.body, bodyMedium = type.small,
        labelLarge = type.label.copy(fontSize = 15.sp, letterSpacing = 1.sp), labelMedium = type.label, labelSmall = type.label.copy(fontSize = 11.sp),
    )
    CompositionLocalProvider(LocalPalette provides palette, LocalSarType provides type) {
        MaterialTheme(colorScheme = scheme(palette), typography = m3Type, content = content)
    }
}
