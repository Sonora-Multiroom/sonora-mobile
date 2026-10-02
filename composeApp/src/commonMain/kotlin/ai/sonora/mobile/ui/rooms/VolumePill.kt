package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.ui.theme.SonoraIcons
import ai.sonora.mobile.ui.theme.SonoraTheme
import ai.sonora.mobile.ui.theme.minTouchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The volume control: a 44 dp fully rounded bar that fills with `accentContainer`, speaker icon on
 * the left, percentage on the right. Deliberately no thumb and no thin track, so it never looks
 * like a progress bar (FR-012, Constitution II).
 */
@Composable
fun VolumePill(
    value: Int,
    muted: Boolean,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(minTouchTarget)
            .clip(SonoraTheme.shapes.pill)
            .background(colors.surfaceRaised)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = value.coerceIn(0, 100) / 100f)
                .background(colors.accentContainer),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp).clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = if (muted) SonoraIcons.SpeakerMuted else SonoraIcons.Speaker,
                contentDescription = null,
                tint = if (enabled || muted) colors.accent else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "$value%",
                style = SonoraTheme.type.body13Semi.copy(fontFeatureSettings = "tnum"),
                color = colors.text,
            )
        }
    }
}
