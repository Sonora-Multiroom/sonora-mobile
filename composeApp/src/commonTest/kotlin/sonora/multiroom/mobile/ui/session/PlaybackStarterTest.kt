package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Source
import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.domain.SourceOrigin
import sonora.multiroom.mobile.domain.StartNames
import sonora.multiroom.mobile.domain.StartWhat
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.StartFailure
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.unreachable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlaybackStarterTest {
    private val address = HubAddress("http://hub:8080")
    private val bedroom = Target.Room("bedroom")
    private val names = StartNames("Jazz24", "Bedroom")

    private fun room(id: String, name: String) = Room(id, name, 50, muted = false, enabled = true, available = true)
    private fun jazz(enabled: Boolean = true) =
        Source("jazz", "Jazz24", "http://jazz.example/s", SourceOrigin.Configured, false, enabled, SourceKind.Stream)

    private fun snapshot(
        sources: List<Source> = listOf(jazz()),
        rooms: List<Room> = listOf(room("bedroom", "Bedroom"), room("kitchen", "Kitchen")),
        routes: List<Route> = emptyList(),
    ) = HubSnapshot(rooms, emptyList(), routes, sources, masterMuted = false)

    private fun route(id: String, input: String = "jazz", target: Target = bedroom, status: RouteStatus = RouteStatus.Active) =
        Route(id, input, target, status, paused = false, pauseable = false, transferable = true)

    private class Setup(
        val starter: PlaybackStarter,
        val session: HubSession,
        val factory: FakeFactory,
        val posted: MutableList<String>,
    ) {
        val repo get() = factory.last
        val attempt get() = starter.attempt.value
    }

    private fun TestScope.setup(initial: HubSnapshot = snapshot()): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        val messages = AppMessages()
        val posted = mutableListOf<String>()
        backgroundScope.launch { messages.messages.collect { posted += it } }
        runCurrent()
        factory.last.snapshotResult = { HubResult.Ok(initial) }
        session.acquire(); runCurrent()
        return Setup(PlaybackStarter(backgroundScope, session, messages), session, factory, posted)
    }

    private fun Setup.startJazz() = starter.start(StartWhat.Source("jazz"), bedroom, names)

    // ---- success ------------------------------------------------------------------------------

    @Test
    fun successIsDoneWithTheSeqCapturedAtTheAnswerAndARefreshIsRequested() = runTest {
        val s = setup()
        s.repo.startDelayMs = 100
        s.repo.startResults += HubResult.Ok(route("r9"))
        val seqAtStart = s.session.startedSeq
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Starting, s.attempt)
        advanceTimeBy(100); runCurrent()
        assertEquals(StartAttempt.Done(route("r9"), seqAtStart), s.attempt)
        // The refresh asked for right after the answer started later than the captured seq.
        assertEquals(seqAtStart + 1, s.session.startedSeq)
        assertEquals(listOf("startSource"), s.repo.calls.map { it.name })
        assertEquals(listOf<Any>("jazz", bedroom), s.repo.calls.single().args)
    }

    @Test
    fun consumeClearsTheAttempt() = runTest {
        val s = setup()
        s.startJazz(); runCurrent()
        assertIs<StartAttempt.Done>(s.attempt)
        s.starter.consume()
        assertNull(s.attempt)
    }

    @Test
    fun aSecondStartWhileOneRunsIsIgnored() = runTest {
        val s = setup()
        s.repo.startDelayMs = 100
        s.startJazz(); runCurrent()
        s.startJazz(); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertEquals(1, s.repo.calls.count { it.name == "startSource" })
    }

    // ---- refusals -----------------------------------------------------------------------------

    @Test
    fun anAdmissionRefusalNamesTheRoomFromTheLatestSnapshot() = runTest {
        val s = setup()
        s.repo.startResults += HubResult.Err(HubError.Rejected(409, null, "ROUTE_LIMIT_REACHED", "kitchen"))
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.RoomFull("Kitchen")), s.attempt)
    }

    @Test
    fun otherFailuresAreMappedWithoutWaiting() = runTest {
        val s = setup()
        s.repo.startResults += HubResult.Err(HubError.Rejected(422, null))
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.Other), s.attempt)
        s.starter.consume()
        s.repo.startResults += HubResult.Err(HubError.Unexpected)
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.Other), s.attempt)
    }

    // ---- 404 (research R7) --------------------------------------------------------------------

    private val notFound = HubResult.Err(HubError.Rejected(404, "urn:multiroom:error:not-found"))

    @Test
    fun a404WaitsForAFreshSnapshotThenNamesTheMissingSource() = runTest {
        val s = setup()
        s.repo.startResults += notFound
        s.repo.snapshotDelayMs = 1_000
        s.repo.snapshotResult = { HubResult.Ok(snapshot(sources = emptyList())) }
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Starting, s.attempt)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.NoLongerOnHub("Jazz24")), s.attempt)
    }

    @Test
    fun a404WithTheSourceTurnedOffNamesTheSource() = runTest {
        val s = setup()
        s.repo.startResults += notFound
        s.repo.snapshotResult = { HubResult.Ok(snapshot(sources = listOf(jazz(enabled = false)))) }
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.NoLongerOnHub("Jazz24")), s.attempt)
    }

    @Test
    fun a404WithTheTargetGoneNamesTheTarget() = runTest {
        val s = setup()
        s.repo.startResults += notFound
        s.repo.snapshotResult = { HubResult.Ok(snapshot(rooms = listOf(room("kitchen", "Kitchen")))) }
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.NoLongerOnHub("Bedroom")), s.attempt)
    }

    @Test
    fun a404WithEverythingStillThereIsOther() = runTest {
        val s = setup()
        s.repo.startResults += notFound
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.Other), s.attempt)
    }

    // ---- unreachable (research R8) ------------------------------------------------------------

    private val unreachableAnswer = HubResult.Err(HubError.Unreachable)

    @Test
    fun anUnreachableAnswerIsConfirmedByTheHubStateBeforeItIsReported() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        s.repo.snapshotDelayMs = 1_000
        s.repo.snapshotResult = { HubResult.Ok(snapshot(routes = listOf(route("r5")))) }
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Starting, s.attempt)
        advanceTimeBy(1_001); runCurrent()
        val done = assertIs<StartAttempt.Done>(s.attempt)
        assertEquals("r5", done.route.id)
    }

    @Test
    fun severalMatchesTakeTheLastInHubOrder() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        s.repo.snapshotResult = { HubResult.Ok(snapshot(routes = listOf(route("r5"), route("r6"), route("r7", input = "other")))) }
        s.startJazz(); runCurrent()
        assertEquals("r6", assertIs<StartAttempt.Done>(s.attempt).route.id)
    }

    @Test
    fun aRouteOnAnotherTargetOrAStoppedOneDoesNotMatch() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        s.repo.snapshotResult = {
            HubResult.Ok(snapshot(routes = listOf(route("r5", target = Target.Room("kitchen")), route("r6", status = RouteStatus.Stopped))))
        }
        s.startJazz(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.HubUnreachable), s.attempt)
    }

    @Test
    fun noFreshSnapshotWithinFiveSecondsIsHubUnreachable() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        s.repo.snapshotResult = { unreachable }
        s.startJazz(); runCurrent()
        advanceTimeBy(4_900); runCurrent()
        assertEquals(StartAttempt.Starting, s.attempt)
        advanceTimeBy(200); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.HubUnreachable), s.attempt)
    }

    // ---- the screen was closed (research R9) --------------------------------------------------

    @Test
    fun aDetachedFailureIsPostedOnceAndLeavesNoAttempt() = runTest {
        val s = setup()
        s.repo.startDelayMs = 100
        s.repo.startResults += HubResult.Err(HubError.Rejected(409, null, "ROUTE_LIMIT_REACHED", "kitchen"))
        s.startJazz(); runCurrent()
        s.starter.detach()
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf("Kitchen can't play more at once"), s.posted)
        assertNull(s.attempt)
    }

    @Test
    fun aDetachedSuccessPostsNothing() = runTest {
        val s = setup()
        s.repo.startDelayMs = 100
        s.startJazz(); runCurrent()
        s.starter.detach()
        advanceTimeBy(100); runCurrent()
        assertEquals(emptyList(), s.posted)
        assertNull(s.attempt)
    }

    @Test
    fun aDetachedRecoveryThatFindsNothingPostsTheCopy() = runTest {
        val s = setup()
        s.repo.startDelayMs = 100
        s.repo.startResults += unreachableAnswer
        s.startJazz(); runCurrent()
        s.starter.detach()
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf("Couldn't reach the hub"), s.posted)
    }

    // ---- links (research R8) ---------------------------------------------------------------------

    private val linkNames = StartNames(null, "Bedroom")
    private val link = "https://soundcloud.com/artist/track"
    private fun Setup.playLink() = starter.start(StartWhat.Link(link), bedroom, linkNames)
    private fun runtimeInput(uri: String) =
        Source("link-1", "track", uri, SourceOrigin.Runtime, false, true, SourceKind.Link)

    @Test
    fun aLinkSuccessIsDone() = runTest {
        val s = setup()
        s.repo.startResults += HubResult.Ok(route("r9", input = "link-1"))
        s.playLink(); runCurrent()
        assertEquals("r9", assertIs<StartAttempt.Done>(s.attempt).route.id)
        assertEquals(listOf("playLink"), s.repo.calls.map { it.name })
        assertEquals(listOf<Any>(link, bedroom), s.repo.calls.single().args)
    }

    @Test
    fun anUnreachableLinkIsConfirmedByARuntimeInputWithTheSentAddress() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        s.repo.snapshotResult = {
            HubResult.Ok(snapshot(sources = listOf(runtimeInput("HTTPS://SoundCloud.com/artist/track")), routes = listOf(route("r5", input = "link-1"))))
        }
        s.playLink(); runCurrent()
        assertEquals("r5", assertIs<StartAttempt.Done>(s.attempt).route.id)
    }

    @Test
    fun aRuntimeInputWithAnotherAddressDoesNotMatch() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        s.repo.snapshotResult = {
            HubResult.Ok(snapshot(sources = listOf(runtimeInput("https://soundcloud.com/other/thing")), routes = listOf(route("r5", input = "link-1"))))
        }
        s.playLink(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.HubUnreachable), s.attempt)
    }

    @Test
    fun aConfiguredInputWithTheSameAddressDoesNotMatchALink() = runTest {
        val s = setup()
        s.repo.startResults += unreachableAnswer
        val configured = runtimeInput(link).copy(origin = SourceOrigin.Configured)
        s.repo.snapshotResult = { HubResult.Ok(snapshot(sources = listOf(configured), routes = listOf(route("r5", input = "link-1")))) }
        s.playLink(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.HubUnreachable), s.attempt)
    }

    @Test
    fun aLink404NamesTheTargetAfterAFreshSnapshot() = runTest {
        val s = setup()
        s.repo.startResults += notFound
        s.repo.snapshotResult = { HubResult.Ok(snapshot(rooms = listOf(room("kitchen", "Kitchen")))) }
        s.playLink(); runCurrent()
        assertEquals(StartAttempt.Failed(StartFailure.NoLongerOnHub("Bedroom")), s.attempt)
    }

    @Test
    fun aLinkFailureWhileDetachedIsPosted() = runTest {
        val s = setup()
        s.repo.startDelayMs = 100
        s.repo.startResults += HubResult.Err(HubError.Rejected(502, null))
        s.playLink(); runCurrent()
        s.starter.detach()
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf("Couldn't reach that link"), s.posted)
    }
}
