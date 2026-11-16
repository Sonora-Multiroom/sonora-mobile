package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.stationDetail
import sonora.multiroom.mobile.ui.RadioFailure
import sonora.multiroom.mobile.ui.rooms.IconCircleButton
import sonora.multiroom.mobile.ui.rooms.StaleBanner
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/** The radio screen: find a station by name and choose it (contracts/radio-ui.md). */
@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    // The screen is over: close it. Once.
    LaunchedEffect(state.exit) {
        if (state.exit != null) {
            onClose()
            viewModel.consumeExit()
        }
    }

    // The one poll loop runs while this screen is visible, so the connection state is current.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onVisible()
            try {
                awaitCancellation()
            } finally {
                viewModel.onHidden()
            }
        }
    }

    val colors = SonoraTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = SonoraIcons.Back,
                label = RadioText.BACK,
                background = Color.Transparent,
                tint = colors.textSoft,
                size = 44.dp,
                onClick = onClose,
            )
            Text(RadioText.TITLE, style = SonoraTheme.type.cardTitle.copy(fontSize = 20.sp), color = colors.text)
        }
        if (state.connection is Connection.Unreachable) StaleBanner()
        NameField(
            text = state.nameText,
            onChange = viewModel::onNameChange,
            onClear = viewModel::onClearName,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        FilterChips(
            filters = state.filters,
            onOpen = viewModel::onOpenSheet,
            onRemove = viewModel::onRemoveFilter,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        StationListView(
            state,
            onRetry = viewModel::retry,
            onOrder = { viewModel.onOpenSheet(FilterSheetState.Kind.Order) },
            onClearFilters = viewModel::onClearFilters,
            onChoose = viewModel::onChoose,
            onListPosition = viewModel::onListPosition,
            onRemoveRecent = viewModel::removeRecent,
            modifier = Modifier.weight(1f),
        )
    }
    state.sheet?.let { sheet ->
        FilterSheet(
            state = sheet,
            onSearch = viewModel::onSheetSearch,
            onChoose = viewModel::onSheetChoose,
            onListPosition = viewModel::onSheetListPosition,
            onRetry = viewModel::retrySheet,
            onDismiss = viewModel::onCloseSheet,
        )
    }
}

