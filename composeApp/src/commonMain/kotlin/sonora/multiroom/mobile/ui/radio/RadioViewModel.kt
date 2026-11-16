package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.data.HubRepository
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.RadioStore
import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.Language
import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.StationOrder
import sonora.multiroom.mobile.domain.Tag
import sonora.multiroom.mobile.domain.TagPage
import sonora.multiroom.mobile.domain.narrow
import sonora.multiroom.mobile.domain.regionCountry
import sonora.multiroom.mobile.domain.tagName
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationQuery
import sonora.multiroom.mobile.domain.canLoadMore
import sonora.multiroom.mobile.domain.limitReached
import sonora.multiroom.mobile.domain.mergePage
import sonora.multiroom.mobile.domain.stationName
import sonora.multiroom.mobile.ui.RadioFailure
import sonora.multiroom.mobile.ui.radioFailure
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.RadioAvailability
import sonora.multiroom.mobile.ui.session.SessionState
import sonora.multiroom.mobile.ui.session.StationPicker
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The radio screen: search the hub's catalogue by name, page the list, hand a chosen station back
 * to Start Playback. All requests run in [viewModelScope], so leaving the screen cancels them and
 * nothing can be reported later. The screen runs no poll of its own; it holds the shared session.
 */
class RadioViewModel(
    private val savedState: SavedStateHandle,
    private val session: HubSession,
    private val availability: RadioAvailability,
    private val picker: StationPicker,
    private val store: RadioStore,
    /** The phone's region, for the first visit's country (filters, MOB-009). */
    private val region: () -> String,
) : ViewModel() {
    private val initialName = (savedState.get<String>(NAME_KEY) ?: "").let(::cleanField)

    private val _state = MutableStateFlow(RadioUiState(nameText = initialName))
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    private val nameInput = MutableStateFlow(initialName)
    private val filters = MutableStateFlow<RadioFilters?>(null)

    /** The query the list on screen belongs to. */
    private var query: StationQuery? = null

    private var storedRecent: List<Station> = emptyList()

    private var loadedOnce = false
    private var loadedName: String? = null
    private var pageJob: Job? = null
    private var retryJob: Job? = null
    private var retryAction: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            session.state.collect { s ->
                if (s is SessionState.Connected) _state.update { it.copy(connection = s.connection) }
            }
        }
        viewModelScope.launch {
            store.recent.collect { list ->
                storedRecent = list
                _state.update { it.copy(recent = shownRecent(it.nameText)) }
            }
        }
        viewModelScope.launch {
            val stored = store.filters.first()
            if (stored != null) {
                _state.update { it.copy(filters = stored) }
                filters.value = stored
            } else {
                firstVisit()
            }
        }
        viewModelScope.launch {
            // A change of the name waits for the typing to stop; a change of filter or order does not.
            combine(nameInput.map(::stationName), filters.filterNotNull()) { name, f -> name to f }
                .distinctUntilChanged()
                .collectLatest { (name, f) ->
                    if (loadedOnce && name != loadedName) delay(NAME_WAIT_MILLIS)
                    pageJob?.cancel()
                    retryJob?.cancel()
                    loadedOnce = true
                    loadedName = name
                    loadFirst(f.toQuery(name))
                }
        }
    }

    // ---- The name ------------------------------------------------------------------------------

    fun onNameChange(text: String) {
        val clean = cleanField(text)
        savedState[NAME_KEY] = clean
        _state.update { it.copy(nameText = clean, recent = shownRecent(clean)) }
        nameInput.value = clean
    }

    fun onClearName() = onNameChange("")

    /** All recent stations while the name is empty (whatever the filters or the list say); none while one is typed. */
    private fun shownRecent(name: String): List<Station> = if (stationName(name) == null) storedRecent else emptyList()

    /** Forgets one recent station at once; nothing else changes, not even a playback. */
    fun removeRecent(uuid: String) {
        viewModelScope.launch { store.removeRecent(uuid) }
    }

    // ---- The filters ---------------------------------------------------------------------------

    /**
     * No filters stored yet: the country list is asked for first, the country of the phone's region
     * (if the catalogue lists it) is chosen and stored, and only then the stations load. If the list
     * cannot be had nothing is stored, so the next opening is a first visit again.
     */
    private suspend fun firstVisit() {
        retryAction = {
            retryJob?.cancel()
            retryJob = viewModelScope.launch { firstVisit() }
        }
        _state.update { it.copy(list = StationList(phase = ListPhase.LoadingFirst)) }
        request(
            call = { it.radioCountries() },
            onOk = { list ->
                val chosen = RadioFilters(country = regionCountry(region(), list))
                store.saveFilters(chosen)
                _state.update { it.copy(filters = chosen) }
                filters.value = chosen
            },
            onCatalogue = { _state.update { it.copy(list = StationList(phase = ListPhase.Loaded, problem = RadioFailure.Catalogue)) } },
        )
    }

    /** Applies and stores a change; the list reloads from its first page at once. */
    private fun setFilters(next: RadioFilters) {
        if (next == _state.value.filters) return
        _state.update { it.copy(filters = next) }
        filters.value = next
        viewModelScope.launch { store.saveFilters(next) }
    }

    fun onRemoveFilter(kind: FilterSheetState.Kind) {
        val f = _state.value.filters
        setFilters(
            when (kind) {
                FilterSheetState.Kind.Country -> f.copy(country = null)
                FilterSheetState.Kind.Tag -> f.copy(tag = null)
                FilterSheetState.Kind.Language -> f.copy(language = null)
                FilterSheetState.Kind.Order -> f
            },
        )
    }

    /** Removes country, tag and language; keeps the order and the typed name. */
    fun onClearFilters() = setFilters(_state.value.filters.cleared())

    // ---- The sheets ----------------------------------------------------------------------------

    private var countries: List<Country>? = null
    private var languages: List<Language>? = null
    private var tags: List<Tag> = emptyList()
    private var tagNext: Int? = null
    private var tagsFor: String? = null
    private var sheetJob: Job? = null
    private var tagSearchJob: Job? = null
    private var sheetRetry: (() -> Unit)? = null

    private fun updateSheet(change: (FilterSheetState) -> FilterSheetState) {
        _state.update { s -> s.sheet?.let { s.copy(sheet = change(it)) } ?: s }
    }

    fun onOpenSheet(kind: FilterSheetState.Kind) {
        sheetJob?.cancel()
        tagSearchJob?.cancel()
        val cached = when (kind) {
            FilterSheetState.Kind.Country -> countries != null
            FilterSheetState.Kind.Language -> languages != null
            else -> true
        }
        _state.update { it.copy(sheet = FilterSheetState(kind, loading = !cached)) }
        rebuildSheet()
        when (kind) {
            FilterSheetState.Kind.Order -> Unit
            FilterSheetState.Kind.Country -> if (countries == null) loadCountries()
            FilterSheetState.Kind.Language -> if (languages == null) loadLanguages()
            FilterSheetState.Kind.Tag -> loadTagsFirst(null)
        }
    }

    /** Cancel, the scrim and Back: nothing changes. */
    fun onCloseSheet() {
        sheetJob?.cancel()
        tagSearchJob?.cancel()
        _state.update { it.copy(sheet = null) }
    }

    fun onSheetSearch(text: String) {
        val sheet = _state.value.sheet ?: return
        when (sheet.kind) {
            FilterSheetState.Kind.Order -> return
            FilterSheetState.Kind.Tag -> {
                val clean = text.replace("%", "").replace("/", "").take(100)
                updateSheet { it.copy(searchText = clean) }
                tagSearchJob?.cancel()
                tagSearchJob = viewModelScope.launch {
                    delay(NAME_WAIT_MILLIS)
                    loadTagsFirst(tagName(clean))
                }
            }
            else -> {
                updateSheet { it.copy(searchText = text.take(100)) }
                rebuildSheet()
            }
        }
    }

    fun onSheetChoose(row: FilterRow) {
        val sheet = _state.value.sheet ?: return
        val f = _state.value.filters
        val next = when (sheet.kind) {
            FilterSheetState.Kind.Country -> row.code?.let { f.copy(country = Country(it, row.name, row.count ?: 0)) }
            FilterSheetState.Kind.Tag -> f.copy(tag = row.name)
            FilterSheetState.Kind.Language -> f.copy(language = row.name)
            FilterSheetState.Kind.Order -> StationOrder.entries.firstOrNull { it.name == row.code }?.let { f.copy(order = it) }
        }
        onCloseSheet()
        if (next != null) setFilters(next)
    }

    fun retrySheet() {
        sheetRetry?.invoke()
    }

    /** The tag sheet scrolled: [lastVisible] is the index of the last row on screen. */
    fun onSheetListPosition(lastVisible: Int) {
        val sheet = _state.value.sheet ?: return
        if (sheet.kind != FilterSheetState.Kind.Tag || sheet.loading || sheet.loadingMore || sheet.problem != null) return
        if (!canLoadMore(tagNext) || lastVisible < sheet.rows.size - NEAR_END_ROWS) return
        loadTagsNext()
    }

    private fun sheetProblem() = updateSheet { it.copy(loading = false, loadingMore = false, problem = RadioFailure.Catalogue) }

    private fun loadCountries() {
        sheetRetry = { loadCountries() }
        updateSheet { it.copy(loading = true, problem = null) }
        sheetJob = viewModelScope.launch {
            request(
                call = { it.radioCountries() },
                onOk = { list ->
                    countries = list
                    updateSheet { it.copy(loading = false) }
                    rebuildSheet()
                },
                onCatalogue = ::sheetProblem,
            )
        }
    }

    private fun loadLanguages() {
        sheetRetry = { loadLanguages() }
        updateSheet { it.copy(loading = true, problem = null) }
        sheetJob = viewModelScope.launch {
            request(
                call = { it.radioLanguages() },
                onOk = { list ->
                    languages = list
                    updateSheet { it.copy(loading = false) }
                    rebuildSheet()
                },
                onCatalogue = ::sheetProblem,
            )
        }
    }

    private fun loadTagsFirst(name: String?) {
        sheetJob?.cancel()
        tags = emptyList()
        tagNext = null
        tagsFor = name
        sheetRetry = { loadTagsFirst(name) }
        updateSheet { it.copy(loading = true, loadingMore = false, problem = null) }
        rebuildSheet()
        sheetJob = viewModelScope.launch {
            request(
                call = { it.radioTags(name, 0) },
                onOk = { page ->
                    tags = page.tags
                    tagNext = page.nextOffset
                    updateSheet { it.copy(loading = false) }
                    rebuildSheet()
                },
                onCatalogue = ::sheetProblem,
            )
        }
    }

    private fun loadTagsNext() {
        val offset = tagNext ?: return
        val name = tagsFor
        sheetRetry = { loadTagsNext() }
        updateSheet { it.copy(loadingMore = true, problem = null) }
        sheetJob = viewModelScope.launch {
            request(
                call = { it.radioTags(name, offset) },
                onOk = { page: TagPage ->
                    val seen = tags.mapTo(HashSet()) { it.name }
                    tags = tags + page.tags.filter { seen.add(it.name) }
                    tagNext = page.nextOffset
                    updateSheet { it.copy(loadingMore = false) }
                    rebuildSheet()
                },
                onCatalogue = ::sheetProblem,
            )
        }
    }

    /** Works out the rows from what is loaded, the field's text and the chosen filters. */
    private fun rebuildSheet() {
        val sheet = _state.value.sheet ?: return
        val f = _state.value.filters
        val rows = when (sheet.kind) {
            FilterSheetState.Kind.Country -> narrow(countries.orEmpty(), sheet.searchText) { it.name }.map {
                FilterRow(it.name, it.stationCount, null, f.country?.code.equals(it.code, ignoreCase = true), it.code)
            }
            FilterSheetState.Kind.Language -> narrow(languages.orEmpty(), sheet.searchText) { it.name }.map {
                FilterRow(it.name, it.stationCount, null, f.language.equals(it.name, ignoreCase = true))
            }
            FilterSheetState.Kind.Order -> StationOrder.entries.map {
                FilterRow(orderName(it), null, orderDetail(it), it == f.order, it.name)
            }
            FilterSheetState.Kind.Tag -> {
                val chosen = f.tag
                val wanted = tagName(sheet.searchText)
                val first = chosen?.takeIf { wanted == null || it.contains(wanted, ignoreCase = true) }?.let { name ->
                    FilterRow(name, tags.firstOrNull { it.name.equals(name, ignoreCase = true) }?.stationCount, null, true)
                }
                listOfNotNull(first) + tags.filterNot { chosen != null && it.name.equals(chosen, ignoreCase = true) }
                    .map { FilterRow(it.name, it.stationCount, null, false) }
            }
        }
        updateSheet { it.copy(rows = rows) }
    }

    // ---- The list ------------------------------------------------------------------------------

    private suspend fun loadFirst(q: StationQuery) {
        query = q
        retryAction = { retryFirst(q) }
        _state.update { it.copy(list = StationList(phase = ListPhase.LoadingFirst)) }
        request(
            call = { it.searchStations(q, 0) },
            onOk = { page ->
                _state.update { s ->
                    s.copy(list = StationList(mergePage(emptyList(), page), ListPhase.Loaded, page.nextOffset, limitReached(page.nextOffset)))
                }
            },
            onCatalogue = { _state.update { it.copy(list = StationList(phase = ListPhase.Loaded, problem = RadioFailure.Catalogue)) } },
        )
    }

    private fun retryFirst(q: StationQuery) {
        pageJob?.cancel()
        retryJob?.cancel()
        retryJob = viewModelScope.launch { loadFirst(q) }
    }

    /** The screen scrolled: [lastVisible] is the index of the last row on screen. */
    fun onListPosition(lastVisible: Int) {
        val list = _state.value.list
        if (list.phase != ListPhase.Loaded || list.problem != null || !canLoadMore(list.nextOffset)) return
        if (lastVisible < list.stations.size - NEAR_END_ROWS) return
        loadNext()
    }

    private fun loadNext() {
        val q = query ?: return
        retryAction = { loadNext() }
        pageJob?.cancel()
        pageJob = viewModelScope.launch { fetchNext(q) }
    }

    private suspend fun fetchNext(q: StationQuery) {
        while (true) {
            val offset = _state.value.list.nextOffset ?: return
            _state.update { it.copy(list = it.list.copy(phase = ListPhase.LoadingMore, problem = null)) }
            var followAtOnce = false
            request(
                call = { it.searchStations(q, offset) },
                onOk = { page ->
                    if (q != query) return@request
                    val before = _state.value.list
                    val merged = mergePage(before.stations, page)
                    _state.update {
                        it.copy(list = StationList(merged, ListPhase.Loaded, page.nextOffset, limitReached(page.nextOffset)))
                    }
                    // A page of only stations already listed must not stall the list.
                    followAtOnce = merged.size == before.stations.size && canLoadMore(page.nextOffset)
                },
                onCatalogue = {
                    if (q == query) _state.update { it.copy(list = it.list.copy(phase = ListPhase.Loaded, problem = RadioFailure.Catalogue)) }
                },
            )
            if (!followAtOnce) return
        }
    }

    /** Repeats the request that failed: the first page or the next one. */
    fun retry() {
        retryAction?.invoke()
    }

    /**
     * Runs one radio request and says what became of it. An answer for a repository that is no
     * longer the session's is dropped. An unreachable hub is told from a silent catalogue by a
     * fresh refresh, never by the session's last known state (that still reads "live" right after
     * the phone loses its network): if the hub answers, the catalogue is the problem; if not, the
     * request is repeated after the first successful refresh that started after the failure.
     */
    private suspend fun <T> request(
        call: suspend (HubRepository) -> HubResult<T>,
        onOk: suspend (T) -> Unit,
        onCatalogue: () -> Unit,
    ) {
        while (true) {
            val repo = session.repository ?: return
            val result = call(repo)
            currentCoroutineContext().ensureActive()
            when (result) {
                is HubResult.Ok -> {
                    if (session.repository === repo) onOk(result.value)
                    return
                }

                is HubResult.Err -> when (radioFailure(result.error)) {
                    RadioFailure.Catalogue -> {
                        onCatalogue()
                        return
                    }

                    RadioFailure.Gone -> {
                        gone()
                        return
                    }

                    RadioFailure.HubUnreachable -> {
                        if (session.awaitFreshSnapshot() != null) {
                            onCatalogue()
                            return
                        }
                        val fence = session.startedSeq
                        session.state.first { it is SessionState.Connected && it.refreshSeq > fence }
                    }
                }
            }
        }
    }

    // ---- Leaving -------------------------------------------------------------------------------

    fun onChoose(station: Station) {
        picker.pick(station)
        _state.update { it.copy(exit = RadioExit.Chosen) }
    }

    private fun gone() {
        availability.markGone()
        picker.reportGone()
        _state.update { it.copy(exit = RadioExit.Gone) }
    }

    fun consumeExit() = _state.update { it.copy(exit = null) }

    /** Foreground only: the screen calls this while it is visible. */
    fun onVisible() = session.acquire()

    fun onHidden() = session.release()

    private companion object {
        const val NAME_KEY = "name"
        const val NAME_WAIT_MILLIS = 500L
        const val NEAR_END_ROWS = 10

        /** The field drops `%` and stops at 100 characters; the hub never sees either. */
        fun cleanField(text: String): String = text.replace("%", "").take(100)
    }
}
