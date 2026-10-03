package sonora.multiroom.mobile.ui.nav

import sonora.multiroom.mobile.AppGraph
import sonora.multiroom.mobile.ui.nowplaying.NowPlayingScreen
import sonora.multiroom.mobile.ui.placeholder.PlaceholderScreen
import sonora.multiroom.mobile.ui.rooms.RoomsScreen
import sonora.multiroom.mobile.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

@Composable
fun AppNavigation(graph: AppGraph, backStack: AppBackStack, onExit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        NavDisplay(
            backStack = backStack.stack,
            onBack = { if (!backStack.pop()) onExit() },
            modifier = Modifier.weight(1f),
            // View models live in their entry's store, so each Now Playing entry gets its own and
            // it is cleared when the entry is popped (research R2).
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
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
                entry<Destination.NowPlaying> { key ->
                    NowPlayingScreen(
                        // One view model per entry (the decorators above), with its own saved state.
                        viewModel = viewModel { graph.nowPlayingViewModel(key.routeId, createSavedStateHandle()) },
                        onBack = { backStack.pop() },
                        onExit = { backStack.pop() },
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
