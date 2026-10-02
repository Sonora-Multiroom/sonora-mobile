package ai.sonora.mobile

import ai.sonora.mobile.data.DataStoreHubAddressStore
import ai.sonora.mobile.data.HubAddressStore
import ai.sonora.mobile.data.HubError
import ai.sonora.mobile.data.HubRepository
import ai.sonora.mobile.data.HubRepositoryFactory
import ai.sonora.mobile.data.HubResult
import ai.sonora.mobile.domain.HubSnapshot
import ai.sonora.mobile.ui.rooms.RoomsViewModel
import ai.sonora.mobile.ui.settings.SettingsViewModel

/** Manual wiring of the few app-wide objects (no DI library, research R8). */
class AppGraph(
    val addressStore: HubAddressStore,
    val repositoryFactory: HubRepositoryFactory = HubRepositoryFactory { StubRepository },
) {
    /** Production wiring: the platform only supplies where the preferences file lives. */
    constructor(dataStorePath: String) : this(DataStoreHubAddressStore(dataStorePath))

    fun settingsViewModel() = SettingsViewModel(addressStore)

    fun roomsViewModel() = RoomsViewModel(addressStore, repositoryFactory)
}

/** Stand-in until the Ktor repository exists: every call fails. */
private object StubRepository : HubRepository {
    private val failure = HubResult.Err(HubError.Unexpected)
    override suspend fun snapshot(): HubResult<HubSnapshot> = failure
    override suspend fun setRoomVolume(roomId: String, volume: Int) = failure
    override suspend fun stopRoute(routeId: String) = failure
    override suspend fun setRoutePaused(routeId: String, paused: Boolean) = failure
    override suspend fun setMasterMute(muted: Boolean) = failure
}
