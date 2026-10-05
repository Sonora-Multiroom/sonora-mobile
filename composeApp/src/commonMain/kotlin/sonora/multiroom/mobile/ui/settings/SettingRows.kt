package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.ConfiguredSourceRow
import sonora.multiroom.mobile.domain.ExtensionBadge
import sonora.multiroom.mobile.domain.ExtensionRow
import sonora.multiroom.mobile.domain.GroupRow
import sonora.multiroom.mobile.domain.RoomRow
import sonora.multiroom.mobile.domain.RoomStatus
import sonora.multiroom.mobile.domain.RuntimeSourceRow
import sonora.multiroom.mobile.ui.rooms.forKind
import sonora.multiroom.mobile.ui.rooms.iconForKind
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The list card (UI contract "List card"): rows divided by a 1 dp line, none after the last. */
@Composable
fun <T> SettingsCard(
    items: List<T>,
    modifier: Modifier = Modifier,
    /** The design gives the runtime rows 6 dp at the end, so the trash button sits close to the edge. */
    endPadding: androidx.compose.ui.unit.Dp = 14.dp,
    row: @Composable ColumnScope.(T) -> Unit,
) {
    val colors = SonoraTheme.colors
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(colors.surface, SonoraTheme.shapes.card)
            .padding(start = 14.dp, end = endPadding, top = 4.dp, bottom = 4.dp),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowDivider))
            row(item)
        }
    }
}

/**
 * The shared body of a switch row. The whole row toggles and is the switch for accessibility
 * (FR-023): its texts, name first, are read once as the switch's label with its on/off state,
 * announced as disabled when stale or in flight.
 */
@Composable
private fun SwitchRow(
    item: Item<*>,
    controlsEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    minHeight: androidx.compose.ui.unit.Dp,
    tile: @Composable () -> Unit,
    lines: @Composable ColumnScope.() -> Unit,
) {
    val enabled = controlsEnabled && !item.inFlight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(value = item.shownEnabled, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = lines)
        SonoraSwitch(checked = item.shownEnabled)
    }
}

@Composable
private fun IconTile(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 40.dp, radius: androidx.compose.ui.unit.Dp = 12.dp) {
    val colors = SonoraTheme.colors
    Box(
        Modifier.size(size).background(colors.surfaceRaised, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun RoomSettingRow(item: Item<RoomRow>, controlsEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val row = item.row
    SwitchRow(item, controlsEnabled, onToggle, 64.dp, tile = { IconTile(SonoraIcons.Room) }) {
        Text(
            row.name,
            style = type.body16Semi,
            color = if (item.shownEnabled) colors.text else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            roomStatusText(row.status),
            style = type.body13,
            color = if (row.status is RoomStatus.Playing) colors.accent else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GroupSettingRow(item: Item<GroupRow>, controlsEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val row = item.row
    SwitchRow(item, controlsEnabled, onToggle, 72.dp, tile = { IconTile(SonoraIcons.GroupStack) }) {
        Text(
            row.name,
            style = type.body16Semi,
            color = if (item.shownEnabled) colors.text else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(groupMembersText(row.members), style = type.body13.copy(lineHeight = 17.55.sp), color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        groupPlayingText(row.playing)?.let {
            Text(it, style = type.body13, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ConfiguredSourceSettingRow(item: Item<ConfiguredSourceRow>, controlsEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val row = item.row
    val kind = colors.forKind(row.kind)
    SwitchRow(
        item, controlsEnabled, onToggle, 60.dp,
        tile = {
            Box(
                Modifier.size(38.dp).background(kind.tile, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconForKind(row.kind), contentDescription = null, tint = kind.icon, modifier = Modifier.size(20.dp))
            }
        },
    ) {
        Text(
            row.name,
            style = type.body15.copy(fontWeight = FontWeight.SemiBold),
            color = if (item.shownEnabled) colors.text else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(configuredLine(row), style = type.label12, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A source added at run time: the added line under its name and a trash button (UI contract "List
 * card"). Card padding is 4 6 4 14, so the 44 dp button sits close to the edge.
 */
@Composable
fun RuntimeSourceSettingRow(item: Item<RuntimeSourceRow>, controlsEnabled: Boolean, onRemove: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val row = item.row
    val kind = colors.forKind(sonora.multiroom.mobile.domain.SourceKind.Link)
    val canRemove = controlsEnabled && !item.removing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (item.removing) 0.5f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(kind.tile, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconForKind(sonora.multiroom.mobile.domain.SourceKind.Link), contentDescription = null, tint = kind.icon, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.name,
                style = type.body15.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = if (item.removing) REMOVING_LINE else addedLineText(row.added)
            if (line != null) Text(line, style = type.label12, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .alpha(if (canRemove) 1f else 0.5f)
                .clickable(enabled = canRemove, role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeLabel(row.name) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SonoraIcons.Trash, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(20.dp))
        }
    }
}

/** One extension: name, connection line and the status badge. Read-only, not clickable. */
@Composable
fun ExtensionSettingRow(row: ExtensionRow) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.name,
                style = type.body15.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(extensionLineText(row.line), style = type.label12, color = colors.textMuted)
        }
        val (content, container) = when (row.badge) {
            ExtensionBadge.Active -> colors.positive to colors.positiveContainer
            ExtensionBadge.Rejected -> colors.danger to colors.dangerContainer
            else -> colors.textMuted to colors.surfaceRaised
        }
        Row(
            modifier = Modifier.background(container, SonoraTheme.shapes.pill).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(content, CircleShape))
            Text(extensionBadgeText(row.badge), style = type.label12.copy(fontWeight = FontWeight.SemiBold), color = content)
        }
    }
}
