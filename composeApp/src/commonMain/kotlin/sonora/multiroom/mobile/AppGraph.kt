package sonora.multiroom.mobile

import sonora.multiroom.mobile.data.DataStoreHubAddressStore
import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.KtorHubRepositoryFactory
import sonora.multiroom.mobile.data.createHubHttpClient
import sonora.multiroom.mobile.data.httpEngine
import sonora.multiroom.mobile.ui.nowplaying.NowPlayingViewModel
import sonora.multiroom.mobile.ui.rooms.RoomsViewModel
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.PlaybackStarter
import sonora.multiroom.mobile.ui.session.SettingsActions
import sonora.multiroom.mobile.ui.session.SettingsNavigator
import sonora.multiroom.mobile.ui.startplayback.StartPlaybackViewModel
import sonora.multiroom.mobile.ui.settings.SettingsViewModel
import androidx.lifecycle.SavedStateHandle
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
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val session = HubSession(addressStore, repositoryFactory, appScope)

    /** One-shot messages that cross screens. */
    val messages = AppMessages()

    /** Runs starts so they outlive their screen (research R9). */
    val starter = PlaybackStarter(appScope, session, messages)

    /** The Settings tab and the sheet request, which outlive the Settings entry (research R2). */
    val settingsNavigator = SettingsNavigator()

    /** Runs Settings' switch changes so they outlive the screen (research R5). */
    val settingsActions = SettingsActions(appScope, session, messages)

    fun settingsViewModel() = SettingsViewModel(session, addressStore, settingsNavigator, settingsActions, repositoryFactory)

    fun nowPlayingViewModel(routeId: String, savedState: SavedStateHandle, startedAfterSeq: Long? = null, targetName: String? = null) =
        NowPlayingViewModel(routeId, savedState, session, messages, startedAfterSeq, targetName)

    fun startPlaybackViewModel(targetId: String?, savedState: SavedStateHandle) =
        StartPlaybackViewModel(targetId, savedState, session, starter)

    fun roomsViewModel() = RoomsViewModel(session, messages)
}
