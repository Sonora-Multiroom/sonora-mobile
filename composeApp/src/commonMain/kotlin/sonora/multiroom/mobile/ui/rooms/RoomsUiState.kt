package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.domain.CardAction
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.RoomsContent

sealed interface Connection {
    /** No answer yet. */
    data object Loading : Connection

    data object Live : Connection

    /** The last refresh failed; [lastSuccessAt] is epoch millis of the last good one, if any. */
    data class Unreachable(val lastSuccessAt: Long?) : Connection
}

/** A request the user started that is still running: its control stays disabled (FR-019). */
sealed interface ActionKey {
    data class Card(val cardKey: String, val action: CardAction) : ActionKey
    data object MasterMute : ActionKey
}

sealed interface RoomsUiState {
    /** Before the saved address has been read; draws nothing so "no hub" doesn't flash. */
    data object Initial : RoomsUiState

    /** No hub address saved: no request is ever made (FR-003). */
    data object NoAddress : RoomsUiState

    data class Connected(
        val address: HubAddress,
        val connection: Connection = Connection.Loading,
        /** Last successful build; null before the first success. */
        val content: RoomsContent? = null,
        /** Volume the user is dragging to, per card key: wins over refreshed values. */
        val volumeOverrides: Map<String, Int> = emptyMap(),
        val inFlight: Set<ActionKey> = emptySet(),
        /** One-shot snackbar text. */
        val message: String? = null,
    ) : RoomsUiState
}
