package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubRepository
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.oneRoomSnapshot
import sonora.multiroom.mobile.ui.rooms.unreachable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Counts what the session asks of the hub, without ever answering usefully. */
private class CountingFactory : HubRepositoryFactory {
    val created = mutableListOf<HubAddress>()
    var calls = 0

    override fun create(address: HubAddress): HubRepository {
        created += address
        return object : HubRepository {
            private fun fail(): HubResult.Err {
                calls++
                return HubResult.Err(HubError.Unreachable)
            }

            override suspend fun snapshot(): HubResult<HubSnapshot> = fail()
            override suspend fun setRoomVolume(roomId: String, volume: Int) = fail()
            override suspend fun stopRoute(routeId: String) = fail()
            override suspend fun setRoutePaused(routeId: String, paused: Boolean) = fail()
            override suspend fun setMasterMute(muted: Boolean) = fail()
            override suspend fun setRoomMute(roomId: String, muted: Boolean) = fail()
            override suspend fun setGroupMute(groupId: String, muted: Boolean) = fail()
            override suspend fun setRoomEnabled(roomId: String, enabled: Boolean) = fail()
            override suspend fun setGroupEnabled(groupId: String, enabled: Boolean) = fail()
            override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = fail()
            override suspend fun removeSource(sourceId: String) = fail()
            override suspend fun transferRoute(routeId: String, target: Target) = fail()
            override suspend fun startSource(inputId: String, target: Target) = fail()
            override suspend fun playLink(uri: String, target: Target) = fail()
        }
    }
}

class HubSessionTest {
    private val address = HubAddress("http://hub:8080")

    private class Setup(val session: HubSession, val factory: FakeFactory, val store: InMemoryHubAddressStore) {
        val repo get() = factory.last
    }

    private fun TestScope.setup(address: HubAddress? = this@HubSessionTest.address): Setup {
        val factory = FakeFactory { currentTime }
        val store = InMemoryHubAddressStore(address)
        // `now` is offset from the scheduler so lastSuccessAt is distinguishable from 0.
        val session = HubSession(store, factory, backgroundScope, now = { 1_000L + currentTime })
        // Let the session read the saved address and create its repository.
        runCurrent()
        return Setup(session, factory, store)
    }

    private fun Setup.connected(): SessionState.Connected {
        val s = session.state.value
        assertIs<SessionState.Connected>(s)
        return s
    }

    // ---- Moved from the 001 Rooms polling tests --------------------------------------------

