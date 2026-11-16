package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.domain.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands a result from the radio screen back to Start Playback, which lives on beneath it. Only one
 * Start Playback can be on the back stack, so one pending value of each kind is enough.
 */
class StationPicker {
    private val _picked = MutableStateFlow<Station?>(null)
    val picked: StateFlow<Station?> = _picked.asStateFlow()

    private val _gone = MutableStateFlow(false)

    /** The radio screen found out the hub has no radio extension (any more). */
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    fun pick(station: Station) {
        _picked.value = station
    }

    fun reportGone() {
        _gone.value = true
    }

    fun consumePicked() {
        _picked.value = null
    }

    fun consumeGone() {
        _gone.value = false
    }
}
