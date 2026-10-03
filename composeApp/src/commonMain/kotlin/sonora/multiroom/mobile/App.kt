package sonora.multiroom.mobile

import sonora.multiroom.mobile.ui.nav.AppBackStack
import sonora.multiroom.mobile.ui.nav.AppNavigation
import sonora.multiroom.mobile.ui.theme.SonoraTheme
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
