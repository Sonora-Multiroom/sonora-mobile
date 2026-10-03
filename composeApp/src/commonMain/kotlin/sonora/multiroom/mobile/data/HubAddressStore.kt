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

/**
 * Persists the address in a DataStore Preferences file holding one key. Only the file path comes
 * from the platform, so this stays free of any Android `Context`. One instance per file per
 * process: DataStore refuses a second active instance on the same file.
 */
class DataStoreHubAddressStore(path: String) : HubAddressStore {
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

    override val address: Flow<HubAddress?> =
        dataStore.data.map { prefs -> prefs[KEY]?.let(::HubAddress) }

    override suspend fun save(address: HubAddress) {
        dataStore.edit { it[KEY] = address.baseUrl }
    }

    private companion object {
        val KEY = stringPreferencesKey("hub_address")
    }
}
