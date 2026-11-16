package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.Station
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
}

/** The radio screen's second line: the known parts alone, or none. */
fun stationRowDetail(station: Station): String? = stationDetail(station)

/** A station row read as one node: "<name>, <second line>". */
fun stationSpoken(station: Station, detail: String?): String =
    if (detail == null) stationDisplayName(station) else "${stationDisplayName(station)}, $detail"

/** The spoken label of a recent row's remove button. */
fun removeRecentLabel(station: Station): String = "Remove ${stationDisplayName(station)} from Recent"
