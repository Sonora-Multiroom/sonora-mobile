package sonora.multiroom.mobile.ui.session

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot messages that cross screens, e.g. "Playback on Bedroom ended" posted by Now Playing and
 * shown by Rooms (research R4). One consumer; when several are posted before it collects, the
 * latest wins.
 */
class AppMessages {
    private val channel = Channel<String>(Channel.CONFLATED)

    val messages: Flow<String> = channel.receiveAsFlow()

    fun post(text: String) {
        channel.trySend(text)
    }
}
