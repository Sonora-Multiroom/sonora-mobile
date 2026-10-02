package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.data.HubAddressStore
import ai.sonora.mobile.data.HubRepository
import ai.sonora.mobile.data.HubRepositoryFactory
import ai.sonora.mobile.domain.HubAddress
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoomsViewModel(
    private val addressStore: HubAddressStore,
    private val repositoryFactory: HubRepositoryFactory,
) : ViewModel() {
    private val _state = MutableStateFlow<RoomsUiState>(RoomsUiState.Initial)
    val state: StateFlow<RoomsUiState> = _state.asStateFlow()

    private var repository: HubRepository? = null

    init {
        viewModelScope.launch {
            addressStore.address.collect(::onAddress)
        }
    }

    private fun onAddress(address: HubAddress?) {
        repository = address?.let(repositoryFactory::create)
        _state.value = if (address == null) RoomsUiState.NoAddress else RoomsUiState.Connected(address)
    }

    // Polling arrives with US2 (T035).
    fun startPolling() {}

    fun stopPolling() {}
}
