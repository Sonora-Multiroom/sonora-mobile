package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.ConfiguredSourceRow
import sonora.multiroom.mobile.domain.Confirmation
import sonora.multiroom.mobile.domain.RoomStatus
import sonora.multiroom.mobile.domain.joinNames
import sonora.multiroom.mobile.domain.kindLabel
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

// ---- Rows and dialogs -------------------------------------------------------------------------

fun roomStatusText(status: RoomStatus): String = when (status) {
    RoomStatus.Off -> "Off"
    RoomStatus.NotConnected -> "Not connected"
    RoomStatus.Speaker -> "Speaker"
    is RoomStatus.Playing -> "Playing · " + status.sources.joinToString(" + ")
    is RoomStatus.InGroups -> "In " + status.groups.joinToString(", ")
}

fun groupMembersText(members: List<String>): String =
    if (members.isEmpty()) "No rooms" else members.joinToString(", ")

/** The amber line under a group's members; null when nothing plays. */
fun groupPlayingText(sources: List<String>): String? =
    if (sources.isEmpty()) null else "Playing · " + sources.joinToString(" + ")

fun configuredLine(row: ConfiguredSourceRow): String =
    row.detail?.let { "${kindLabel(row.kind)} · $it" } ?: kindLabel(row.kind)

const val CONFIGURED_EMPTY = "No sources in the hub's configuration."

fun confirmTitle(c: Confirmation): String = when (c) {
    is Confirmation.TurnOffRoom -> "Turn off ${c.room}?"
    is Confirmation.TurnOffGroup -> "Turn off ${c.group}?"
}

/** "is playing" stays singular for several sources, as the spec writes it. */
fun confirmBody(c: Confirmation): String = when (c) {
    is Confirmation.TurnOffRoom ->
        "${joinNames(c.sources)} is playing in ${c.room}. Turning the room off stops playback there."

    is Confirmation.TurnOffGroup ->
        "${joinNames(c.sources)} is playing on ${c.members.joinToString(", ")}. " +
            "Turning the group off stops playback in all of these rooms."
}

fun keepsPlayingMessage(name: String): String = "$name is off. What's playing from it keeps playing."
