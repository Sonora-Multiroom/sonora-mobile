package sonora.multiroom.mobile

import sonora.multiroom.mobile.data.DataStoreHubAddressStore
import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.KtorHubRepositoryFactory
import sonora.multiroom.mobile.data.createHubHttpClient
import sonora.multiroom.mobile.data.httpEngine
import sonora.multiroom.mobile.ui.rooms.RoomsViewModel
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual wiring of the few app-wide objects (no DI library, research R8). */
class AppGraph(
    val addressStore: HubAddressStore,
    val repositoryFactory: HubRepositoryFactory = KtorHubRepositoryFactory(createHubHttpClient(httpEngine().create())),
) {
    /** Production wiring: the platform only supplies where the preferences file lives. */
    constructor(dataStorePath: String) : this(DataStoreHubAddressStore(dataStorePath))

    /** The one poll loop of the app, shared by every screen (research R1). */
    val session = HubSession(addressStore, repositoryFactory, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))

    /** One-shot messages that cross screens. */
    val messages = AppMessages()

    fun settingsViewModel() = SettingsViewModel(addressStore)

    fun roomsViewModel() = RoomsViewModel(session, messages)
}
