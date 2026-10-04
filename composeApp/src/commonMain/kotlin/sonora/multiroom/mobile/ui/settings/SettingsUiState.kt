package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.session.SettingsTab

/** The address sheet's state; null [SettingsUiState.sheet] means it is closed. */
data class SheetState(
    val draft: String,
    val error: String? = null,
)

/** What the lists below the tab bar show, from the session's state. */
sealed interface SettingsBody {
    /** Before the saved address has been read. */
    data object Initial : SettingsBody
    data object NoAddress : SettingsBody

    /** Connected, no snapshot yet. */
    data object Waiting : SettingsBody

    /** Unreachable and nothing to show. */
    data class CantReach(val address: String) : SettingsBody

    /** [stale]: the hub stopped answering, the lists are the last known ones and their controls are off. */
    data class Lists(val stale: Boolean, val controlsEnabled: Boolean) : SettingsBody
}

data class SettingsUiState(
    val hub: HubRow = HubRow(HubStatus.Hidden, null),
    val tab: SettingsTab = SettingsTab.Rooms,
    val body: SettingsBody = SettingsBody.Initial,
    val sheet: SheetState? = null,
    /** One-shot snackbar text. */
    val message: String? = null,
)
