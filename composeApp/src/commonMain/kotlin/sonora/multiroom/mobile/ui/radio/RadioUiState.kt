package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.ui.RadioFailure
import sonora.multiroom.mobile.ui.session.Connection

enum class ListPhase {
    /** Placeholder rows instead of the list. */
    LoadingFirst,
    Loaded,

    /** "Loading more stations…" under the last row. */
    LoadingMore,
}

/** The stations the screen lists and what is wrong with getting them. */
data class StationList(
    val stations: List<Station> = emptyList(),
    val phase: ListPhase = ListPhase.LoadingFirst,
    /** Where the next page starts; null when there is none. */
    val nextOffset: Int? = null,
    /** The catalogue stops at 10,000; the screen says so. */
    val limitReached: Boolean = false,
    /** `Catalogue` shows the message with Retry above the rows. Never `HubUnreachable` or `Gone`. */
    val problem: RadioFailure? = null,
) {
    /** "No stations match". */
    val empty: Boolean get() = phase == ListPhase.Loaded && stations.isEmpty() && problem == null
}

/** How the radio screen is over; the screen consumes it and navigation pops. */
enum class RadioExit { Chosen, Gone }

/** The open sheet; filled in with the filters (MOB-009, User Story 4). */
data class FilterSheetState(val kind: Kind) {
    enum class Kind { Country, Tag, Language, Order }
}

data class RadioUiState(
    val connection: Connection = Connection.Loading,
    /** What the field shows: at most 100 characters, no `%`. */
    val nameText: String = "",
    val filters: RadioFilters = RadioFilters(),
    val recent: List<Station> = emptyList(),
    val list: StationList = StationList(),
    val sheet: FilterSheetState? = null,
    val exit: RadioExit? = null,
)
