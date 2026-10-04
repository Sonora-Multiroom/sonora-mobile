package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.ConfiguredSourceRow
import sonora.multiroom.mobile.domain.Confirmation
import sonora.multiroom.mobile.domain.GroupRow
import sonora.multiroom.mobile.domain.ItemKey
import sonora.multiroom.mobile.domain.RoomRow
import sonora.multiroom.mobile.domain.RuntimeSourceRow
import sonora.multiroom.mobile.ui.session.SettingsTab

/** The address sheet's state; null [SettingsUiState.sheet] means it is closed. */
data class SheetState(
    val draft: String,
    val error: String? = null,
)

/**
 * A list row with what only the screen knows: the switch value shown (the user's request while it
 * is pending, else the hub's) and whether a request for it is in flight.
 */
data class Item<R>(
    val row: R,
    val shownEnabled: Boolean,
    val inFlight: Boolean,
    val removing: Boolean = false,
)

data class SettingsLists(
    val rooms: List<Item<RoomRow>>,
    val groups: List<Item<GroupRow>>,
    val configuredSources: List<Item<ConfiguredSourceRow>>,
    /** Without the ids the hub already removed; a row being removed is marked. */
    val runtimeSources: List<Item<RuntimeSourceRow>>,
)

/** The open turn-off or remove dialog: the item it is about and the text's source, rebuilt per snapshot. */
data class ConfirmState(val key: ItemKey, val name: String, val confirmation: Confirmation)

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
    data class Lists(val lists: SettingsLists, val stale: Boolean, val controlsEnabled: Boolean) : SettingsBody
}

data class SettingsUiState(
    val hub: HubRow = HubRow(HubStatus.Hidden, null),
    val tab: SettingsTab = SettingsTab.Rooms,
    val body: SettingsBody = SettingsBody.Initial,
    val sheet: SheetState? = null,
    val confirm: ConfirmState? = null,
    /** One-shot snackbar text. */
    val message: String? = null,
)
