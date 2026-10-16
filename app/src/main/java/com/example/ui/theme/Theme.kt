package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SoundglassDarkColorScheme = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = PurpleSecondaryDark,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = CyanAccentDark,
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF004F58),
    onTertiaryContainer = Color(0xFF97F0FF),
    error = RedClipping,
    onError = Color.White,
    background = CharcoalBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = CharcoalBorderDark
)

private val SoundglassLightColorScheme = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = CyanAccentLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4E7EB),
    onTertiaryContainer = Color(0xFF001F24),
    error = RedClipping,
    onError = Color.White,
    background = CharcoalBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = CharcoalSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CharcoalSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = CharcoalBorderLight
)

@Composable
fun SoundglassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default to custom branded charcoal & purple audio design
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SoundglassDarkColorScheme
        else -> SoundglassLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
