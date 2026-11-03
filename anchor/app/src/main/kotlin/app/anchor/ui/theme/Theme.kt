package app.anchor.ui.theme

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

// NFR-09: colour pairs chosen for WCAG 2.2 AA contrast (≥ 4.5:1 for body text).
private val Light = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0A2540),
    secondary = Color(0xFF8A4B14),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDC2),
    onSecondaryContainer = Color(0xFF3A1D00),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFE6E2DA),
    onSurfaceVariant = Color(0xFF45464B),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA3C9F0),
    onPrimary = Color(0xFF0A2540),
    primaryContainer = Color(0xFF173B5C),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF3A1D00),
    secondaryContainer = Color(0xFF6A3A0E),
    onSecondaryContainer = Color(0xFFFFDDC2),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2B2E33),
    onSurfaceVariant = Color(0xFFC5C6CC),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AnchorTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, content: @Composable () -> Unit) {
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
