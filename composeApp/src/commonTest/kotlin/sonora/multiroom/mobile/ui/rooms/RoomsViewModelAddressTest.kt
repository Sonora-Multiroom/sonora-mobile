package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubRepository
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.runViewModelTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Counts what the view model asks of the hub, without ever answering usefully. */
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
        }
    }
}

class RoomsViewModelAddressTest {
    @Test
    fun withoutAnAddressNothingIsRequestedEvenAfterThirtySeconds() = runViewModelTest {
        val factory = CountingFactory()
        val vm = RoomsViewModel(InMemoryHubAddressStore(), factory)
        vm.startPolling()
        runCurrent()
        advanceTimeBy(30_000)
        assertEquals(RoomsUiState.NoAddress, vm.state.value)
        assertEquals(0, factory.calls)
        assertEquals(emptyList(), factory.created)
    }

    @Test
    fun savingAnAddressMovesToConnectedAndCreatesARepositoryForIt() = runViewModelTest {
        val factory = CountingFactory()
        val store = InMemoryHubAddressStore()
        val vm = RoomsViewModel(store, factory)
        advanceUntilIdle()
        assertEquals(RoomsUiState.NoAddress, vm.state.value)

        val address = HubAddress("http://multiroom.lan:8080")
        store.save(address)
        advanceUntilIdle()

        val state = vm.state.value
        assertIs<RoomsUiState.Connected>(state)
        assertEquals(address, state.address)
        assertEquals(Connection.Loading, state.connection)
        assertEquals(listOf(address), factory.created)
    }

    @Test
    fun aSavedAddressIsPickedUpAtStart() = runViewModelTest {
        val factory = CountingFactory()
        val address = HubAddress("http://multiroom.lan:8080")
        val vm = RoomsViewModel(InMemoryHubAddressStore(address), factory)
        advanceUntilIdle()
        val state = vm.state.value
        assertIs<RoomsUiState.Connected>(state)
        assertEquals(address, state.address)
    }
}
