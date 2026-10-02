package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.data.HubError
import ai.sonora.mobile.data.HubRepository
import ai.sonora.mobile.data.HubRepositoryFactory
import ai.sonora.mobile.data.HubResult
import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.domain.HubSnapshot
import ai.sonora.mobile.domain.Room
import kotlinx.coroutines.delay

/** A snapshot with a single room, enough to produce `RoomsContent.Rooms`. */
val oneRoomSnapshot = HubSnapshot(
    rooms = listOf(Room("a", "A", 50, muted = false, enabled = true, available = true)),
    groups = emptyList(),
    routes = emptyList(),
    sources = emptyList(),
    masterMuted = false,
)

/** A controllable hub for view-model tests; all times are virtual (`time()` = scheduler time). */
class FakeRepository(private val time: () -> Long) : HubRepository {
    var snapshotDelayMs = 0L
    var snapshotResult: () -> HubResult<HubSnapshot> = { HubResult.Ok(oneRoomSnapshot) }

    val snapshotTimes = mutableListOf<Long>()
    var concurrentSnapshots = 0
    var maxConcurrentSnapshots = 0
    val snapshotCalls get() = snapshotTimes.size

    override suspend fun snapshot(): HubResult<HubSnapshot> {
        snapshotTimes += time()
        concurrentSnapshots++
        if (concurrentSnapshots > maxConcurrentSnapshots) maxConcurrentSnapshots = concurrentSnapshots
        try {
            if (snapshotDelayMs > 0) delay(snapshotDelayMs)
            return snapshotResult()
        } finally {
            concurrentSnapshots--
        }
    }

    /** Action calls, recorded with the time they arrived. */
    data class Call(val name: String, val args: List<Any>, val at: Long)

    val calls = mutableListOf<Call>()
    var actionDelayMs = 0L
    var actionResult: () -> HubResult<Unit> = { HubResult.Ok(Unit) }

    private suspend fun action(name: String, vararg args: Any): HubResult<Unit> {
        calls += Call(name, args.toList(), time())
        if (actionDelayMs > 0) delay(actionDelayMs)
        return actionResult()
    }

    override suspend fun setRoomVolume(roomId: String, volume: Int) = action("setRoomVolume", roomId, volume)
    override suspend fun stopRoute(routeId: String) = action("stopRoute", routeId)
    override suspend fun setRoutePaused(routeId: String, paused: Boolean) = action("setRoutePaused", routeId, paused)
    override suspend fun setMasterMute(muted: Boolean) = action("setMasterMute", muted)
}

/** Hands out one [FakeRepository] per address, remembering them. */
class FakeFactory(private val time: () -> Long) : HubRepositoryFactory {
    val repositories = linkedMapOf<HubAddress, FakeRepository>()
    val created = mutableListOf<HubAddress>()

    override fun create(address: HubAddress): HubRepository {
        created += address
        return FakeRepository(time).also { repositories[address] = it }
    }

    val last: FakeRepository get() = repositories.values.last()
}

val unreachable = HubResult.Err(HubError.Unreachable)
