package io.github.doubletwist13.orbit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// Green-phosphor terminal look. Pure black background so OLED pixels stay off.
private val Phosphor = Color(0xFF33FF66)
private val PhosphorDim = Color(0xFF1FA84A)
private val PhosphorDeep = Color(0xFF0B4A1E)
private val Amber = Color(0xFFFFB000) // errors must stand out from the green

private val scheme = darkColorScheme(
    primary = Phosphor, onPrimary = Color.Black,
    secondary = PhosphorDim, onSecondary = Color.Black,
    background = Color.Black, onBackground = Phosphor,
    surface = Color.Black, onSurface = Phosphor,
    surfaceVariant = Color.Black, onSurfaceVariant = PhosphorDim,
    outline = PhosphorDim, outlineVariant = PhosphorDeep,
    error = Amber, onError = Color.Black,
)

private val typography = Typography().let { t ->
    val mono = FontFamily.Monospace
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontFamily = mono),
        titleLarge = t.titleLarge.copy(fontFamily = mono),
        bodyLarge = t.bodyLarge.copy(fontFamily = mono),
        labelLarge = t.labelLarge.copy(fontFamily = mono),
    )
}

@Composable
fun OrbitTheme(content: @Composable () -> Unit) = MaterialTheme(scheme, typography = typography, content = content)
