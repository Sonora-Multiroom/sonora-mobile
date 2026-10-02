package ai.sonora.mobile.android

import ai.sonora.mobile.App
import ai.sonora.mobile.AppGraph
import ai.sonora.mobile.data.InMemoryStore
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(remember { AppGraph(InMemoryStore()) }, onExit = { finish() })
        }
    }
}
