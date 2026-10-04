package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.CardStatus
import sonora.multiroom.mobile.domain.NowPlayingContent
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.rooms.StaleBanner
import sonora.multiroom.mobile.ui.rooms.statusWord
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import sonora.multiroom.mobile.ui.theme.minTouchTarget
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

/** Everything the screen can ask for, so the stateless view stays easy to preview. */
class NowPlayingActions(
    val onBack: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onPauseResume: () -> Unit = {},
    val onMuteToggle: () -> Unit = {},
    val onVolumeDragStart: (String) -> Unit = {},
    val onVolumeDrag: (String, Int) -> Unit = { _, _ -> },
    val onVolumeDragEnd: (String, Int) -> Unit = { _, _ -> },
    val onOpenMove: () -> Unit = {},
    val onSelect: (Target) -> Unit = {},
    val onDismissMove: () -> Unit = {},
    val onConfirmMove: () -> Unit = {},
)

@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // One message at a time; consumed once shown.
    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // The screen is over (stopped here, or ended elsewhere): leave, once.
    LaunchedEffect(state.exit) {
        if (state.exit != null) {
            onExit()
            viewModel.consumeExit()
        }
    }

    // Poll only while this screen is visible and the app is in the foreground (FR-003).
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
        NowPlayingContentView(
            state = state,
            actions = NowPlayingActions(
                onBack = onBack,
                onStop = viewModel::onStop,
                onPauseResume = viewModel::onPauseResume,
                onMuteToggle = viewModel::onMuteToggle,
                onVolumeDragStart = viewModel::onVolumeDragStart,
                onVolumeDrag = viewModel::onVolumeDrag,
                onVolumeDragEnd = viewModel::onVolumeDragEnd,
                onOpenMove = viewModel::onOpenMove,
                onSelect = viewModel::onSelect,
                onDismissMove = viewModel::onDismissMove,
                onConfirmMove = viewModel::onConfirmMove,
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
fun NowPlayingContentView(
    state: NowPlayingUiState,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackControl(actions.onBack)
        val content = state.content
        when {
            content == null && state.connection is Connection.Unreachable -> Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Can't reach the hub", style = type.cardTitle, color = colors.text)
                Text("Tried ${state.address?.baseUrl.orEmpty()}. Retrying…", style = type.body14, color = colors.textMuted)
            }

            content == null -> Text(
                "Connecting to ${state.address?.baseUrl.orEmpty()}…",
                style = type.body14,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            else -> {
                if (state.stale) StaleBanner()
                PlaybackView(content, state, actions)
            }
        }
    }

    state.sheet?.let { sheet ->
        MoveSheet(
            state = sheet,
            canConfirm = state.controlsEnabled && NowPlayingAction.Move !in state.inFlight,
            onSelect = actions.onSelect,
            onConfirm = actions.onConfirmMove,
            onDismiss = actions.onDismissMove,
        )
    }
}

@Composable
private fun BackControl(onBack: () -> Unit) {
    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp)) {
        Row(
            modifier = Modifier
                .height(minTouchTarget)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(SonoraIcons.Back, contentDescription = null, tint = SonoraTheme.colors.textSoft, modifier = Modifier.size(20.dp))
            Text("Rooms", style = SonoraTheme.type.body15, color = SonoraTheme.colors.textSoft)
        }
    }
}

@Composable
private fun PlaybackView(content: NowPlayingContent.Playback, state: NowPlayingUiState, actions: NowPlayingActions) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val primary = if (state.stale) colors.textMuted else colors.text

    Column(modifier = Modifier.fillMaxWidth()) {
        KindPanel(content.kind, content.live, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                content.sourceName,
                style = type.cardTitle.copy(fontSize = 26.sp, letterSpacing = (-0.52).sp),
                color = primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle(content), style = type.body14, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(content.status)
                Text(
                    targetText(content.target),
                    style = type.body13,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActionsRow(content, state, actions)
            if (content.live) {
                Text("Live streams can't be paused", style = type.label12.copy(fontWeight = FontWeight.Normal), color = colors.textMuted)
            }
        }

        content.volume?.let { section ->
            VolumeSection(
                section = section,
                pending = state.pending,
                enabled = state.controlsEnabled,
                muteInFlight = NowPlayingAction.Mute in state.inFlight,
                actions = actions,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun ActionsRow(content: NowPlayingContent.Playback, state: NowPlayingUiState, actions: NowPlayingActions) {
    val colors = SonoraTheme.colors
    val enabled = state.controlsEnabled
    Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.Top) {
        RoundAction(
            icon = SonoraIcons.Stop,
            caption = "Stop",
            label = "Stop",
            background = colors.accent,
            tint = colors.onAccent,
            enabled = enabled && NowPlayingAction.Stop !in state.inFlight,
            onClick = actions.onStop,
        )
        if (content.pauseVisible) {
            RoundAction(
                icon = if (content.paused) SonoraIcons.Play else SonoraIcons.Pause,
                caption = if (content.paused) "Resume" else "Pause",
                label = (if (content.paused) "Resume " else "Pause ") + content.target.name,
                background = colors.surface,
                tint = colors.text,
                enabled = enabled && content.pauseEnabled && NowPlayingAction.PauseResume !in state.inFlight,
                onClick = actions.onPauseResume,
            )
        }
        if (content.moveVisible) {
            RoundAction(
                icon = SonoraIcons.Arrow,
                caption = "Move to room…",
                label = "Move to room",
                background = colors.surface,
                tint = colors.text,
                enabled = enabled && NowPlayingAction.Move !in state.inFlight,
                onClick = actions.onOpenMove,
            )
        }
    }
}

/** A 76 dp round button with a caption below. The caption is decoration: [label] is what is announced. */
@Composable
private fun RoundAction(
    icon: ImageVector,
    caption: String,
    label: String,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(min = 76.dp).alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Text(
            caption,
            style = SonoraTheme.type.label12.copy(fontWeight = FontWeight.Normal),
            color = SonoraTheme.colors.textMuted,
            softWrap = false,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The status chip: only Playing is drawn in the design, the others reuse the muted tokens. */
@Composable
private fun StatusChip(status: CardStatus) {
    val colors = SonoraTheme.colors
    val (background, foreground) = when (status) {
        CardStatus.Playing, CardStatus.LiveStream -> colors.streamPanel to colors.accent
        CardStatus.Failed -> colors.accentContainer to colors.warningText
        CardStatus.Paused -> colors.surfaceRaised to colors.textSoft
        else -> colors.surfaceRaised to colors.textMuted
    }
    Row(
        modifier = Modifier
            .clip(SonoraTheme.shapes.pill)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(foreground, CircleShape))
        Text(statusWord(status), style = SonoraTheme.type.body13Semi, color = foreground)
    }
}
