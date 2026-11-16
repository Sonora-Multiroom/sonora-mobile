package sonora.multiroom.mobile.domain

// MOB-009: radio stations. Plain types and pure rules; the wire types stay in data/.

const val RADIO_PAGE_SIZE = 50

/** The hub's catalogue search cannot go past this many results. */
const val RADIO_RESULT_LIMIT = 10_000

const val RECENT_LIMIT = 10

/** The most recent stations Start Playback lists directly. */
const val RECENT_IN_START_PLAYBACK = 3

private const val NAME_LIMIT = 100

/** A radio station as the hub's catalogue lists it. Only what the app shows or sends is kept. */
data class Station(
    /** Identity. */
    val uuid: String,
    /** Passed unchanged to `POST /api/v2/play`. */
    val uri: String,
    val name: String?,
    val countryCode: String?,
    val codec: String?,
    /** kbit/s. */
    val bitrate: Int?,
)

enum class StationOrder { MostVoted, Trending, NameAZ }

/** [name] is already cleaned with [stationName]. */
data class StationQuery(
    val name: String?,
    val countryCode: String?,
    val tag: String?,
    val language: String?,
    val order: StationOrder,
)

data class StationPage(val stations: List<Station>, val nextOffset: Int?)

data class Country(val code: String, val name: String, val stationCount: Long)

data class Language(val name: String, val stationCount: Long)

data class Tag(val name: String, val stationCount: Long)

data class TagPage(val tags: List<Tag>, val nextOffset: Int?)

/** What the radio screen's chips and order control show; kept on the phone. */
data class RadioFilters(
    val country: Country? = null,
    val tag: String? = null,
    val language: String? = null,
    val order: StationOrder = StationOrder.MostVoted,
) {
    fun toQuery(name: String?) = StationQuery(name, country?.code, tag, language, order)
}

/**
 * A started station waiting to qualify for "Recent". [trackedAt] is the phone's clock (epoch ms)
 * when the start request was sent; [playingSince] is fixed once, by the first refresh that shows
 * the route playing.
 */
data class PendingStation(
    val station: Station,
    val routeId: String,
    val trackedAt: Long,
    val playingSince: Long?,
)

// ---- Names typed by the user --------------------------------------------------------------

/** The name to search for: `%` removed, trimmed, at most 100 characters; null when nothing is left. */
fun stationName(text: String): String? =
    text.replace("%", "").trim().take(NAME_LIMIT).trim().ifEmpty { null }

/** Like [stationName], with `/` removed too. */
fun tagName(text: String): String? =
    text.replace("%", "").replace("/", "").trim().take(NAME_LIMIT).trim().ifEmpty { null }

// ---- What a row says ----------------------------------------------------------------------

fun stationDisplayName(station: Station): String = station.name ?: "Unnamed station"

/** The known parts joined with " · ": country code, codec, "<n> kbps"; null when none is known. */
fun stationDetail(station: Station): String? =
    listOfNotNull(station.countryCode, station.codec, station.bitrate?.let { "$it kbps" })
        .joinToString(" · ")
        .ifEmpty { null }

// ---- Paging -------------------------------------------------------------------------------

/** [listed] plus the stations of [page] not yet listed (by `uuid`), in page order. */
fun mergePage(listed: List<Station>, page: StationPage): List<Station> {
    val seen = listed.mapTo(HashSet()) { it.uuid }
    return listed + page.stations.filter { seen.add(it.uuid) }
}

fun canLoadMore(nextOffset: Int?): Boolean = nextOffset != null && nextOffset < RADIO_RESULT_LIMIT

fun limitReached(nextOffset: Int?): Boolean = nextOffset != null && nextOffset >= RADIO_RESULT_LIMIT

// ---- Filters ------------------------------------------------------------------------------

/** The country whose code equals the phone's region, ignoring case. */
fun regionCountry(region: String, countries: List<Country>): Country? =
    region.takeIf { it.isNotBlank() }?.let { r -> countries.firstOrNull { it.code.equals(r, ignoreCase = true) } }

/** The items whose name contains the trimmed [text], ignoring case; all of them when it is blank. */
fun <T> narrow(items: List<T>, text: String, name: (T) -> String): List<T> {
    val needle = text.trim()
    return if (needle.isEmpty()) items else items.filter { name(it).contains(needle, ignoreCase = true) }
}

/** 6965 becomes "6,965". */
fun groupThousands(count: Long): String {
    val digits = count.toString()
    val negative = digits.startsWith("-")
    val body = digits.removePrefix("-")
    val grouped = body.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

// ---- Recent -------------------------------------------------------------------------------

/** [station] first, any older entry with its `uuid` removed, cut to [RECENT_LIMIT]. */
fun addRecent(list: List<Station>, station: Station): List<Station> =
    (listOf(station) + list.filterNot { it.uuid == station.uuid }).take(RECENT_LIMIT)

// ---- Availability -------------------------------------------------------------------------

/** An extension with id `radio` (any letter case) and status Active. */
fun radioOffered(inventory: ExtensionInventory): Boolean =
    inventory.extensions.any { it.id.equals("radio", ignoreCase = true) && it.status == ExtensionStatus.Active }
