package ai.sonora.mobile.android

import ai.sonora.mobile.AppGraph
import android.app.Application

class SonoraApplication : Application() {
    /** Process-wide: DataStore allows one instance per file, so it must not follow the activity. */
    val graph: AppGraph by lazy {
        AppGraph(dataStorePath = filesDir.resolve("sonora.preferences_pb").absolutePath)
    }
}
