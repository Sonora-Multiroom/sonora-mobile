package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.ItemKey
import sonora.multiroom.mobile.domain.ItemKind
import sonora.multiroom.mobile.domain.SettingsBuilder
import sonora.multiroom.mobile.domain.SettingsContent
import sonora.multiroom.mobile.domain.Confirmation
import sonora.multiroom.mobile.domain.ExtensionsContent
import sonora.multiroom.mobile.domain.extensionRows
import sonora.multiroom.mobile.domain.keepsPlaying
import sonora.multiroom.mobile.domain.removeConfirmation
import sonora.multiroom.mobile.domain.turnOffConfirmation
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.Pending
import sonora.multiroom.mobile.ui.session.Phase
import sonora.multiroom.mobile.ui.session.SessionState
import sonora.multiroom.mobile.ui.session.SettingsActions
import sonora.multiroom.mobile.ui.session.SettingsNavigator
import sonora.multiroom.mobile.ui.session.SettingsTab
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Settings screen: the hub row, the tab, the lists of the shared [HubSession] with the
 * switches' overrides merged in, the turn-off dialog and the address sheet. The tab lives in the
 * app-scoped [SettingsNavigator], and requests run in [SettingsActions], so both outlive this view
 * model.
 */
class SettingsViewModel(
    private val session: HubSession,
    private val store: HubAddressStore,
    private val navigator: SettingsNavigator,
    private val actions: SettingsActions,
    private val repositoryFactory: HubRepositoryFactory,
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private data class Local(
        val sheet: SheetState? = null,
        val message: String? = null,
        val confirm: ConfirmState? = null,
    )

    private val local = MutableStateFlow(Local())

    /** The last snapshot built into content, so the build runs once per refresh. */
    private var built: Pair<HubSnapshot, SettingsContent>? = null

    private fun contentOf(snapshot: HubSnapshot): SettingsContent {
        built?.takeIf { it.first === snapshot }?.let { return it.second }
        return SettingsBuilder.build(snapshot, now(), zone()).also { built = snapshot to it }
    }

    /** What Settings hides or marks because of removals: (being removed, already removed). */
    private val removals = combine(actions.removing, actions.removed) { removing, removed -> removing to removed }

    private val visible = MutableStateFlow(false)
    private val extensions = MutableStateFlow<ExtensionsContent?>(null)

    private class Overlay(val removals: Pair<Set<String>, Set<String>>, val extensions: ExtensionsContent?)

    private val overlay = combine(removals, extensions, ::Overlay)

    val state: StateFlow<SettingsUiState> =
        combine(session.state, navigator.tab, local, actions.pending, overlay) { s, tab, local, pending, overlay ->
            SettingsUiState(
                hub = hubRow(s),
                tab = tab,
                body = bodyOf(s, pending, overlay.removals),
                sheet = local.sheet,
                confirm = local.confirm,
                extensions = overlay.extensions,
                message = local.message,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(tab = navigator.tab.value))

    private fun bodyOf(s: SessionState, pending: Map<ItemKey, Pending>, removals: Pair<Set<String>, Set<String>>): SettingsBody = when (s) {
        SessionState.Initial -> SettingsBody.Initial
        SessionState.NoAddress -> SettingsBody.NoAddress
        is SessionState.Connected -> when {
            s.snapshot != null -> {
                val live = s.connection == Connection.Live
                SettingsBody.Lists(
                    lists = listsOf(contentOf(s.snapshot), pending, removals),
                    stale = !live && s.connection is Connection.Unreachable,
                    controlsEnabled = live,
                )
            }

            s.connection is Connection.Unreachable -> SettingsBody.CantReach(s.address.baseUrl)
            else -> SettingsBody.Waiting
        }
    }

    private fun listsOf(content: SettingsContent, pending: Map<ItemKey, Pending>, removals: Pair<Set<String>, Set<String>>): SettingsLists {
        val (removing, removed) = removals
        fun <R> item(kind: ItemKind, id: String, enabled: Boolean, row: R): Item<R> {
            val p = pending[ItemKey(kind, id)]
            return Item(row, shownEnabled = p?.value ?: enabled, inFlight = p?.phase == Phase.InFlight)
        }
        return SettingsLists(
            rooms = content.rooms.map { item(ItemKind.Room, it.id, it.enabled, it) },
            groups = content.groups.map { item(ItemKind.Group, it.id, it.enabled, it) },
            configuredSources = content.configuredSources.map { item(ItemKind.Source, it.id, it.enabled, it) },
            runtimeSources = content.runtimeSources.filter { it.id !in removed }
                .map { Item(it, shownEnabled = it.enabled, inFlight = false, removing = it.id in removing) },
        )
    }

    init {
        viewModelScope.launch {
            actions.messages.collect { text -> local.update { it.copy(message = text) } }
        }
        // Extensions piggyback on the session's refreshes while their tab is on screen (research R8).
        viewModelScope.launch {
            combine(visible, navigator.tab) { v, tab -> v && tab == SettingsTab.Extensions }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) followExtensions() }
        }
        // Another hub has other extensions: forget the old list when the address changes.
        viewModelScope.launch {
            var address: HubAddress? = (session.state.value as? SessionState.Connected)?.address
            session.state.collect { s ->
                val now = (s as? SessionState.Connected)?.address
                if (now != address) {
                    address = now
                    extensions.value = null
                }
            }
        }
        // The open dialog follows the hub: its text is rebuilt per snapshot, and kept when the
        // item no longer plays or is gone (spec Edge Cases).
        viewModelScope.launch {
            session.state.collect { s ->
                val snapshot = (s as? SessionState.Connected)?.snapshot ?: return@collect
                local.update { l ->
                    val open = l.confirm ?: return@update l
                    val fresh = confirmationFor(open.key, snapshot) ?: return@update l
                    l.copy(confirm = open.copy(confirmation = fresh))
                }
            }
        }
    }

    /** Fetches once now, then after every successful refresh, until cancelled. A failure keeps the last list. */
    private suspend fun followExtensions() {
        fun liveSeq(s: SessionState) =
            (s as? SessionState.Connected)?.takeIf { it.connection == Connection.Live }?.refreshSeq
        val start = liveSeq(session.state.value)
        fetchExtensions()
        session.state.mapNotNull(::liveSeq).distinctUntilChanged().filter { it != start }
            .collectLatest { fetchExtensions() }
    }

    private suspend fun fetchExtensions() {
        val repo = session.repository ?: return
        val answer = repo.extensions()
        // An answer from the previous hub must not land after the address changed.
        if (session.repository !== repo) return
        if (answer is HubResult.Ok) extensions.value = extensionRows(answer.value)
    }

    /** Foreground only: the screen calls this when it becomes visible. */
    fun onVisible() {
        visible.value = true
        session.acquire()
        actions.attach()
        if (navigator.openSheetRequested.value) {
            navigator.consumeSheetRequest()
            onHubRowTapped()
        }
    }

    fun onHidden() {
        visible.value = false
        actions.detach()
        session.release()
    }

    fun onTabSelected(tab: SettingsTab) = navigator.select(tab)

    // ---- Switches -------------------------------------------------------------------------

    /** The snapshot, only while the hub answers: stale lists are not controllable. */
    private fun liveSnapshot(): HubSnapshot? {
        val s = session.state.value as? SessionState.Connected ?: return null
        return if (s.connection == Connection.Live) s.snapshot else null
    }

    fun onToggle(key: ItemKey, name: String, value: Boolean) {
        val snapshot = liveSnapshot() ?: return
        if (actions.pending.value[key]?.phase == Phase.InFlight) return
        if (!value) {
            val confirmation = turnOffConfirmation(key, snapshot)
            if (confirmation != null) {
                local.update { it.copy(confirm = ConfirmState(key, name, confirmation)) }
                return
            }
        }
        actions.setEnabled(
            key, name, value,
            keepsPlaying = key.kind == ItemKind.Source && !value && keepsPlaying(key.id, snapshot),
        )
    }

    /** Turning off asks for rooms and groups, removing asks for a runtime source. */
    private fun confirmationFor(key: ItemKey, snapshot: HubSnapshot): Confirmation? =
        if (key.kind == ItemKind.Source) removeConfirmation(key.id, snapshot) else turnOffConfirmation(key, snapshot)

    fun onRemove(sourceId: String, name: String) {
        val snapshot = liveSnapshot() ?: return
        if (sourceId in actions.removing.value) return
        val key = ItemKey(ItemKind.Source, sourceId)
        val confirmation = removeConfirmation(sourceId, snapshot)
        if (confirmation != null) local.update { it.copy(confirm = ConfirmState(key, name, confirmation)) }
        else actions.remove(sourceId, name)
    }

    fun onConfirm() {
        val open = local.value.confirm ?: return
        local.update { it.copy(confirm = null) }
        if (open.confirmation is Confirmation.Remove) actions.remove(open.key.id, open.name)
        else actions.setEnabled(open.key, open.name, false, keepsPlaying = false)
    }

    fun onConfirmCancel() = local.update { it.copy(confirm = null) }

    fun consumeMessage() = local.update { it.copy(message = null) }

    // ---- Address sheet --------------------------------------------------------------------

    fun onHubRowTapped() {
        val saved = (session.state.value as? SessionState.Connected)?.address?.baseUrl.orEmpty()
        local.update { it.copy(sheet = SheetState(draft = saved)) }
    }

    fun onDraftChange(text: String) {
        cancelTest()
        local.update { l -> l.copy(sheet = l.sheet?.copy(draft = text, error = null, test = TestState.Idle)) }
    }

    /** The one connection test; editing, Save, Close and a new test cancel it. */
    private var testJob: Job? = null

    private fun cancelTest() {
        testJob?.cancel()
        testJob = null
    }

    /** Asks the hub at the drafted address how many rooms it has; nothing is saved (FR-007). */
    fun onTest() {
        val sheet = local.value.sheet ?: return
        when (val result = HubAddress.parse(sheet.draft)) {
            is HubAddress.ParseResult.Invalid ->
                local.update { it.copy(sheet = it.sheet?.copy(error = result.message, test = TestState.Idle)) }

            is HubAddress.ParseResult.Valid -> {
                cancelTest()
                local.update { it.copy(sheet = it.sheet?.copy(error = null, test = TestState.Checking)) }
                testJob = viewModelScope.launch {
                    val answer = repositoryFactory.create(result.address).countRooms()
                    val test = if (answer is HubResult.Ok) TestState.Found(answer.value) else TestState.Failed
                    local.update { it.copy(sheet = it.sheet?.copy(test = test)) }
                }
            }
        }
    }

    fun onSave() {
        val sheet = local.value.sheet ?: return
        when (val result = HubAddress.parse(sheet.draft)) {
            is HubAddress.ParseResult.Invalid ->
                local.update { it.copy(sheet = it.sheet?.copy(error = result.message)) }

            is HubAddress.ParseResult.Valid -> {
                cancelTest()
                local.update { it.copy(sheet = null) }
                viewModelScope.launch { store.save(result.address) }
            }
        }
    }

    /** Close, Back or a swipe: the draft is discarded. */
    fun onSheetClosed() {
        cancelTest()
        local.update { it.copy(sheet = null) }
    }
}
