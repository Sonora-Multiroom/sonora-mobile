package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.MoveDestination
import sonora.multiroom.mobile.domain.MoveDestinationKind
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.domain.warning
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import sonora.multiroom.mobile.ui.theme.minTouchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The "Move playback" bottom sheet (contracts/now-playing-ui.md, design/screens/Transfer.dc.html).
 * Closing it by swipe, scrim or Back calls [onDismiss]; nothing is requested until [onConfirm].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(
    state: MoveSheetState,
    /** False while the hub is unreachable or a move is already running: the button cannot be used. */
    canConfirm: Boolean,
    onSelect: (Target) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val content = state.content
    val selected = (content.rooms + content.groups).firstOrNull { it.target == state.selected && it.selectable }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.sheetHandle, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Move playback", style = type.cardTitle.copy(fontSize = 22.sp), color = colors.text)
                Text(
                    "${content.sourceName} · now on ${content.currentTargetName}",
                    style = type.body14,
                    color = colors.textMuted,
                )
            }

            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Section("Move to", content.rooms, state.selected, onSelect)
                if (content.groups.isNotEmpty()) Section("Groups", content.groups, state.selected, onSelect)
                if (content.rooms.isEmpty() && content.groups.isEmpty()) {
                    Text("Nothing to move to", style = type.body14, color = colors.textMuted)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(SonoraIcons.Check, contentDescription = null, tint = colors.kindLineIn.icon, modifier = Modifier.size(16.dp))
                Text("Keeps playing while it moves, no restart", style = type.body13, color = colors.textMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val enabled = canConfirm && selected != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .alpha(if (enabled) 1f else 0.5f)
                        .clip(SonoraTheme.shapes.pill)
                        .background(colors.accent)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onConfirm),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SonoraIcons.Arrow, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
                    Text(selected?.let { "Move to ${it.ctaName}" } ?: "Move", style = type.button16, color = colors.onAccent)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minTouchTarget)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", style = type.body15.copy(fontWeight = FontWeight.Medium), color = colors.textSoft)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, rows: List<MoveDestination>, selected: Target?, onSelect: (Target) -> Unit) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            style = SonoraTheme.type.sectionLabel,
            color = SonoraTheme.colors.textMuted,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        for (row in rows) {
            DestinationRow(row, isSelected = row.selectable && row.target == selected, onSelect = { onSelect(row.target) })
        }
    }
}

@Composable
private fun DestinationRow(row: MoveDestination, isSelected: Boolean, onSelect: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val shape = SonoraTheme.shapes.tile
    val note = destinationNoteText(row.note)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .alpha(if (row.selectable) 1f else 0.5f)
            .clip(shape)
            .background(if (isSelected) colors.selectedBg else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (isSelected) Modifier.border(1.dp, colors.selectedOutline, shape) else Modifier)
            .selectable(selected = isSelected, enabled = row.selectable, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = "${row.label}, $note" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The row's own semantics carry the label and the note; its parts add nothing.
        Box(
            modifier = Modifier.size(38.dp).background(colors.surfaceRaised, SonoraTheme.shapes.chip).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (row.kind == MoveDestinationKind.Group) SonoraIcons.Group else SonoraIcons.Room,
                contentDescription = null,
                tint = colors.textSoft,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(row.label, style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(note, style = type.label12.copy(fontWeight = FontWeight.Normal), color = if (row.note.warning) colors.warningText else colors.textMuted)
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (isSelected) colors.accent else colors.textMuted, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) Box(Modifier.size(10.dp).background(colors.accent, CircleShape))
        }
    }
}
