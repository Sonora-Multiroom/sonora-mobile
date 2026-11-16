package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.data.InMemoryRadioStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.PendingStation
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Source
import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.domain.SourceOrigin
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentStationTrackerTest {
    private val address = HubAddress("http://hub:8080")
    private val base = 1_000_000L

    private fun station(id: String) = Station(id, "radiobrowser://$id", "Station $id", null, null, null)
    private val mango = station("u1")

    /** What the hub says plays: one run-time input and one route per station. */
    private data class Playing(val station: Station, val routeId: String, val status: RouteStatus, val startedAt: Long?, val room: String = "kitchen")

    private class Setup(
        val tracker: RecentStationTracker,
        val session: HubSession,
        val factory: FakeFactory,
        val store: InMemoryRadioStore,
    ) {
        val repo get() = factory.last
        var playing: List<Playing> = emptyList()
    }

    private fun TestScope.setup(store: InMemoryRadioStore = InMemoryRadioStore(), acquire: Boolean = true): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        runCurrent()
        val tracker = RecentStationTracker(backgroundScope, session, store) { base + currentTime }
        val s = Setup(tracker, session, factory, store)
        s.repo.snapshotResult = {
            HubResult.Ok(
                HubSnapshot(
                    rooms = listOf(Room("kitchen", "Kitchen", 50, false, true, true), Room("office", "Office", 50, false, true, true)),
                    groups = emptyList(),
                    routes = s.playing.map {
                        Route(
                            it.routeId, "in-${it.routeId}", Target.Room(it.room), it.status, false, false, true,
                            startedAt = it.startedAt?.let(kotlin.time.Instant::fromEpochMilliseconds),
                        )
                    },
                    sources = s.playing.map {
                        Source("in-${it.routeId}", it.station.name!!, it.station.uri, SourceOrigin.Runtime, false, true, SourceKind.Radio)
                    },
                    masterMuted = false,
                ),
            )
        }
        runCurrent()
        if (acquire) session.acquire()
        return s
    }

    private suspend fun Setup.recent() = store.recent.first().map { it.uuid }

    private fun Setup.track(station: Station, routeId: String, sentAt: Long = base + 0, fence: Long = session.startedSeq) {
        tracker.track(station, Route(routeId, "in-$routeId", Target.Room("kitchen"), RouteStatus.Starting, false, false, true), sentAt, fence)
    }

    @Test
    fun trackStoresAPendingEntryWithTheSendingTimeAndNoPlayingSince() = runTest {
        val s = setup(acquire = false)
        s.track(mango, "r1", sentAt = base + 123)
        runCurrent()
        val expected = listOf(PendingStation(mango, "r1", base + 123, null))
        assertEquals(expected, s.tracker.pending.value)
        assertEquals(expected, s.store.pending())
    }

    @Test
    fun theFirstRefreshShowingTheRouteActiveFixesPlayingSinceAndStoresIt() = runTest {
        val s = setup()
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base + 1_000))
        runCurrent()
        s.track(mango, "r1")
        advanceTimeBy(2_500); runCurrent()
        assertEquals(base + 1_000, s.store.pending().single().playingSince)
        assertEquals(emptyList(), s.recent())
    }

    @Test
    fun aRefreshThirtySecondsAfterPlayingSinceAddsTheStationFirstAndDropsTheEntry() = runTest {
        val s = setup()
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base))
        runCurrent()
        s.track(mango, "r1")
        advanceTimeBy(27_500); runCurrent()
        assertEquals(emptyList(), s.recent())
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("u1"), s.recent())
        assertEquals(emptyList(), s.store.pending())
        assertTrue(s.tracker.pending.value.isEmpty())
    }

    @Test
    fun aStartRecoveredLongAfterItWasSentIsCountedFromTheSending() = runTest {
        val s = setup()
        advanceTimeBy(35_000); runCurrent()
        // The answer was lost; the start was found 35 s later. The hub says the audio began 2 s after sending.
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base + 2_000))
        s.track(mango, "r1", sentAt = base)
        advanceTimeBy(2_500); runCurrent()
        assertEquals(listOf("u1"), s.recent())
    }

    @Test
    fun aStationLeftWhileStartingAndFoundPlayingForMinutesIsAddedByTheFirstRefresh() = runTest {
        val s = setup(acquire = false)
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Starting, startedAt = null))
        s.track(mango, "r1")
        runCurrent()
        advanceTimeBy(300_000); runCurrent()
        // The app was in the background; on return the hub says it has played for minutes.
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base + 20_000))
        s.session.acquire()
        runCurrent()
        assertEquals(listOf("u1"), s.recent())
    }

    @Test
    fun aStopWithinThirtySecondsAddsNothing() = runTest {
        val s = setup()
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base))
        s.track(mango, "r1")
        advanceTimeBy(10_000); runCurrent()
        s.playing = emptyList()
        advanceTimeBy(5_000); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(emptyList(), s.recent())
        assertEquals(emptyList(), s.store.pending())
    }

    @Test
    fun aFailedRefreshReplayingAnOldSnapshotNeverCountsAsPlaying() = runTest {
        val s = setup(acquire = false)
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base))
        s.track(mango, "r1")
        runCurrent()
        s.session.acquire()
        runCurrent()
        advanceTimeBy(3_000); runCurrent()
        s.session.release()
        // Stopped while the app was away; on return the hub cannot be reached.
        advanceTimeBy(60_000); runCurrent()
        s.playing = emptyList()
        val live = s.repo.snapshotResult
        s.repo.snapshotResult = { HubResult.Err(HubError.Unreachable) }
        s.session.acquire()
        runCurrent()
        assertEquals(emptyList(), s.recent())
        // The hub answers again: the route is gone, the entry is dropped.
        s.repo.snapshotResult = live
        advanceTimeBy(2_500); runCurrent()
        assertEquals(emptyList(), s.recent())
        assertEquals(emptyList(), s.store.pending())
    }

    @Test
    fun anEntryMadeInThisProcessWaitsForARefreshThatStartedAfterTheStartsAnswer() = runTest {
        val s = setup()
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base - 120_000))
        runCurrent() // refresh 1 is in; it shows the route playing for two minutes
        s.track(mango, "r1", sentAt = base - 130_000, fence = s.session.startedSeq)
        runCurrent()
        // Nothing is judged by what was already in.
        assertEquals(emptyList(), s.recent())
        assertEquals(1, s.store.pending().size)
        advanceTimeBy(2_500); runCurrent()
        assertEquals(listOf("u1"), s.recent())
    }

    @Test
    fun anEntryRestoredFromTheStoreIsJudgedByTheFirstRefresh() = runTest {
        val restored = PendingStation(mango, "r1", base - 100_000, null)
        val s = setup(store = InMemoryRadioStore(pending = listOf(restored)), acquire = false)
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base - 60_000))
        s.session.acquire()
        runCurrent()
        assertEquals(listOf("u1"), s.recent())
    }

    @Test
    fun twoStationsInTwoRoomsAreJudgedByTheirOwnRoutes() = runTest {
        val other = station("u2")
        val s = setup()
        s.playing = listOf(
            Playing(mango, "r1", RouteStatus.Active, startedAt = base),
            Playing(other, "r2", RouteStatus.Active, startedAt = base, room = "office"),
        )
        s.track(mango, "r1")
        s.track(other, "r2")
        advanceTimeBy(10_000); runCurrent()
        // The second room's playback ends; the first goes on.
        s.playing = s.playing.take(1)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(listOf("u1"), s.recent())
    }

    @Test
    fun aRemovedStationCanQualifyAgain() = runTest {
        val s = setup()
        s.playing = listOf(Playing(mango, "r1", RouteStatus.Active, startedAt = base))
        s.track(mango, "r1")
        advanceTimeBy(40_000); runCurrent()
        assertEquals(listOf("u1"), s.recent())
        s.store.removeRecent("u1")
        s.track(mango, "r1")
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf("u1"), s.recent())
    }

    @Test
    fun theListStaysAtTenWithEachStationOnce() = runTest {
        val existing = (1..10).map { station("s$it") }
        val s = setup(store = InMemoryRadioStore(recent = existing))
        s.playing = listOf(Playing(existing[4], "r1", RouteStatus.Active, startedAt = base))
        s.track(existing[4], "r1")
        advanceTimeBy(40_000); runCurrent()
        val ids = s.recent()
        assertEquals(10, ids.size)
        assertEquals("s5", ids.first())
        assertEquals(10, ids.toSet().size)
    }

    @Test
    fun theTrackerNeverPollsByItself() = runTest {
        val s = setup(acquire = false)
        s.track(mango, "r1")
        advanceTimeBy(120_000); runCurrent()
        assertEquals(0, s.repo.snapshotCalls)
    }
}
