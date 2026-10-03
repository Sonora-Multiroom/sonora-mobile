package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubRepository
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.GroupVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Volume dragging shared by Rooms and Now Playing (research R5). It owns the throttle and the
 * "local value wins" rule, and holds **pending volumes per room id**: what the user dragged to,
 * shown until the hub confirms it.
 *
 * A drag is started with a [key] (the pill) and a `base` of room id -> volume for the rooms the
 * pill covers. Each value is spread over the base with [GroupVolume.scale] and only rooms whose
 * target changed are sent, via `HubRepository.setRoomVolume`. `PUT /groups/{id}/volume` is never
 * used (FR-015). A room's pending value is dropped once a refresh that *started after* its final
 * send succeeds, or when the room disappears.
 */
class VolumeDragController(
    private val scope: CoroutineScope,
    private val session: HubSession,
    private val onError: (targetName: String, error: HubError) -> Unit,
    private val throttleMillis: Long = THROTTLE_MILLIS,
) {
    private val _pending = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Per room id: the value the user last dragged that room to and the hub has not confirmed. */
    val pending: StateFlow<Map<String, Int>> = _pending.asStateFlow()

    private class Drag(
        val targetName: String,
        val base: Map<String, Int>,
        val repository: HubRepository,
    ) {
        var latest: Int = 0
        var sender: Job? = null
    }

    private val drags = mutableMapOf<String, Drag>()

    /** The latest drag that touched a room: only that one may settle or roll back the room. */
    private val owner = mutableMapOf<String, Drag>()

    /** Per room: what was last sent to it, so a value already sent is not sent again. */
    private val lastSent = mutableMapOf<String, Int>()

    /** Per room: [HubSession.startedSeq] when its final send finished; null while still dragging. */
    private val settledAt = mutableMapOf<String, Long>()

    /** One request batch at a time, so a drag started right after a release queues behind it. */
    private val lock = Mutex()

    fun start(key: String, targetName: String, base: Map<String, Int>) {
        if (base.isEmpty()) return
        val repository = session.repository ?: return
        drags.remove(key)?.sender?.cancel()
        val drag = Drag(targetName, base, repository).also { it.latest = base.values.max() }
        drags[key] = drag
        for ((room, volume) in base) {
            owner[room] = drag
            settledAt.remove(room)
            lastSent.getOrPut(room) { volume }
        }
        _pending.value = _pending.value + base
    }

    fun drag(key: String, value: Int) {
        val drag = drags[key] ?: return
        val v = value.coerceIn(0, 100)
        drag.latest = v
        _pending.value = _pending.value + GroupVolume.scale(drag.base, v)
        if (drag.sender == null) {
            drag.sender = scope.launch {
                try {
                    // At most one batch per throttle window, always the latest value.
                    while (true) {
                        val sent = drag.latest
                        send(drag, sent)
                        delay(throttleMillis)
                        if (drag.latest == sent) break
                    }
                } finally {
                    drag.sender = null
                }
            }
        }
    }

    fun end(key: String, value: Int) {
        val drag = drags.remove(key) ?: return
        // Only the throttle's wait is cancelled: a batch already on the wire finishes first, so the
        // final value is always the last one the hub receives.
        drag.sender?.cancel()
        val v = value.coerceIn(0, 100)
        drag.latest = v
        _pending.value = _pending.value + GroupVolume.scale(drag.base, v)
        scope.launch {
            val ok = send(drag, v)
            // Only rooms no newer drag has taken over: that drag owns their display now.
            val mine = drag.base.keys.filter { owner[it] === drag }
            if (ok) {
                // Refreshes that started from now on carry what the hub holds after this send.
                val seq = session.startedSeq
                for (room in mine) settledAt[room] = seq
            } else {
                forget(mine)
            }
            session.requestRefresh()
        }
    }

    /**
     * Called for every successful refresh with its `refreshSeq` (never the seq captured at a send:
     * HubSession fence rule). Drops what that refresh confirms and what no longer exists.
     */
    fun onRefresh(refreshSeq: Long, knownRoomIds: Set<String>) {
        val active = drags.values.flatMap { it.base.keys }.toSet()
        val drop = _pending.value.keys.filter { room ->
            room !in active && (room !in knownRoomIds || settledAt[room]?.let { it < refreshSeq } == true)
        }
        forget(drop)
    }

    /** The hub address changed: nothing pending applies to the new hub. */
    fun clear() {
        drags.values.forEach { it.sender?.cancel() }
        drags.clear()
        owner.clear()
        lastSent.clear()
        settledAt.clear()
        _pending.value = emptyMap()
    }

    /** What a pill covering [roomIds] shows: the loudest room, pending value else hub value. */
    fun shown(roomIds: Collection<String>, hub: Map<String, Int>): Int {
        val pending = _pending.value
        return roomIds.maxOfOrNull { pending[it] ?: hub[it] ?: 0 } ?: 0
    }

    private fun forget(rooms: Collection<String>) {
        if (rooms.isEmpty()) return
        for (room in rooms) {
            owner.remove(room)
            lastSent.remove(room)
            settledAt.remove(room)
        }
        _pending.value = _pending.value - rooms.toSet()
    }

    /** Sends [value] spread over the drag's base; only rooms whose target changed. */
    private suspend fun send(drag: Drag, value: Int): Boolean {
        val failure = withContext(NonCancellable) {
            lock.withLock {
                val changed = GroupVolume.scale(drag.base, value).filter { (id, target) -> lastSent[id] != target }
                val results = coroutineScope {
                    changed.map { (id, target) -> async { Triple(id, target, drag.repository.setRoomVolume(id, target)) } }.awaitAll()
                }
                var failed: HubError? = null
                for ((id, target, result) in results) {
                    when (result) {
                        is HubResult.Ok -> lastSent[id] = target
                        is HubResult.Err -> if (failed == null) failed = result.error
                    }
                }
                failed
            }
        }
        if (failure != null) onError(drag.targetName, failure)
        return failure == null
    }

    companion object {
        const val THROTTLE_MILLIS = 250L
    }
}
