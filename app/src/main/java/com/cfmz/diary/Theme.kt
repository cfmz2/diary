package com.cfmz.diary

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Dark = darkColorScheme(
    primary = Color(0xFF9D8CFF), onPrimary = Color(0xFF14102E),
    primaryContainer = Color(0xFF2B2554), onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFF4FD1C5), onSecondary = Color(0xFF00201D),
    tertiary = Color(0xFFFFB86B), onTertiary = Color(0xFF2B1700),
    background = Color(0xFF0D0E1A), onBackground = Color(0xFFECECF8),
    surface = Color(0xFF16172A), onSurface = Color(0xFFECECF8),
    surfaceVariant = Color(0xFF212340), onSurfaceVariant = Color(0xFF9FA1BF),
    surfaceContainer = Color(0xFF12131F), surfaceContainerHigh = Color(0xFF1C1E36),
    outline = Color(0xFF3A3D5C), outlineVariant = Color(0xFF2A2C47),
    error = Color(0xFFFF6B6B), onError = Color(0xFF330000)
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BDB), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E0FF), onPrimaryContainer = Color(0xFF1B1259),
    secondary = Color(0xFF0E9F94), onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFE98A1F), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5FC), onBackground = Color(0xFF14152B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14152B),
    surfaceVariant = Color(0xFFE9E9F6), onSurfaceVariant = Color(0xFF5E607E),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF0F0FA),
    outline = Color(0xFFC3C5DE), outlineVariant = Color(0xFFE0E1F0),
    error = Color(0xFFD93A3A), onError = Color(0xFFFFFFFF)
)

@Composable
fun DiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
