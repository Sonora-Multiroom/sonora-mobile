package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.MoveDestinations
import sonora.multiroom.mobile.domain.NowPlayingBuilder
import sonora.multiroom.mobile.domain.NowPlayingContent
import sonora.multiroom.mobile.domain.PillModel
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.UserAction
import sonora.multiroom.mobile.ui.actionErrorMessage
import sonora.multiroom.mobile.ui.session.AppMessages
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.SessionState
import sonora.multiroom.mobile.ui.session.VolumeDragController
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One playback in detail. It follows [followedRouteId] (which changes when the hub answers a move
 * with a new route id), derives everything from the shared [HubSession]'s snapshot, and decides
 * when the screen is over ([NowPlayingUiState.exit]) without ever touching the back stack.
 */
class NowPlayingViewModel(
    routeId: String,
    private val savedState: SavedStateHandle,
    private val session: HubSession,
    private val messages: AppMessages,
) : ViewModel() {
    private val _state = MutableStateFlow(NowPlayingUiState())
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    /**
     * The route being shown. Kept in the saved state so rotation and process death keep following
     * a moved playback instead of claiming it ended (research R3).
     */
    private var followedRouteId: String
        get() = savedState.get<String>(FOLLOWED_KEY) ?: initialRouteId
        set(value) {
            savedState[FOLLOWED_KEY] = value
        }

    private val initialRouteId = routeId

    /**
     * After a move: a missing route is not "ended" until a refresh that *started after* the move
     * answered has arrived (research R1/R3). In memory only: after a restore a fresh snapshot decides.
     */
    private var fenceSeq: Long? = null

    private var lastTargetName: String? = null

    /**
     * Set once the screen is over (stopped here or ended elsewhere). Unlike [NowPlayingUiState.exit],
     * which the screen consumes, it stays set while the popped entry lives through its exit animation,
     * so a late refresh lacking the route cannot announce "ended" again.
     */
    private var finished = false
    private var address: sonora.multiroom.mobile.domain.HubAddress? = null

    private val volume = VolumeDragController(
        scope = viewModelScope,
        session = session,
        onError = { name, error -> reportFailure(UserAction.Volume, name, error) },
    )

    init {
        if (savedState.get<String>(FOLLOWED_KEY) == null) followedRouteId = routeId
        viewModelScope.launch { volume.pending.collect { p -> _state.update { it.copy(pending = p) } } }
        viewModelScope.launch { session.state.collect(::onSession) }
    }

    // ---- Session -------------------------------------------------------------------------------

    private fun onSession(s: SessionState) {
        when (s) {
            SessionState.Initial, SessionState.NoAddress -> Unit
            is SessionState.Connected -> {
                if (s.address != address) {
                    address = s.address
                    volume.clear()
                }
                _state.update { it.copy(address = s.address, connection = s.connection) }
                val snapshot = s.snapshot ?: return
                volume.onRefresh(s.refreshSeq, snapshot.rooms.map { it.id }.toSet())
                when (val content = NowPlayingBuilder.build(snapshot, followedRouteId)) {
                    is NowPlayingContent.Playback -> onPlayback(content, s)
                    NowPlayingContent.Gone -> onGone(s.refreshSeq)
                }
            }
        }
    }

    private fun onPlayback(content: NowPlayingContent.Playback, s: SessionState.Connected) {
        lastTargetName = content.target.name
        _state.update { current ->
            val sheet = current.sheet?.let { sheet ->
                if (!content.moveVisible) return@let null
                val rebuilt = MoveDestinations.build(s.snapshot!!, followedRouteId) ?: return@let null
                // A destination that became unselectable or vanished loses the selection.
                val selected = sheet.selected?.takeIf { t -> rebuilt.rooms.plus(rebuilt.groups).any { it.target == t && it.selectable } }
                MoveSheetState(rebuilt, selected)
            }
            current.copy(content = content, sheet = sheet)
        }
    }

    private fun onGone(refreshSeq: Long) {
        val current = _state.value
        // The answer to a Stop or a Move decides, never a refresh that merely lacks the route.
        if (NowPlayingAction.Stop in current.inFlight || NowPlayingAction.Move in current.inFlight) return
        if (fenceSeq?.let { refreshSeq <= it } == true) return
        end(lastTargetName)
    }

    /** It ended elsewhere: leave and tell Rooms (FR-002). */
    private fun end(targetName: String?) {
        if (finished) return
        finished = true
        messages.post(if (targetName != null) "Playback on $targetName ended" else "Playback ended")
        _state.update { it.copy(exit = Exit.Ended(targetName), sheet = null) }
    }

    /** Foreground only: the screen calls this while it is visible (FR-003). */
    fun onVisible() = session.acquire()

    fun onHidden() = session.release()

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeExit() = _state.update { it.copy(exit = null) }

    // ---- Helpers -------------------------------------------------------------------------------

    /** The playback, only while the hub answers and nothing else has ended the screen. */
    private fun liveContent(): NowPlayingContent.Playback? {
        val s = _state.value
        return if (s.controlsEnabled) s.content else null
    }

    private fun reportFailure(action: UserAction, targetName: String, error: HubError) =
        _state.update { it.copy(message = actionErrorMessage(action, targetName, error)) }

    /** Marks [action] in flight (its control is disabled, FR-013); false if it already was. */
    private fun startAction(action: NowPlayingAction): Boolean {
        var started = false
        _state.update { if (action in it.inFlight) it else it.copy(inFlight = it.inFlight + action).also { started = true } }
        return started
    }

    private fun finishAction(action: NowPlayingAction) = _state.update { it.copy(inFlight = it.inFlight - action) }

    // ---- Stop, Pause / Resume ------------------------------------------------------------------

    fun onStop() {
        val content = liveContent() ?: return
        val repo = session.repository ?: return
        if (!startAction(NowPlayingAction.Stop)) return
        val id = followedRouteId
        viewModelScope.launch {
            val result = repo.stopRoute(id)
            finishAction(NowPlayingAction.Stop)
            when {
                result is HubResult.Ok -> {
                    finished = true
                    _state.update { it.copy(exit = Exit.Stopped) }
                }
                // Already gone: it ended elsewhere, so the usual notice rather than an error.
                result is HubResult.Err && result.error is HubError.Rejected && result.error.status == 404 ->
                    end(content.target.name)
                result is HubResult.Err -> reportFailure(UserAction.Stop, content.target.name, result.error)
            }
            session.requestRefresh()
        }
    }

    fun onPauseResume() {
        val content = liveContent() ?: return
        if (!content.pauseVisible || !content.pauseEnabled) return
        val repo = session.repository ?: return
        if (!startAction(NowPlayingAction.PauseResume)) return
        val id = followedRouteId
        val pause = !content.paused
        viewModelScope.launch {
            val result = repo.setRoutePaused(id, pause)
            finishAction(NowPlayingAction.PauseResume)
            if (result is HubResult.Err) {
                reportFailure(if (pause) UserAction.Pause else UserAction.Resume, content.target.name, result.error)
            }
            session.requestRefresh()
        }
    }

    // ---- Volume and mute -----------------------------------------------------------------------

    private fun pill(key: String): PillModel? {
        val section = liveContent()?.volume ?: return null
        return if (key == section.main.key) section.main else section.members.firstOrNull { it.key == key }
    }

    fun onVolumeDragStart(pillKey: String) {
        val pill = pill(pillKey) ?: return
        if (pill.muted || pill.roomVolumes.isEmpty()) return
        // A release the hub has not confirmed yet is the base of the next drag.
        val pending = volume.pending.value
        volume.start(pillKey, pill.label, pill.roomVolumes.mapValues { (room, v) -> pending[room] ?: v })
    }

    fun onVolumeDrag(pillKey: String, value: Int) = volume.drag(pillKey, value)

    fun onVolumeDragEnd(pillKey: String, value: Int) = volume.end(pillKey, value)

    fun onMuteToggle() {
        val section = liveContent()?.volume ?: return
        val mute = section.mute
        if (!mute.enabled) return
        val repo = session.repository ?: return
        if (!startAction(NowPlayingAction.Mute)) return
        val muted = !mute.muted
        viewModelScope.launch {
            val result = when (val t = mute.target) {
                is Target.Room -> repo.setRoomMute(t.id, muted)
                is Target.Group -> repo.setGroupMute(t.id, muted)
                // The builder never offers a mute for an unknown target.
                is Target.Unknown -> HubResult.Err(HubError.Unexpected)
            }
            finishAction(NowPlayingAction.Mute)
            if (result is HubResult.Err) reportFailure(UserAction.Mute(on = muted), mute.name, result.error)
            // The button shows the hub's state only: the refresh is the confirmation (research R9).
            session.requestRefresh()
        }
    }

    // ---- Move to room… -------------------------------------------------------------------------

    fun onOpenMove() {
        val content = liveContent() ?: return
        if (!content.moveVisible || NowPlayingAction.Move in _state.value.inFlight) return
        val snapshot = (session.state.value as? SessionState.Connected)?.snapshot ?: return
        val destinations = MoveDestinations.build(snapshot, followedRouteId) ?: return
        _state.update { it.copy(sheet = MoveSheetState(destinations, selected = null)) }
    }

    fun onSelect(target: Target) {
        _state.update { current ->
            val sheet = current.sheet ?: return@update current
            val ok = sheet.content.rooms.plus(sheet.content.groups).any { it.target == target && it.selectable }
            if (ok) current.copy(sheet = sheet.copy(selected = target)) else current
        }
    }

    fun onDismissMove() = _state.update { it.copy(sheet = null) }

    fun onConfirmMove() {
        val content = liveContent() ?: return
        val sheet = _state.value.sheet ?: return
        val selected = sheet.selected ?: return
        val repo = session.repository ?: return
        if (!startAction(NowPlayingAction.Move)) return
        val id = followedRouteId
        val destination = sheet.content.rooms.plus(sheet.content.groups).first { it.target == selected }
        viewModelScope.launch {
            when (val result = repo.transferRoute(id, selected)) {
                is HubResult.Ok -> {
                    // The hub's answer is a NEW route id. Follow it, and ignore refreshes that
                    // started before this moment: they cannot know the new route yet.
                    fenceSeq = session.startedSeq
                    followedRouteId = result.value.id
                    finishAction(NowPlayingAction.Move)
                    _state.update { it.copy(sheet = null) }
                }

                is HubResult.Err -> {
                    finishAction(NowPlayingAction.Move)
                    _state.update { it.copy(sheet = null) }
                    reportFailure(UserAction.Move(destination.ctaName), content.sourceName, result.error)
                }
            }
            session.requestRefresh()
        }
    }

    private companion object {
        const val FOLLOWED_KEY = "followedRouteId"
    }
}
