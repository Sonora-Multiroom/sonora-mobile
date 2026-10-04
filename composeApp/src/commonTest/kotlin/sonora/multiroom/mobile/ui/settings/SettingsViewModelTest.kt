package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
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
        val vm = SettingsViewModel(session, store, navigator)
        runCurrent()
        return Setup(vm, session, factory, store, navigator)
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
        assertEquals(SettingsBody.Lists(stale = false, controlsEnabled = true), s.state.body)

        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertEquals(SettingsBody.Lists(stale = true, controlsEnabled = false), s.state.body)
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
}
