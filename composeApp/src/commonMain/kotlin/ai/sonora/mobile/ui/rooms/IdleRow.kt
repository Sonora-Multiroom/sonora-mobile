package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.domain.IdleRow
import ai.sonora.mobile.domain.IdleState
import ai.sonora.mobile.ui.theme.SonoraIcons
import ai.sonora.mobile.ui.theme.SonoraTheme
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One room that plays nothing (contracts/rooms-ui.md "Idle"). */
@Composable
fun IdleRow(row: IdleRow, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val dimmed = row.state != IdleState.NothingPlaying
    val tile = if (dimmed) colors.offTile else colors.surfaceRaised
    val tileIcon = if (dimmed) colors.offTileIcon else colors.idleTileText
    val secondary = when (row.state) {
        IdleState.NothingPlaying -> "Nothing playing"
        IdleState.TurnedOff -> "Turned off"
        IdleState.NotConnected -> "Not connected"
    }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(tile, SonoraTheme.shapes.smallTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (row.state == IdleState.NotConnected) SonoraIcons.RoomNotConnected else SonoraIcons.Room,
                contentDescription = null,
                tint = tileIcon,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.name,
                style = type.body16Semi,
                color = if (dimmed) colors.textMuted else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(secondary, style = type.body13, color = colors.textMuted)
        }
        when (row.state) {
            IdleState.NothingPlaying -> IconCircleButton(
                icon = SonoraIcons.Plus,
                label = "Play something in ${row.name}",
                background = colors.badge,
                tint = colors.accent,
                size = 44.dp,
                onClick = onPlay,
            )

            IdleState.TurnedOff -> Text(
                "Off",
                style = type.label12.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = colors.textMuted,
                modifier = Modifier
                    .border(1.dp, colors.outline, SonoraTheme.shapes.chip)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            IdleState.NotConnected -> Unit
        }
    }
}
