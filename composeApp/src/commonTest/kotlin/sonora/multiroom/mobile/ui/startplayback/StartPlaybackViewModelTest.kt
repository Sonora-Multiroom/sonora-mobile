package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.ConsequenceLine
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
import sonora.multiroom.mobile.ui.session.PlaybackStarter
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

class StartPlaybackViewModelTest {
    private val address = HubAddress("http://hub:8080")
    private val bedroom = Target.Room("bedroom")

    private fun room(id: String, name: String, enabled: Boolean = true) =
        Room(id, name, 50, muted = false, enabled = enabled, available = true)

    private fun source(id: String, name: String, enabled: Boolean = true) =
        Source(id, name, "http://x/$id", SourceOrigin.Configured, false, enabled, SourceKind.Stream)

    private fun snapshot(
        sources: List<Source> = listOf(source("jazz", "Jazz24"), source("news", "News")),
        rooms: List<Room> = listOf(room("bedroom", "Bedroom"), room("kitchen", "Kitchen"), room("office", "Office")),
        groups: List<Group> = listOf(Group("down", "Downstairs", listOf("kitchen", "office"), muted = false, enabled = true)),
        routes: List<Route> = emptyList(),
    ) = HubSnapshot(rooms, groups, routes, sources, masterMuted = false)

    private class Setup(
        val vm: StartPlaybackViewModel,
        val session: HubSession,
        val factory: FakeFactory,
        val starter: PlaybackStarter,
        val posted: MutableList<String>,
        val handle: SavedStateHandle,
    ) {
        val repo get() = factory.last
        val ui get() = vm.state.value
    }

    private fun TestScope.live(
        initialTargetId: String? = null,
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
        val starter = PlaybackStarter(backgroundScope, session, messages)
        val vm = StartPlaybackViewModel(initialTargetId, handle, session, starter)
        if (visible) vm.onVisible()
        runCurrent()
        return Setup(vm, session, factory, starter, posted, handle)
    }

    private fun Setup.refresh(snapshot: HubSnapshot) {
        repo.snapshotResult = { HubResult.Ok(snapshot) }
        session.requestRefresh()
    }

    /** Types [text] one character at a time, as a keyboard does. */
    private fun StartPlaybackViewModel.type(text: String) {
        for (i in text.indices) onLinkChange(text.substring(0, i + 1))
    }

    private fun Setup.pickJazzInBedroom() {
        vm.onSelectSource("jazz")
        vm.onSelectTarget(bedroom)
    }

    // ---- opening --------------------------------------------------------------------------------

    @Test
    fun opensWithNothingSelectedAndPlayDisabled() = runViewModelTest {
        val s = live()
        assertNull(s.ui.selectedSourceId)
        assertNull(s.ui.selectedTarget)
        assertEquals(PlayLabel.Play, s.ui.playLabel)
        assertFalse(s.ui.playEnabled)
        assertEquals(listOf("jazz", "news"), s.ui.content?.sources?.map { it.id })
        s.vm.onHidden()
    }

    @Test
    fun anInitialRoomIsPreselectedWhenSelectable() = runViewModelTest {
        val s = live(initialTargetId = "bedroom")
        assertEquals(bedroom, s.ui.selectedTarget)
        s.vm.onHidden()
    }

    @Test
    fun anInitialRoomThatIsUnselectableOrUnknownIsDropped() = runViewModelTest {
        val off = live(initialTargetId = "bedroom", snapshot = snapshot(rooms = listOf(room("bedroom", "Bedroom", enabled = false))))
        assertNull(off.ui.selectedTarget)
        off.vm.onHidden()
        val unknown = live(initialTargetId = "ghost")
        assertNull(unknown.ui.selectedTarget)
        unknown.vm.onHidden()
    }

