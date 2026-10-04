package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.appVersionLabel
import sonora.multiroom.mobile.ui.rooms.Message
import sonora.multiroom.mobile.ui.rooms.StaleBanner
import sonora.multiroom.mobile.ui.session.SettingsTab
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
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

    // Poll only while this screen is visible and the app is in the foreground.
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
        SettingsContentView(
            state = state,
            onHubRowTapped = viewModel::onHubRowTapped,
            onTabSelected = viewModel::onTabSelected,
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
        state.sheet?.let { sheet ->
            HubAddressSheet(
                sheet = sheet,
                onDraftChange = viewModel::onDraftChange,
                onSave = viewModel::onSave,
                onClose = viewModel::onSheetClosed,
            )
        }
    }
}

@Composable
fun SettingsContentView(
    state: SettingsUiState,
    onHubRowTapped: () -> Unit,
    onTabSelected: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier,
    versionLabel: String = appVersionLabel(),
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Settings",
            style = type.screenTitle,
            color = colors.text,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
        )
        HubRowView(state.hub, onClick = onHubRowTapped)
        SettingsTabs(state.tab, onSelect = onTabSelected)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val body = state.body) {
                SettingsBody.Initial, SettingsBody.Waiting -> Unit
                SettingsBody.NoAddress -> Message(
                    title = "Set your hub address",
                    body = "Rooms, groups and sources will appear here once the app knows where your hub is.",
                    actionLabel = "Set hub address",
                    onAction = onHubRowTapped,
                )

                is SettingsBody.CantReach -> Message(
                    title = "Can't reach the hub",
                    body = "Tried ${body.address}. Retrying…",
                    actionLabel = "Change address",
                    onAction = onHubRowTapped,
                )

                is SettingsBody.Lists -> {
                    if (body.stale) StaleBanner()
                    // The tab bodies are filled by each user story.
                }
            }

            // Version footer (FR-001): tells testers which build they run, at the end of the content.
            Text(
                text = versionLabel,
                style = type.label12,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
            )
        }
    }
}
