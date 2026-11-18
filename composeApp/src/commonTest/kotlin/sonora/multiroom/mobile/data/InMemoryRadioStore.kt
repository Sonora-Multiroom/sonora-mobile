package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.PendingStation
import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.addRecent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryRadioStore(
    recent: List<Station> = emptyList(),
    filters: RadioFilters? = null,
    pending: List<PendingStation> = emptyList(),
) : RadioStore {
    private val recentState = MutableStateFlow(recent)
    private val filtersState = MutableStateFlow(filters)
    var pendingList: List<PendingStation> = pending
        private set

    override val recent: Flow<List<Station>> = recentState
    override val filters: Flow<RadioFilters?> = filtersState

    override suspend fun pending(): List<PendingStation> = pendingList

    override suspend fun addRecent(station: Station) {
        recentState.value = addRecent(recentState.value, station)
    }

    override suspend fun removeRecent(uuid: String) {
        recentState.value = recentState.value.filterNot { it.uuid == uuid }
    }

    override suspend fun saveFilters(filters: RadioFilters) {
        filtersState.value = filters
    }

    override suspend fun savePending(list: List<PendingStation>) {
        pendingList = list
    }
}
