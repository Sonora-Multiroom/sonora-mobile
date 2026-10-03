package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.MoveDestinationNote
import sonora.multiroom.mobile.domain.joinNames

/** The Move sheet's note copy, written here and only here (research R8). */
fun destinationNoteText(note: MoveDestinationNote): String = when (note) {
    MoveDestinationNote.Idle -> "Idle"
    is MoveDestinationNote.WillStop ->
        "${joinNames(note.sources)} will stop" + suffix(note.turnedOff, note.notConnected)
    is MoveDestinationNote.WillStopOnGroup -> "${note.source} will stop on ${note.group}"
    is MoveDestinationNote.OthersStop ->
        joinNames(note.rooms) + if (note.rooms.size == 1) " stops" else " stop"
    is MoveDestinationNote.Members ->
        note.rooms.joinToString(" + ") + suffix(note.turnedOff, note.notConnected)
    MoveDestinationNote.TurnedOff -> "Turned off"
    MoveDestinationNote.NotConnected -> "Not connected"
    MoveDestinationNote.NoRooms -> "No rooms"
}

private fun suffix(turnedOff: List<String>, notConnected: List<String>): String =
    (if (turnedOff.isNotEmpty()) " · ${joinNames(turnedOff)} turned off" else "") +
        (if (notConnected.isNotEmpty()) " · ${joinNames(notConnected)} not connected" else "")
