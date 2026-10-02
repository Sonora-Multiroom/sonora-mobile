package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun RoomsScreen(
    viewModel: RoomsViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text("Rooms", style = SonoraTheme.type.screenTitle, color = SonoraTheme.colors.text)
        when (val s = state) {
            RoomsUiState.Initial -> Unit
            RoomsUiState.NoAddress -> NoAddress(onOpenSettings)
            // Replaced by the live screen in US2.
            is RoomsUiState.Connected -> Text(
                "Connecting to ${s.address.baseUrl}…",
                style = SonoraTheme.type.body14,
                color = SonoraTheme.colors.textMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NoAddress(onOpenSettings: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Set your hub address", style = type.cardTitle, color = colors.text)
        Text(
            "Rooms will appear here once the app knows where your hub is.",
            style = type.body14,
            color = colors.textMuted,
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.accent, SonoraTheme.shapes.pill)
                .clickable(role = Role.Button, onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("Open Settings", style = type.button16, color = colors.onAccent)
        }
    }
}
