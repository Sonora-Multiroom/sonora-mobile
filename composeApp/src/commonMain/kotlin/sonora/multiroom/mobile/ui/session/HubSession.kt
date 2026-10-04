package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.data.HubRepository
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import kotlinx.coroutines.CoroutineScope
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

sealed interface SessionState {
    /** Before the saved address has been read; draws nothing so "no hub" doesn't flash. */
    data object Initial : SessionState

    /** No hub address saved: no request is ever made. */
    data object NoAddress : SessionState

    data class Connected(
        val address: HubAddress,
        val connection: Connection = Connection.Loading,
        /** The last successful refresh; null before the first success. */
        val snapshot: HubSnapshot? = null,
        /** The [HubSession.startedSeq] of the refresh whose result [snapshot] is (0 before any). */
        val refreshSeq: Long = 0,
    ) : SessionState
}

/**
 * The one poll loop of the app, shared by every screen (research R1). Polling runs while at least
 * one visible screen holds the session ([acquire]/[release]) and an address exists, never with two
 * refreshes overlapping.
 *
 * Fence rule: to wait for "a refresh that started after X", capture [startedSeq] at X and accept
 * the first state whose `refreshSeq` is greater. Never capture the state's own `refreshSeq`: a
 * refresh already in flight at X would then pass although it may predate X.
 */
class HubSession(
    private val addressStore: HubAddressStore,
    private val repositoryFactory: HubRepositoryFactory,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Initial)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** The number of the most recent refresh started; incremented just before its request. */
    var startedSeq: Long = 0L
        private set

    var repository: HubRepository? = null
        private set

    private var holders = 0
    private var pollJob: Job? = null
    private var lastSuccessAt: Long? = null

    /**
     * Ends the poll loop's wait early. It never starts a refresh itself: if one is running the
     * signal is picked up right after it, so refreshes can never overlap.
     */
    private val refreshNow = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { addressStore.address.collect(::onAddress) }
    }

    private fun onAddress(address: HubAddress?) {
        repository = address?.let(repositoryFactory::create)
        lastSuccessAt = null
        _state.value = if (address == null) SessionState.NoAddress else SessionState.Connected(address)
        restartLoop()
    }

    /** A screen became visible. Going from 0 to 1 holders starts polling with an immediate refresh. */
    fun acquire() {
        holders++
        if (holders == 1) restartLoop()
    }

    /** A screen left the foreground. The last release cancels the loop and any refresh in flight. */
    fun release() {
        if (holders == 0) return
        holders--
        if (holders == 0) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    fun requestRefresh() {
        refreshNow.trySend(Unit)
    }

    private fun restartLoop() {
        pollJob?.cancel()
        pollJob = null
        // A stale signal is moot: starting the loop refreshes anyway.
        refreshNow.tryReceive()
        val repo = repository ?: return
        if (holders == 0) return
        pollJob = scope.launch {
            // The only caller of refreshOnce(): sequential by construction.
            while (isActive) {
                refreshOnce(repo)
                withTimeoutOrNull(pollIntervalMillis) { refreshNow.receive() }
            }
        }
    }

    private suspend fun refreshOnce(repo: HubRepository) {
        val seq = ++startedSeq
        when (val result = repo.snapshot()) {
            is HubResult.Ok -> {
                lastSuccessAt = now()
                _state.update { s ->
                    if (s is SessionState.Connected) {
                        s.copy(connection = Connection.Live, snapshot = result.value, refreshSeq = seq)
                    } else s
                }
            }

            is HubResult.Err -> _state.update { s ->
                if (s is SessionState.Connected) s.copy(connection = Connection.Unreachable(lastSuccessAt)) else s
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 2500L
    }
}
