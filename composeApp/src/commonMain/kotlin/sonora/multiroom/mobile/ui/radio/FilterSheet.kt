package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.groupThousands
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The filter sheets (country, tag, language, order): a modal bottom sheet in the style of the Move
 * to room sheet. Closing it by Cancel, scrim, swipe or Back calls [onDismiss] and changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    state: FilterSheetState,
    onSearch: (String) -> Unit,
    onChoose: (FilterRow) -> Unit,
    onListPosition: (Int) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
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
            Text(
                RadioText.sheetTitle(state.kind),
                style = type.cardTitle.copy(fontSize = 22.sp),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            RadioText.searchLabel(state.kind)?.let { label ->
                SheetField(state.searchText, label, onSearch)
            }
            SheetRows(state, onChoose, onListPosition, onRetry, modifier = Modifier.weight(1f, fill = false))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchTarget)
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(RadioText.CANCEL, style = type.body15.copy(fontWeight = FontWeight.Medium), color = colors.textSoft)
            }
        }
    }
}

@Composable
private fun SheetField(text: String, label: String, onChange: (String) -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surfaceRaised, SonoraTheme.shapes.tile)
            .padding(start = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SonoraIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(label, style = type.body15, color = colors.textMuted, maxLines = 1)
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type.body15.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun SheetRows(
    state: FilterSheetState,
    onChoose: (FilterRow) -> Unit,
    onListPosition: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    val listState = rememberLazyListState()

    // The chosen row is in view when the list is there.
    LaunchedEffect(state.kind, state.loading) {
        if (!state.loading) state.chosenIndex?.let { listState.scrollToItem(it) }
    }
    val lastVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(lastVisible, state.rows.size, state.loadingMore) { onListPosition(lastVisible) }

    LazyColumn(
        state = listState,
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.problem != null) {
            item(key = "problem") { CatalogueCard(onRetry = onRetry) }
        }
        if (state.loading) {
            item(key = "loading") {
                Column(
                    modifier = Modifier.semantics {
                        contentDescription = RadioText.LOADING
                        liveRegion = LiveRegionMode.Polite
                    },
                ) {
                    repeat(PLACEHOLDER_ROWS) { StationPlaceholderRow() }
                }
            }
        }
        items(state.rows, key = { it.code ?: it.name }) { row ->
            SheetRow(row, onClick = { onChoose(row) })
        }
        if (state.loadingMore) {
            item(key = "more") {
                Text(
                    RadioText.LOADING_MORE_TAGS,
                    style = SonoraTheme.type.body13,
                    color = colors.textMuted,
                    modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

private const val PLACEHOLDER_ROWS = 6

@Composable
private fun SheetRow(row: FilterRow, onClick: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val shape = SonoraTheme.shapes.tile
    val count = row.count?.let(::groupThousands)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (row.selected) colors.selectedBg else Color.Transparent, shape)
            .then(if (row.selected) Modifier.border(1.dp, colors.selectedOutline, shape) else Modifier)
            .selectable(selected = row.selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = listOfNotNull(row.name, count, row.detail).joinToString(", ") }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).clearAndSetSemantics { }) {
            Text(
                row.name,
                style = type.body15.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.detail?.let { Text(it, style = type.label12.copy(fontWeight = FontWeight.Normal), color = colors.textMuted) }
        }
        // Measured before the weighted name, so it never moves or shrinks.
        if (count != null) {
            Text(
                count,
                style = type.body13,
                color = if (row.selected) colors.accent else colors.textMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
