package sonora.multiroom.mobile.android

import sonora.multiroom.mobile.AppGraph
import android.app.Application

class SonoraApplication : Application() {
    /** Process-wide: DataStore allows one instance per file, so it must not follow the activity. */
    val graph: AppGraph by lazy {
        AppGraph(dataStorePath = filesDir.resolve("sonora.preferences_pb").absolutePath)
    }
}
