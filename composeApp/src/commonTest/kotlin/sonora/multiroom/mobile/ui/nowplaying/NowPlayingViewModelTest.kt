package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.Group
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Source
import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.domain.SourceOrigin
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.runViewModelTest
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.unreachable
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.HubSession
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NowPlayingViewModelTest {
    private val address = HubAddress("http://hub:8080")

    private fun room(id: String, name: String, volume: Int, muted: Boolean = false, enabled: Boolean = true) =
        Room(id, name, volume, muted, enabled, available = true)

    private fun route(
        id: String, input: String, target: Target,
        status: RouteStatus = RouteStatus.Active, paused: Boolean = false,
        pauseable: Boolean = false, transferable: Boolean = true,
    ) = Route(id, input, target, status, paused, pauseable, transferable)

    private val radio = Source("radio", "Radio Paradise", "http://radio.example/s", SourceOrigin.Configured, false, true, SourceKind.Stream)
    private val playlist = Source("playlist", "Morning playlist", "file:///m.m3u", SourceOrigin.Configured, true, true, SourceKind.File)

    /** Group Downstairs (living 70, kitchen 35) plays radio (r1); Office plays a playlist (r2). */
    private fun snapshot(
        r1: Boolean = true,
        r2: Boolean = true,
        r1Transferable: Boolean = true,
        r2Status: RouteStatus = RouteStatus.Active,
        r2Paused: Boolean = false,
        r2Transferable: Boolean = true,
        masterMuted: Boolean = false,
        groupMuted: Boolean = false,
        bedroomEnabled: Boolean = true,
        patioEnabled: Boolean = true,
        extra: List<Route> = emptyList(),
        r1Target: Target = Target.Group("down"),
    ) = HubSnapshot(
        rooms = listOf(
            room("living", "Living Room", 70), room("kitchen", "Kitchen", 35), room("office", "Office", 40),
            room("bedroom", "Bedroom", 50, enabled = bedroomEnabled), room("patio", "Patio", 20, enabled = patioEnabled),
        ),
        groups = listOf(Group("down", "Downstairs", listOf("living", "kitchen"), muted = groupMuted, enabled = true)),
        routes = buildList {
            if (r1) add(route("r1", "radio", r1Target, transferable = r1Transferable))
            if (r2) add(route("r2", "playlist", Target.Room("office"), r2Status, r2Paused, pauseable = true, transferable = r2Transferable))
            addAll(extra)
        },
        sources = listOf(radio, playlist),
        masterMuted = masterMuted,
    )

    private class Setup(
        val vm: NowPlayingViewModel,
        val session: HubSession,
        val factory: FakeFactory,
        val messages: AppMessages,
        val posted: MutableList<String>,
        val handle: SavedStateHandle,
    ) {
        val repo get() = factory.last
        val ui get() = vm.state.value
    }

    private fun TestScope.live(
        routeId: String = "r1",
        snapshot: HubSnapshot = snapshot(),
        handle: SavedStateHandle = SavedStateHandle(),
        visible: Boolean = true,
    ): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        val messages = AppMessages()
        val posted = mutableListOf<String>()
        backgroundScope.launch { messages.messages.collect { posted += it } }
        runCurrent()
        factory.last.snapshotResult = { HubResult.Ok(snapshot) }
        val vm = NowPlayingViewModel(routeId, handle, session, messages)
        if (visible) vm.onVisible()
        runCurrent()
        return Setup(vm, session, factory, messages, posted, handle)
    }

    private fun Setup.content() = assertNotNull(ui.content)
    private fun Setup.calls(name: String) = repo.calls.filter { it.name == name }
    private fun Setup.refresh(snapshot: HubSnapshot) {
        repo.snapshotResult = { HubResult.Ok(snapshot) }
        session.requestRefresh()
    }

    // ---- US1: opening and following --------------------------------------------------------------

    @Test
    fun openingShowsTheSessionsExistingSnapshotBeforeAnyNewRequest() = runViewModelTest {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        runCurrent()
        factory.last.snapshotResult = { HubResult.Ok(snapshot()) }
        session.acquire(); runCurrent(); session.release()
        assertEquals(1, factory.last.snapshotCalls)

        val vm = NowPlayingViewModel("r1", SavedStateHandle(), session, AppMessages())
        runCurrent()
        assertEquals("Radio Paradise", vm.state.value.content?.sourceName)
        assertEquals(1, factory.last.snapshotCalls)
    }

    @Test
    fun onVisibleAcquiresPollingAndOnHiddenReleasesIt() = runViewModelTest {
        val s = live(visible = false)
        assertEquals(0, s.repo.snapshotCalls)
        s.vm.onVisible(); runCurrent()
        assertEquals(1, s.repo.snapshotCalls)
        s.vm.onHidden()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, s.repo.snapshotCalls)
    }

    @Test
    fun theStateShowsTheRouteDetails() = runViewModelTest {
        val s = live()
        val c = s.content()
        assertEquals("Radio Paradise", c.sourceName)
        assertEquals("Downstairs", c.target.name)
        assertTrue(c.live)
        assertEquals(Connection.Live, s.ui.connection)
        assertEquals(address, s.ui.address)
    }

    @Test
    fun stopCallsStopRouteAndLeavesWithoutAMessage() = runViewModelTest {
        val s = live()
        s.vm.onStop(); runCurrent()
        assertEquals(listOf(listOf<Any>("r1")), s.calls("stopRoute").map { it.args })
        assertEquals(Exit.Stopped, s.ui.exit)
        assertEquals(emptyList(), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun stopFindingThePlaybackGoneLeavesWithTheEndedMessage() = runViewModelTest {
        val s = live()
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(404, null)) }
        s.vm.onStop(); runCurrent()
        assertEquals(Exit.Ended("Downstairs"), s.ui.exit)
        assertEquals(listOf("Playback on Downstairs ended"), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshLackingTheRouteAfterAStopWasConsumedStaysSilent() = runViewModelTest {
        val s = live()
        s.vm.onStop(); runCurrent()
        // The screen pops and consumes the exit; the entry lives on through its exit animation.
        s.vm.consumeExit()
        s.refresh(snapshot(r1 = false)); runCurrent()
        assertNull(s.ui.exit)
        assertEquals(emptyList(), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun theEndedMessageIsPostedOnceEvenAfterTheExitWasConsumed() = runViewModelTest {
        val s = live()
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(404, null)) }
        s.vm.onStop(); runCurrent()
        s.vm.consumeExit()
        s.refresh(snapshot(r1 = false)); runCurrent()
        assertNull(s.ui.exit)
        assertEquals(listOf("Playback on Downstairs ended"), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun aFailedStopStaysOpenWithAMessage() = runViewModelTest {
        val s = live()
        s.repo.actionResult = { unreachable }
        s.vm.onStop(); runCurrent()
        assertNull(s.ui.exit)
        assertEquals("Couldn't stop Downstairs. Can't reach the hub.", s.ui.message)
        assertTrue(s.ui.inFlight.isEmpty())
        s.vm.consumeMessage()
        assertNull(s.ui.message)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshLackingTheRouteWhileStopIsInFlightNeverEndsTheScreenAndTheAnswerDecides() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 1000
        s.vm.onStop(); runCurrent()
        assertTrue(NowPlayingAction.Stop in s.ui.inFlight)
        s.refresh(snapshot(r1 = false)); runCurrent()
        assertNull(s.ui.exit)
        assertEquals(emptyList(), s.posted)
        advanceTimeBy(1000); runCurrent()
        assertEquals(Exit.Stopped, s.ui.exit)
        assertEquals(emptyList(), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun theRouteDisappearingFromARefreshEndsTheScreenWithAMessage() = runViewModelTest {
        val s = live()
        s.refresh(snapshot(r1 = false)); runCurrent()
        assertEquals(Exit.Ended("Downstairs"), s.ui.exit)
        assertEquals(listOf("Playback on Downstairs ended"), s.posted)
        s.vm.consumeExit()
        assertNull(s.ui.exit)
        s.vm.onHidden()
    }

    @Test
    fun aRouteIdAbsentFromTheFirstSnapshotCountsAsEnded() = runViewModelTest {
        val s = live(routeId = "nope")
        assertIs<Exit.Ended>(s.ui.exit)
        assertEquals(1, s.posted.size)
        s.vm.onHidden()
    }

    @Test
    fun pauseAndResumeCallSetRoutePaused() = runViewModelTest {
        val s = live(routeId = "r2")
        s.vm.onPauseResume(); runCurrent()
        s.refresh(snapshot(r2Paused = true)); runCurrent()
        assertTrue(s.content().paused)
        s.vm.onPauseResume(); runCurrent()
        assertEquals(
            listOf(listOf<Any>("r2", true), listOf<Any>("r2", false)),
            s.calls("setRoutePaused").map { it.args },
        )
        s.vm.onHidden()
    }

    @Test
    fun aRepeatedTapWhileInFlightSendsNothing() = runViewModelTest {
        val s = live(routeId = "r2")
        s.repo.actionDelayMs = 1000
        s.vm.onPauseResume(); runCurrent()
        s.vm.onPauseResume(); runCurrent()
        s.vm.onStop(); s.vm.onStop(); runCurrent()
        assertEquals(1, s.calls("setRoutePaused").size)
        assertEquals(1, s.calls("stopRoute").size)
        s.vm.onHidden()
    }

    @Test
    fun pauseWhilePauseIsNotEnabledSendsNothing() = runViewModelTest {
        val s = live(routeId = "r2", snapshot = snapshot(r2Status = RouteStatus.Starting))
        assertFalse(s.content().pauseEnabled)
        s.vm.onPauseResume(); runCurrent()
        assertEquals(emptyList(), s.calls("setRoutePaused"))
        // A live stream has no Pause at all.
        val live = live(routeId = "r1")
        live.vm.onPauseResume(); runCurrent()
        assertEquals(emptyList(), live.calls("setRoutePaused"))
        s.vm.onHidden(); live.vm.onHidden()
    }

    @Test
    fun whileTheHubIsUnreachableEveryActionIsIgnoredAndTheStateSaysSo() = runViewModelTest {
        val s = live(routeId = "r2")
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertTrue(s.ui.stale)
        assertFalse(s.ui.controlsEnabled)
        assertNotNull(s.ui.content)

        s.vm.onStop(); s.vm.onPauseResume(); s.vm.onMuteToggle(); s.vm.onOpenMove()
        s.vm.onVolumeDragStart("main"); s.vm.onVolumeDrag("main", 70); s.vm.onVolumeDragEnd("main", 70)
        advanceTimeBy(1000); runCurrent()
        assertEquals(emptyList(), s.repo.calls)
        assertNull(s.ui.sheet)
        s.vm.onHidden()
    }

    @Test
    fun afterAnyActionARefreshIsRequested() = runViewModelTest {
        val s = live(routeId = "r2")
        val before = s.repo.snapshotCalls
        s.vm.onPauseResume(); runCurrent()
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.vm.onHidden()
    }

    // ---- US2: volume and mute ----------------------------------------------------------------------

    @Test
    fun aMainPillDragOnAGroupScalesEveryMemberAndTheMemberPillsFollow() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("main")
        s.vm.onVolumeDrag("main", 35)
        runCurrent()
        assertEquals(setOf<List<Any>>(listOf("living", 35), listOf("kitchen", 18)), s.calls("setRoomVolume").map { it.args }.toSet())
        val section = assertNotNull(s.content().volume)
        assertEquals(35, shownVolume(section.main, s.ui.pending))
        assertEquals(listOf(35, 18), section.members.map { shownVolume(it, s.ui.pending) })
        assertTrue(s.repo.calls.none { it.name.contains("Group") })
        s.vm.onHidden()
    }

    @Test
    fun aMemberDragChangesOnlyThatRoomAndTheGroupMaxFollows() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("member:kitchen")
        s.vm.onVolumeDrag("member:kitchen", 90)
        runCurrent()
        assertEquals(listOf<List<Any>>(listOf("kitchen", 90)), s.calls("setRoomVolume").map { it.args })
        assertEquals(90, shownVolume(s.content().volume!!.main, s.ui.pending))
        s.vm.onVolumeDragEnd("member:kitchen", 90); runCurrent()
        s.vm.onHidden()
    }

    @Test
    fun aMutedPillIgnoresDragsWhateverTheReason() = runViewModelTest {
        for (snap in listOf(snapshot(masterMuted = true), snapshot(groupMuted = true))) {
            val s = live(snapshot = snap)
            s.vm.onVolumeDragStart("main"); s.vm.onVolumeDrag("main", 50); s.vm.onVolumeDragEnd("main", 50)
            advanceTimeBy(1000); runCurrent()
            assertEquals(emptyList(), s.calls("setRoomVolume"))
            s.vm.onHidden()
        }
    }

    @Test
    fun muteTogglesTheGroupThroughSetGroupMuteWithoutFlippingTheShownState() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 1000
        s.vm.onMuteToggle(); runCurrent()
        assertEquals(listOf<List<Any>>(listOf("down", true)), s.calls("setGroupMute").map { it.args })
        assertFalse(s.content().volume!!.mute.muted, "no optimistic flip")
        // In flight blocks a repeat.
        s.vm.onMuteToggle(); runCurrent()
        assertEquals(1, s.calls("setGroupMute").size)
        advanceTimeBy(1000); runCurrent()
        // Once the hub reports it muted, the next tap unmutes.
        s.refresh(snapshot(groupMuted = true)); runCurrent()
        assertTrue(s.content().volume!!.mute.muted)
        s.vm.onMuteToggle(); runCurrent()
        assertEquals(listOf<List<Any>>(listOf("down", true), listOf("down", false)), s.calls("setGroupMute").map { it.args })
        s.vm.onHidden()
    }

    @Test
    fun muteOnASingleRoomUsesSetRoomMute() = runViewModelTest {
        val s = live(routeId = "r2")
        s.vm.onMuteToggle(); runCurrent()
        assertEquals(listOf<List<Any>>(listOf("office", true)), s.calls("setRoomMute").map { it.args })
        s.vm.onHidden()
    }

    @Test
    fun masterMuteIgnoresTheMuteButtonAndFlagsTheState() = runViewModelTest {
        val s = live(snapshot = snapshot(masterMuted = true))
        assertTrue(s.content().volume!!.masterMuted)
        s.vm.onMuteToggle(); runCurrent()
        assertEquals(emptyList(), s.repo.calls)
        s.vm.onHidden()
    }

    @Test
    fun aFailedMuteShowsAPlainMessage() = runViewModelTest {
        val s = live()
        s.repo.actionResult = { unreachable }
        s.vm.onMuteToggle(); runCurrent()
        assertEquals("Couldn't mute Downstairs. Can't reach the hub.", s.ui.message)
        s.vm.onHidden()
    }

    // ---- US3: Move to room… ------------------------------------------------------------------------

    @Test
    fun theSheetOpensOnlyWhenMoveIsVisibleAndStartsWithNothingSelected() = runViewModelTest {
        val s = live()
        s.vm.onOpenMove(); runCurrent()
        assertNotNull(s.ui.sheet)
        assertNull(s.ui.sheet!!.selected)
        s.vm.onHidden()

        val blocked = live(snapshot = snapshot(r1Transferable = false))
        blocked.vm.onOpenMove(); runCurrent()
        assertNull(blocked.ui.sheet)
        blocked.vm.onHidden()
    }

    @Test
    fun onlySelectableDestinationsCanBeSelectedAndTheButtonNeedsOne() = runViewModelTest {
        val s = live(snapshot = snapshot(patioEnabled = false))
        s.vm.onOpenMove(); runCurrent()
        s.vm.onSelect(Target.Room("patio")); runCurrent()
        assertNull(s.ui.sheet!!.selected)
        s.vm.onConfirmMove(); runCurrent()
        assertEquals(emptyList(), s.calls("transferRoute"))
        s.vm.onSelect(Target.Room("bedroom")); runCurrent()
        assertEquals(Target.Room("bedroom"), s.ui.sheet!!.selected)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshMakingTheSelectionUnselectableClearsIt() = runViewModelTest {
        val s = live()
        s.vm.onOpenMove(); s.vm.onSelect(Target.Room("bedroom")); runCurrent()
        s.refresh(snapshot(bedroomEnabled = false)); runCurrent()
        assertNull(s.ui.sheet!!.selected)
        s.vm.onHidden()
    }

    @Test
    fun theSheetClosesWhenMoveBecomesUnavailable() = runViewModelTest {
        val s = live(routeId = "r2")
        s.vm.onOpenMove(); runCurrent()
        assertNotNull(s.ui.sheet)
        s.refresh(snapshot(r2Paused = true)); runCurrent()
        assertNull(s.ui.sheet, "paused")
        s.vm.onOpenMove(); runCurrent()
        s.refresh(snapshot(r2Paused = false)); runCurrent()
        s.vm.onOpenMove(); runCurrent()
        assertNotNull(s.ui.sheet)
        s.refresh(snapshot(r2Transferable = false)); runCurrent()
        assertNull(s.ui.sheet, "not transferable")
        s.vm.onHidden()
    }

    @Test
    fun withNothingToMoveToTheSheetOpensButNothingCanBeSelected() = runViewModelTest {
        val only = HubSnapshot(
            rooms = listOf(room("living", "Living Room", 70)),
            groups = emptyList(),
            routes = listOf(route("r1", "radio", Target.Room("living"))),
            sources = listOf(radio),
            masterMuted = false,
        )
        val s = live(snapshot = only)
        s.vm.onOpenMove(); runCurrent()
        assertEquals(emptyList(), s.ui.sheet!!.content.rooms)
        s.vm.onSelect(Target.Room("living")); s.vm.onConfirmMove(); runCurrent()
        assertNull(s.ui.sheet!!.selected)
        assertEquals(emptyList(), s.calls("transferRoute"))
        s.vm.onHidden()
    }

    @Test
    fun dismissingClosesTheSheetWithoutARequest() = runViewModelTest {
        val s = live()
        s.vm.onOpenMove(); s.vm.onSelect(Target.Room("bedroom")); s.vm.onDismissMove(); runCurrent()
        assertNull(s.ui.sheet)
        assertEquals(emptyList(), s.calls("transferRoute"))
        s.vm.onHidden()
    }

    private fun Setup.confirmMoveToBedroom() {
        vm.onOpenMove(); vm.onSelect(Target.Room("bedroom")); vm.onConfirmMove()
    }

    private fun moved(id: String = "r9", target: Target = Target.Room("bedroom")) =
        route(id, "radio", target)

    @Test
    fun confirmTransfersTheFollowedRouteAndARepeatedConfirmWhileInFlightSendsNothing() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 1000
        s.repo.transferResult = { HubResult.Ok(moved()) }
        s.confirmMoveToBedroom(); runCurrent()
        s.vm.onConfirmMove(); runCurrent()
        assertEquals(listOf<List<Any>>(listOf("r1", Target.Room("bedroom"))), s.calls("transferRoute").map { it.args })
        s.vm.onHidden()
    }

    @Test
    fun afterAMoveTheScreenFollowsTheNewRouteAndNeverLeavesOnARefreshStartedBeforeIt() = runViewModelTest {
        val s = live()
        s.repo.transferResult = { HubResult.Ok(moved()) }
        // The hub reflects the move as soon as it has answered.
        s.repo.snapshotResult = { HubResult.Ok(snapshot(r1 = false, extra = listOf(moved()))) }
        s.confirmMoveToBedroom(); runCurrent()
        assertEquals("r9", s.handle.get<String>("followedRouteId"))
        assertNull(s.ui.sheet)
        assertNull(s.ui.exit)
        // The first refresh started after the switch shows r9's target.
        assertEquals("Bedroom", s.content().target.name)
        // Later, r9 disappearing is a real end.
        s.refresh(snapshot(r1 = false)); runCurrent()
        assertEquals(Exit.Ended("Bedroom"), s.ui.exit)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshAlreadyRunningWhenTheMoveAnsweredCannotEndTheScreen() = runViewModelTest {
        val s = live()
        s.repo.transferResult = { HubResult.Ok(moved()) }
        s.repo.actionDelayMs = 100
        s.confirmMoveToBedroom()
        // A slow refresh begins before the answer and finishes after it, without r1 or r9.
        s.repo.snapshotDelayMs = 500
        s.repo.snapshotResult = { HubResult.Ok(snapshot(r1 = false)) }
        s.session.requestRefresh()
        advanceTimeBy(50); runCurrent()
        advanceTimeBy(600); runCurrent()
        assertNull(s.ui.exit)
        assertEquals(emptyList(), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshLackingTheRouteBeforeTheMoveIsAnsweredNeverEndsTheScreen() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 1000
        s.repo.transferResult = { HubResult.Ok(moved()) }
        s.confirmMoveToBedroom(); runCurrent()
        s.refresh(snapshot(r1 = false)); runCurrent()
        assertNull(s.ui.exit)
        assertEquals(emptyList(), s.posted)
        // By the time the answer arrives the hub shows the moved route.
        s.repo.snapshotResult = { HubResult.Ok(snapshot(r1 = false, extra = listOf(moved()))) }
        advanceTimeBy(1000); runCurrent()
        assertEquals("r9", s.handle.get<String>("followedRouteId"))
        assertNull(s.ui.exit)
        s.vm.onHidden()
    }

    @Test
    fun aRestoredViewModelFollowsTheMovedRouteAndPostsNoEndedMessage() = runViewModelTest {
        val handle = SavedStateHandle()
        val first = live(handle = handle)
        first.repo.transferResult = { HubResult.Ok(moved()) }
        first.confirmMoveToBedroom(); runCurrent()
        first.vm.onHidden()

        val restored = live(routeId = "r1", handle = handle, snapshot = snapshot(r1 = false, extra = listOf(moved())))
        assertEquals("Bedroom", restored.content().target.name)
        assertNull(restored.ui.exit)
        assertEquals(emptyList(), restored.posted)
        restored.vm.onHidden()
    }

    @Test
    fun aFailedMoveClosesTheSheetAndSaysSo() = runViewModelTest {
        val s = live()
        s.repo.transferResult = { HubResult.Err(HubError.Rejected(422, null)) }
        s.confirmMoveToBedroom(); runCurrent()
        assertNull(s.ui.sheet)
        assertEquals("Couldn't move Radio Paradise to Bedroom.", s.ui.message)
        assertEquals("r1", s.handle.get<String>("followedRouteId"))
        s.vm.consumeMessage()

        s.repo.transferResult = { unreachable }
        s.confirmMoveToBedroom(); runCurrent()
        assertEquals("Couldn't move Radio Paradise to Bedroom. Can't reach the hub.", s.ui.message)
        s.vm.onHidden()
    }

    @Test
    fun confirmIsIgnoredWhileTheHubIsUnreachable() = runViewModelTest {
        val s = live()
        s.vm.onOpenMove(); s.vm.onSelect(Target.Room("bedroom")); runCurrent()
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        s.vm.onConfirmMove(); runCurrent()
        assertEquals(emptyList(), s.calls("transferRoute"))
        s.vm.onHidden()
    }

    // ---- 003: opened for a playback the snapshot may not know yet ---------------------------------

    /** Session with one refresh done and a second, slow one in flight; the VM is fenced at that moment. */
    private fun TestScope.fenced(): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        val messages = AppMessages()
        val posted = mutableListOf<String>()
        backgroundScope.launch { messages.messages.collect { posted += it } }
        runCurrent()
        factory.last.snapshotResult = { HubResult.Ok(snapshot()) }
        session.acquire(); runCurrent()
        factory.last.snapshotDelayMs = 500
        session.requestRefresh(); runCurrent()
        val handle = SavedStateHandle()
        val vm = NowPlayingViewModel("r9", handle, session, messages, startedAfterSeq = session.startedSeq, targetName = "Bedroom")
        runCurrent()
        return Setup(vm, session, factory, messages, posted, handle)
    }

    @Test
    fun aFencedViewModelIgnoresTheRefreshInFlightWhenItWasCreated() = runViewModelTest {
        val s = fenced()
        assertNull(s.ui.exit)
        advanceTimeBy(600); runCurrent()
        assertNull(s.ui.exit)
        assertEquals(emptyList(), s.posted)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshStartedAfterTheFenceThatHasTheRouteShowsIt() = runViewModelTest {
        val s = fenced()
        advanceTimeBy(600); runCurrent()
        s.repo.snapshotDelayMs = 0
        s.refresh(snapshot(extra = listOf(route("r9", "radio", Target.Room("bedroom"))))); runCurrent()
        assertNull(s.ui.exit)
        assertEquals("Bedroom", s.content().target.name)
        s.vm.onHidden()
    }

    @Test
    fun aRefreshStartedAfterTheFenceThatStillLacksTheRouteEndsItWithTheSeededName() = runViewModelTest {
        val s = fenced()
        advanceTimeBy(600); runCurrent()
        s.repo.snapshotDelayMs = 0
        s.refresh(snapshot()); runCurrent()
        assertEquals(Exit.Ended("Bedroom"), s.ui.exit)
        assertEquals(listOf("Playback on Bedroom ended"), s.posted)
        s.vm.onHidden()
    }
}
