package com.studiofagnani.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Viola smorzato, leggibile su schermo chiaro e scuro. */
private val Viola = Color(0xFF6B4E87)
private val VioloChiaro = Color(0xFFD5C2E6)
private val Senape = Color(0xFFB8893B)
private val Rosso = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Viola,
    onPrimary = Color.White,
    primaryContainer = VioloChiaro,
    onPrimaryContainer = Color(0xFF24103B),
    secondary = Senape,
    onSecondary = Color.White,
    error = Rosso,
    background = Color(0xFFFCF8FB),
    surface = Color(0xFFFCF8FB),
    surfaceVariant = Color(0xFFEDE3EE),
)

private val DarkColors = darkColorScheme(
    primary = VioloChiaro,
    onPrimary = Color(0xFF24103B),
    primaryContainer = Color(0xFF4D3663),
    onPrimaryContainer = VioloChiaro,
    secondary = Color(0xFFE2BE7F),
    onSecondary = Color(0xFF3A2A08),
    error = Color(0xFFF2B8B5),
    background = Color(0xFF15111A),
    surface = Color(0xFF15111A),
    surfaceVariant = Color(0xFF2C2433),
)

/**
 * Testo grande (SPEC §3): l'app si usa in piedi, a un braccio di distanza, a volte
 * mentre si fa altro. Nessuna dimensione sotto i 14 sp.
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

/** Spaziature usate in tutta l'app. */
object Spazi {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

@Composable
fun StudioFagnaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
