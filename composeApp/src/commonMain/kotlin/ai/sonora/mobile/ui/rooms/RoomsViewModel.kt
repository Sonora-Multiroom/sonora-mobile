package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.data.HubAddressStore
import ai.sonora.mobile.data.HubError
import ai.sonora.mobile.data.HubRepository
import ai.sonora.mobile.data.HubRepositoryFactory
import ai.sonora.mobile.data.HubResult
import ai.sonora.mobile.domain.CardAction
import ai.sonora.mobile.domain.GroupVolume
import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.domain.NowPlayingCard
import ai.sonora.mobile.domain.RoomsBuilder
import ai.sonora.mobile.domain.RoomsContent
import ai.sonora.mobile.ui.UserAction
import ai.sonora.mobile.ui.actionErrorMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

class RoomsViewModel(
    private val addressStore: HubAddressStore,
    private val repositoryFactory: HubRepositoryFactory,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
) : ViewModel() {
    private val _state = MutableStateFlow<RoomsUiState>(RoomsUiState.Initial)
    val state: StateFlow<RoomsUiState> = _state.asStateFlow()

    private var repository: HubRepository? = null
    private var pollingWanted = false
    private var pollJob: Job? = null
    private var lastSuccessAt: Long? = null

    /** One per card being dragged: the base volumes and what was last sent (research R10). */
    private class Drag(val title: String, val base: Map<String, Int>, val repository: HubRepository) {
        val lastSent: MutableMap<String, Int> = base.toMutableMap()
        val lock = Mutex()
        var latest: Int = 0
        var sender: Job? = null
    }

    private val drags = mutableMapOf<String, Drag>()

    /**
     * Ends the poll loop's wait early. It never starts a refresh itself: if one is running the
     * signal is picked up right after it, so refreshes can never overlap (research R5).
     */
    private val refreshNow = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            addressStore.address.collect(::onAddress)
        }
    }

    private fun onAddress(address: HubAddress?) {
        drags.values.forEach { it.sender?.cancel() }
        drags.clear()
        repository = address?.let(repositoryFactory::create)
        lastSuccessAt = null
        _state.value = if (address == null) RoomsUiState.NoAddress else RoomsUiState.Connected(address)
        restartLoop()
    }

    /** Foreground only: the screen calls this when it becomes visible. Refreshes immediately. */
    fun startPolling() {
        pollingWanted = true
        restartLoop()
    }

    /** Called when the screen leaves the foreground; cancels any refresh in flight (SC-006). */
    fun stopPolling() {
        pollingWanted = false
        pollJob?.cancel()
        pollJob = null
    }

    internal fun requestRefresh() {
        refreshNow.trySend(Unit)
    }

    // ---- Controls -------------------------------------------------------------------------

    /** The card for [key], only while the hub is reachable: stale cards are not controllable (FR-004). */
    private fun liveCard(key: String): NowPlayingCard? {
        val s = _state.value as? RoomsUiState.Connected ?: return null
        if (s.connection != Connection.Live) return null
        return (s.content as? RoomsContent.Rooms)?.cards?.firstOrNull { it.key == key }
    }

    private fun updateConnected(change: (RoomsUiState.Connected) -> RoomsUiState.Connected) {
        _state.update { if (it is RoomsUiState.Connected) change(it) else it }
    }

    private fun setOverride(key: String, value: Int?) = updateConnected {
        it.copy(volumeOverrides = if (value == null) it.volumeOverrides - key else it.volumeOverrides + (key to value))
    }

    private fun reportFailure(action: UserAction, targetName: String, error: HubError) =
        updateConnected { it.copy(message = actionErrorMessage(action, targetName, error)) }

    fun consumeMessage() = updateConnected { it.copy(message = null) }

    fun onVolumeDragStart(key: String) {
        val card = liveCard(key) ?: return
        if (card.muted || !card.volumeAdjustable) return
        val repo = repository ?: return
        val base = if (card.isGroup) card.memberVolumes else card.roomId?.let { mapOf(it to (card.volume ?: 0)) }
        if (base.isNullOrEmpty()) return
        drags.remove(key)?.sender?.cancel()
        drags[key] = Drag(card.title, base, repo).also { it.latest = card.volume ?: 0 }
        setOverride(key, card.volume ?: 0)
    }

    fun onVolumeDrag(key: String, value: Int) {
        val drag = drags[key] ?: return
        val v = value.coerceIn(0, 100)
        drag.latest = v
        setOverride(key, v)
        if (drag.sender == null) {
            drag.sender = viewModelScope.launch {
                try {
                    // At most one batch per THROTTLE_MILLIS, always the latest value.
                    while (true) {
                        val sent = drag.latest
                        sendVolume(drag, sent)
                        delay(VOLUME_THROTTLE_MILLIS)
                        if (drag.latest == sent) break
                    }
                } finally {
                    drag.sender = null
                }
            }
        }
    }

    fun onVolumeDragEnd(key: String, value: Int) {
        val drag = drags.remove(key) ?: return
        // Only the throttle's wait is cancelled: a batch already on the wire finishes first, so the
        // final value is always the last one the hub receives.
        drag.sender?.cancel()
        drag.latest = value.coerceIn(0, 100)
        setOverride(key, drag.latest)
        viewModelScope.launch {
            sendVolume(drag, drag.latest)
            setOverride(key, null)
            requestRefresh()
        }
    }

    /**
     * Sets the drag's members to [value] scaled from the drag-start base. A member is sent only when
     * its target differs from what was last sent to it, so dragging back to the start still restores
     * it. `PUT /groups/{id}/volume` is never used (FR-013c).
     */
    private suspend fun sendVolume(drag: Drag, value: Int) {
        val failure = withContext(NonCancellable) {
            drag.lock.withLock {
                val changed = GroupVolume.scale(drag.base, value).filter { (id, target) -> drag.lastSent[id] != target }
                val results = coroutineScope {
                    changed.map { (id, target) -> async { Triple(id, target, drag.repository.setRoomVolume(id, target)) } }.awaitAll()
                }
                var failed: HubError? = null
                for ((id, target, result) in results) {
                    when (result) {
                        is HubResult.Ok -> drag.lastSent[id] = target
                        is HubResult.Err -> if (failed == null) failed = result.error
                    }
                }
                failed
            }
        }
        if (failure != null) reportFailure(UserAction.Volume, drag.title, failure)
    }

    fun onCardAction(key: String) {
        val card = liveCard(key) ?: return
        if (!card.actionEnabled) return
        val repo = repository ?: return
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
        val s = _state.value as? RoomsUiState.Connected ?: return
        if (s.connection != Connection.Live) return
        val muted = (s.content as? RoomsContent.Rooms)?.masterMuted ?: return
        val repo = repository ?: return
        if (!startAction(ActionKey.MasterMute)) return
        viewModelScope.launch {
            val result = repo.setMasterMute(!muted)
            finishAction(ActionKey.MasterMute, result, UserAction.MasterMute(on = !muted), "")
        }
    }

    /** Marks [key] in flight (its control is disabled, FR-019); false if it already was. */
    private fun startAction(key: ActionKey): Boolean {
        val s = _state.value as? RoomsUiState.Connected ?: return false
        if (key in s.inFlight) return false
        updateConnected { it.copy(inFlight = it.inFlight + key) }
        return true
    }

    private fun finishAction(key: ActionKey, result: HubResult<Unit>, action: UserAction, targetName: String) {
        updateConnected { it.copy(inFlight = it.inFlight - key) }
        if (result is HubResult.Err) reportFailure(action, targetName, result.error)
        // The next refresh is the confirmation either way (never started directly, research R5).
        requestRefresh()
    }

    private fun restartLoop() {
        pollJob?.cancel()
        pollJob = null
        // A stale signal is moot: starting the loop refreshes anyway.
        refreshNow.tryReceive()
        val repo = repository ?: return
        if (!pollingWanted) return
        pollJob = viewModelScope.launch {
            // The only caller of refreshOnce(): sequential by construction.
            while (isActive) {
                refreshOnce(repo)
                withTimeoutOrNull(pollIntervalMillis) { refreshNow.receive() }
            }
        }
    }

    private suspend fun refreshOnce(repo: HubRepository) {
        when (val result = repo.snapshot()) {
            is HubResult.Ok -> {
                lastSuccessAt = now()
                val content = RoomsBuilder.build(result.value)
                _state.update { s ->
                    if (s is RoomsUiState.Connected) s.copy(connection = Connection.Live, content = content) else s
                }
            }

            is HubResult.Err -> _state.update { s ->
                if (s is RoomsUiState.Connected) s.copy(connection = Connection.Unreachable(lastSuccessAt)) else s
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 2500L
        const val VOLUME_THROTTLE_MILLIS = 250L
    }
}
