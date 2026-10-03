package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.RoomsContent
import sonora.multiroom.mobile.runViewModelTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomsViewModelPollingTest {
    private val address = HubAddress("http://hub:8080")

    private class Setup(val vm: RoomsViewModel, val factory: FakeFactory, val store: InMemoryHubAddressStore) {
        val repo get() = factory.last
    }

    private fun TestScope.setup(address: HubAddress? = this@RoomsViewModelPollingTest.address): Setup {
        val factory = FakeFactory { currentTime }
        val store = InMemoryHubAddressStore(address)
        // `now` is offset from the scheduler so lastSuccessAt is distinguishable from 0.
        val vm = RoomsViewModel(store, factory, now = { 1_000L + currentTime })
        // Let the view model read the saved address and create its repository.
        runCurrent()
        return Setup(vm, factory, store)
    }

    private fun Setup.connected(): RoomsUiState.Connected {
        val s = vm.state.value
        assertIs<RoomsUiState.Connected>(s)
        return s
    }

    @Test
    fun firstRefreshIsImmediateThenEvery2500ms() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        assertEquals(listOf(0L), s.repo.snapshotTimes)
        advanceTimeBy(2500); runCurrent()
        advanceTimeBy(2500); runCurrent()
        assertEquals(listOf(0L, 2500L, 5000L), s.repo.snapshotTimes)
        assertEquals(Connection.Live, s.connected().connection)
        assertIs<RoomsContent.Rooms>(s.connected().content)
        s.vm.stopPolling()
    }

    @Test
    fun aSlowRefreshNeverOverlapsTheNextOne() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        s.repo.snapshotDelayMs = 4000
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, s.repo.maxConcurrentSnapshots)
        s.vm.stopPolling()
    }

    @Test
    fun aRefreshTakingFourSecondsDelaysTheNextInsteadOfPilingUp() = runViewModelTest {
        val s = setup()
        s.repo.snapshotDelayMs = 4000
        s.vm.startPolling()
        advanceTimeBy(7000); runCurrent()
        // 0..4000 refresh, then 2500 wait, next at 6500.
        assertEquals(listOf(0L, 6500L), s.repo.snapshotTimes)
        assertEquals(1, s.repo.maxConcurrentSnapshots)
        s.vm.stopPolling()
    }

    @Test
    fun firstFailureWithoutContentIsUnreachableWithNoContent() = runViewModelTest {
        val s = setup()
        s.repo.snapshotResult = { unreachable }
        s.vm.startPolling()
        runCurrent()
        assertEquals(Connection.Unreachable(null), s.connected().connection)
        assertNull(s.connected().content)
        s.vm.stopPolling()
    }

    @Test
    fun failureAfterSuccessKeepsContentAndRemembersWhenItLastWorked() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        val content = s.connected().content
        assertNotNull(content)
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Connection.Unreachable(lastSuccessAt = 1_000L), s.connected().connection)
        assertEquals(content, s.connected().content)
        s.vm.stopPolling()
    }

    @Test
    fun successAfterUnreachableIsLiveAgain() = runViewModelTest {
        val s = setup()
        s.repo.snapshotResult = { unreachable }
        s.vm.startPolling()
        runCurrent()
        s.repo.snapshotResult = { HubResult.Ok(oneRoomSnapshot) }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Connection.Live, s.connected().connection)
        assertNotNull(s.connected().content)
        s.vm.stopPolling()
    }

    @Test
    fun noRequestsWhileStoppedAndAnImmediateRefreshOnReturn() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        s.vm.stopPolling()
        val before = s.repo.snapshotCalls
        advanceTimeBy(60_000); runCurrent()
        assertEquals(before, s.repo.snapshotCalls)

        s.vm.startPolling()
        runCurrent()
        assertEquals(before + 1, s.repo.snapshotCalls)
        assertEquals(60_000L, s.repo.snapshotTimes.last())
        s.vm.stopPolling()
    }

    @Test
    fun anAddressChangeRestartsPollingAgainstTheNewRepository() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        val old = s.repo
        advanceTimeBy(1000)

        val other = HubAddress("http://other:8080")
        s.store.save(other)
        runCurrent()

        val fresh = s.factory.repositories.getValue(other)
        assertEquals(listOf(1000L), fresh.snapshotTimes)
        val oldCalls = old.snapshotCalls
        advanceTimeBy(10_000); runCurrent()
        assertEquals(oldCalls, old.snapshotCalls)
        assertEquals(other, s.connected().address)
        s.vm.stopPolling()
    }

    @Test
    fun requestRefreshWhileARefreshIsInFlightNeverOverlapsAndFollowsRightAfter() = runViewModelTest {
        val s = setup()
        s.repo.snapshotDelayMs = 1000
        s.vm.startPolling()
        runCurrent()
        advanceTimeBy(500)
        s.vm.requestRefresh()
        advanceTimeBy(500); runCurrent()
        // The first refresh ends at 1000 and the signal starts exactly one more right away.
        assertEquals(listOf(0L, 1000L), s.repo.snapshotTimes)
        assertEquals(1, s.repo.maxConcurrentSnapshots)
        advanceTimeBy(1000); runCurrent()
        // That one ends at 2000, then the normal 2500 ms wait: nothing before 4500.
        advanceTimeBy(2400); runCurrent()
        assertEquals(listOf(0L, 1000L), s.repo.snapshotTimes)
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf(0L, 1000L, 4500L), s.repo.snapshotTimes)
        s.vm.stopPolling()
    }

    @Test
    fun requestRefreshDuringTheWaitRefreshesNowAndRestartsTheInterval() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        advanceTimeBy(1000)
        s.vm.requestRefresh()
        runCurrent()
        assertEquals(listOf(0L, 1000L), s.repo.snapshotTimes)
        advanceTimeBy(2499); runCurrent()
        assertEquals(2, s.repo.snapshotCalls)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(0L, 1000L, 3500L), s.repo.snapshotTimes)
        s.vm.stopPolling()
    }

    @Test
    fun requestRefreshAfterStopPollingDoesNothingUntilStartPolling() = runViewModelTest {
        val s = setup()
        s.vm.startPolling()
        runCurrent()
        s.vm.stopPolling()
        val before = s.repo.snapshotCalls
        s.vm.requestRefresh()
        advanceUntilIdle()
        assertEquals(before, s.repo.snapshotCalls)
        s.vm.startPolling()
        runCurrent()
        // The stale signal is drained: exactly one refresh for the start, not two.
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.vm.stopPolling()
    }
}
