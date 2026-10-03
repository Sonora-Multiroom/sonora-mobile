package sonora.multiroom.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

private val LocalColors = staticCompositionLocalOf { SonoraColors() }
private val LocalShapes = staticCompositionLocalOf { SonoraShapes() }
private val LocalType = staticCompositionLocalOf<SonoraType> { error("SonoraTheme missing") }

/** Access to the design tokens: `SonoraTheme.colors`, `.type`, `.shapes`. */
object SonoraTheme {
    val colors: SonoraColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: SonoraType @Composable @ReadOnlyComposable get() = LocalType.current
    val shapes: SonoraShapes @Composable @ReadOnlyComposable get() = LocalShapes.current
}

private fun SonoraColors.toMaterial(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentContainer,
    onPrimaryContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textMuted,
    outline = outline,
    outlineVariant = outline,
    inverseSurface = surfaceRaised,
    inverseOnSurface = text,
    inversePrimary = accent,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceRaised,
    error = warningText,
)

/** Wraps content in the Sonora tokens and a dark material3 scheme mapped from them. */
@Composable
fun SonoraTheme(content: @Composable () -> Unit) {
    val colors = SonoraColors()
    val type = rememberSonoraType()
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalShapes provides SonoraShapes(),
        LocalType provides type,
    ) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = Typography()) {
            Box(Modifier.fillMaxSize().background(colors.background)) { content() }
        }
    }
}
