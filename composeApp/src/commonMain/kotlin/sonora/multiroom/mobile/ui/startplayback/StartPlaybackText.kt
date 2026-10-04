package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.AffectedPlayback
import sonora.multiroom.mobile.domain.CardStatus
import sonora.multiroom.mobile.domain.ConsequenceLine
import sonora.multiroom.mobile.domain.MuteNote
import sonora.multiroom.mobile.domain.TargetStatus
import sonora.multiroom.mobile.domain.WontPlay
import sonora.multiroom.mobile.domain.joinNames
import sonora.multiroom.mobile.ui.rooms.statusWord

// All Start Playback wording, in one place like `destinationNoteText` (research R4/R5).

/** The target tile's one status line (data-model.md `TargetStatus`). */
internal fun targetStatusText(status: TargetStatus): String = when (status) {
    TargetStatus.Idle -> "Idle"
    is TargetStatus.Playing -> {
        // A live stream reads plain "Playing" here: this screen's list has no "Live stream" state.
        val state = if (status.status == CardStatus.LiveStream) "Playing" else statusWord(status.status)
        "$state · ${status.sources.joinToString(" + ")}"
    }
    is TargetStatus.InGroup -> "In ${status.group}"
    is TargetStatus.GroupPlaying -> "Group · ${status.sources.joinToString(" + ")}"
    is TargetStatus.GroupMembers -> "Group · ${status.rooms.joinToString(" + ")}"
    TargetStatus.TurnedOff -> "Turned off"
    TargetStatus.NotConnected -> "Not connected"
    TargetStatus.NoRooms -> "No rooms"
}

internal fun playLabelText(label: PlayLabel): String = when (label) {
    PlayLabel.Play -> "Play"
    is PlayLabel.PlaySource -> "Play ${label.source} in ${label.target}"
    is PlayLabel.PlayLink -> "Play link in ${label.target}"
    PlayLabel.Starting -> "Starting…"
}

/**
 * "A <verb> in X, B in Y and C in Z": the verb appears once, after the first playback, so several
 * playbacks read as one sentence ("Jazz24 will stop in Office and Morning playlist in Kitchen").
 */
private fun listed(items: List<AffectedPlayback>, verb: String): String =
    joinNames(items.mapIndexed { i, p -> if (i == 0) "${p.source} $verb in ${p.where}" else "${p.source} in ${p.where}" })

internal fun consequenceLineText(line: ConsequenceLine): String = when (line) {
    is ConsequenceLine.WillStop -> listed(line.items, "will stop")
    is ConsequenceLine.PlaysAlongside -> "Plays alongside " + joinNames(line.items.map { "${it.source} in ${it.where}" })
    is ConsequenceLine.WillBeLowered -> listed(line.items, "will be lowered") + " while it plays"
    is ConsequenceLine.AlreadyPlaying -> "${line.source} is already playing in ${line.target}"
}

internal fun wontPlayText(note: WontPlay): String {
    val clauses = buildList {
        if (note.turnedOff.isNotEmpty()) add("${joinNames(note.turnedOff)} (turned off)")
        if (note.notConnected.isNotEmpty()) add("${joinNames(note.notConnected)} (not connected)")
    }
    return "Won't play in " + clauses.joinToString(", ")
}

internal fun muteNoteText(note: MuteNote): String = when (note) {
    MuteNote.AllRoomsMuted -> "All rooms are muted"
    is MuteNote.TargetMuted -> "${note.name} is muted"
}
