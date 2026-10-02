package ai.sonora.mobile.ui.nav

import ai.sonora.mobile.AppGraph
import ai.sonora.mobile.ui.rooms.RoomsScreen
import ai.sonora.mobile.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
fun AppNavigation(graph: AppGraph, backStack: AppBackStack, onExit: () -> Unit) {
    NavDisplay(
        backStack = backStack.stack,
        onBack = { if (!backStack.pop()) onExit() },
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        entryProvider = entryProvider<Destination> {
            entry<Destination.Rooms> {
                RoomsScreen(
                    viewModel = viewModel { graph.roomsViewModel() },
                    onOpenSettings = { backStack.selectTab(Destination.Settings) },
                )
            }
            entry<Destination.Settings> {
                SettingsScreen(viewModel { graph.settingsViewModel() })
            }
            // Temporary text until the placeholders arrive with US4.
            entry<Destination.Sources> { Temp("Sources") }
            entry<Destination.NowPlaying> { Temp("Now Playing") }
            entry<Destination.StartPlayback> { Temp("Start Playback") }
        },
    )
}

@Composable
private fun Temp(text: String) {
    Box { BasicText(text, style = TextStyle(color = Color.White)) }
}
