package ai.sonora.mobile.data

import ai.sonora.mobile.domain.HubAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryHubAddressStore(initial: HubAddress? = null) : HubAddressStore {
    private val state = MutableStateFlow(initial)
    override val address: Flow<HubAddress?> = state

    override suspend fun save(address: HubAddress) {
        state.value = address
    }
}
