package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.stationDisplayName
import sonora.multiroom.mobile.ui.rooms.MarqueeText
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One station in a list (contracts/radio-ui.md "A station row"): a stream-coloured tile, the name
 * on one line ending in "…" and a second line of the known facts. The name scrolls instead when
 * [scrollName] is set (the selected station in Start Playback). Never shows votes, images or the
 * station's address. Read as one node.
 */
@Composable
fun StationRow(
    station: Station,
    secondLine: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    scrollName: Boolean = false,
    stale: Boolean = false,
    enabled: Boolean = true,
    role: Role = Role.Button,
    spoken: String = stationSpoken(station, secondLine),
    trailing: @Composable () -> Unit = {},
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val shape = SonoraTheme.shapes.tile
    val kind = colors.kindStream
    val nameStyle = type.body15.copy(fontWeight = FontWeight.SemiBold)
    val interaction = if (role == Role.RadioButton) {
        Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
    } else {
        Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(if (selected) colors.selectedBg else Color.Transparent, shape)
            .then(if (selected) Modifier.border(1.dp, colors.selectedOutline, shape) else Modifier)
            .then(interaction)
            .semantics { contentDescription = spoken }
            .padding(start = 8.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(kind.tile, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SonoraIcons.Stream, contentDescription = null, tint = kind.icon, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val name = stationDisplayName(station)
            if (scrollName) {
                MarqueeText(name, style = nameStyle, color = colors.text, maxLines = 1, stale = stale)
            } else {
                Text(name, style = nameStyle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (secondLine != null) {
                Text(secondLine, style = type.label12, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** A grey stand-in for a station row while the first page loads. */
@Composable
fun StationPlaceholderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 8.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(SonoraTheme.colors.surface, RoundedCornerShape(10.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 160.dp, height = 12.dp).background(SonoraTheme.colors.surface, RoundedCornerShape(6.dp)))
            Box(Modifier.size(width = 90.dp, height = 10.dp).background(SonoraTheme.colors.surface, RoundedCornerShape(6.dp)))
        }
    }
}
