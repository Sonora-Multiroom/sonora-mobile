package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.StartPlaybackContent
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.rooms.IconCircleButton
import sonora.multiroom.mobile.ui.rooms.Message
import sonora.multiroom.mobile.ui.rooms.StaleBanner
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

@Composable
fun StartPlaybackScreen(
    viewModel: StartPlaybackViewModel,
    onClose: () -> Unit,
    onStarted: (StartExit.Started) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // One message at a time; consumed once shown (FR-016).
    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // The screen is over: close it, or hand over to Now Playing (FR-015). Once.
    LaunchedEffect(state.exit) {
        when (val exit = state.exit) {
            null -> Unit
            StartExit.Closed -> {
                onClose()
                viewModel.consumeExit()
            }
            is StartExit.Started -> {
                onStarted(exit)
                viewModel.consumeExit()
            }
        }
    }

    // Poll only while this screen is visible and the app is in the foreground (FR-002).
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
        StartPlaybackContentView(
            state = state,
            onClose = viewModel::onClose,
            onOpenSettings = onOpenSettings,
            onSelectSource = viewModel::onSelectSource,
            onSelectTarget = viewModel::onSelectTarget,
            onPlay = viewModel::onPlay,
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
internal fun StartPlaybackContentView(
    state: StartPlaybackUiState,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (Target) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(modifier = modifier.fillMaxSize()) {
        Header(onClose)
        val address = state.address
        val content = state.content
        val connection = state.connection
        when {
            address == null -> Message(
                title = "Set your hub address",
                body = "Rooms will appear here once the app knows where your hub is.",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
            )

            content == null && connection is Connection.Unreachable -> Message(
                title = "Can't reach the hub",
                body = "Tried ${address.baseUrl}. Retrying…",
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
            )

            content == null -> {
                Body(modifier = Modifier.weight(1f)) {
                    Text("Connecting to ${address.baseUrl}…", style = type.body14, color = colors.textMuted)
                }
                Footer(state, onPlay)
            }

            else -> {
                if (connection is Connection.Unreachable) StaleBanner()
                Body(modifier = Modifier.weight(1f)) {
                    Lists(content, state, onSelectSource, onSelectTarget)
                }
                Footer(state, onPlay)
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    val colors = SonoraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(
            icon = SonoraIcons.Close,
            label = "Close",
            background = Color.Transparent,
            tint = colors.textSoft,
            size = 44.dp,
            onClick = onClose,
        )
        Text("Play something", style = SonoraTheme.type.cardTitle.copy(fontSize = 20.sp), color = colors.text)
    }
}

@Composable
private fun Body(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionLegend(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SonoraTheme.type.sectionLabel, color = SonoraTheme.colors.textMuted, modifier = modifier)
}

@Composable
private fun Lists(
    content: StartPlaybackContent,
    state: StartPlaybackUiState,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (Target) -> Unit,
) {
    val colors = SonoraTheme.colors
    val locked = state.starting

    SectionLegend("Or pick a source", Modifier.padding(top = 6.dp))
    if (content.sources.isEmpty()) {
        Text("No sources on the hub. Paste a link above.", style = SonoraTheme.type.body14, color = colors.textMuted)
    } else {
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (option in content.sources) {
                SourceRow(option, selected = option.id == state.selectedSourceId, enabled = !locked, onClick = { onSelectSource(option.id) })
            }
        }
    }

    SectionLegend("Play in", Modifier.padding(top = 6.dp))
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (pair in content.targets.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (option in pair) {
                    TargetTile(
                        option,
                        selected = option.target == state.selectedTarget,
                        enabled = !locked,
                        onClick = { onSelectTarget(option.target) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Footer(state: StartPlaybackUiState, onPlay: () -> Unit) {
    val colors = SonoraTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val look = if (state.playEnabled || state.starting) colors.accent else colors.accent.copy(alpha = 0.38f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(look, SonoraTheme.shapes.pill)
                .clickable(enabled = state.playEnabled, role = Role.Button, onClick = onPlay),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.starting) {
                Icon(SonoraIcons.Play, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
            }
            Text(
                playLabelText(state.playLabel),
                style = SonoraTheme.type.button16,
                color = colors.onAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
