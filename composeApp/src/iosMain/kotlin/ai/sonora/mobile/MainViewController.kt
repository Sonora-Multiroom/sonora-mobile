package ai.sonora.mobile

import ai.sonora.mobile.data.InMemoryStore
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App(AppGraph(InMemoryStore())) }
