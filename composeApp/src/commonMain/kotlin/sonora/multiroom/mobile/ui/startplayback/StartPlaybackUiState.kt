package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.Consequence
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.StartPlaybackContent
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.session.Connection

/** What the Play button says (FR-012). */
sealed interface PlayLabel {
    data object Play : PlayLabel
    data class PlaySource(val source: String, val target: String) : PlayLabel
    data class PlayLink(val target: String) : PlayLabel
    data object Starting : PlayLabel
}

/** How the screen is over; the screen consumes it and the navigation acts on it. */
sealed interface StartExit {
    data object Closed : StartExit

    /** Replace this screen with Now Playing for [routeId] (FR-015). */
    data class Started(val routeId: String, val startedAfterSeq: Long, val targetName: String) : StartExit
}

data class StartPlaybackUiState(
    /** Null: no hub address saved. */
    val address: HubAddress? = null,
    val connection: Connection = Connection.Loading,
    /** Null before the first snapshot. */
    val content: StartPlaybackContent? = null,
    val selectedSourceId: String? = null,
    val selectedTarget: Target? = null,
    /** Only while something to play and a target are both selected. */
    val consequence: Consequence? = null,
    val starting: Boolean = false,
    val playLabel: PlayLabel = PlayLabel.Play,
    val playEnabled: Boolean = false,
    /** One-shot failure text (FR-016). */
    val message: String? = null,
    val exit: StartExit? = null,
)
