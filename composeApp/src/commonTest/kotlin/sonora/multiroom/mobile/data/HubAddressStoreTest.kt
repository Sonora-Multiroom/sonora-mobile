package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.RadioFilters
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class SharedMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HubAddressStoreTest {
    @Test
    fun writingAnotherValueInTheSharedFileDoesNotReAnnounceTheAddress() = runTest {
        val dataStore = SharedMemoryDataStore()
        val addresses = DataStoreHubAddressStore(dataStore)
        val radio = DataStoreRadioStore(dataStore)
        val seen = mutableListOf<HubAddress?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { addresses.address.collect { seen += it } }

        val hub = HubAddress("http://hub:8080")
        addresses.save(hub)
        // Each of these writes the same file; none of them is a new address, and a new address
        // replaces the session's repository, which drops any radio answer still on its way.
        radio.saveFilters(RadioFilters(tag = "jazz"))
        radio.savePending(emptyList())
        radio.removeRecent("x")

        assertEquals(listOf(null, hub), seen)
    }
}
