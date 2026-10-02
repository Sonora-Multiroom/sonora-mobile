package ai.sonora.mobile.data

import ai.sonora.mobile.domain.HubAddress
import kotlinx.coroutines.flow.Flow

interface HubAddressStore {
    /** The saved address, or null when none was ever saved (there is no default). */
    val address: Flow<HubAddress?>

    suspend fun save(address: HubAddress)
}
