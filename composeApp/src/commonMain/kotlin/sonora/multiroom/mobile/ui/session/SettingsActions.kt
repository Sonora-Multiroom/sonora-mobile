package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.ItemKey
import sonora.multiroom.mobile.domain.ItemKind
import sonora.multiroom.mobile.ui.SettingsAction
import sonora.multiroom.mobile.ui.settings.keepsPlayingMessage
import sonora.multiroom.mobile.ui.settingsFailure
import sonora.multiroom.mobile.ui.settingsFailureMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Phase {
    /** The request is on its way; the switch shows the value asked for and ignores taps. */
    data object InFlight : Phase

    /** The hub said yes; the value holds until a refresh that started after [fence] arrives. */
    data class AwaitingRefresh(val fence: Long) : Phase
}

/** An override of what the hub reported: [value] is what the user asked for. */
data class Pending(val value: Boolean, val phase: Phase)

/**
 * Runs every switch change from Settings in the app's scope, so a request outlives the screen
 * (research R5). The switch shows the requested value at once (optimistic); on success it keeps
 * it until the first refresh that *started after* the answer (the fence rule of [HubSession]),
 * from then on the hub decides. On failure the override goes at once and the reason is reported.
 * Messages go to [messages] while the screen is [attach]ed, else to [AppMessages] (research R7).
 */
class SettingsActions(
    private val scope: CoroutineScope,
    private val session: HubSession,
    private val appMessages: AppMessages,
) {
    private val _pending = MutableStateFlow<Map<ItemKey, Pending>>(emptyMap())
    val pending: StateFlow<Map<ItemKey, Pending>> = _pending.asStateFlow()

    private val channel = Channel<String>(Channel.CONFLATED)
    val messages: Flow<String> = channel.receiveAsFlow()

    private var attached = false

    /** Counts address changes: an answer to a request made for an old address is dropped. */
    private var generation = 0
    private var address: HubAddress? = null

    init {
        scope.launch { session.state.collect(::onState) }
    }

    private fun onState(state: SessionState) {
        val now = (state as? SessionState.Connected)?.address
        if (now != address) {
            address = now
            generation++
            _pending.value = emptyMap()
            return
        }
        if (state is SessionState.Connected && state.snapshot != null) {
            _pending.update { map ->
                map.filterValues { p -> !(p.phase is Phase.AwaitingRefresh && state.refreshSeq > p.phase.fence) }
            }
        }
    }

    fun attach() {
        attached = true
    }

    fun detach() {
        attached = false
    }

    /** Turns [key] on or off. [keepsPlaying] only matters for a source turned off (FR-014). */
    fun setEnabled(key: ItemKey, name: String, value: Boolean, keepsPlaying: Boolean) {
        val repo = session.repository ?: return
        var started = false
        _pending.update { map ->
            if (map[key]?.phase == Phase.InFlight) map
            else (map + (key to Pending(value, Phase.InFlight))).also { started = true }
        }
        if (!started) return
        val mine = generation
        scope.launch {
            val result = when (key.kind) {
                ItemKind.Room -> repo.setRoomEnabled(key.id, value)
                ItemKind.Group -> repo.setGroupEnabled(key.id, value)
                ItemKind.Source -> repo.setSourceEnabled(key.id, value)
            }
            if (generation != mine) return@launch
            when (result) {
                is HubResult.Ok -> {
                    _pending.update { it + (key to Pending(value, Phase.AwaitingRefresh(session.startedSeq))) }
                    session.requestRefresh()
                    if (keepsPlaying && !value) report(keepsPlayingMessage(name))
                }

                is HubResult.Err -> {
                    _pending.update { it - key }
                    val failure = settingsFailure(SettingsAction.Turn(value), name, result.error)
                    if (failure != null) {
                        report(settingsFailureMessage(failure))
                        if (failure.needsRefresh) session.requestRefresh()
                    }
                }
            }
        }
    }

    private fun report(text: String) {
        if (attached) channel.trySend(text) else appMessages.post(text)
    }
}
