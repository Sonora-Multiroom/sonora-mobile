package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.StartNames
import sonora.multiroom.mobile.domain.StartPlaybackBuilder
import sonora.multiroom.mobile.domain.StartPlaybackContent
import sonora.multiroom.mobile.domain.StartWhat
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.PlaybackStarter
import sonora.multiroom.mobile.ui.session.SessionState
import sonora.multiroom.mobile.ui.session.StartAttempt
import sonora.multiroom.mobile.ui.startFailureMessage
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Start Playback: what to play, where, and the Play button. Everything shown is derived from the
 * shared [HubSession] snapshot; the request itself runs in the app-scoped [PlaybackStarter]
 * (research R9, R12).
 */
class StartPlaybackViewModel(
    private val initialTargetId: String?,
    private val savedState: SavedStateHandle,
    private val session: HubSession,
    private val starter: PlaybackStarter,
) : ViewModel() {
    private val _state = MutableStateFlow(StartPlaybackUiState())
    val state: StateFlow<StartPlaybackUiState> = _state.asStateFlow()

    private var snapshot: HubSnapshot? = null

    /** Names captured when Play was tapped, for the hand-off to Now Playing. */
    private var pendingNames: StartNames? = null

    init {
        _state.update {
            it.copy(
                selectedSourceId = savedState.get<String>(SOURCE_KEY),
                selectedTarget = savedState.get<String>(TARGET_KEY)?.let(::decodeTarget),
            )
        }
        viewModelScope.launch { session.state.collect(::onSession) }
        viewModelScope.launch { starter.attempt.collect(::onAttempt) }
    }

    // ---- Session -------------------------------------------------------------------------------

    private fun onSession(s: SessionState) {
        when (s) {
            SessionState.Initial -> Unit
            SessionState.NoAddress -> _state.update { it.copy(address = null) }
            is SessionState.Connected -> {
                snapshot = s.snapshot
                _state.update { it.copy(address = s.address, connection = s.connection) }
                s.snapshot?.let { rebuild(StartPlaybackBuilder.build(it)) }
                    ?: _state.update(::derive)
            }
        }
    }

    /** New content: prune what vanished, apply the opening preselection once, refresh derived fields. */
    private fun rebuild(content: StartPlaybackContent) {
        _state.update { current ->
            var sourceId = current.selectedSourceId?.takeIf { id -> content.sources.any { it.id == id } }
            var target = current.selectedTarget?.takeIf { t -> content.targets.any { it.target == t && it.selectable } }
            if (!savedState.get<Boolean>(PRESELECTED_KEY).let { it == true }) {
                savedState[PRESELECTED_KEY] = true
                if (target == null && initialTargetId != null) {
                    target = content.targets.firstOrNull { it.target == Target.Room(initialTargetId) && it.selectable }?.target
                }
            }
            if (sourceId != current.selectedSourceId || target != current.selectedTarget) save(sourceId, target)
            derive(current.copy(content = content, selectedSourceId = sourceId, selectedTarget = target))
        }
    }

    /** Fields that follow from the others: the Play label and whether Play can be tapped. */
    private fun derive(s: StartPlaybackUiState): StartPlaybackUiState {
        val content = s.content
        val sourceName = content?.sources?.firstOrNull { it.id == s.selectedSourceId }?.name
        val targetName = content?.targets?.firstOrNull { it.target == s.selectedTarget }?.name
        val label = when {
            s.starting -> PlayLabel.Starting
            sourceName != null && targetName != null -> PlayLabel.PlaySource(sourceName, targetName)
            else -> PlayLabel.Play
        }
        val ready = sourceName != null && targetName != null
        return s.copy(playLabel = label, playEnabled = ready && !s.starting && s.connection == Connection.Live)
    }

    private fun save(sourceId: String?, target: Target?) {
        savedState[SOURCE_KEY] = sourceId
        savedState[TARGET_KEY] = target?.let(::encodeTarget)
    }

    // ---- Selection -----------------------------------------------------------------------------

    fun onSelectSource(id: String) {
        _state.update { current ->
            if (current.starting || current.content?.sources?.any { it.id == id } != true) return@update current
            save(id, current.selectedTarget)
            derive(current.copy(selectedSourceId = id))
        }
    }

    fun onSelectTarget(target: Target) {
        _state.update { current ->
            if (current.starting || current.content?.targets?.any { it.target == target && it.selectable } != true) {
                return@update current
            }
            save(current.selectedSourceId, target)
            derive(current.copy(selectedTarget = target))
        }
    }

    // ---- Play ----------------------------------------------------------------------------------

    fun onPlay() {
        val s = _state.value
        if (!s.playEnabled) return
        val content = s.content ?: return
        val source = content.sources.firstOrNull { it.id == s.selectedSourceId } ?: return
        val target = content.targets.firstOrNull { it.target == s.selectedTarget } ?: return
        val names = StartNames(source.name, target.name)
        pendingNames = names
        starter.start(StartWhat.Source(source.id), target.target, names)
    }

    private fun onAttempt(attempt: StartAttempt?) {
        when (attempt) {
            null -> _state.update { derive(it.copy(starting = false)) }
            StartAttempt.Starting -> _state.update { derive(it.copy(starting = true)) }
            is StartAttempt.Done -> {
                val name = pendingNames?.target ?: ""
                _state.update {
                    derive(it.copy(starting = false, exit = StartExit.Started(attempt.route.id, attempt.startedAfterSeq, name)))
                }
                starter.consume()
            }
            is StartAttempt.Failed -> {
                _state.update { derive(it.copy(starting = false, message = startFailureMessage(attempt.failure))) }
                starter.consume()
            }
        }
    }

    // ---- Closing and lifecycle -----------------------------------------------------------------

    /** Close or Back. A start in flight carries on and reports to Rooms (research R9). */
    fun onClose() {
        starter.detach()
        _state.update { it.copy(exit = StartExit.Closed) }
    }

    /** Foreground only: the screen calls this while it is visible (FR-002). */
    fun onVisible() = session.acquire()

    fun onHidden() = session.release()

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeExit() = _state.update { it.copy(exit = null) }

    override fun onCleared() {
        starter.detach()
    }

    private companion object {
        const val SOURCE_KEY = "sourceId"
        const val TARGET_KEY = "target"
        const val PRESELECTED_KEY = "preselected"

        fun encodeTarget(t: Target): String = when (t) {
            is Target.Room -> "room:${t.id}"
            is Target.Group -> "group:${t.id}"
            is Target.Unknown -> "unknown:${t.id}"
        }

        fun decodeTarget(s: String): Target? = when {
            s.startsWith("room:") -> Target.Room(s.removePrefix("room:"))
            s.startsWith("group:") -> Target.Group(s.removePrefix("group:"))
            else -> null
        }
    }
}