@Composable
private fun StationListView(
    state: RadioUiState,
    onRetry: () -> Unit,
    onOrder: () -> Unit,
    onClearFilters: () -> Unit,
    onChoose: (sonora.multiroom.mobile.domain.Station) -> Unit,
    onListPosition: (Int) -> Unit,
    onRemoveRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    // Saved with the screen, so rotation and a return from the background keep the position.
    val listState = rememberLazyListState()
    val list = state.list

    val lastVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    // The "Recent" rows and the list heading come before the stations.
    val rowsBefore = (if (state.recent.isEmpty()) 0 else state.recent.size + 1) + 1
    LaunchedEffect(lastVisible, list.stations.size, list.phase) { onListPosition(lastVisible - rowsBefore) }

    LazyColumn(modifier = modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        if (state.recent.isNotEmpty()) {
            item(key = "recent-heading") {
                Text(
                    RadioText.RECENT_HEADING,
                    style = type.sectionLabel,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            items(state.recent, key = { "recent:" + it.uuid }) { station ->
                StationRow(
                    station,
                    secondLine = stationDetail(station),
                    onClick = { onChoose(station) },
                    trailing = {
                        IconCircleButton(
                            icon = SonoraIcons.Close,
                            label = removeRecentLabel(station),
                            background = Color.Transparent,
                            tint = colors.textMuted,
                            size = 44.dp,
                            onClick = { onRemoveRecent(station.uuid) },
                        )
                    },
                )
            }
        }
        item(key = "heading") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    listHeading(state.filters.country),
                    style = type.sectionLabel,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OrderControl(state.filters.order, onOrder)
            }
        }
        if (list.empty) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(RadioText.NO_MATCH, style = type.cardTitle.copy(fontSize = 17.sp), color = colors.text)
                    Text(RadioText.NO_MATCH_HINT, style = type.body14, color = colors.textMuted)
                    if (state.filters.narrowed) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .heightIn(min = 44.dp)
                                .clip(SonoraTheme.shapes.pill)
                                .background(colors.surface)
                                .clickable(role = Role.Button, onClick = onClearFilters)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(RadioText.CLEAR_FILTERS, style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.textSoft)
                        }
                    }
                }
            }
        }
        if (list.problem == RadioFailure.Catalogue) {
            item(key = "problem") { CatalogueCard(onRetry = onRetry, modifier = Modifier.padding(bottom = 8.dp)) }
        }
        if (list.phase == ListPhase.LoadingFirst) {
            item(key = "placeholders") {
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
        items(list.stations, key = { it.uuid }) { station ->
            StationRow(station, secondLine = stationDetail(station), onClick = { onChoose(station) })
        }
        if (list.limitReached) {
            item(key = "limit") {
                Text(
                    RadioText.LIMIT_NOTE,
                    style = type.body13,
                    color = colors.textMuted,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        if (list.phase == ListPhase.LoadingMore) {
            item(key = "more") {
                Text(
                    RadioText.LOADING_MORE,
                    style = type.body13,
                    color = colors.textMuted,
                    modifier = Modifier
                        .padding(8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

private const val PLACEHOLDER_ROWS = 6

/** The order's name and the sort icon, 44 dp high; opens the Order sheet. */
@Composable
private fun OrderControl(order: sonora.multiroom.mobile.domain.StationOrder, onClick: () -> Unit) {
    val colors = SonoraTheme.colors
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(SonoraTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = orderControlLabel(order) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(orderName(order), style = SonoraTheme.type.body13.copy(fontWeight = FontWeight.Medium), color = colors.textSoft, maxLines = 1)
        Icon(SonoraIcons.Sort, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(18.dp))
    }
}

/** Country, Tag, Language in one row that scrolls sideways when it must. */
@Composable
private fun FilterChips(
    filters: RadioFilters,
    onOpen: (FilterSheetState.Kind) -> Unit,
    onRemove: (FilterSheetState.Kind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(FilterSheetState.Kind.Country, filters.country?.name, onOpen, onRemove)
        FilterChip(FilterSheetState.Kind.Tag, filters.tag, onOpen, onRemove)
        FilterChip(FilterSheetState.Kind.Language, filters.language, onOpen, onRemove)
    }
}

@Composable
private fun FilterChip(
    kind: FilterSheetState.Kind,
    value: String?,
    onOpen: (FilterSheetState.Kind) -> Unit,
    onRemove: (FilterSheetState.Kind) -> Unit,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val shape = SonoraTheme.shapes.pill
    if (value == null) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(shape)
                .background(colors.surface, shape)
                .clickable(role = Role.Button) { onOpen(kind) }
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(RadioText.sheetTitle(kind), style = type.body14.copy(fontWeight = FontWeight.Medium), color = colors.textSoft)
            Icon(SonoraIcons.ChevronDown, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(16.dp))
        }
    } else {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(shape)
                .background(colors.selectedBg, shape)
                .border(1.dp, colors.selectedOutline, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                style = type.body14.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button) { onOpen(kind) }
                    .semantics { contentDescription = "${RadioText.sheetTitle(kind)}: $value" }
                    .padding(start = 16.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 44.dp)
                    .clickable(role = Role.Button) { onRemove(kind) }
                    .semantics { contentDescription = removeFilterLabel(kind, value) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SonoraIcons.Close, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The 52 dp search field. Its keyboard action only hides the keyboard: the list follows the text by itself. */
@Composable
private fun NameField(text: String, onChange: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.surface, shape)
            .then(if (focused) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .padding(start = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SonoraIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(RadioText.NAME_FIELD, style = type.body15, color = colors.textMuted, maxLines = 1)
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type.body15.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { inner() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = RadioText.NAME_FIELD },
            )
        }
        if (text.isNotEmpty()) {
            IconCircleButton(
                icon = SonoraIcons.Close,
                label = RadioText.CLEAR_NAME,
                background = Color.Transparent,
                tint = colors.textSoft,
                size = 44.dp,
                onClick = onClear,
            )
        }
    }
}
