package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.SessionState
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

/**
 * The Settings screen: the hub row, the tab, the lists of the shared [HubSession] and the address
 * sheet. The tab lives in the app-scoped [SettingsNavigator], so it outlives this view model.
 */
class SettingsViewModel(
    private val session: HubSession,
    private val store: HubAddressStore,
    private val navigator: SettingsNavigator,
) : ViewModel() {
    private class Local(val sheet: SheetState? = null, val message: String? = null)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<SettingsUiState> = combine(session.state, navigator.tab, local) { s, tab, local ->
        SettingsUiState(
            hub = hubRow(s),
            tab = tab,
            body = bodyOf(s),
            sheet = local.sheet,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(tab = navigator.tab.value))

    private fun bodyOf(s: SessionState): SettingsBody = when (s) {
        SessionState.Initial -> SettingsBody.Initial
        SessionState.NoAddress -> SettingsBody.NoAddress
        is SessionState.Connected -> when {
            s.snapshot != null -> {
                val live = s.connection == Connection.Live
                SettingsBody.Lists(stale = !live && s.connection is Connection.Unreachable, controlsEnabled = live)
            }
            s.connection is Connection.Unreachable -> SettingsBody.CantReach(s.address.baseUrl)
            else -> SettingsBody.Waiting
        }
    }

    /** Foreground only: the screen calls this when it becomes visible. */
    fun onVisible() {
        session.acquire()
        if (navigator.openSheetRequested.value) {
            navigator.consumeSheetRequest()
            onHubRowTapped()
        }
    }

    fun onHidden() = session.release()

    fun onTabSelected(tab: SettingsTab) = navigator.select(tab)

    // ---- Address sheet --------------------------------------------------------------------

    fun onHubRowTapped() {
        val saved = (session.state.value as? SessionState.Connected)?.address?.baseUrl.orEmpty()
        local.update { Local(SheetState(draft = saved), it.message) }
    }

    fun onDraftChange(text: String) = local.update { l ->
        Local(l.sheet?.copy(draft = text, error = null), l.message)
    }

    fun onSave() {
        val sheet = local.value.sheet ?: return
        when (val result = HubAddress.parse(sheet.draft)) {
            is HubAddress.ParseResult.Invalid ->
                local.update { Local(it.sheet?.copy(error = result.message), it.message) }

            is HubAddress.ParseResult.Valid -> {
                local.update { Local(null, it.message) }
                viewModelScope.launch { store.save(result.address) }
            }
        }
    }

    /** Close, Back or a swipe: the draft is discarded. */
    fun onSheetClosed() = local.update { Local(null, it.message) }

    fun consumeMessage() = local.update { Local(it.sheet, null) }
}
