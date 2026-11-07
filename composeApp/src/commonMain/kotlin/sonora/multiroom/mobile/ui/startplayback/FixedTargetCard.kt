package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.FixedTargetCard
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The room or group Change source is fixed on, in place of the target picker: not a choice, so no
 * radio and no click. Dimmed while the target cannot take a start.
 */
@Composable
internal fun FixedTargetCard(card: FixedTargetCard, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val status = fixedTargetStatusText(card)
    val shape = SonoraTheme.shapes.tile
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(colors.surface, shape)
            .then(if (card.unusable != null) Modifier.alpha(0.5f) else Modifier)
            .clearAndSetSemantics { contentDescription = "${card.name}, $status" }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(card.name, style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
        Text(status, style = type.label12, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
