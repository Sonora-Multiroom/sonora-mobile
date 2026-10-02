package ai.sonora.mobile.ui.nav

import ai.sonora.mobile.AppGraph
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

@Composable
fun AppNavigation(graph: AppGraph, backStack: AppBackStack, onExit: () -> Unit) {
    NavDisplay(
        backStack = backStack.stack,
        onBack = { if (!backStack.pop()) onExit() },
        entryProvider = entryProvider<Destination> {
            // Temporary text per destination; the real screens arrive with the user stories.
            entry<Destination.Rooms> { Temp("Rooms") }
            entry<Destination.Sources> { Temp("Sources") }
            entry<Destination.Settings> { Temp("Settings") }
            entry<Destination.NowPlaying> { Temp("Now Playing") }
            entry<Destination.StartPlayback> { Temp("Start Playback") }
        },
    )
}

@Composable
private fun Temp(text: String) {
    BasicText(text, style = TextStyle(color = Color.White))
}
