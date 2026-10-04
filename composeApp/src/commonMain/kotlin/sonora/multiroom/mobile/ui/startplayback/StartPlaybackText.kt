package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.CardStatus
import sonora.multiroom.mobile.domain.TargetStatus
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
