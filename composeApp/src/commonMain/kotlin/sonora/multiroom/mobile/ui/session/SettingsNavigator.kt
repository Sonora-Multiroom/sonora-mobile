package sonora.multiroom.mobile.ui.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SettingsTab { Rooms, Groups, Sources, Extensions }

/**
 * What outlives the Settings entry (research R2): the selected tab, which `selectTab` would
 * otherwise lose with the view model, and a one-shot request to open the address sheet, made by
 * Rooms' and Start Playback's "Set your hub address". Lasts for as long as the process runs.
 */
class SettingsNavigator {
    private val _tab = MutableStateFlow(SettingsTab.Rooms)
    val tab: StateFlow<SettingsTab> = _tab.asStateFlow()

    private val _openSheetRequested = MutableStateFlow(false)
    val openSheetRequested: StateFlow<Boolean> = _openSheetRequested.asStateFlow()

    fun select(tab: SettingsTab) {
        _tab.value = tab
    }

    fun openSheet() {
        _openSheetRequested.value = true
    }

    fun consumeSheetRequest() {
        _openSheetRequested.value = false
    }
}
