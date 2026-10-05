package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.Confirmation
import sonora.multiroom.mobile.domain.Extension
import sonora.multiroom.mobile.domain.ExtensionConnection
import sonora.multiroom.mobile.domain.ExtensionInventory
import sonora.multiroom.mobile.domain.ExtensionStatus
import sonora.multiroom.mobile.domain.ExtensionsContent
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.ItemKey
import sonora.multiroom.mobile.domain.ItemKind
import sonora.multiroom.mobile.domain.RoomStatus
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.domain.group
import sonora.multiroom.mobile.domain.room
import sonora.multiroom.mobile.domain.route
import sonora.multiroom.mobile.domain.snapshot
import sonora.multiroom.mobile.domain.source
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.SettingsActions
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import sonora.multiroom.mobile.runViewModelTest
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.unreachable
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.SettingsNavigator
import sonora.multiroom.mobile.ui.session.SettingsTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    private val hub = HubAddress("http://multiroom.lan:8080")

    private class Setup(
        val vm: SettingsViewModel,
        val session: HubSession,
        val factory: FakeFactory,
        val store: InMemoryHubAddressStore,
        val navigator: SettingsNavigator,
        val actions: SettingsActions,
    ) {
        val repo get() = factory.last
        val state get() = vm.state.value
    }

    private fun TestScope.setup(
        address: HubAddress? = hub,
        navigator: SettingsNavigator = SettingsNavigator(),
    ): Setup {
        val factory = FakeFactory { currentTime }
        val store = InMemoryHubAddressStore(address)
        val session = HubSession(store, factory, backgroundScope, now = { currentTime })
        val actions = SettingsActions(backgroundScope, session, AppMessages())
        val vm = SettingsViewModel(
            session, store, navigator, actions, factory,
            now = { Instant.parse("2026-10-04T12:00:00Z") },
            zone = { TimeZone.UTC },
        )
        runCurrent()
        return Setup(vm, session, factory, store, navigator, actions)
    }

    // ---- Visibility, hub row and tab --------------------------------------------------------

    @Test
    fun visibilityAcquiresAndReleasesTheSession() = runViewModelTest {
        val s = setup()
        assertEquals(0, s.repo.snapshotCalls)
        s.vm.onVisible()
        runCurrent()
        assertEquals(1, s.repo.snapshotCalls)
        s.vm.onHidden()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, s.repo.snapshotCalls)
    }

    @Test
    fun theHubRowFollowsTheSession() = runViewModelTest {
        val s = setup()
        assertEquals(HubRow(HubStatus.Connecting, hub.baseUrl), s.state.hub)
        s.vm.onVisible(); runCurrent()
        assertEquals(HubRow(HubStatus.Connected, hub.baseUrl), s.state.hub)
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertEquals(HubRow(HubStatus.NotConnected, hub.baseUrl), s.state.hub)
        s.vm.onHidden()
    }

    @Test
    fun theTabFollowsTheNavigatorAndIsRemembered() = runViewModelTest {
        val navigator = SettingsNavigator()
        val s = setup(navigator = navigator)
        assertEquals(SettingsTab.Rooms, s.state.tab)
        s.vm.onTabSelected(SettingsTab.Groups)
        runCurrent()
        assertEquals(SettingsTab.Groups, navigator.tab.value)
        assertEquals(SettingsTab.Groups, s.state.tab)
        // A new view model (the screen was left and reopened) starts on the same tab (FR-002).
        val again = setup(navigator = navigator)
        assertEquals(SettingsTab.Groups, again.state.tab)
    }

    // ---- Body -------------------------------------------------------------------------------

    @Test
    fun bodyFollowsTheSessionStates() = runViewModelTest {
        val none = setup(address = null)
        assertEquals(SettingsBody.NoAddress, none.state.body)
        assertEquals(HubRow(HubStatus.NotSet, null), none.state.hub)

        val s = setup()
        assertEquals(SettingsBody.Waiting, s.state.body)
        s.vm.onVisible(); runCurrent()
        assertIs<SettingsBody.Lists>(s.state.body).let {
            assertFalse(it.stale)
            assertTrue(it.controlsEnabled)
        }

        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertIs<SettingsBody.Lists>(s.state.body).let {
            assertTrue(it.stale)
            assertFalse(it.controlsEnabled)
        }
        s.vm.onHidden()
    }

    @Test
    fun unreachableWithoutASnapshotIsItsOwnBody() = runViewModelTest {
        val s = setup()
        s.repo.snapshotResult = { unreachable }
        s.vm.onVisible(); runCurrent()
        assertEquals(SettingsBody.CantReach(hub.baseUrl), s.state.body)
        s.vm.onHidden()
    }

    // ---- Address sheet (001 rules, unchanged) -----------------------------------------------

    @Test
    fun theHubRowOpensTheSheetWithTheSavedAddress() = runViewModelTest {
        val s = setup()
        assertNull(s.state.sheet)
        s.vm.onHubRowTapped(); runCurrent()
        assertEquals(SheetState(draft = hub.baseUrl), s.state.sheet)
    }

    @Test
    fun withNoAddressTheDraftIsEmpty() = runViewModelTest {
        val s = setup(address = null)
        s.vm.onHubRowTapped(); runCurrent()
        assertEquals(SheetState(draft = ""), s.state.sheet)
    }

    @Test
    fun editingClearsTheError() = runViewModelTest {
        val s = setup()
        s.vm.onHubRowTapped()
        s.vm.onDraftChange("a b")
        s.vm.onSave(); advanceUntilIdle()
        assertNotNull(s.state.sheet?.error)
        s.vm.onDraftChange("a"); runCurrent()
        assertNull(s.state.sheet?.error)
        assertEquals("a", s.state.sheet?.draft)
    }

    @Test
    fun savingInvalidInputSavesNothingAndShowsTheMessage() = runViewModelTest {
        val s = setup(address = null)
        s.vm.onHubRowTapped()
        s.vm.onSave(); advanceUntilIdle()
        assertEquals("Enter the hub's address, e.g. multiroom.lan", s.state.sheet?.error)
        s.vm.onDraftChange("a b")
        s.vm.onSave(); advanceUntilIdle()
        assertEquals("The address can't contain spaces", s.state.sheet?.error)
        assertNull(s.store.address.first())
        assertNotNull(s.state.sheet)
    }

    @Test
    fun savingValidInputStoresTheNormalisedAddressAndClosesTheSheet() = runViewModelTest {
        val s = setup(address = null)
        s.vm.onHubRowTapped()
        s.vm.onDraftChange("multiroom.lan")
        s.vm.onSave(); advanceUntilIdle()
        assertEquals(HubAddress("http://multiroom.lan:8080"), s.store.address.first())
        assertNull(s.state.sheet)
    }

    @Test
    fun closingDiscardsTheDraft() = runViewModelTest {
        val s = setup()
        s.vm.onHubRowTapped()
        s.vm.onDraftChange("other.lan")
        s.vm.onSheetClosed(); runCurrent()
        assertNull(s.state.sheet)
        s.vm.onHubRowTapped(); runCurrent()
        assertEquals(hub.baseUrl, s.state.sheet?.draft)
    }

    @Test
    fun aSheetRequestOpensTheSheetOnceAtOnVisible() = runViewModelTest {
        val navigator = SettingsNavigator()
        navigator.openSheet()
        val s = setup(address = null, navigator = navigator)
        s.vm.onVisible(); runCurrent()
        assertNotNull(s.state.sheet)
        assertFalse(navigator.openSheetRequested.value)
        s.vm.onHidden()
    }

    // ---- Switches (US1) ----------------------------------------------------------------------

    private val radio = source("radio", "Radio Paradise")
    private val jazz = source("jazz", "Jazz FM")

    /** Living Room plays Radio Paradise through group "Downstairs"; Kitchen is a member too; Office is idle. */
    private fun hubSnapshot(
        routes: List<sonora.multiroom.mobile.domain.Route> = listOf(route("r", "radio", Target.Group("g"))),
        sources: List<sonora.multiroom.mobile.domain.Source> = listOf(radio, jazz),
    ) = snapshot(
        rooms = listOf(room("living", "Living Room"), room("kitchen", "Kitchen"), room("office", "Office")),
        groups = listOf(group("g", "Downstairs", listOf("living", "kitchen"))),
        routes = routes,
        sources = sources,
    )

    private fun TestScope.live(snapshot: HubSnapshot = hubSnapshot()): Setup {
        val s = setup()
        s.repo.snapshotResult = { HubResult.Ok(snapshot) }
        s.vm.onVisible(); runCurrent()
        return s
    }

    private fun Setup.lists() = assertIs<SettingsBody.Lists>(state.body).lists

    private val living = ItemKey(ItemKind.Room, "living")
    private val office = ItemKey(ItemKind.Room, "office")
    private val downstairs = ItemKey(ItemKind.Group, "g")

    @Test
    fun rowsCarryTheDomainRowsAndTheirSwitchState() = runViewModelTest {
        val s = live()
        val rooms = s.lists().rooms
        assertEquals(listOf("Kitchen", "Living Room", "Office"), rooms.map { it.row.name })
        assertEquals(RoomStatus.Playing(listOf("Radio Paradise")), rooms.first { it.row.id == "living" }.row.status)
        assertTrue(rooms.all { it.shownEnabled && !it.inFlight })
        s.vm.onVisible()
        s.vm.onHidden()
    }

    @Test
    fun anIdleRoomIsTurnedOffAtOnceAndShowsTheRequestedValue() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 100
        s.vm.onToggle(office, "Office", false); runCurrent()
        val row = s.lists().rooms.first { it.row.id == "office" }
        assertFalse(row.shownEnabled)
        assertTrue(row.inFlight)
        assertNull(s.state.confirm)
        assertEquals(listOf(listOf<Any>("office", false)), s.repo.calls.filter { it.name == "setRoomEnabled" }.map { it.args })
    }

    @Test
    fun turningOnNeverAsks() = runViewModelTest {
        val s = live(hubSnapshot().copy(rooms = listOf(room("living", "Living Room", enabled = false))))
        s.vm.onToggle(living, "Living Room", true); runCurrent()
        assertNull(s.state.confirm)
        assertEquals(1, s.repo.calls.count { it.name == "setRoomEnabled" })
    }

    @Test
    fun turningOffAPlayingRoomAsksFirstAndKeepsTheSwitchOn() = runViewModelTest {
        val s = live()
        s.vm.onToggle(living, "Living Room", false); runCurrent()
        assertEquals(Confirmation.TurnOffRoom("Living Room", listOf("Radio Paradise")), s.state.confirm?.confirmation)
        assertTrue(s.repo.calls.none { it.name == "setRoomEnabled" })
        assertTrue(s.lists().rooms.first { it.row.id == "living" }.shownEnabled)
    }

    @Test
    fun cancelClosesTheDialogAndSendsNothing() = runViewModelTest {
        val s = live()
        s.vm.onToggle(living, "Living Room", false); runCurrent()
        s.vm.onConfirmCancel(); runCurrent()
        assertNull(s.state.confirm)
        assertTrue(s.repo.calls.none { it.name == "setRoomEnabled" })
    }

    @Test
    fun confirmingClosesTheDialogAndSends() = runViewModelTest {
        val s = live()
        s.vm.onToggle(living, "Living Room", false); runCurrent()
        s.vm.onConfirm(); runCurrent()
        assertNull(s.state.confirm)
        assertEquals(listOf(listOf<Any>("living", false)), s.repo.calls.filter { it.name == "setRoomEnabled" }.map { it.args })
    }

    @Test
    fun aGroupAsksAsAGroup() = runViewModelTest {
        val s = live()
        s.vm.onToggle(downstairs, "Downstairs", false); runCurrent()
        assertEquals(
            Confirmation.TurnOffGroup("Downstairs", listOf("Radio Paradise"), listOf("Living Room", "Kitchen")),
            s.state.confirm?.confirmation,
        )
        s.vm.onConfirm(); runCurrent()
        assertEquals(1, s.repo.calls.count { it.name == "setGroupEnabled" })
    }

    @Test
    fun aSourceTurnedOffWhileInUseDoesNotAskAndSaysItKeepsPlaying() = runViewModelTest {
        val s = live()
        s.vm.onToggle(ItemKey(ItemKind.Source, "radio"), "Radio Paradise", false); runCurrent()
        assertNull(s.state.confirm)
        assertEquals(1, s.repo.calls.count { it.name == "setSourceEnabled" })
        assertEquals("Radio Paradise is off. What's playing from it keeps playing.", s.state.message)
        s.vm.consumeMessage(); runCurrent()
        assertNull(s.state.message)
    }

    @Test
    fun theDialogTextFollowsTheHubAndKeepsTheLastTextWhenThereIsNothingToShow() = runViewModelTest {
        val s = live()
        s.vm.onToggle(living, "Living Room", false); runCurrent()

        s.repo.snapshotResult = { HubResult.Ok(hubSnapshot(routes = listOf(route("r2", "jazz", Target.Room("living"))))) }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Confirmation.TurnOffRoom("Living Room", listOf("Jazz FM")), s.state.confirm?.confirmation)

        s.repo.snapshotResult = { HubResult.Ok(hubSnapshot(routes = emptyList())) }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Confirmation.TurnOffRoom("Living Room", listOf("Jazz FM")), s.state.confirm?.confirmation)

        // Item gone: the dialog stays, and "Turn off" still sends; the hub's 404 then says why.
        s.repo.snapshotResult = { HubResult.Ok(hubSnapshot().copy(rooms = listOf(room("office", "Office")))) }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Confirmation.TurnOffRoom("Living Room", listOf("Jazz FM")), s.state.confirm?.confirmation)
        s.repo.actionResult = { HubResult.Err(sonora.multiroom.mobile.data.HubError.Rejected(404, null)) }
        s.vm.onConfirm(); runCurrent()
        assertEquals("Living Room is no longer on the hub", s.state.message)
    }

    @Test
    fun tapsAreIgnoredWhileStaleOrInFlight() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 1000
        s.vm.onToggle(office, "Office", false); runCurrent()
        s.vm.onToggle(office, "Office", true); runCurrent()
        assertEquals(1, s.repo.calls.count { it.name == "setRoomEnabled" })

        advanceTimeBy(3000); runCurrent()
        s.repo.calls.clear()
        s.repo.snapshotResult = { sonora.multiroom.mobile.ui.rooms.unreachable }
        advanceTimeBy(2500); runCurrent()
        s.vm.onToggle(ItemKey(ItemKind.Room, "kitchen"), "Kitchen", false); runCurrent()
        assertTrue(s.repo.calls.isEmpty())
        assertNull(s.state.confirm)
    }

    @Test
    fun messagesShowOnlyWhileVisible() = runViewModelTest {
        val s = live()
        s.vm.onHidden(); runCurrent()
        s.vm.onVisible(); runCurrent()
        s.repo.actionResult = { sonora.multiroom.mobile.ui.rooms.unreachable }
        s.vm.onToggle(office, "Office", false); runCurrent()
        assertEquals("Couldn't reach the hub", s.state.message)
    }

    // ---- Removal (US2) -----------------------------------------------------------------------

    private val link = source("link", "A link", sonora.multiroom.mobile.domain.SourceOrigin.Runtime, "https://soundcloud.com/x")

    private fun withLink(routes: List<sonora.multiroom.mobile.domain.Route> = emptyList()) =
        hubSnapshot(routes = routes, sources = listOf(radio, link))

    @Test
    fun runtimeRowsExcludeRemovedIdsAndMarkTheOnesBeingRemoved() = runViewModelTest {
        val s = live(withLink())
        assertEquals(listOf("link"), s.lists().runtimeSources.map { it.row.id })
        s.repo.actionDelayMs = 100
        s.vm.onRemove("link", "A link"); runCurrent()
        assertTrue(s.lists().runtimeSources.single().removing)
        advanceTimeBy(101); runCurrent()
        assertTrue(s.lists().runtimeSources.isEmpty())
    }

    @Test
    fun anUnusedSourceIsRemovedAtOnce() = runViewModelTest {
        val s = live(withLink())
        s.vm.onRemove("link", "A link"); runCurrent()
        assertNull(s.state.confirm)
        assertEquals(listOf(listOf<Any>("link")), s.repo.calls.filter { it.name == "removeSource" }.map { it.args })
    }

    @Test
    fun aSourceInUseAsksFirst() = runViewModelTest {
        val s = live(withLink(listOf(route("r", "link", Target.Room("office")))))
        s.vm.onRemove("link", "A link"); runCurrent()
        assertEquals(Confirmation.Remove("A link", listOf("Office")), s.state.confirm?.confirmation)
        assertTrue(s.repo.calls.none { it.name == "removeSource" })

        s.vm.onConfirmCancel(); runCurrent()
        assertNull(s.state.confirm)
        assertTrue(s.repo.calls.none { it.name == "removeSource" })

        s.vm.onRemove("link", "A link"); runCurrent()
        s.vm.onConfirm(); runCurrent()
        assertNull(s.state.confirm)
        assertEquals(1, s.repo.calls.count { it.name == "removeSource" })
    }

    @Test
    fun theCheckUsesTheSnapshotAtTheTimeOfTheTap() = runViewModelTest {
        val s = live(withLink(listOf(route("r", "link", Target.Room("office")))))
        s.repo.snapshotResult = { HubResult.Ok(withLink()) }
        advanceTimeBy(2500); runCurrent()
        s.vm.onRemove("link", "A link"); runCurrent()
        assertNull(s.state.confirm)
        assertEquals(1, s.repo.calls.count { it.name == "removeSource" })
    }

    @Test
    fun trashTapsAreIgnoredWhileStale() = runViewModelTest {
        val s = live(withLink())
        s.repo.snapshotResult = { sonora.multiroom.mobile.ui.rooms.unreachable }
        advanceTimeBy(2500); runCurrent()
        s.vm.onRemove("link", "A link"); runCurrent()
        assertTrue(s.repo.calls.none { it.name == "removeSource" })
    }

    // ---- Connection test (US3) ---------------------------------------------------------------

    private val other = HubAddress("http://other.lan:8080")

    private fun Setup.openSheet(draft: String) {
        vm.onHubRowTapped()
        vm.onDraftChange(draft)
    }

    @Test
    fun aTestOfAnInvalidDraftShowsTheMessageAndCreatesNoRepository() = runViewModelTest {
        val s = setup()
        s.openSheet("a b")
        val created = s.factory.created.size
        s.vm.onTest(); runCurrent()
        assertEquals("The address can't contain spaces", s.state.sheet?.error)
        assertEquals(TestState.Idle, s.state.sheet?.test)
        assertEquals(created, s.factory.created.size)
    }

    @Test
    fun aTestAsksTheNormalisedDraftsHubNotTheSessions() = runViewModelTest {
        val s = setup()
        s.openSheet("other.lan")
        s.repo.countDelayMs = 0
        s.factory.onCreate = { it.countDelayMs = 1000 }
        s.vm.onTest(); runCurrent()
        assertEquals(TestState.Checking, s.state.sheet?.test)
        assertEquals(other, s.factory.created.last())
        advanceTimeBy(1001); runCurrent()
        assertEquals(TestState.Found(5), s.state.sheet?.test)
        assertEquals(1, s.factory.repositories.getValue(other).calls.count { it.name == "countRooms" })
        assertEquals(0, s.factory.repositories.getValue(hub).calls.count { it.name == "countRooms" })
        // Nothing is saved.
        assertEquals(hub, s.store.address.first())
    }

    @Test
    fun anyErrorIsFailed() = runViewModelTest {
        for (result in listOf(
            HubResult.Err(sonora.multiroom.mobile.data.HubError.Unreachable),
            HubResult.Err(sonora.multiroom.mobile.data.HubError.Rejected(503, null)),
            HubResult.Err(sonora.multiroom.mobile.data.HubError.Unexpected),
        )) {
            val s = setup()
            s.factory.onCreate = { it.countRoomsResult = { result }; it.countDelayMs = 100 }
            s.openSheet("other.lan")
            s.vm.onTest(); advanceTimeBy(101); runCurrent()
            assertEquals(TestState.Failed, s.state.sheet?.test)
        }
    }

    @Test
    fun editingSavingOrClosingCancelsTheTest() = runViewModelTest {
        val s = setup()
        s.factory.onCreate = { it.countDelayMs = 1000 }

        s.openSheet("other.lan")
        s.vm.onTest(); runCurrent()
        s.vm.onDraftChange("other.lan2"); runCurrent()
        assertEquals(TestState.Idle, s.state.sheet?.test)
        advanceTimeBy(2000); runCurrent()
        assertEquals(TestState.Idle, s.state.sheet?.test)

        s.vm.onTest(); runCurrent()
        s.vm.onSheetClosed(); runCurrent()
        advanceTimeBy(2000); runCurrent()
        s.vm.onHubRowTapped(); runCurrent()
        assertEquals(TestState.Idle, s.state.sheet?.test)

        s.vm.onDraftChange("other.lan"); s.vm.onTest(); runCurrent()
        s.vm.onSave(); advanceTimeBy(2000); runCurrent()
        assertNull(s.state.sheet)
    }

    @Test
    fun aSecondTestReplacesTheFirst() = runViewModelTest {
        val s = setup()
        var n = 0
        s.factory.onCreate = { repo -> val mine = ++n; repo.countDelayMs = if (mine == 1) 2000L else 100L; repo.countRoomsResult = { HubResult.Ok(mine) } }
        s.openSheet("other.lan")
        s.vm.onTest(); runCurrent()
        s.vm.onTest(); advanceTimeBy(101); runCurrent()
        assertEquals(TestState.Found(2), s.state.sheet?.test)
        advanceTimeBy(3000); runCurrent()
        assertEquals(TestState.Found(2), s.state.sheet?.test)
    }

    // ---- Extensions (US4, research R8) ---------------------------------------------------------

    private val inventory = ExtensionInventory(true, listOf(Extension("tts", "Text to speech", ExtensionStatus.Active, ExtensionConnection.NotApplicable)))

    private fun Setup.onExtensionsTab() = navigator.select(SettingsTab.Extensions)

    @Test
    fun extensionsAreFetchedOnceWhenTheTabIsShownAndAfterEachRefresh() = runViewModelTest {
        val s = setup()
        s.repo.extensionsResult = { HubResult.Ok(inventory) }
        s.onExtensionsTab()
        s.vm.onVisible(); runCurrent()
        assertEquals(1, s.repo.extensionsCalls)
        assertIs<ExtensionsContent.Rows>(s.state.extensions)
        advanceTimeBy(2500); runCurrent()
        assertEquals(2, s.repo.extensionsCalls)
        advanceTimeBy(2500); runCurrent()
        assertEquals(3, s.repo.extensionsCalls)
        s.vm.onHidden()
    }

    @Test
    fun otherTabsAndAHiddenScreenNeverFetch() = runViewModelTest {
        val s = setup()
        s.vm.onVisible(); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertEquals(0, s.repo.extensionsCalls)
        s.vm.onHidden()
        s.onExtensionsTab(); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertEquals(0, s.repo.extensionsCalls)
    }

    @Test
    fun beforeTheFirstAnswerThereIsNothing() = runViewModelTest {
        val s = setup()
        s.repo.extensionsDelayMs = 1000
        s.repo.extensionsResult = { HubResult.Ok(inventory) }
        s.onExtensionsTab()
        s.vm.onVisible(); runCurrent()
        assertNull(s.state.extensions)
        advanceTimeBy(1001); runCurrent()
        assertIs<ExtensionsContent.Rows>(s.state.extensions)
        s.vm.onHidden()
    }

    @Test
    fun aFailureKeepsTheLastInventoryWithoutAMessage() = runViewModelTest {
        val s = setup()
        s.repo.extensionsResult = { HubResult.Ok(inventory) }
        s.onExtensionsTab()
        s.vm.onVisible(); runCurrent()
        s.repo.extensionsResult = { sonora.multiroom.mobile.ui.rooms.unreachable }
        advanceTimeBy(2500); runCurrent()
        assertEquals(2, s.repo.extensionsCalls)
        assertIs<ExtensionsContent.Rows>(s.state.extensions)
        assertNull(s.state.message)
        s.vm.onHidden()
    }

    @Test
    fun switchingAwayAndBackFetchesAgainAtOnce() = runViewModelTest {
        val s = setup()
        s.onExtensionsTab()
        s.vm.onVisible(); runCurrent()
        assertEquals(1, s.repo.extensionsCalls)
        s.vm.onTabSelected(SettingsTab.Rooms); runCurrent()
        s.vm.onTabSelected(SettingsTab.Extensions); runCurrent()
        assertEquals(2, s.repo.extensionsCalls)
        s.vm.onHidden()
    }

    @Test
    fun anAddressChangeForgetsTheListAndDropsALateAnswerFromTheOldHub() = runViewModelTest {
        val s = setup()
        s.repo.extensionsResult = { HubResult.Ok(inventory) }
        s.onExtensionsTab()
        s.vm.onVisible(); runCurrent()
        assertIs<ExtensionsContent.Rows>(s.state.extensions)

        // The old hub answers slowly; the address changes meanwhile.
        s.repo.extensionsDelayMs = 1000
        advanceTimeBy(2500); runCurrent()
        s.store.save(HubAddress("http://other.lan:8080")); runCurrent()
        // The old list is gone (the new hub may already have answered with its own).
        assertTrue(s.state.extensions !is ExtensionsContent.Rows)
        advanceTimeBy(1500); runCurrent()
        // Only the new hub's own answer (an empty inventory by default) may show.
        assertTrue(s.state.extensions == null || s.state.extensions == ExtensionsContent.Empty)
        s.vm.onHidden()
    }
}
