package ai.sonora.mobile

import ai.sonora.mobile.ui.nav.AppBackStack
import ai.sonora.mobile.ui.nav.AppNavigation
import ai.sonora.mobile.ui.theme.SonoraTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun App(graph: AppGraph, onExit: () -> Unit = {}) {
    SonoraTheme {
        // Saveable, so rotation or a theme switch keeps the user on the same screen.
        val backStack = rememberSaveable(saver = AppBackStack.Saver) { AppBackStack() }
        AppNavigation(graph, backStack, onExit)
    }
}
