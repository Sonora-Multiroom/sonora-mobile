package sonora.multiroom.mobile.ui.session

sealed interface Connection {
    /** No answer yet. */
    data object Loading : Connection

    data object Live : Connection

    /** The last refresh failed; [lastSuccessAt] is epoch millis of the last good one, if any. */
    data class Unreachable(val lastSuccessAt: Long?) : Connection
}
