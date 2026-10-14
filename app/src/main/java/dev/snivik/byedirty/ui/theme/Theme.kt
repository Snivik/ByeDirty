package dev.snivik.byedirty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed brand palette rather than dynamic colour, so ByeDirty looks the same on every phone.
// `secondary` doubles as the single-series chart colour; both modes' values pass the chart checks.
private val LightColors = lightColorScheme(
    primary = Lagoon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE6E8),
    onPrimaryContainer = Color(0xFF00252B),
    secondary = Aqua,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F0EC),
    onSecondaryContainer = Color(0xFF00302D),
    tertiary = Color(0xFFB8552C),
    onTertiary = Color.White,
    background = Mist,
    onBackground = Color(0xFF111A1C),
    surface = Mist,
    onSurface = Color(0xFF111A1C),
    surfaceVariant = Color(0xFFDDE6E7),
    onSurfaceVariant = Color(0xFF5B6B6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAFA),
    surfaceContainer = Color(0xFFE9F0F0),
    surfaceContainerHigh = Color(0xFFE3EBEC),
    surfaceContainerHighest = Color(0xFFDCE5E6),
    outline = Color(0xFF9DAEB1),
    outlineVariant = Color(0xFFD3DEDF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED8DE),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF1C4E56),
    onPrimaryContainer = Color(0xFFCFE6E8),
    secondary = AquaDark,
    onSecondary = Color(0xFF00201E),
    secondaryContainer = Color(0xFF1C4542),
    onSecondaryContainer = Color(0xFFC4EDE8),
    tertiary = Color(0xFFFFB596),
    onTertiary = Color(0xFF5A1C00),
    background = Night,
    onBackground = Color(0xFFDFE7E8),
    surface = Night,
    onSurface = Color(0xFFDFE7E8),
    surfaceVariant = Color(0xFF34403F),
    onSurfaceVariant = Color(0xFFA9B8BA),
    surfaceContainerLowest = Color(0xFF172022),
    surfaceContainerLow = Color(0xFF172022),
    surfaceContainer = Color(0xFF1C2628),
    surfaceContainerHigh = Color(0xFF243032),
    surfaceContainerHighest = Color(0xFF2E3A3C),
    outline = Color(0xFF7C8C8E),
    outlineVariant = Color(0xFF34403F),
)

@Composable
fun ByeDirtyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
