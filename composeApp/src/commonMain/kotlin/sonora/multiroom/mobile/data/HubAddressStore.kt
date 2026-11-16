package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.HubAddress
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

interface HubAddressStore {
    /** The saved address, or null when none was ever saved (there is no default). */
    val address: Flow<HubAddress?>

    suspend fun save(address: HubAddress)
}

/** The app's one preferences file. DataStore refuses a second active instance on the same file. */
fun createPreferencesDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

/**
 * Persists the address in the app's preferences file. The file itself is created once by the app
 * (only its path comes from the platform, so this stays free of any Android `Context`) and shared
 * with the radio store.
 */
class DataStoreHubAddressStore(private val dataStore: DataStore<Preferences>) : HubAddressStore {
    override val address: Flow<HubAddress?> =
        dataStore.data.map { prefs -> prefs[KEY]?.let(::HubAddress) }

    override suspend fun save(address: HubAddress) {
        dataStore.edit { it[KEY] = address.baseUrl }
    }

    private companion object {
        val KEY = stringPreferencesKey("hub_address")
    }
}
