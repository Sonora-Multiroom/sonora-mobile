package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The hub row (UI contract "Layout"): one button that opens the address sheet. */
@Composable
fun HubRowView(row: HubRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val label = hubRowLabel(row)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
            .heightIn(min = 68.dp)
            .background(colors.surface, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(colors.surfaceRaised, SonoraTheme.shapes.smallTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SonoraIcons.Server, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hub", style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
                val status = hubStatusText(row.status)
                if (status != null) {
                    val tint = statusColor(row.status)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(tint, CircleShape))
                        Text(status, style = type.label12.copy(fontWeight = FontWeight.SemiBold), color = tint)
                    }
                }
            }
            Text(
                hubAddressLine(row),
                style = type.body13,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(SonoraIcons.Chevron, contentDescription = null, tint = colors.chevron, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun statusColor(status: HubStatus): Color = when (status) {
    HubStatus.Connected -> SonoraTheme.colors.positive
    HubStatus.NotConnected -> SonoraTheme.colors.danger
    else -> SonoraTheme.colors.textMuted
}
