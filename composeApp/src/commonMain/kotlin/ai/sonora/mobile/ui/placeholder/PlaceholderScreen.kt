package ai.sonora.mobile.ui.placeholder

import ai.sonora.mobile.ui.rooms.IconCircleButton
import ai.sonora.mobile.ui.theme.SonoraIcons
import ai.sonora.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A screen that exists so navigation is complete but whose content comes in a later feature.
 * [onBack] is given when it was opened from a card or row and shows a "Back" arrow.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = SonoraTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            IconCircleButton(
                icon = SonoraIcons.Back,
                label = "Back",
                background = colors.surface,
                tint = colors.text,
                size = 44.dp,
                onClick = onBack,
            )
        }
        Text(title, style = SonoraTheme.type.screenTitle, color = colors.text)
        Text("Coming soon", style = SonoraTheme.type.cardTitle, color = colors.accent)
        Text(description, style = SonoraTheme.type.body14, color = colors.textMuted)
    }
}
