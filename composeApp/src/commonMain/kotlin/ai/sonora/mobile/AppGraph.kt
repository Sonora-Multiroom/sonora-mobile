package ai.sonora.mobile

import ai.sonora.mobile.data.DataStoreHubAddressStore
import ai.sonora.mobile.data.HubAddressStore
import ai.sonora.mobile.data.HubRepositoryFactory
import ai.sonora.mobile.data.KtorHubRepositoryFactory
import ai.sonora.mobile.data.createHubHttpClient
import ai.sonora.mobile.data.httpEngine
import ai.sonora.mobile.ui.rooms.RoomsViewModel
import ai.sonora.mobile.ui.settings.SettingsViewModel

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
