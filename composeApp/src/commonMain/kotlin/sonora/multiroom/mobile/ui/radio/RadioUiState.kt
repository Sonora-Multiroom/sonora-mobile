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

/** One row of a filter sheet. A country row carries its [code]; an Order row the order's name. */
data class FilterRow(
    val name: String,
    val count: Long?,
    val detail: String?,
    val selected: Boolean,
    val code: String? = null,
)

/** The open sheet and what it lists. */
data class FilterSheetState(
    val kind: Kind,
    val searchText: String = "",
    val rows: List<FilterRow> = emptyList(),
    /** Placeholder rows instead of the list. */
    val loading: Boolean = false,
    /** "Loading more tags…" under the last row. */
    val loadingMore: Boolean = false,
    /** `Catalogue` shows the message with Retry inside the sheet. */
    val problem: RadioFailure? = null,
) {
    enum class Kind { Country, Tag, Language, Order }

    /** The row to bring into view on opening. */
    val chosenIndex: Int? get() = rows.indexOfFirst { it.selected }.takeIf { it >= 0 }
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
