package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubRepository
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Language
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.StationPage
import sonora.multiroom.mobile.domain.StationQuery
import sonora.multiroom.mobile.domain.TagPage
import sonora.multiroom.mobile.domain.Target
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
    override suspend fun setRoomMute(roomId: String, muted: Boolean) = action("setRoomMute", roomId, muted)
    override suspend fun setGroupMute(groupId: String, muted: Boolean) = action("setGroupMute", groupId, muted)
    override suspend fun setRoomEnabled(roomId: String, enabled: Boolean) = action("setRoomEnabled", roomId, enabled)
    override suspend fun setGroupEnabled(groupId: String, enabled: Boolean) = action("setGroupEnabled", groupId, enabled)
    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = action("setSourceEnabled", sourceId, enabled)
    override suspend fun removeSource(sourceId: String) = action("removeSource", sourceId)

    /** What the extensions call answers, after [extensionsDelayMs]. */
    var extensionsResult: () -> HubResult<sonora.multiroom.mobile.domain.ExtensionInventory> =
        { HubResult.Ok(sonora.multiroom.mobile.domain.ExtensionInventory(true, emptyList())) }
    var extensionsDelayMs = 0L
    val extensionsCalls get() = calls.count { it.name == "extensions" }

    override suspend fun extensions(): HubResult<sonora.multiroom.mobile.domain.ExtensionInventory> {
        calls += Call("extensions", emptyList(), time())
        if (extensionsDelayMs > 0) delay(extensionsDelayMs)
        return extensionsResult()
    }

    /** What the connection test answers, after [countDelayMs]. */
    var countRoomsResult: () -> HubResult<Int> = { HubResult.Ok(5) }
    var countDelayMs = 0L

    override suspend fun countRooms(): HubResult<Int> {
        calls += Call("countRooms", emptyList(), time())
        if (countDelayMs > 0) delay(countDelayMs)
        return countRoomsResult()
    }

    /** What a transfer answers; the default hands back a route "moved". */
    var transferResult: () -> HubResult<Route> =
        { HubResult.Ok(Route("moved", "input", Target.Room("a"), sonora.multiroom.mobile.domain.RouteStatus.Active, false, false, true)) }

    override suspend fun transferRoute(routeId: String, target: Target): HubResult<Route> {
        calls += Call("transferRoute", listOf(routeId, target), time())
        if (actionDelayMs > 0) delay(actionDelayMs)
        return transferResult()
    }

    /** What a start answers: the queue first, else a started route. [startDelayMs] delays the answer. */
    val startResults = ArrayDeque<HubResult<Route>>()
    var startDelayMs = 0L
    var startResult: () -> HubResult<Route> = {
        startResults.removeFirstOrNull()
            ?: HubResult.Ok(Route("started", "input", Target.Room("a"), sonora.multiroom.mobile.domain.RouteStatus.Starting, false, false, true))
    }

    override suspend fun startSource(inputId: String, target: Target): HubResult<Route> {
        calls += Call("startSource", listOf(inputId, target), time())
        if (startDelayMs > 0) delay(startDelayMs)
        return startResult()
    }

    override suspend fun playLink(uri: String, target: Target): HubResult<Route> {
        calls += Call("playLink", listOf(uri, target), time())
        if (startDelayMs > 0) delay(startDelayMs)
        return startResult()
    }

    // ---- Radio (MOB-009): scripted answers and recorded calls ------------------------------

    /** Answers the queue first, then [stationsResult]; [radioDelayMs] delays every radio answer. */
    val stationResults = ArrayDeque<HubResult<StationPage>>()
    var stationsResult: (StationQuery, Int) -> HubResult<StationPage> = { _, _ -> HubResult.Ok(StationPage(emptyList(), null)) }
    var radioDelayMs = 0L
    var countriesResult: () -> HubResult<List<Country>> = { HubResult.Ok(emptyList()) }
    var languagesResult: () -> HubResult<List<Language>> = { HubResult.Ok(emptyList()) }
    val tagResults = ArrayDeque<HubResult<TagPage>>()
    var tagsResult: (String?, Int) -> HubResult<TagPage> = { _, _ -> HubResult.Ok(TagPage(emptyList(), null)) }

    /** The searches asked for, in order, with the offset. */
    val searches = mutableListOf<Pair<StationQuery, Int>>()
    val tagSearches = mutableListOf<Pair<String?, Int>>()
    val countriesCalls get() = calls.count { it.name == "radioCountries" }
    val languagesCalls get() = calls.count { it.name == "radioLanguages" }

    override suspend fun searchStations(query: StationQuery, offset: Int): HubResult<StationPage> {
        searches += query to offset
        calls += Call("searchStations", listOf(query, offset), time())
        if (radioDelayMs > 0) delay(radioDelayMs)
        return stationResults.removeFirstOrNull() ?: stationsResult(query, offset)
    }

    override suspend fun radioCountries(): HubResult<List<Country>> {
        calls += Call("radioCountries", emptyList(), time())
        if (radioDelayMs > 0) delay(radioDelayMs)
        return countriesResult()
    }

    override suspend fun radioLanguages(): HubResult<List<Language>> {
        calls += Call("radioLanguages", emptyList(), time())
        if (radioDelayMs > 0) delay(radioDelayMs)
        return languagesResult()
    }

    override suspend fun radioTags(name: String?, offset: Int): HubResult<TagPage> {
        tagSearches += name to offset
        calls += Call("radioTags", listOf(name ?: "", offset), time())
        if (radioDelayMs > 0) delay(radioDelayMs)
        return tagResults.removeFirstOrNull() ?: tagsResult(name, offset)
    }
}

/** Hands out one [FakeRepository] per address, remembering them. */
class FakeFactory(private val time: () -> Long) : HubRepositoryFactory {
    val repositories = linkedMapOf<HubAddress, FakeRepository>()
    val created = mutableListOf<HubAddress>()

    /** Runs on every repository right after it is created, e.g. to set what it answers. */
    var onCreate: (FakeRepository) -> Unit = {}

    override fun create(address: HubAddress): HubRepository {
        created += address
        return FakeRepository(time).also { onCreate(it); repositories[address] = it }
    }

    val last: FakeRepository get() = repositories.values.last()
}

val unreachable = HubResult.Err(HubError.Unreachable)
