package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.CardAction
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.NowPlayingCard
import sonora.multiroom.mobile.domain.RoomsBuilder
import sonora.multiroom.mobile.domain.RoomsContent
import sonora.multiroom.mobile.ui.UserAction
import sonora.multiroom.mobile.ui.actionErrorMessage
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.SessionState
import sonora.multiroom.mobile.ui.session.VolumeDragController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Rooms screen's state: the shared [HubSession]'s snapshot built into cards, plus what only
 * this screen knows (requests in flight, the snackbar message). Volume dragging is the shared
 * [VolumeDragController].
 */
class RoomsViewModel(
    private val session: HubSession,
    messages: AppMessages,
) : ViewModel() {
    private class Local(val inFlight: Set<ActionKey> = emptySet(), val message: String? = null)

    private val local = MutableStateFlow(Local())

    private val volume = VolumeDragController(
        scope = viewModelScope,
        session = session,
        onError = { name, error -> reportFailure(UserAction.Volume, name, error) },
    )

    /** The last snapshot built into content, so the build runs once per refresh. */
    private var built: Pair<HubSnapshot, RoomsContent>? = null

    private fun contentOf(snapshot: HubSnapshot): RoomsContent {
        built?.takeIf { it.first === snapshot }?.let { return it.second }
        return RoomsBuilder.build(snapshot).also { built = snapshot to it }
    }

    val state: StateFlow<RoomsUiState> = combine(session.state, volume.pending, local) { s, pending, local ->
        when (s) {
            SessionState.Initial -> RoomsUiState.Initial
            SessionState.NoAddress -> RoomsUiState.NoAddress
            is SessionState.Connected -> {
                val content = s.snapshot?.let(::contentOf)
                RoomsUiState.Connected(
                    address = s.address,
                    connection = s.connection,
                    content = content,
                    volumeOverrides = if (content is RoomsContent.Rooms) overrides(content, pending) else emptyMap(),
                    inFlight = local.inFlight,
                    message = local.message,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RoomsUiState.Initial)

    private var address: HubAddress? = null

    init {
        viewModelScope.launch {
            messages.messages.collect { text -> local.update { Local(it.inFlight, text) } }
        }
        viewModelScope.launch {
            session.state.collect { s ->
                val now = (s as? SessionState.Connected)?.address
                if (now != address) {
                    address = now
                    volume.clear()
                }
                if (s is SessionState.Connected && s.snapshot != null) {
                    volume.onRefresh(s.refreshSeq, s.snapshot.rooms.map { it.id }.toSet())
                }
            }
        }
    }

    /** Per card: the dragged value, for cards with a pending room. */
    private fun overrides(content: RoomsContent.Rooms, pending: Map<String, Int>): Map<String, Int> {
        if (pending.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        for (card in content.cards) {
            val hub = hubVolumes(card)
            if (hub.keys.any { it in pending }) result[card.key] = volume.shown(hub.keys, hub)
        }
        return result
    }

    private fun hubVolumes(card: NowPlayingCard): Map<String, Int> = when {
        card.isGroup -> card.memberVolumes
        card.roomId != null -> mapOf(card.roomId to (card.volume ?: 0))
        else -> emptyMap()
    }

    /** Foreground only: the screen calls this when it becomes visible (FR-005). */
    fun onVisible() = session.acquire()

    /** Called when the screen leaves the foreground (SC-006). */
    fun onHidden() = session.release()

    // ---- Controls -------------------------------------------------------------------------

    /** The card for [key], only while the hub is reachable: stale cards are not controllable (FR-004). */
    private fun liveCard(key: String): NowPlayingCard? {
        val s = session.state.value as? SessionState.Connected ?: return null
        if (s.connection != Connection.Live) return null
        return (s.snapshot?.let(::contentOf) as? RoomsContent.Rooms)?.cards?.firstOrNull { it.key == key }
    }

    private fun reportFailure(action: UserAction, targetName: String, error: HubError) =
        local.update { Local(it.inFlight, actionErrorMessage(action, targetName, error)) }

    fun consumeMessage() = local.update { Local(it.inFlight, null) }

    fun onVolumeDragStart(key: String) {
        val card = liveCard(key) ?: return
        if (card.muted || !card.volumeAdjustable) return
        val base = hubVolumes(card).takeIf { it.isNotEmpty() } ?: return
        // A release the hub has not confirmed yet is the base of the next drag.
        val pending = volume.pending.value
        volume.start(key, card.title, base.mapValues { (room, v) -> pending[room] ?: v })
    }

    fun onVolumeDrag(key: String, value: Int) = volume.drag(key, value)

    fun onVolumeDragEnd(key: String, value: Int) = volume.end(key, value)

    fun onCardAction(key: String) {
        val card = liveCard(key) ?: return
        if (!card.actionEnabled) return
        val repo = session.repository ?: return
        val actionKey = ActionKey.Card(key, card.action)
        if (!startAction(actionKey)) return
        viewModelScope.launch {
            val (result, action) = when (card.action) {
                CardAction.Stop -> repo.stopRoute(key) to UserAction.Stop
                CardAction.Pause -> repo.setRoutePaused(key, true) to UserAction.Pause
                CardAction.Resume -> repo.setRoutePaused(key, false) to UserAction.Resume
            }
            finishAction(actionKey, result, action, card.title)
        }
    }

    fun onMasterMuteToggle() {
        val s = session.state.value as? SessionState.Connected ?: return
        if (s.connection != Connection.Live) return
        val muted = (s.snapshot?.let(::contentOf) as? RoomsContent.Rooms)?.masterMuted ?: return
        val repo = session.repository ?: return
        if (!startAction(ActionKey.MasterMute)) return
        viewModelScope.launch {
            val result = repo.setMasterMute(!muted)
            finishAction(ActionKey.MasterMute, result, UserAction.MasterMute(on = !muted), "")
        }
    }

    /** Marks [key] in flight (its control is disabled, FR-019); false if it already was. */
    private fun startAction(key: ActionKey): Boolean {
        if (session.state.value !is SessionState.Connected) return false
        var started = false
        local.update {
            if (key in it.inFlight) it else Local(it.inFlight + key, it.message).also { started = true }
        }
        return started
    }

    private fun finishAction(key: ActionKey, result: HubResult<Unit>, action: UserAction, targetName: String) {
        local.update { Local(it.inFlight - key, it.message) }
        if (result is HubResult.Err) reportFailure(action, targetName, result.error)
        // The next refresh is the confirmation either way (never started directly).
        session.requestRefresh()
    }
}
