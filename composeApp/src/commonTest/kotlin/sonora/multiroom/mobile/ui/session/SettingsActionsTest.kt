package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.ItemKey
import sonora.multiroom.mobile.domain.ItemKind
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.unreachable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsActionsTest {
    private val address = HubAddress("http://hub:8080")
    private val room = ItemKey(ItemKind.Room, "a")

    private class Setup(
        val actions: SettingsActions,
        val session: HubSession,
        val factory: FakeFactory,
        val store: InMemoryHubAddressStore,
        val app: AppMessages,
        val attached: MutableList<String>,
        val posted: MutableList<String>,
    ) {
        val repo get() = factory.last
        val pending get() = actions.pending.value
        fun calls(name: String) = repo.calls.filter { it.name == name }
    }

    private fun TestScope.setup(attach: Boolean = true): Setup {
        val factory = FakeFactory { currentTime }
        val store = InMemoryHubAddressStore(address)
        val session = HubSession(store, factory, backgroundScope, now = { currentTime })
        val app = AppMessages()
        val actions = SettingsActions(backgroundScope, session, app)
        val attached = mutableListOf<String>()
        val posted = mutableListOf<String>()
        backgroundScope.launch { actions.messages.collect { attached += it } }
        backgroundScope.launch { app.messages.collect { posted += it } }
        runCurrent()
        if (attach) actions.attach()
        return Setup(actions, session, factory, store, app, attached, posted)
    }

    // ---- Switch part ------------------------------------------------------------------------

    @Test
    fun aTapShowsTheValueAtOnceAndSendsOneRequest() = runTest {
        val s = setup()
        s.repo.actionDelayMs = 100
        s.actions.setEnabled(room, "Kitchen", false, keepsPlaying = false)
        assertEquals(Pending(false, Phase.InFlight), s.pending[room])
        runCurrent()
        assertEquals(listOf(listOf<Any>("a", false)), s.calls("setRoomEnabled").map { it.args })
    }

    @Test
    fun theKindSelectsTheRequest() = runTest {
        val s = setup()
        s.actions.setEnabled(ItemKey(ItemKind.Group, "g"), "G", true, false)
        s.actions.setEnabled(ItemKey(ItemKind.Source, "i"), "I", true, false)
        runCurrent()
        assertEquals(listOf("setGroupEnabled", "setSourceEnabled"), s.repo.calls.map { it.name })
    }

    @Test
    fun aSecondTapWhilePendingIsIgnored() = runTest {
        val s = setup()
        s.repo.actionDelayMs = 100
        s.actions.setEnabled(room, "Kitchen", false, false)
        s.actions.setEnabled(room, "Kitchen", true, false)
        runCurrent(); advanceTimeBy(200)
        assertEquals(1, s.calls("setRoomEnabled").size)
        assertEquals(false, s.pending[room]?.value)
    }

    @Test
    fun successWaitsForARefreshThatStartedAfterIt() = runTest {
        val s = setup()
        s.repo.snapshotDelayMs = 1000
        s.session.acquire()
        runCurrent()                       // refresh 1 starts at t=0 and takes 1 s
        s.repo.actionDelayMs = 200
        advanceTimeBy(100)
        s.actions.setEnabled(room, "Kitchen", false, false)
        advanceTimeBy(201)                 // t=301: the request is done, fence = 1
        assertEquals(Pending(false, Phase.AwaitingRefresh(fence = 1)), s.pending[room])

        advanceTimeBy(1000)                // t=1300: refresh 1 finished; it started before completion
        assertEquals(Phase.AwaitingRefresh(1), s.pending[room]?.phase)

        advanceTimeBy(1000)                // t=2300: refresh 2 started after completion
        assertNull(s.pending[room])
        s.session.release()
    }

    @Test
    fun successAsksForARefresh() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        val before = s.repo.snapshotCalls
        s.actions.setEnabled(room, "Kitchen", false, false)
        runCurrent()
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.session.release()
    }

    @Test
    fun failureDropsTheOverrideAndReportsWhy() = runTest {
        val s = setup()
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(500, null)) }
        s.actions.setEnabled(room, "Kitchen", false, false)
        runCurrent()
        assertNull(s.pending[room])
        assertEquals(listOf("Couldn't turn Kitchen off"), s.attached)
    }

    @Test
    fun aNotFoundAlsoRefreshes() = runTest {
        val s = setup()
        s.session.acquire(); runCurrent()
        val before = s.repo.snapshotCalls
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(404, null)) }
        s.actions.setEnabled(room, "Kitchen", false, false)
        runCurrent()
        assertEquals(listOf("Kitchen is no longer on the hub"), s.attached)
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.session.release()
    }

    @Test
    fun anUnreachableHubIsReported() = runTest {
        val s = setup()
        s.repo.actionResult = { unreachable }
        s.actions.setEnabled(room, "Kitchen", true, false)
        runCurrent()
        assertEquals(listOf("Couldn't reach the hub"), s.attached)
    }

    @Test
    fun aSourceStillInUseSaysItKeepsPlayingOnSuccessOnly() = runTest {
        val s = setup()
        val key = ItemKey(ItemKind.Source, "radio")
        s.actions.setEnabled(key, "Radio", false, keepsPlaying = true)
        runCurrent()
        assertEquals(listOf("Radio is off. What's playing from it keeps playing."), s.attached)

        s.attached.clear()
        advanceTimeBy(5000)
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(500, null)) }
        s.actions.setEnabled(ItemKey(ItemKind.Source, "radio2"), "Radio 2", false, keepsPlaying = true)
        runCurrent()
        assertEquals(listOf("Couldn't turn Radio 2 off"), s.attached)
    }

    @Test
    fun messagesGoToAppMessagesWhileDetached() = runTest {
        val s = setup(attach = false)
        s.repo.actionResult = { unreachable }
        s.actions.setEnabled(room, "Kitchen", true, false)
        runCurrent()
        assertEquals(listOf("Couldn't reach the hub"), s.posted)
        assertTrue(s.attached.isEmpty())

        s.actions.attach()
        s.posted.clear()
        s.actions.setEnabled(room, "Kitchen", true, false)
        runCurrent()
        assertEquals(listOf("Couldn't reach the hub"), s.attached)
        assertTrue(s.posted.isEmpty())
        s.actions.detach()
    }

    @Test
    fun anAddressChangeClearsEveryOverride() = runTest {
        val s = setup()
        s.repo.actionDelayMs = 1000
        s.actions.setEnabled(room, "Kitchen", false, false)
        runCurrent()
        assertEquals(1, s.pending.size)
        s.store.save(HubAddress("http://other:8080"))
        runCurrent()
        assertTrue(s.pending.isEmpty())
        advanceTimeBy(2000)
        assertTrue(s.pending.isEmpty())
    }
}
