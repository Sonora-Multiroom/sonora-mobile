package sonora.multiroom.mobile.ui.nav

import sonora.multiroom.mobile.AppGraph
import sonora.multiroom.mobile.ui.nowplaying.NowPlayingScreen
import sonora.multiroom.mobile.ui.rooms.RoomsScreen
import sonora.multiroom.mobile.ui.session.SettingsTab
import sonora.multiroom.mobile.ui.settings.SettingsScreen
import sonora.multiroom.mobile.ui.startplayback.StartPlaybackScreen
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
            onBack = {
                // System Back leaves Start Playback without its Close button: a start in flight must
                // report to Rooms, not to the closing screen (research R9).
                if (backStack.top is Destination.StartPlayback) graph.starter.detach()
                if (!backStack.pop()) onExit()
            },
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
                entry<Destination.NowPlaying> { key ->
                    NowPlayingScreen(
                        // One view model per entry (the decorators above), with its own saved state.
                        viewModel = viewModel { graph.nowPlayingViewModel(key.routeId, createSavedStateHandle(), key.startedAfterSeq, key.targetName) },
                        onBack = { backStack.pop() },
                        onExit = { backStack.pop() },
                    )
                }
                entry<Destination.StartPlayback> { key ->
                    StartPlaybackScreen(
                        viewModel = viewModel { graph.startPlaybackViewModel(key.targetId, createSavedStateHandle()) },
                        onClose = { backStack.pop() },
                        // Back from Now Playing returns to where Start Playback was opened (FR-015).
                        onStarted = { started ->
                            if (backStack.top is Destination.StartPlayback) backStack.replaceTop(Destination.NowPlaying(started.routeId, started.startedAfterSeq, started.targetName))
                        },
                        onOpenSettings = { backStack.selectTab(Destination.Settings) },
                    )
                }
            },
        )
        if (backStack.showsBottomBar) {
            BottomBar(
                selected = backStack.currentTab,
                onSelectRooms = { backStack.selectTab(Destination.Rooms) },
                onOpenSources = {
                    graph.settingsNavigator.select(SettingsTab.Sources)
                    backStack.selectTab(Destination.Settings)
                },
                onSelectSettings = { backStack.selectTab(Destination.Settings) },
            )
        }
    }
}
