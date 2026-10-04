package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.PillModel
import sonora.multiroom.mobile.domain.VolumeSection
import sonora.multiroom.mobile.ui.rooms.IconCircleButton
import sonora.multiroom.mobile.ui.rooms.VolumePill
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The room (or group) pill with its mute button and, for a group, one pill per member
 * (contracts/now-playing-ui.md "Layout" 7). Dragging, throttling and reconciling are the view
 * model's; this only draws what [shownVolume] says.
 */
@Composable
fun VolumeSection(
    section: VolumeSection,
    pending: Map<String, Int>,
    enabled: Boolean,
    muteInFlight: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(section.main, pending, enabled, actions, height = 52.dp, showIcon = true, modifier = Modifier.weight(1f))
            val muteEnabled = enabled && section.mute.enabled && !muteInFlight
            IconCircleButton(
                icon = if (section.mute.muted) SonoraIcons.SpeakerMuted else SonoraIcons.Speaker,
                label = if (section.mute.muted) "Unmute ${section.mute.name}" else "Mute ${section.mute.name}",
                background = colors.surface,
                tint = if (muteEnabled) colors.textSoft else colors.textMuted,
                size = 52.dp,
                enabled = muteEnabled,
                onClick = actions.onMuteToggle,
            )
        }
        // Two columns; an odd count leaves the last cell empty.
        for (pair in section.members.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (member in pair) {
                    Pill(
                        member, pending, enabled, actions, height = 44.dp, showIcon = false,
                        label = member.label, modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Row(Modifier.weight(1f)) {}
            }
        }
        if (section.masterMuted) {
            Text(
                "All rooms are muted",
                style = SonoraTheme.type.body13,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Pill(
    pill: PillModel,
    pending: Map<String, Int>,
    enabled: Boolean,
    actions: NowPlayingActions,
    height: androidx.compose.ui.unit.Dp,
    showIcon: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = SonoraTheme.colors
    VolumePill(
        value = shownVolume(pill, pending),
        muted = pill.muted,
        enabled = enabled,
        contentDescription = "${pill.label} volume",
        modifier = modifier,
        height = height,
        label = label ?: pill.label.takeIf { showIcon },
        labelStyle = if (showIcon) {
            SonoraTheme.type.body14.copy(fontWeight = FontWeight.SemiBold)
        } else {
            SonoraTheme.type.body14
        },
        labelColor = if (showIcon) colors.text else colors.textBright,
        showIcon = showIcon,
        onDragStart = { actions.onVolumeDragStart(pill.key) },
        onDrag = { actions.onVolumeDrag(pill.key, it) },
        onDragEnd = { actions.onVolumeDragEnd(pill.key, it) },
    )
}
