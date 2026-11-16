package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.PendingStation
import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationOrder
import sonora.multiroom.mobile.domain.addRecent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What the phone remembers about radio (MOB-009). These values belong to the phone, not to a hub. */
interface RadioStore {
    /** Most recent first, at most ten; empty when nothing is stored or the value is unreadable. */
    val recent: Flow<List<Station>>

    /** Null before the first visit to the radio screen, or when the stored value is unreadable. */
    val filters: Flow<RadioFilters?>

    /** Stations waiting to qualify for "Recent"; read once at start-up. */
    suspend fun pending(): List<PendingStation>

    suspend fun addRecent(station: Station)

    /** Removes that entry only. */
    suspend fun removeRecent(uuid: String)

    suspend fun saveFilters(filters: RadioFilters)

    /** Replaces the stored list. */
    suspend fun savePending(list: List<PendingStation>)
}

@Serializable
private data class StationJson(
    val uuid: String = "",
    val uri: String = "",
    val name: String? = null,
    val countryCode: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
) {
    fun toStation(): Station? =
        if (uuid.isBlank() || uri.isBlank()) null else Station(uuid, uri, name, countryCode, codec, bitrate)
}

private fun Station.toJson() = StationJson(uuid, uri, name, countryCode, codec, bitrate)

@Serializable
private data class CountryJson(val code: String = "", val name: String = "")

@Serializable
private data class FiltersJson(
    val country: CountryJson? = null,
    val tag: String? = null,
    val language: String? = null,
    val order: String? = null,
)

@Serializable
private data class PendingJson(
    val station: StationJson = StationJson(),
    val routeId: String = "",
    val trackedAt: Long = 0,
    val playingSince: Long? = null,
)

private val StoreJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** The station as JSON; also used for Start Playback's saved state. */
fun encodeStation(station: Station): String = StoreJson.encodeToString(StationJson.serializer(), station.toJson())

/** Null for text that is not a station. */
fun decodeStation(text: String): Station? =
    runCatching { StoreJson.decodeFromString(StationJson.serializer(), text).toStation() }.getOrNull()

private fun decodeRecent(text: String?): List<Station> =
    text?.let {
        runCatching { StoreJson.decodeFromString(ListSerializer(StationJson.serializer()), it) }.getOrNull()
    }?.mapNotNull { it.toStation() }.orEmpty()

private fun decodeFilters(text: String?): RadioFilters? {
    val json = text?.let {
        runCatching { StoreJson.decodeFromString(FiltersJson.serializer(), it) }.getOrNull()
    } ?: return null
    return RadioFilters(
        country = json.country?.takeIf { it.code.isNotBlank() }?.let { Country(it.code, it.name, 0) },
        tag = json.tag?.takeIf { it.isNotBlank() },
        language = json.language?.takeIf { it.isNotBlank() },
        // An order this app does not know reads as the default.
        order = StationOrder.entries.firstOrNull { it.name == json.order } ?: StationOrder.MostVoted,
    )
}

private fun decodePending(text: String?): List<PendingStation> =
    text?.let {
        runCatching { StoreJson.decodeFromString(ListSerializer(PendingJson.serializer()), it) }.getOrNull()
    }?.mapNotNull { p ->
        val station = p.station.toStation() ?: return@mapNotNull null
        if (p.routeId.isBlank()) null else PendingStation(station, p.routeId, p.trackedAt, p.playingSince)
    }.orEmpty()

/**
 * Keeps the three values as JSON in the app's one preferences file. An unreadable value counts as
 * absent; it never crashes the app.
 */
class DataStoreRadioStore(private val dataStore: DataStore<Preferences>) : RadioStore {
    override val recent: Flow<List<Station>> = dataStore.data.map { decodeRecent(it[RECENT]) }

    override val filters: Flow<RadioFilters?> = dataStore.data.map { decodeFilters(it[FILTERS]) }

    override suspend fun pending(): List<PendingStation> = decodePending(dataStore.data.first()[PENDING])

    override suspend fun addRecent(station: Station) {
        dataStore.edit { prefs ->
            val next = addRecent(decodeRecent(prefs[RECENT]), station)
            prefs[RECENT] = StoreJson.encodeToString(ListSerializer(StationJson.serializer()), next.map { it.toJson() })
        }
    }

    override suspend fun removeRecent(uuid: String) {
        dataStore.edit { prefs ->
            val next = decodeRecent(prefs[RECENT]).filterNot { it.uuid == uuid }
            prefs[RECENT] = StoreJson.encodeToString(ListSerializer(StationJson.serializer()), next.map { it.toJson() })
        }
    }

    override suspend fun saveFilters(filters: RadioFilters) {
        val json = FiltersJson(
            country = filters.country?.let { CountryJson(it.code, it.name) },
            tag = filters.tag,
            language = filters.language,
            order = filters.order.name,
        )
        dataStore.edit { it[FILTERS] = StoreJson.encodeToString(FiltersJson.serializer(), json) }
    }

    override suspend fun savePending(list: List<PendingStation>) {
        val json = list.map { PendingJson(it.station.toJson(), it.routeId, it.trackedAt, it.playingSince) }
        dataStore.edit { it[PENDING] = StoreJson.encodeToString(ListSerializer(PendingJson.serializer()), json) }
    }

    private companion object {
        val RECENT = stringPreferencesKey("radio_recent")
        val FILTERS = stringPreferencesKey("radio_filters")
        val PENDING = stringPreferencesKey("radio_pending")
    }
}
