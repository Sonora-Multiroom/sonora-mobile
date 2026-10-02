package ai.sonora.mobile.ui.settings

import ai.sonora.mobile.data.HubAddressStore
import ai.sonora.mobile.domain.HubAddress
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val text: String = "",
    val savedAddress: HubAddress? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

class SettingsViewModel(private val store: HubAddressStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = store.address.first()
            // Don't overwrite what the user already started typing.
            _state.update { current ->
                if (current.text.isEmpty()) current.copy(text = saved?.baseUrl.orEmpty(), savedAddress = saved)
                else current.copy(savedAddress = saved)
            }
        }
    }

    fun onTextChange(text: String) {
        _state.update { it.copy(text = text, error = null, saved = false) }
    }

    fun onSave() {
        when (val result = HubAddress.parse(_state.value.text)) {
            is HubAddress.ParseResult.Invalid ->
                _state.update { it.copy(error = result.message, saved = false) }

            is HubAddress.ParseResult.Valid -> viewModelScope.launch {
                store.save(result.address)
                _state.update {
                    it.copy(text = result.address.baseUrl, savedAddress = result.address, error = null, saved = true)
                }
            }
        }
    }
}
