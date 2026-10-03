package sonora.multiroom.mobile

import sonora.multiroom.mobile.data.DataStoreHubAddressStore
import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.KtorHubRepositoryFactory
import sonora.multiroom.mobile.data.createHubHttpClient
import sonora.multiroom.mobile.data.httpEngine
import sonora.multiroom.mobile.ui.rooms.RoomsViewModel
import sonora.multiroom.mobile.ui.settings.SettingsViewModel

/** Manual wiring of the few app-wide objects (no DI library, research R8). */
class AppGraph(
    val addressStore: HubAddressStore,
    val repositoryFactory: HubRepositoryFactory = KtorHubRepositoryFactory(createHubHttpClient(httpEngine().create())),
) {
    /** Production wiring: the platform only supplies where the preferences file lives. */
    constructor(dataStorePath: String) : this(DataStoreHubAddressStore(dataStorePath))

    fun settingsViewModel() = SettingsViewModel(addressStore)

    fun roomsViewModel() = RoomsViewModel(addressStore, repositoryFactory)
}
