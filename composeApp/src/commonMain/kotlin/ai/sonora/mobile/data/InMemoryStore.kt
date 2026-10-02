package ai.sonora.mobile.data

import ai.sonora.mobile.domain.HubAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Temporary until the DataStore-backed store replaces it (T023). */
class InMemoryStore : HubAddressStore {
    private val state = MutableStateFlow<HubAddress?>(null)
    override val address: Flow<HubAddress?> = state

    override suspend fun save(address: HubAddress) {
        state.value = address
    }
}
