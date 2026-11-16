package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationOrder
import sonora.multiroom.mobile.domain.stationDetail
import sonora.multiroom.mobile.domain.stationDisplayName

/** All wording and spoken labels of the radio screen (MOB-009); decisions live in `domain/`. */
object RadioText {
    const val TITLE = "Radio stations"
    const val BACK = "Back"
    const val NAME_FIELD = "Station name"
    const val CLEAR_NAME = "Clear the name"
    const val LIST_HEADING = "STATIONS"
    const val UNNAMED = "Unnamed station"
    const val LOADING = "Loading stations"
    const val LOADING_MORE = "Loading more stations…"
    const val RECENT_HEADING = "RECENT"
    const val CANCEL = "Cancel"
    const val NO_MATCH = "No stations match"
    const val NO_MATCH_HINT = "Try another name or fewer filters."
    const val CLEAR_FILTERS = "Clear filters"
    const val LOADING_MORE_TAGS = "Loading more tags…"

    fun sheetTitle(kind: FilterSheetState.Kind): String = when (kind) {
        FilterSheetState.Kind.Country -> "Country"
        FilterSheetState.Kind.Tag -> "Tag"
        FilterSheetState.Kind.Language -> "Language"
        FilterSheetState.Kind.Order -> "Order"
    }

    /** The field's placeholder and spoken label; the Order sheet has no field. */
    fun searchLabel(kind: FilterSheetState.Kind): String? = when (kind) {
        FilterSheetState.Kind.Country -> "Search countries"
        FilterSheetState.Kind.Tag -> "Search tags"
        FilterSheetState.Kind.Language -> "Search languages"
        FilterSheetState.Kind.Order -> null
    }
}

fun orderName(order: StationOrder): String = when (order) {
    StationOrder.MostVoted -> "Most voted"
    StationOrder.Trending -> "Trending"
    StationOrder.NameAZ -> "Name A–Z"
}

fun orderDetail(order: StationOrder): String? = when (order) {
    StationOrder.MostVoted -> "The catalogue's all-time favourites"
    StationOrder.Trending -> "Most plays in the last 24 hours"
    StationOrder.NameAZ -> null
}

fun orderControlLabel(order: StationOrder): String = "Order: ${orderName(order)}. Change the order"

/** "STATIONS IN UKRAINE" with a country chosen, else "STATIONS". */
fun listHeading(country: Country?): String =
    if (country == null) RadioText.LIST_HEADING else "${RadioText.LIST_HEADING} IN ${country.name.uppercase()}"

/** The spoken label of a chosen chip's remove button. */
fun removeFilterLabel(kind: FilterSheetState.Kind, value: String): String = when (kind) {
    FilterSheetState.Kind.Country -> "Remove the country $value"
    FilterSheetState.Kind.Tag -> "Remove the tag $value"
    FilterSheetState.Kind.Language -> "Remove the language $value"
    FilterSheetState.Kind.Order -> "Remove the order $value"
}

/** The radio screen's second line: the known parts alone, or none. */
fun stationRowDetail(station: Station): String? = stationDetail(station)

/** A station row read as one node: "<name>, <second line>". */
fun stationSpoken(station: Station, detail: String?): String =
    if (detail == null) stationDisplayName(station) else "${stationDisplayName(station)}, $detail"

/** The spoken label of a recent row's remove button. */
fun removeRecentLabel(station: Station): String = "Remove ${stationDisplayName(station)} from Recent"
