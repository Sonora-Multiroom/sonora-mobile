package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.SessionState

/**
 * Every Settings string that is not an error (errors are in `ui/Messages.kt`), verbatim from the
 * UI contract "Copy" table. Rows are made from domain values, so the wording is testable here.
 */
enum class HubStatus { Hidden, NotSet, Connecting, Connected, NotConnected }

/** The hub row: [address] is the normalised base URL, null when none is saved. */
data class HubRow(val status: HubStatus, val address: String?)

fun hubRow(state: SessionState): HubRow = when (state) {
    SessionState.Initial -> HubRow(HubStatus.Hidden, null)
    SessionState.NoAddress -> HubRow(HubStatus.NotSet, null)
    is SessionState.Connected -> HubRow(
        status = when (state.connection) {
            Connection.Loading -> HubStatus.Connecting
            Connection.Live -> HubStatus.Connected
            is Connection.Unreachable -> HubStatus.NotConnected
        },
        address = state.address.baseUrl,
    )
}

fun hubStatusText(status: HubStatus): String? = when (status) {
    HubStatus.Hidden -> null
    HubStatus.NotSet -> "Not set"
    HubStatus.Connecting -> "Connecting…"
    HubStatus.Connected -> "Connected"
    HubStatus.NotConnected -> "Not connected"
}

fun hubAddressLine(row: HubRow): String = row.address ?: "Set the hub address to start"

/** The row is one button: its label carries status and address (FR-023). */
fun hubRowLabel(row: HubRow): String {
    val status = hubStatusText(row.status) ?: return "Hub connection. Change address"
    return if (row.address == null) "Hub connection: $status. Change address"
    else "Hub connection: $status, ${row.address}. Change address"
}
