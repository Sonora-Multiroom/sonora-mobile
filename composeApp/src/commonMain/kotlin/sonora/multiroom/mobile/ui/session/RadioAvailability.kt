package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.radioOffered
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class RadioOffer {
    /** Not asked yet, or the hub address changed: radio shows nothing. */
    Unknown,
    Offered,
    NotOffered,
}

/**
 * Whether the hub offers radio (MOB-009): the extension `radio` is listed as active. Asked when
 * Start Playback opens, never on the 2.5 s poll. `Unknown` and `NotOffered` look the same on
 * screen: nothing.
 */
class RadioAvailability(private val session: HubSession, scope: CoroutineScope) {
    private val _state = MutableStateFlow(RadioOffer.Unknown)
    val state: StateFlow<RadioOffer> = _state.asStateFlow()

    init {
        // A different hub may offer something else, so forget what the last one said.
        scope.launch {
            session.state
                .map { (it as? SessionState.Connected)?.address }
                .distinctUntilChanged()
                .collect { _state.value = RadioOffer.Unknown }
        }
    }

    /**
     * Asks the hub for its extensions. An answer for a repository that is no longer the session's
     * is dropped. A readable inventory decides; an error answer turns `Unknown` into `NotOffered`
     * (a hub that cannot list its extensions is not shown to offer radio) but never takes away a
     * confirmed `Offered`; an unreachable hub changes nothing. Returns false only when the hub
     * could not be reached, so the caller may ask once more later.
     */
    suspend fun refresh(): Boolean {
        val repo = session.repository ?: return true
        val result = repo.extensions()
        if (session.repository !== repo) return true
        when (result) {
            is HubResult.Ok ->
                _state.value = if (radioOffered(result.value)) RadioOffer.Offered else RadioOffer.NotOffered

            is HubResult.Err -> if (result.error != HubError.Unreachable && _state.value == RadioOffer.Unknown) {
                _state.value = RadioOffer.NotOffered
            }
        }
        return !(result is HubResult.Err && result.error == HubError.Unreachable)
    }

    /** A radio request showed that the extension is not there. */
    fun markGone() {
        _state.value = RadioOffer.NotOffered
    }
}
