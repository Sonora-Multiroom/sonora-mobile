package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.RadioStore
import sonora.multiroom.mobile.domain.PendingStation
import sonora.multiroom.mobile.domain.RecentVerdict
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.judgeRecent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Decides which started stations become "recent" (MOB-009): one that was started from this phone
 * and has played for 30 seconds, by what the hub reports. It judges every successful refresh of the
 * shared session and never polls, so while the app is in the background nothing is judged and the
 * first refresh on return does it. Pending entries are kept on the phone, so one survives the
 * process being ended.
 */
class RecentStationTracker(
    private val scope: CoroutineScope,
    private val session: HubSession,
    private val store: RadioStore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** [fence] is the session's `startedSeq` when the start was answered; null for a restored entry. */
    private data class Entry(val pending: PendingStation, val fence: Long?)

    private var entries: List<Entry> = emptyList()
    private val loaded = CompletableDeferred<Unit>()
    private val _pending = MutableStateFlow<List<PendingStation>>(emptyList())

    /** What is being watched. */
    val pending: StateFlow<List<PendingStation>> = _pending.asStateFlow()

    init {
        scope.launch {
            // Restored entries have no fence: any refresh may judge them.
            entries = store.pending().map { Entry(it, null) }
            publish()
            loaded.complete(Unit)
            session.state.collect { s ->
                // A failed refresh re-emits the last good snapshot as Unreachable: not evidence.
                if (s is SessionState.Connected && s.snapshot != null && s.connection == Connection.Live) judge(s)
            }
        }
    }

    /**
     * A station start succeeded (also a recovered one, also when Start Playback was closed).
     * [sentAt] is the phone's clock when the request was sent: the audio cannot have begun earlier.
     * An entry made in this process is judged only by a refresh that started after [fence].
     */
    fun track(station: Station, route: Route, sentAt: Long, fence: Long) {
        scope.launch {
            // Never before the stored entries are read, or saving would wipe them.
            loaded.await()
            val entry = Entry(PendingStation(station, route.id, sentAt, null), fence)
            // A newer start of the same route replaces the older entry.
            entries = entries.filterNot { it.pending.routeId == route.id } + entry
            save()
        }
    }

    private suspend fun judge(s: SessionState.Connected) {
        loaded.await()
        val snapshot = s.snapshot ?: return
        var changed = false
        val kept = mutableListOf<Entry>()
        for (entry in entries) {
            if (entry.fence != null && s.refreshSeq <= entry.fence) {
                kept += entry
                continue
            }
            when (val verdict = judgeRecent(entry.pending, snapshot, now())) {
                RecentVerdict.Recent -> {
                    store.addRecent(entry.pending.station)
                    changed = true
                }
                RecentVerdict.Drop -> changed = true
                is RecentVerdict.Wait -> {
                    if (verdict.pending != entry.pending) changed = true
                    kept += entry.copy(pending = verdict.pending)
                }
            }
        }
        entries = kept
        if (changed) save()
    }

    private suspend fun save() {
        publish()
        store.savePending(entries.map { it.pending })
    }

    private fun publish() {
        _pending.value = entries.map { it.pending }
    }
}
