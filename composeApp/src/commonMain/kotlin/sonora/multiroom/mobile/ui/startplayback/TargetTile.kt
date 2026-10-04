package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.TargetOption
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One room or group in "Play in" (design: min-h 64, radius 14, padding 10/12). */
@Composable
internal fun TargetTile(option: TargetOption, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val status = targetStatusText(option.status)
    val shape = SonoraTheme.shapes.tile
    // Unselectable targets are dimmed and announced as disabled; they take no taps.
    val interactive = enabled && option.selectable
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (selected) colors.selectedBg else colors.surface, shape)
            .then(if (selected) Modifier.border(1.dp, colors.selectedOutline, shape) else Modifier)
            .then(if (option.selectable) Modifier else Modifier.alpha(0.5f))
            .selectable(selected = selected, enabled = interactive, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${option.name}, $status" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.name, style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(status, style = type.label12, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RadioDot(selected, 18.dp, Modifier.padding(top = 2.dp))
    }
}
