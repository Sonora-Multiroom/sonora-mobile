package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.domain.RoomsContent
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/** Everything the Rooms screen can ask for, so the stateless view stays easy to preview. */
class RoomsActions(
    val onOpenSettings: () -> Unit = {},
    val onOpenCard: (String) -> Unit = {},
    val onPlayInRoom: (String) -> Unit = {},
    val onPlaySomething: () -> Unit = {},
    val onVolumeDragStart: (String) -> Unit = {},
    val onVolumeDrag: (String, Int) -> Unit = { _, _ -> },
    val onVolumeDragEnd: (String, Int) -> Unit = { _, _ -> },
    val onCardAction: (String) -> Unit = {},
    val onMasterMuteToggle: () -> Unit = {},
)

@Composable
fun RoomsScreen(
    viewModel: RoomsViewModel,
    onOpenSettings: () -> Unit,
    onOpenCard: (String) -> Unit = {},
    onPlayInRoom: (String) -> Unit = {},
    onPlaySomething: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // One message at a time; consumed once shown (FR-018).
    val message = (state as? RoomsUiState.Connected)?.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // Poll only while this screen is visible and the app is in the foreground (FR-005, SC-006).
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

    Box(modifier = modifier.fillMaxSize()) {
        RoomsContentView(
            state = state,
            actions = RoomsActions(
                onOpenSettings = onOpenSettings,
                onOpenCard = onOpenCard,
                onPlayInRoom = onPlayInRoom,
                onPlaySomething = onPlaySomething,
                onVolumeDragStart = viewModel::onVolumeDragStart,
                onVolumeDrag = viewModel::onVolumeDrag,
                onVolumeDragEnd = viewModel::onVolumeDragEnd,
                onCardAction = viewModel::onCardAction,
                onMasterMuteToggle = viewModel::onMasterMuteToggle,
            ),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = SonoraTheme.colors.surfaceRaised,
                contentColor = SonoraTheme.colors.text,
            )
        }
    }
}

@Composable
fun RoomsContentView(
    state: RoomsUiState,
    actions: RoomsActions,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(modifier = modifier.fillMaxSize()) {
        val connected = state as? RoomsUiState.Connected
        val content = connected?.content as? RoomsContent.Rooms
        Header(
            subtitle = content?.let { roomsInUse(it.inUse, it.total) },
            masterMuted = content?.masterMuted ?: false,
            muteEnabled = connected != null && connected.connection == Connection.Live &&
                ActionKey.MasterMute !in connected.inFlight,
            onToggleMute = actions.onMasterMuteToggle,
        )

        when (state) {
            RoomsUiState.Initial -> Unit
            RoomsUiState.NoAddress -> Message(
                title = "Set your hub address",
                body = "Rooms will appear here once the app knows where your hub is.",
                actionLabel = "Open Settings",
                onAction = actions.onOpenSettings,
            )

            is RoomsUiState.Connected -> {
                val connection = state.connection
                val stale = connection is Connection.Unreachable && state.content != null
                when {
                    state.content == null && connection is Connection.Unreachable -> Message(
                        title = "Can't reach the hub",
                        body = "Tried ${state.address.baseUrl}. Retrying…",
                        actionLabel = "Open Settings",
                        onAction = actions.onOpenSettings,
                    )

                    state.content == null -> Text(
                        "Connecting to ${state.address.baseUrl}…",
                        style = type.body14,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )

                    state.content is RoomsContent.NoRooms -> Text(
                        "Your hub has no rooms configured.",
                        style = type.body14,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )

                    state.content is RoomsContent.Rooms -> {
                        if (stale) StaleBanner()
                        RoomsList(
                            content = state.content,
                            state = state,
                            stale = stale,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

internal fun roomsInUse(inUse: Int, total: Int): String =
    "$inUse of $total ${if (total == 1) "room" else "rooms"} in use"

@Composable
private fun Header(subtitle: String?, masterMuted: Boolean, muteEnabled: Boolean, onToggleMute: () -> Unit) {
    val colors = SonoraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text("Rooms", style = SonoraTheme.type.screenTitle, color = colors.text)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = SonoraTheme.type.body14,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (subtitle != null) {
            IconCircleButton(
                icon = if (masterMuted) SonoraIcons.SpeakerMuted else SonoraIcons.Speaker,
                label = if (masterMuted) "Unmute all rooms" else "Mute all rooms",
                background = if (masterMuted && muteEnabled) colors.accent else colors.surface,
                tint = if (masterMuted && muteEnabled) colors.onAccent else if (muteEnabled) colors.text else colors.textMuted,
                size = 44.dp,
                enabled = muteEnabled,
                onClick = onToggleMute,
            )
        }
    }
}

@Composable
internal fun StaleBanner() {
    Text(
        "Can't reach the hub · showing last known state",
        style = SonoraTheme.type.body13Semi,
        color = SonoraTheme.colors.warningText,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(SonoraTheme.colors.accentContainer, SonoraTheme.shapes.smallTile)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
internal fun Message(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = type.cardTitle, color = colors.text)
        Text(body, style = type.body14, color = colors.textMuted)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.accent, SonoraTheme.shapes.pill)
                .clickable(role = Role.Button, onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(actionLabel, style = type.button16, color = colors.onAccent)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = SonoraTheme.type.sectionLabel,
        color = SonoraTheme.colors.textMuted,
        modifier = modifier,
    )
}

@Composable
private fun RoomsList(
    content: RoomsContent.Rooms,
    state: RoomsUiState.Connected,
    stale: Boolean,
    actions: RoomsActions,
) {
    val colors = SonoraTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (content.cards.isNotEmpty()) {
            item(key = "now-playing-label") { SectionLabel("Now playing") }
            items(content.cards, key = { it.key }) { card ->
                NowPlayingCard(
                    card = card,
                    volume = state.volumeOverrides[card.key] ?: card.volume,
                    stale = stale,
                    actionInFlight = state.inFlight.any { it is ActionKey.Card && it.cardKey == card.key },
                    onOpen = { actions.onOpenCard(card.key) },
                    onAction = { actions.onCardAction(card.key) },
                    onVolumeDragStart = { actions.onVolumeDragStart(card.key) },
                    onVolumeDrag = { actions.onVolumeDrag(card.key, it) },
                    onVolumeDragEnd = { actions.onVolumeDragEnd(card.key, it) },
                )
            }
        }
        if (content.idle.isNotEmpty()) {
            item(key = "idle-label") { SectionLabel("Idle", Modifier.padding(top = 8.dp)) }
            item(key = "idle") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, SonoraTheme.shapes.card)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    content.idle.forEachIndexed { index, row ->
                        IdleRow(row, onPlay = { actions.onPlayInRoom(row.roomId) })
                        if (index < content.idle.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.surfaceRaised))
                        }
                    }
                }
            }
            item(key = "play-something") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(colors.accent, SonoraTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = actions.onPlaySomething),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SonoraIcons.Plus, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
                    Text("Play something", style = SonoraTheme.type.button16, color = colors.onAccent)
                }
            }
        }
    }
}
