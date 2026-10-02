package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.domain.CardAction
import ai.sonora.mobile.domain.NowPlayingCard
import ai.sonora.mobile.ui.theme.SonoraIcons
import ai.sonora.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A now-playing card (contracts/rooms-ui.md, design/screens/Main.dc.html). [stale] means the hub
 * could not be reached: the text is dimmed with `textMuted` (not alpha) and every control that
 * would change something is disabled.
 */
@Composable
fun NowPlayingCard(
    card: NowPlayingCard,
    volume: Int?,
    stale: Boolean,
    actionInFlight: Boolean,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    pill: @Composable (volume: Int) -> Unit = { v ->
        VolumePill(
            value = v,
            muted = card.muted,
            enabled = card.volumeAdjustable && !card.muted && !stale,
            contentDescription = "${card.title} volume",
        )
    },
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val kind = colors.forKind(card.kind)
    val primary = if (stale) colors.textMuted else colors.text

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SonoraTheme.shapes.card)
            .padding(PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(56.dp).background(kind.tile, SonoraTheme.shapes.tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(iconForKind(card.kind), contentDescription = null, tint = kind.icon, modifier = Modifier.size(26.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            card.title,
                            style = type.cardTitle,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (card.isGroup) {
                            Text(
                                "Group",
                                style = type.label12.copy(fontSize = type.label12.fontSize * 11 / 12),
                                color = colors.badgeText,
                                modifier = Modifier
                                    .background(colors.badge, SonoraTheme.shapes.badge)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(card.sourceName, style = type.body14, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        statusLine(card),
                        style = type.body13,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ActionButton(card, stale = stale, inFlight = actionInFlight, onClick = onAction)
        }
        if (volume != null) pill(volume)
    }
}

@Composable
private fun ActionButton(card: NowPlayingCard, stale: Boolean, inFlight: Boolean, onClick: () -> Unit) {
    val colors = SonoraTheme.colors
    val enabled = card.actionEnabled && !stale && !inFlight
    val (icon, verb) = when (card.action) {
        CardAction.Stop -> SonoraIcons.Stop to "Stop"
        CardAction.Pause -> SonoraIcons.Pause to "Pause"
        CardAction.Resume -> SonoraIcons.Play to "Resume"
    }
    val background = when {
        !enabled -> colors.surfaceRaised
        card.action == CardAction.Pause -> colors.accent
        else -> colors.outline
    }
    val tint = when {
        !enabled -> colors.textMuted
        card.action == CardAction.Pause -> colors.onAccent
        else -> colors.text
    }
    IconCircleButton(
        icon = icon,
        label = "$verb ${card.title}",
        background = background,
        tint = tint,
        enabled = enabled,
        onClick = onClick,
    )
}
