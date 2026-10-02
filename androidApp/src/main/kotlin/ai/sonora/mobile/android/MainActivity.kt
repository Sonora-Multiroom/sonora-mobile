package ai.sonora.mobile.android

import ai.sonora.mobile.App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as SonoraApplication).graph
        setContent { App(graph, onExit = { finish() }) }
    }
}
