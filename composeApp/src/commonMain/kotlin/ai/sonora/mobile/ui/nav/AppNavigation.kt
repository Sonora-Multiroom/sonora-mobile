package ai.sonora.mobile.ui.nav

import ai.sonora.mobile.AppGraph
import ai.sonora.mobile.ui.placeholder.PlaceholderScreen
import ai.sonora.mobile.ui.rooms.RoomsScreen
import ai.sonora.mobile.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
fun AppNavigation(graph: AppGraph, backStack: AppBackStack, onExit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        NavDisplay(
            backStack = backStack.stack,
            onBack = { if (!backStack.pop()) onExit() },
            modifier = Modifier.weight(1f),
            entryProvider = entryProvider<Destination> {
                entry<Destination.Rooms> {
                    RoomsScreen(
                        viewModel = viewModel { graph.roomsViewModel() },
                        onOpenSettings = { backStack.selectTab(Destination.Settings) },
                        onOpenCard = { routeId -> backStack.push(Destination.NowPlaying(routeId)) },
                        onPlayInRoom = { roomId -> backStack.push(Destination.StartPlayback(roomId)) },
                        onPlaySomething = { backStack.push(Destination.StartPlayback(null)) },
                    )
                }
                entry<Destination.Settings> {
                    SettingsScreen(viewModel { graph.settingsViewModel() })
                }
                entry<Destination.Sources> {
                    PlaceholderScreen(
                        title = "Sources",
                        description = "Your saved stations, line-ins and files will be listed here.",
                    )
                }
                entry<Destination.NowPlaying> {
                    PlaceholderScreen(
                        title = "Now Playing",
                        description = "Full controls for this room will be here, including Move to room…",
                        onBack = { backStack.pop() },
                    )
                }
                entry<Destination.StartPlayback> {
                    PlaceholderScreen(
                        title = "Start Playback",
                        description = "Pick a source to play here.",
                        onBack = { backStack.pop() },
                    )
                }
            },
        )
        if (backStack.showsBottomBar) {
            BottomBar(selected = backStack.currentTab, onSelect = backStack::selectTab)
        }
    }
}