    @Test
    fun firstRefreshIsImmediateThenEvery2500ms() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        assertEquals(listOf(0L), s.repo.snapshotTimes)
        advanceTimeBy(2500); runCurrent()
        advanceTimeBy(2500); runCurrent()
        assertEquals(listOf(0L, 2500L, 5000L), s.repo.snapshotTimes)
        assertEquals(Connection.Live, s.connected().connection)
        assertNotNull(s.connected().snapshot)
        s.session.release()
    }

    @Test
    fun aSlowRefreshNeverOverlapsTheNextOne() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        s.repo.snapshotDelayMs = 4000
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, s.repo.maxConcurrentSnapshots)
        s.session.release()
    }

    @Test
    fun aRefreshTakingFourSecondsDelaysTheNextInsteadOfPilingUp() = runTest {
        val s = setup()
        s.repo.snapshotDelayMs = 4000
        s.session.acquire()
        advanceTimeBy(7000); runCurrent()
        // 0..4000 refresh, then 2500 wait, next at 6500.
        assertEquals(listOf(0L, 6500L), s.repo.snapshotTimes)
        assertEquals(1, s.repo.maxConcurrentSnapshots)
        s.session.release()
    }

    @Test
    fun firstFailureWithoutASnapshotIsUnreachableWithNoSnapshot() = runTest {
        val s = setup()
        s.repo.snapshotResult = { unreachable }
        s.session.acquire()
        runCurrent()
        assertEquals(Connection.Unreachable(null), s.connected().connection)
        assertNull(s.connected().snapshot)
        s.session.release()
    }

    @Test
    fun failureAfterSuccessKeepsTheSnapshotAndRemembersWhenItLastWorked() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        val snapshot = s.connected().snapshot
        assertNotNull(snapshot)
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Connection.Unreachable(lastSuccessAt = 1_000L), s.connected().connection)
        assertEquals(snapshot, s.connected().snapshot)
        s.session.release()
    }

    @Test
    fun successAfterUnreachableIsLiveAgain() = runTest {
        val s = setup()
        s.repo.snapshotResult = { unreachable }
        s.session.acquire()
        runCurrent()
        s.repo.snapshotResult = { HubResult.Ok(oneRoomSnapshot) }
        advanceTimeBy(2500); runCurrent()
        assertEquals(Connection.Live, s.connected().connection)
        assertNotNull(s.connected().snapshot)
        s.session.release()
    }

    @Test
    fun noRequestsWhileReleasedAndAnImmediateRefreshOnReturn() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        s.session.release()
        val before = s.repo.snapshotCalls
        advanceTimeBy(60_000); runCurrent()
        assertEquals(before, s.repo.snapshotCalls)

        s.session.acquire()
        runCurrent()
        assertEquals(before + 1, s.repo.snapshotCalls)
        assertEquals(60_000L, s.repo.snapshotTimes.last())
        s.session.release()
    }

    @Test
    fun anAddressChangeRestartsPollingAgainstTheNewRepository() = runTest {
        val s = setup()
        s.session.acquire()
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
        s.session.release()
    }

    @Test
    fun requestRefreshWhileARefreshIsInFlightNeverOverlapsAndFollowsRightAfter() = runTest {
        val s = setup()
        s.repo.snapshotDelayMs = 1000
        s.session.acquire()
        runCurrent()
        advanceTimeBy(500)
        s.session.requestRefresh()
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
        s.session.release()
    }

    @Test
    fun requestRefreshDuringTheWaitRefreshesNowAndRestartsTheInterval() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        advanceTimeBy(1000)
        s.session.requestRefresh()
        runCurrent()
        assertEquals(listOf(0L, 1000L), s.repo.snapshotTimes)
        advanceTimeBy(2499); runCurrent()
        assertEquals(2, s.repo.snapshotCalls)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(0L, 1000L, 3500L), s.repo.snapshotTimes)
        s.session.release()
    }

    @Test
    fun requestRefreshAfterReleaseDoesNothingUntilTheNextAcquire() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        s.session.release()
        val before = s.repo.snapshotCalls
        s.session.requestRefresh()
        advanceUntilIdle()
        assertEquals(before, s.repo.snapshotCalls)
        s.session.acquire()
        runCurrent()
        // The stale signal is drained: exactly one refresh for the acquire, not two.
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.session.release()
    }

    // ---- Moved from the 001 Rooms address tests ---------------------------------------------

    @Test
    fun withoutAnAddressNothingIsRequestedEvenAfterThirtySeconds() = runTest {
        val factory = CountingFactory()
        val session = HubSession(InMemoryHubAddressStore(), factory, backgroundScope)
        session.acquire()
        runCurrent()
        advanceTimeBy(30_000)
        assertEquals(SessionState.NoAddress, session.state.value)
        assertEquals(0, factory.calls)
        assertEquals(emptyList(), factory.created)
        assertNull(session.repository)
    }

    @Test
    fun savingAnAddressMovesToConnectedAndCreatesARepositoryForIt() = runTest {
        val factory = CountingFactory()
        val store = InMemoryHubAddressStore()
        val session = HubSession(store, factory, backgroundScope)
        runCurrent()
        assertEquals(SessionState.NoAddress, session.state.value)

        val address = HubAddress("http://multiroom.lan:8080")
        store.save(address)
        runCurrent()

        val state = session.state.value
        assertIs<SessionState.Connected>(state)
        assertEquals(address, state.address)
        assertEquals(Connection.Loading, state.connection)
        assertEquals(listOf(address), factory.created)
    }

    @Test
    fun aSavedAddressIsPickedUpAtStart() = runTest {
        val address = HubAddress("http://multiroom.lan:8080")
        val session = HubSession(InMemoryHubAddressStore(address), CountingFactory(), backgroundScope)
        runCurrent()
        val state = session.state.value
        assertIs<SessionState.Connected>(state)
        assertEquals(address, state.address)
    }

    // ---- Holder counting ---------------------------------------------------------------------

    @Test
    fun withNoHolderNothingIsRequested() = runTest {
        val s = setup()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, s.repo.snapshotCalls)
    }

    @Test
    fun acquireFromZeroRefreshesImmediately() = runTest {
        val s = setup()
        advanceTimeBy(7_000)
        s.session.acquire()
        runCurrent()
        assertEquals(listOf(7_000L), s.repo.snapshotTimes)
        s.session.release()
    }

    @Test
    fun twoAcquiresAndOneReleaseStillPoll() = runTest {
        val s = setup()
        s.session.acquire()
        s.session.acquire()
        runCurrent()
        // The second acquire does not restart the loop: still one immediate refresh.
        assertEquals(listOf(0L), s.repo.snapshotTimes)
        s.session.release()
        advanceTimeBy(2500); runCurrent()
        assertEquals(listOf(0L, 2500L), s.repo.snapshotTimes)
        s.session.release()
        val before = s.repo.snapshotCalls
        advanceTimeBy(30_000); runCurrent()
        assertEquals(before, s.repo.snapshotCalls)
    }

    @Test
    fun releaseToZeroCancelsARefreshInFlight() = runTest {
        val s = setup()
        s.repo.snapshotDelayMs = 3000
        s.session.acquire()
        runCurrent()
        advanceTimeBy(1000)
        assertEquals(1, s.repo.concurrentSnapshots)
        s.session.release()
        runCurrent()
        assertEquals(0, s.repo.concurrentSnapshots)
        advanceTimeBy(10_000); runCurrent()
        assertNull(s.connected().snapshot)
        assertEquals(1, s.repo.snapshotCalls)
    }

    @Test
    fun anAddressChangeClearsTheSnapshot() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        assertNotNull(s.connected().snapshot)
        // Released, so the new address is not refreshed and cannot refill the snapshot.
        s.session.release()
        val other = HubAddress("http://other:8080")
        s.store.save(other)
        runCurrent()
        assertNull(s.connected().snapshot)
        assertEquals(other, s.connected().address)
    }

    // ---- Sequence numbers (research R1) --------------------------------------------------------

    @Test
    fun startedSeqIncrementsJustBeforeEachRefreshAndTheStateCarriesTheSeqOfItsResult() = runTest {
        val s = setup()
        assertEquals(0L, s.session.startedSeq)
        s.repo.snapshotDelayMs = 1000
        s.session.acquire()
        runCurrent()
        assertEquals(1L, s.session.startedSeq)
        assertEquals(0L, s.connected().refreshSeq)
        advanceTimeBy(1000); runCurrent()
        assertEquals(1L, s.connected().refreshSeq)
        advanceTimeBy(2500); runCurrent()
        assertEquals(2L, s.session.startedSeq)
        advanceTimeBy(1000); runCurrent()
        assertEquals(2L, s.connected().refreshSeq)
        s.session.release()
    }

    @Test
    fun aRefreshInFlightWhenAFenceIsCapturedDoesNotPassIt() = runTest {
        val s = setup()
        s.repo.snapshotDelayMs = 1000
        s.session.acquire()
        runCurrent()
        val captured = s.session.startedSeq
        advanceTimeBy(1000); runCurrent()
        // The refresh that was running when the fence was captured carries refreshSeq == captured.
        assertEquals(captured, s.connected().refreshSeq)
        assertTrue(s.connected().refreshSeq <= captured)

        advanceTimeBy(2500); runCurrent()
        advanceTimeBy(1000); runCurrent()
        // The next refresh started after the fence and passes it.
        assertTrue(s.connected().refreshSeq > captured)
        s.session.release()
    }

    @Test
    fun aFailedRefreshDoesNotChangeTheSeqOfTheKeptSnapshot() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        val seq = s.connected().refreshSeq
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertEquals(seq, s.connected().refreshSeq)
        s.session.release()
    }

    // ---- awaitFreshSnapshot (003, research R7/R8) ------------------------------------------------

    private fun muted(on: Boolean) = oneRoomSnapshot.copy(masterMuted = on)

    @Test
    fun awaitFreshSnapshotRefreshesAndReturnsTheNewResult() = runTest {
        val s = setup()
        s.repo.snapshotResult = { HubResult.Ok(muted(false)) }
        s.session.acquire(); runCurrent()
        val before = s.repo.snapshotCalls
        s.repo.snapshotResult = { HubResult.Ok(muted(true)) }
        var result: HubSnapshot? = null
        backgroundScope.launch { result = s.session.awaitFreshSnapshot() }
        runCurrent()
        assertEquals(before + 1, s.repo.snapshotCalls)
        assertEquals(true, result?.masterMuted)
        s.session.release()
    }

    @Test
    fun awaitFreshSnapshotIgnoresARefreshAlreadyInFlight() = runTest {
        val s = setup()
        s.repo.snapshotResult = { HubResult.Ok(muted(false)) }
        s.session.acquire(); runCurrent()
        // A slow refresh is running; the answer of the first one that STARTS after the call is wanted.
        s.repo.snapshotDelayMs = 500
        s.repo.snapshotResult = { HubResult.Ok(muted(s.repo.snapshotCalls >= 3)) }
        s.session.requestRefresh(); runCurrent()
        assertEquals(2, s.repo.snapshotCalls)
        var result: HubSnapshot? = null
        backgroundScope.launch { result = s.session.awaitFreshSnapshot() }
        runCurrent()
        advanceTimeBy(501); runCurrent()
        assertNull(result)
        advanceTimeBy(501); runCurrent()
        assertEquals(3, s.repo.snapshotCalls)
        assertEquals(true, result?.masterMuted)
        s.session.release()
    }

    @Test
    fun awaitFreshSnapshotIsNullAfterTheTimeoutWhenRefreshesFail() = runTest {
        val s = setup()
        s.repo.snapshotResult = { HubResult.Ok(muted(false)) }
        s.session.acquire(); runCurrent()
        s.repo.snapshotResult = { unreachable }
        var result: HubSnapshot? = muted(true)
        var finishedAt = -1L
        backgroundScope.launch { result = s.session.awaitFreshSnapshot(5000); finishedAt = currentTime }
        advanceTimeBy(4_999); runCurrent()
        assertEquals(-1L, finishedAt)
        advanceTimeBy(2); runCurrent()
        assertNull(result)
        assertEquals(5000L, finishedAt)
        s.session.release()
    }

    @Test
    fun awaitFreshSnapshotIsNullWhenNoScreenHoldsTheSession() = runTest {
        val s = setup()
        var result: HubSnapshot? = muted(true)
        backgroundScope.launch { result = s.session.awaitFreshSnapshot(5000) }
        advanceTimeBy(5_001); runCurrent()
        assertNull(result)
        assertEquals(0, s.repo.snapshotCalls)
    }
}
