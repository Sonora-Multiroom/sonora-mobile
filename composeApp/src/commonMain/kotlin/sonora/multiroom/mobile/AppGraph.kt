package sonora.multiroom.mobile

import sonora.multiroom.mobile.data.DataStoreHubAddressStore
import sonora.multiroom.mobile.data.DataStoreRadioStore
import sonora.multiroom.mobile.data.RadioStore
import sonora.multiroom.mobile.data.createPreferencesDataStore
import sonora.multiroom.mobile.data.HubAddressStore
import sonora.multiroom.mobile.data.HubRepositoryFactory
import sonora.multiroom.mobile.data.KtorHubRepositoryFactory
import sonora.multiroom.mobile.data.createHubHttpClient
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.data.httpEngine
import sonora.multiroom.mobile.ui.nowplaying.NowPlayingViewModel
import sonora.multiroom.mobile.ui.radio.RadioViewModel
import sonora.multiroom.mobile.ui.rooms.RoomsViewModel
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.PlaybackStarter
import sonora.multiroom.mobile.ui.session.RadioAvailability
import sonora.multiroom.mobile.ui.session.RecentStationTracker
import sonora.multiroom.mobile.ui.session.StationPicker
import sonora.multiroom.mobile.ui.session.SettingsActions
import sonora.multiroom.mobile.ui.session.SettingsNavigator
import sonora.multiroom.mobile.ui.startplayback.StartPlaybackViewModel
import sonora.multiroom.mobile.ui.settings.SettingsViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual wiring of the few app-wide objects (no DI library, research R8). */
class AppGraph(
    val addressStore: HubAddressStore,
    val radioStore: RadioStore,
    val repositoryFactory: HubRepositoryFactory = KtorHubRepositoryFactory(createHubHttpClient(httpEngine().create())),
) {
    /** Production wiring: the platform only supplies where the preferences file lives. */
    constructor(dataStorePath: String) : this(createPreferencesDataStore(dataStorePath))

    // One DataStore for the one preferences file, shared by both stores.
    private constructor(dataStore: DataStore<Preferences>) :
        this(DataStoreHubAddressStore(dataStore), DataStoreRadioStore(dataStore))

    /** The one poll loop of the app, shared by every screen (research R1). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val session = HubSession(addressStore, repositoryFactory, appScope)

    /** Whether the hub offers radio; asked when Start Playback opens (MOB-009). */
    val radioAvailability = RadioAvailability(session, appScope)

    /** Watches started stations and adds each to "Recent" after 30 seconds of play. */
    private val recentTracker = RecentStationTracker(appScope, session, radioStore)

    /** Hands the station chosen on the radio screen back to Start Playback. */
    val stationPicker = StationPicker()

    /** One-shot messages that cross screens. */
    val messages = AppMessages()

    /** Runs starts so they outlive their screen (research R9). */
    val starter = PlaybackStarter(appScope, session, messages, recentTracker)

    /** The Settings tab and the sheet request, which outlive the Settings entry (research R2). */
    val settingsNavigator = SettingsNavigator()

    /** Runs Settings' switch changes so they outlive the screen (research R5). */
    val settingsActions = SettingsActions(appScope, session, messages)

    fun settingsViewModel() = SettingsViewModel(session, addressStore, settingsNavigator, settingsActions, repositoryFactory)

    fun nowPlayingViewModel(routeId: String, savedState: SavedStateHandle, startedAfterSeq: Long? = null, targetName: String? = null) =
        NowPlayingViewModel(routeId, savedState, session, messages, startedAfterSeq, targetName)

    fun startPlaybackViewModel(targetId: String?, fixedTarget: Target?, fixedName: String?, savedState: SavedStateHandle) =
        StartPlaybackViewModel(targetId, fixedTarget, fixedName, savedState, session, starter, radioAvailability, stationPicker, radioStore)

    fun radioViewModel(savedState: SavedStateHandle, region: () -> String) =
        RadioViewModel(savedState, session, radioAvailability, stationPicker, radioStore, region)

    fun roomsViewModel() = RoomsViewModel(session, messages)
}
