package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.data.HubAddressStore
import ai.sonora.mobile.data.HubRepository
import ai.sonora.mobile.data.HubRepositoryFactory
import ai.sonora.mobile.data.HubResult
import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.domain.RoomsBuilder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    }
}
