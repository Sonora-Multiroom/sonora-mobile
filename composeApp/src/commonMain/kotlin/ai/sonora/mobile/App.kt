package ai.sonora.mobile

import ai.sonora.mobile.ui.nav.AppBackStack
import ai.sonora.mobile.ui.nav.AppNavigation
import ai.sonora.mobile.ui.theme.SonoraTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun App(graph: AppGraph, onExit: () -> Unit = {}) {
    SonoraTheme {
        val backStack = remember { AppBackStack() }
        AppNavigation(graph, backStack, onExit)
    }
}
