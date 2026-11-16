package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.PendingStation
import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationOrder
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A DataStore that lives in memory, so the real store's JSON can be tested without a file. */
private class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class RadioStoreTest {
    private fun station(uuid: String, name: String? = "N $uuid") = Station(uuid, "radiobrowser://$uuid", name, "DE", "MP3", 128)

    private val dataStore = MemoryDataStore()
    private val store = DataStoreRadioStore(dataStore)

    private suspend fun put(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    @Test
    fun nothingStoredMeansEmptyAndNoFilters() = runTest {
        assertEquals(emptyList(), store.recent.first())
        assertNull(store.filters.first())
        assertEquals(emptyList(), store.pending())
    }

    @Test
    fun recentStationsRoundTripMostRecentFirst() = runTest {
        store.addRecent(station("a"))
        store.addRecent(station("b", name = null))
        assertEquals(listOf(station("b", name = null), station("a")), store.recent.first())
    }

    @Test
    fun addRecentKeepsTen() = runTest {
        for (i in 1..12) store.addRecent(station("s$i"))
        val recent = store.recent.first()
        assertEquals(10, recent.size)
        assertEquals("s12", recent.first().uuid)
        assertEquals("s3", recent.last().uuid)
    }

    @Test
    fun removeRecentRemovesOnlyThatEntryAndTouchesNoPending() = runTest {
        store.addRecent(station("a"))
        store.addRecent(station("b"))
        val pending = listOf(PendingStation(station("a"), "r1", 1000L, null))
        store.savePending(pending)
        store.removeRecent("a")
        assertEquals(listOf(station("b")), store.recent.first())
        assertEquals(pending, store.pending())
    }

    @Test
    fun filtersRoundTrip() = runTest {
        val filters = RadioFilters(Country("DE", "Germany", 0), "jazz", "german", StationOrder.NameAZ)
        store.saveFilters(filters)
        assertEquals(filters, store.filters.first())
        val none = RadioFilters()
        store.saveFilters(none)
        assertEquals(none, store.filters.first())
    }

    @Test
    fun anUnknownOrderReadsAsMostVoted() = runTest {
        put("radio_filters", """{"tag":"jazz","order":"Sideways"}""")
        assertEquals(RadioFilters(tag = "jazz", order = StationOrder.MostVoted), store.filters.first())
    }

    @Test
    fun unreadableValuesCountAsAbsent() = runTest {
        put("radio_recent", "not json")
        put("radio_filters", "[1,2")
        put("radio_pending", """{"wrong":"shape"}""")
        assertEquals(emptyList(), store.recent.first())
        assertNull(store.filters.first())
        assertEquals(emptyList(), store.pending())
    }

    @Test
    fun pendingEntriesRoundTripWithAndWithoutPlayingSince() = runTest {
        val list = listOf(
            PendingStation(station("a"), "r1", 1_000L, null),
            PendingStation(station("b"), "r2", 2_000L, 9_000L),
        )
        store.savePending(list)
        assertEquals(list, store.pending())
        store.savePending(emptyList())
        assertEquals(emptyList(), store.pending())
    }

    @Test
    fun stationJsonRoundTripsAndGarbageIsNull() {
        val s = station("a", name = null)
        assertEquals(s, decodeStation(encodeStation(s)))
        assertNull(decodeStation("garbage"))
        assertNull(decodeStation(""))
        assertNull(decodeStation("""{"uuid":"","uri":"x"}"""))
    }

    @Test
    fun theInMemoryStoreBehavesLikeTheRealOne() = runTest {
        val memory = InMemoryRadioStore()
        memory.addRecent(station("a"))
        memory.addRecent(station("b"))
        memory.removeRecent("a")
        assertEquals(listOf(station("b")), memory.recent.first())
    }
}