    @Test
    fun theSessionIsHeldOnlyWhileVisible() = runViewModelTest {
        val s = live(visible = false)
        runCurrent()
        assertEquals(0, s.repo.snapshotCalls)
        s.vm.onVisible(); runCurrent()
        assertEquals(1, s.repo.snapshotCalls)
        s.vm.onHidden()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, s.repo.snapshotCalls)
    }

    // ---- selection and the Play button ----------------------------------------------------------

    @Test
    fun aSourceAndATargetGiveThePlayLabelAndEnablePlayWhileLive() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        assertEquals(PlayLabel.PlaySource("Jazz24", "Bedroom"), s.ui.playLabel)
        assertTrue(s.ui.playEnabled)
        s.vm.onHidden()
    }

    @Test
    fun onlyOneSideSelectedKeepsThePlainLabel() = runViewModelTest {
        val s = live()
        s.vm.onSelectSource("jazz")
        assertEquals(PlayLabel.Play, s.ui.playLabel)
        assertFalse(s.ui.playEnabled)
        s.vm.onHidden()
    }

    @Test
    fun anUnselectableTargetIgnoresSelection() = runViewModelTest {
        val s = live(snapshot = snapshot(rooms = listOf(room("bedroom", "Bedroom", enabled = false), room("kitchen", "Kitchen"))))
        s.vm.onSelectTarget(bedroom)
        assertNull(s.ui.selectedTarget)
        s.vm.onSelectTarget(Target.Room("ghost"))
        assertNull(s.ui.selectedTarget)
        s.vm.onHidden()
    }

    @Test
    fun anUnknownOrTurnedOffSourceCannotBeSelected() = runViewModelTest {
        val s = live(snapshot = snapshot(sources = listOf(source("off", "Off", enabled = false))))
        s.vm.onSelectSource("off")
        s.vm.onSelectSource("ghost")
        assertNull(s.ui.selectedSourceId)
        s.vm.onHidden()
    }

    @Test
    fun staleKeepsListsAndSelectionsButDisablesPlayUntilTheNextSuccess() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        s.repo.snapshotResult = { unreachable }
        s.session.requestRefresh(); runCurrent()
        assertIs<Connection.Unreachable>(s.ui.connection)
        assertNotNull(s.ui.content)
        assertEquals("jazz", s.ui.selectedSourceId)
        assertEquals(bedroom, s.ui.selectedTarget)
        assertFalse(s.ui.playEnabled)
        s.refresh(snapshot()); runCurrent()
        assertTrue(s.ui.playEnabled)
        s.vm.onHidden()
    }

    @Test
    fun aSelectionThatVanishesIsDeselectedAtTheNextSnapshot() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        s.refresh(snapshot(sources = listOf(source("news", "News")))); runCurrent()
        assertNull(s.ui.selectedSourceId)
        assertEquals(bedroom, s.ui.selectedTarget)
        s.refresh(snapshot(rooms = listOf(room("bedroom", "Bedroom", enabled = false), room("kitchen", "Kitchen")))); runCurrent()
        assertNull(s.ui.selectedTarget)
        s.vm.onHidden()
    }

    @Test
    fun aSourceTurnedOffIsDeselected() = runViewModelTest {
        val s = live()
        s.vm.onSelectSource("jazz")
        s.refresh(snapshot(sources = listOf(source("jazz", "Jazz24", enabled = false)))); runCurrent()
        assertNull(s.ui.selectedSourceId)
        s.vm.onHidden()
    }

    // ---- Play -----------------------------------------------------------------------------------

    @Test
    fun playSendsOneStartLocksTheSelectionAndShowsStarting() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        s.repo.startDelayMs = 200
        s.vm.onPlay(); runCurrent()
        assertEquals(1, s.repo.calls.count { it.name == "startSource" })
        assertEquals(listOf<Any>("jazz", bedroom), s.repo.calls.single { it.name == "startSource" }.args)
        assertTrue(s.ui.starting)
        assertEquals(PlayLabel.Starting, s.ui.playLabel)
        assertFalse(s.ui.playEnabled)
        s.vm.onSelectSource("news")
        s.vm.onSelectTarget(Target.Room("kitchen"))
        assertEquals("jazz", s.ui.selectedSourceId)
        assertEquals(bedroom, s.ui.selectedTarget)
        s.vm.onPlay(); runCurrent()
        assertEquals(1, s.repo.calls.count { it.name == "startSource" })
        advanceTimeBy(300); runCurrent()
        s.vm.onHidden()
    }

    @Test
    fun successExitsWithTheNewRouteTheFenceAndTheTargetName() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        s.repo.startResults += HubResult.Ok(Route("r9", "jazz", bedroom, RouteStatus.Starting, false, false, true))
        val seq = s.session.startedSeq
        s.vm.onPlay(); runCurrent()
        assertEquals(StartExit.Started("r9", seq, "Bedroom"), s.ui.exit)
        assertFalse(s.ui.starting)
        s.vm.consumeExit()
        assertNull(s.ui.exit)
        s.vm.onHidden()
    }

    @Test
    fun aFailureKeepsTheSelectionsAndShowsTheCopy() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        s.repo.startResults += HubResult.Err(HubError.Rejected(409, null, "ROUTE_LIMIT_REACHED", "kitchen"))
        s.vm.onPlay(); runCurrent()
        assertFalse(s.ui.starting)
        assertEquals("Kitchen can't play more at once", s.ui.message)
        assertEquals("jazz", s.ui.selectedSourceId)
        assertEquals(bedroom, s.ui.selectedTarget)
        assertNull(s.ui.exit)
        s.vm.consumeMessage()
        assertNull(s.ui.message)
        s.vm.onHidden()
    }

    @Test
    fun playWithoutBothSelectionsOrWhileStaleSendsNothing() = runViewModelTest {
        val s = live()
        s.vm.onPlay(); runCurrent()
        s.vm.onSelectSource("jazz")
        s.vm.onPlay(); runCurrent()
        s.vm.onSelectTarget(bedroom)
        s.repo.snapshotResult = { unreachable }
        s.session.requestRefresh(); runCurrent()
        s.vm.onPlay(); runCurrent()
        assertEquals(0, s.repo.calls.count { it.name == "startSource" })
        s.vm.onHidden()
    }

    // ---- closing --------------------------------------------------------------------------------

    @Test
    fun closeExitsAndDetachesSoALateFailureReachesRooms() = runViewModelTest {
        val s = live()
        s.pickJazzInBedroom()
        s.repo.startDelayMs = 100
        s.repo.startResults += HubResult.Err(HubError.Rejected(422, null))
        s.vm.onPlay(); runCurrent()
        s.vm.onClose()
        assertEquals(StartExit.Closed, s.ui.exit)
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf("Couldn't start playback"), s.posted)
        s.vm.onHidden()
    }

    // ---- saved state ----------------------------------------------------------------------------

    @Test
    fun selectionsAreRestoredFromTheSavedState() = runViewModelTest {
        val handle = SavedStateHandle()
        val first = live(handle = handle)
        first.vm.onSelectSource("news")
        first.vm.onSelectTarget(Target.Group("down"))
        first.vm.onHidden()

        val restored = live(initialTargetId = "bedroom", handle = handle)
        assertEquals("news", restored.ui.selectedSourceId)
        assertEquals(Target.Group("down"), restored.ui.selectedTarget)
        restored.vm.onHidden()
    }

    // ---- consequence (US2) -----------------------------------------------------------------------

    private val busyBedroom = snapshot(routes = listOf(Route("r1", "news", bedroom, RouteStatus.Active, false, false, true)))

    @Test
    fun theConsequenceIsNullUntilASourceAndATargetAreSelected() = runViewModelTest {
        val s = live(snapshot = busyBedroom)
        assertNull(s.ui.consequence)
        s.vm.onSelectSource("jazz")
        assertNull(s.ui.consequence)
        s.vm.onSelectTarget(bedroom)
        assertEquals("News", (s.ui.consequence?.line as ConsequenceLine.WillStop).items.single().source)
        s.vm.onHidden()
    }

    @Test
    fun theConsequenceFollowsTheNextSnapshot() = runViewModelTest {
        val s = live(snapshot = busyBedroom)
        s.pickJazzInBedroom()
        assertIs<ConsequenceLine.WillStop>(s.ui.consequence?.line)
        s.refresh(snapshot()); runCurrent()
        assertNull(s.ui.consequence?.line)
        s.vm.onHidden()
    }

    @Test
    fun theConsequenceStaysVisibleWhilePlayIsDisabledByAStaleConnection() = runViewModelTest {
        val s = live(snapshot = busyBedroom)
        s.pickJazzInBedroom()
        s.repo.snapshotResult = { unreachable }
        s.session.requestRefresh(); runCurrent()
        assertFalse(s.ui.playEnabled)
        assertIs<ConsequenceLine.WillStop>(s.ui.consequence?.line)
        s.vm.onHidden()
    }

    // ---- links (US3) ---------------------------------------------------------------------------------

    @Test
    fun typingALinkDeselectsTheSourceAndPickingASourceClearsTheLink() = runViewModelTest {
        val s = live()
        s.vm.onSelectSource("jazz")
        s.vm.onLinkChange("soundcloud.com/a")
        assertNull(s.ui.selectedSourceId)
        assertEquals("soundcloud.com/a", s.ui.linkText)
        s.vm.onSelectSource("news")
        assertEquals("", s.ui.linkText)
        assertEquals("news", s.ui.selectedSourceId)
        s.vm.onHidden()
    }

    @Test
    fun anEmptiedLinkKeepsTheSourceSelectionClear() = runViewModelTest {
        val s = live()
        s.vm.onSelectSource("jazz")
        s.vm.onLinkChange("x")
        s.vm.onLinkChange("")
        assertNull(s.ui.selectedSourceId)
        s.vm.onHidden()
    }

    @Test
    fun invalidTextWhileTypingShowsNoMessageAndKeepsPlayDisabled() = runViewModelTest {
        val s = live()
        s.vm.onSelectTarget(bedroom)
        for (text in listOf("h", "h.", "h..")) s.vm.onLinkChange(text)
        assertFalse(s.ui.linkMessageShown)
        assertFalse(s.ui.playEnabled)
        s.vm.onHidden()
    }

    @Test
    fun pasteFocusLossAndDoneShowTheMessageForInvalidText() = runViewModelTest {
        val s = live()
        s.vm.type("a..")
        assertFalse(s.ui.linkMessageShown)
        s.vm.onLinkFocusLost()
        assertTrue(s.ui.linkMessageShown)

        val d = live()
        d.vm.type("a..")
        d.vm.onLinkDone()
        assertTrue(d.ui.linkMessageShown)

        val p = live()
        p.vm.onLinkPasted("not a link at all")
        assertTrue(p.ui.linkMessageShown)
        assertEquals("not a link at all", p.ui.linkText)
        s.vm.onHidden(); d.vm.onHidden(); p.vm.onHidden()
    }

    @Test
    fun aValueChangeInsertingMoreThanOneCharacterIsAPaste() = runViewModelTest {
        val s = live()
        s.vm.onLinkChange("not a link at all")
        assertTrue(s.ui.linkMessageShown)
        s.vm.onHidden()
    }

    @Test
    fun theMessageClearsAsSoonAsTheTextIsValidOrEmpty() = runViewModelTest {
        val s = live()
        s.vm.onLinkPasted("a b")
        assertTrue(s.ui.linkMessageShown)
        s.vm.onLinkChange("ab")
        assertFalse(s.ui.linkMessageShown)
        s.vm.onLinkPasted("a b")
        s.vm.onLinkChange("")
        assertFalse(s.ui.linkMessageShown)
        s.vm.onHidden()
    }

    @Test
    fun aValidLinkAndATargetGiveThePlayLinkLabel() = runViewModelTest {
        val s = live()
        s.vm.onLinkChange("soundcloud.com/a/b")
        s.vm.onSelectTarget(bedroom)
        assertEquals(PlayLabel.PlayLink("Bedroom"), s.ui.playLabel)
        assertTrue(s.ui.playEnabled)
        s.vm.onHidden()
    }

    @Test
    fun playSendsTheNormalisedLinkAndStaysStartingUntilTheAnswer() = runViewModelTest {
        val s = live()
        s.vm.onLinkChange("  soundcloud.com/a/b ")
        s.vm.onSelectTarget(bedroom)
        s.repo.startDelayMs = 20_000
        s.repo.startResults += HubResult.Ok(Route("r9", "link-1", bedroom, RouteStatus.Starting, false, false, true))
        s.vm.onPlay(); runCurrent()
        assertEquals(listOf<Any>("https://soundcloud.com/a/b", bedroom), s.repo.calls.single { it.name == "playLink" }.args)
        advanceTimeBy(5_000); runCurrent()
        assertTrue(s.ui.starting)
        assertEquals(PlayLabel.Starting, s.ui.playLabel)
        advanceTimeBy(15_001); runCurrent()
        assertIs<StartExit.Started>(s.ui.exit)
        s.vm.onHidden()
    }

    @Test
    fun aLinkFailureKeepsTheLinkAndTheTargetAndShowsTheCopy() = runViewModelTest {
        val s = live()
        s.vm.onLinkChange("soundcloud.com/a/b")
        s.vm.onSelectTarget(bedroom)
        s.repo.startResults += HubResult.Err(HubError.Rejected(502, null))
        s.vm.onPlay(); runCurrent()
        assertEquals("Couldn't reach that link", s.ui.message)
        assertEquals("soundcloud.com/a/b", s.ui.linkText)
        assertEquals(bedroom, s.ui.selectedTarget)
        assertFalse(s.ui.starting)
        s.vm.onHidden()
    }

    @Test
    fun theLinkConsequenceAlwaysReplaces() = runViewModelTest {
        val s = live(snapshot = busyBedroom)
        s.vm.onLinkChange("soundcloud.com/a/b")
        s.vm.onSelectTarget(bedroom)
        assertIs<ConsequenceLine.WillStop>(s.ui.consequence?.line)
        s.vm.onHidden()
    }

    @Test
    fun theLinkTextIsRestoredFromTheSavedState() = runViewModelTest {
        val handle = SavedStateHandle()
        val first = live(handle = handle)
        first.vm.onLinkChange("soundcloud.com/a/b")
        first.vm.onHidden()
        val restored = live(handle = handle)
        assertEquals("soundcloud.com/a/b", restored.ui.linkText)
        restored.vm.onHidden()
    }
}
