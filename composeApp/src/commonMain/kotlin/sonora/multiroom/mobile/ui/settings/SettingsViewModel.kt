package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.ItemKey
import sonora.multiroom.mobile.domain.ItemKind
import sonora.multiroom.mobile.domain.SettingsBuilder
import sonora.multiroom.mobile.domain.SettingsContent
import sonora.multiroom.mobile.domain.Confirmation
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    val state: StateFlow<SettingsUiState> =
        combine(session.state, navigator.tab, local, actions.pending, removals) { s, tab, local, pending, removals ->
            SettingsUiState(
                hub = hubRow(s),
                tab = tab,
                body = bodyOf(s, pending, removals),
                sheet = local.sheet,
                confirm = local.confirm,
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

    /** Foreground only: the screen calls this when it becomes visible. */
    fun onVisible() {
        session.acquire()
        actions.attach()
        if (navigator.openSheetRequested.value) {
            navigator.consumeSheetRequest()
            onHubRowTapped()
        }
    }

    fun onHidden() {
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

    fun onDraftChange(text: String) = local.update { l ->
        l.copy(sheet = l.sheet?.copy(draft = text, error = null))
    }

    fun onSave() {
        val sheet = local.value.sheet ?: return
        when (val result = HubAddress.parse(sheet.draft)) {
            is HubAddress.ParseResult.Invalid ->
                local.update { it.copy(sheet = it.sheet?.copy(error = result.message)) }

            is HubAddress.ParseResult.Valid -> {
                local.update { it.copy(sheet = null) }
                viewModelScope.launch { store.save(result.address) }
            }
        }
    }

    /** Close, Back or a swipe: the draft is discarded. */
    fun onSheetClosed() = local.update { it.copy(sheet = null) }
}
