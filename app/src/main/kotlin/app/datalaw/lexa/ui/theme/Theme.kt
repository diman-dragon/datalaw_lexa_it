package app.datalaw.lexa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.datalaw.lexa.store.ThemeMode
import android.app.Activity

/** Токены оформления DataLaw DesignLab: «светло-коричневая» (по умолчанию) и AMOLED. */
@Immutable
class LexaColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val warm: Color,
    val danger: Color,
) {
    val accentSoft: Color get() = accent.copy(alpha = if (dark) 0.14f else 0.13f)
    val warmSoft: Color get() = warm.copy(alpha = if (dark) 0.14f else 0.12f)
    val accentLine: Color get() = accent.copy(alpha = if (dark) 0.45f else 0.5f)
}

private val LightPalette = LexaColors(
    dark = false,
    bg = Color(0xFFECDCC4), surface = Color(0xFFF6ECDB), surface2 = Color(0xFFE3D0B3),
    ink = Color(0xFF3B2A1A), ink2 = Color(0xFF5F4A31), ink3 = Color(0xFF86704F),
    line = Color(0xFFD0B891), accent = Color(0xFF8A5A2C), accentInk = Color(0xFFFFF7EA),
    warm = Color(0xFFA33A2A), danger = Color(0xFFB3342C),
)

private val DarkPalette = LexaColors(
    dark = true,
    bg = Color(0xFF000000), surface = Color(0xFF0E0E10), surface2 = Color(0xFF18181B),
    ink = Color(0xFFF4F0E9), ink2 = Color(0xFFC9C3B8), ink3 = Color(0xFF8F8B84),
    line = Color(0xFF25252A), accent = Color(0xFFD9B48A), accentInk = Color(0xFF1A1209),
    warm = Color(0xFFE8836A), danger = Color(0xFFFF6B6B),
)

val LocalLexa = staticCompositionLocalOf { LightPalette }

/** Короткий доступ: `L.c.accent` */
object L {
    val c: LexaColors
        @Composable @ReadOnlyComposable get() = LocalLexa.current
}

val Serif: FontFamily = FontFamily.Serif

/** Заголовки переносятся «поровну» (Heading), абзацы — с оптимальной разбивкой (Paragraph); без переноса внутри слов. */
private val HEAD = LineBreak.Heading
private val BODY = LineBreak.Paragraph

private val LexaTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, lineBreak = HEAD, hyphens = Hyphens.None),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, lineBreak = HEAD, hyphens = Hyphens.None),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, lineBreak = HEAD, hyphens = Hyphens.None),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, lineBreak = HEAD, hyphens = Hyphens.None),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, lineBreak = BODY, hyphens = Hyphens.None),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, lineBreak = BODY, hyphens = Hyphens.None),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, lineBreak = BODY, hyphens = Hyphens.None),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, hyphens = Hyphens.None),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp, hyphens = Hyphens.None),
)

@Composable
fun LexaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.AMOLED -> true
    }
    val c = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.accentInk, background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surface2, onSurfaceVariant = c.ink2,
            outline = c.line, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.accentInk, background = c.bg, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surface2, onSurfaceVariant = c.ink2,
            outline = c.line, error = c.danger,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // светлые иконки статус-бара — на тёмном фоне
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    CompositionLocalProvider(LocalLexa provides c) {
        MaterialTheme(colorScheme = scheme, typography = LexaTypography, content = content)
    }
}
