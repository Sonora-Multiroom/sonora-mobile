package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.SourceOption
import sonora.multiroom.mobile.domain.kindLabel
import sonora.multiroom.mobile.ui.rooms.forKind
import sonora.multiroom.mobile.ui.rooms.iconForKind
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The decorative radio dot of a row or tile; the item itself carries the radio semantics. */
@Composable
internal fun RadioDot(selected: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    Box(
        modifier = modifier.size(size).border(2.dp, if (selected) colors.accent else colors.textMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(size / 2).background(colors.accent, CircleShape))
    }
}

/** One source in "Or pick a source" (design: min-h 54, radius 14, tile 38). */
@Composable
internal fun SourceRow(option: SourceOption, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val kind = colors.forKind(option.kind)
    val label = kindLabel(option.kind)
    val shape = SonoraTheme.shapes.tile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(if (selected) colors.selectedBg else Color.Transparent, shape)
            .then(if (selected) Modifier.border(1.dp, colors.selectedOutline, shape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${option.name}, $label" }
            .padding(start = 8.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(kind.tile, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconForKind(option.kind), contentDescription = null, tint = kind.icon, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(option.name, style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(label, style = type.label12, color = colors.textMuted)
        }
        RadioDot(selected, 20.dp)
    }
}
