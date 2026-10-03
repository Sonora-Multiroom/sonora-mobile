package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.MoveSheetContent
import sonora.multiroom.mobile.domain.NowPlayingContent
import sonora.multiroom.mobile.domain.PillModel
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.session.Connection

/** A request the user started that is still running: its control stays disabled (FR-013). */
enum class NowPlayingAction { Stop, PauseResume, Mute, Move }

/** One-shot: why the screen should close. */
sealed interface Exit {
    /** The user stopped it here: no message. */
    data object Stopped : Exit

    /** It ended elsewhere (or the stop found it already gone); Rooms shows "Playback on … ended". */
    data class Ended(val targetName: String?) : Exit
}

data class MoveSheetState(val content: MoveSheetContent, val selected: Target?)

data class NowPlayingUiState(
    val address: HubAddress? = null,
    val connection: Connection = Connection.Loading,
    /** The last playback seen; null until the first snapshot. */
    val content: NowPlayingContent.Playback? = null,
    /** Per room id: what the user dragged to and the hub has not confirmed yet. */
    val pending: Map<String, Int> = emptyMap(),
    val inFlight: Set<NowPlayingAction> = emptySet(),
    /** Null = closed. */
    val sheet: MoveSheetState? = null,
    /** One-shot snackbar text. */
    val message: String? = null,
    val exit: Exit? = null,
) {
    /** The hub could not be reached: content is the last known state and nothing is controllable. */
    val stale: Boolean get() = connection is Connection.Unreachable

    /** Controls act only while the hub answers (FR-003). */
    val controlsEnabled: Boolean get() = connection == Connection.Live
}

/** What a pill shows: its loudest room, the pending value else the hub's (research R5). */
fun shownVolume(pill: PillModel, pending: Map<String, Int>): Int =
    pill.roomVolumes.maxOfOrNull { (room, hub) -> pending[room] ?: hub } ?: 0
