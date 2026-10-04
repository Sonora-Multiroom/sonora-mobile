package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.StartNames
import sonora.multiroom.mobile.domain.StartWhat
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.ui.StartFailure
import sonora.multiroom.mobile.ui.StartKind
import sonora.multiroom.mobile.ui.startFailure
import sonora.multiroom.mobile.ui.startFailureMessage
import sonora.multiroom.mobile.data.HubError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StartAttempt {
    data object Starting : StartAttempt

    /**
     * [startedAfterSeq] is the session's `startedSeq` once the playback was known to exist: Now
     * Playing must not end on a refresh that started before it (fence rule, `HubSession`).
     */
    data class Done(val route: Route, val startedAfterSeq: Long) : StartAttempt

    data class Failed(val failure: StartFailure) : StartAttempt
}

/**
 * Runs a start in the app's scope, so it survives its screen closing (research R9). After a
 * timeout or a lost answer the hub may still have started the playback, so it is confirmed against
 * a fresh snapshot before a failure is reported (FR-016a, research R7/R8).
 */
class PlaybackStarter(
    private val scope: CoroutineScope,
    private val session: HubSession,
    private val messages: AppMessages,
) {
    private val _attempt = MutableStateFlow<StartAttempt?>(null)
    val attempt: StateFlow<StartAttempt?> = _attempt.asStateFlow()

    private var detached = false

    fun start(what: StartWhat, target: Target, names: StartNames) {
        if (_attempt.value != null) return
        val repo = session.repository ?: return
        detached = false
        _attempt.value = StartAttempt.Starting
        scope.launch {
            val result = when (what) {
                is StartWhat.Source -> repo.startSource(what.id, target)
                is StartWhat.Link -> repo.playLink(what.uri, target)
            }
            val kind = if (what is StartWhat.Source) StartKind.Source else StartKind.Link
            when (result) {
                is HubResult.Ok -> succeed(result.value)
                is HubResult.Err -> when {
                    result.error is HubError.Unreachable -> recover(what, target, StartFailure.HubUnreachable)
                    // The hub may have started it although the answer could not be read.
                    result.error is HubError.Unexpected -> recover(what, target, StartFailure.Other)
                    result.error is HubError.Rejected && result.error.status == 404 -> gone(what, target, names, kind, result.error)
                    else -> fail(startFailure(kind, result.error, names, roomNames()))
                }
            }
        }
    }

    /** The screen closed: its outcome now reaches Rooms through [AppMessages]. */
    fun detach() {
        detached = true
    }

    fun consume() {
        _attempt.value = null
    }

    private fun succeed(route: Route) {
        val seq = session.startedSeq
        session.requestRefresh()
        if (detached) _attempt.value = null else _attempt.value = StartAttempt.Done(route, seq)
    }

    private fun fail(failure: StartFailure) {
        if (detached) {
            _attempt.value = null
            messages.post(startFailureMessage(failure))
        } else {
            _attempt.value = StartAttempt.Failed(failure)
        }
    }

    /** Room names from the latest snapshot, for `outputId`s in refusals. */
    private fun roomNames(): (String) -> String? {
        val snapshot = (session.state.value as? SessionState.Connected)?.snapshot
        return { id -> snapshot?.rooms?.firstOrNull { it.id == id }?.name }
    }

    /** A 404 does not say what vanished: the first fresh snapshot does (research R7). */
    private suspend fun gone(what: StartWhat, target: Target, names: StartNames, kind: StartKind, error: HubError.Rejected) {
        val fresh = session.awaitFreshSnapshot()
        val failure = if (fresh == null) {
            startFailure(kind, error, names, roomNames())
        } else {
            val source = (what as? StartWhat.Source)?.let { w -> fresh.sources.firstOrNull { it.id == w.id } }
            val targetGone = when (target) {
                is Target.Room -> fresh.rooms.none { it.id == target.id }
                is Target.Group -> fresh.groups.none { it.id == target.id }
                is Target.Unknown -> true
            }
            when {
                what is StartWhat.Source && (source == null || !source.enabled) ->
                    StartFailure.NoLongerOnHub(source?.name ?: names.source ?: names.target)
                targetGone -> StartFailure.NoLongerOnHub(names.target)
                else -> StartFailure.Other
            }
        }
        fail(failure)
    }

    /** The answer was lost: look for the playback on the hub before calling it a failure (R8). */
    private suspend fun recover(what: StartWhat, target: Target, notFound: StartFailure) {
        val fresh = session.awaitFreshSnapshot()
        val found = fresh?.let { findStarted(it, what, target) }
        if (found != null) succeed(found) else fail(notFound)
    }

    private fun findStarted(snapshot: HubSnapshot, what: StartWhat, target: Target): Route? {
        val inputIds = when (what) {
            is StartWhat.Source -> setOf(what.id)
            is StartWhat.Link -> snapshot.sources
                .filter { it.origin == sonora.multiroom.mobile.domain.SourceOrigin.Runtime && sameLink(it.uri, what.uri) }
                .map { it.id }.toSet()
        }
        return snapshot.routes.lastOrNull {
            it.status != RouteStatus.Stopped && it.target == target && it.inputId in inputIds
        }
    }
}

/** Trimmed, with the scheme and host compared case-insensitively. */
internal fun sameLink(stored: String?, sent: String): Boolean {
    if (stored == null) return false
    return normaliseForCompare(stored) == normaliseForCompare(sent)
}

private fun normaliseForCompare(link: String): String {
    val trimmed = link.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd < 0) return trimmed
    val hostEnd = trimmed.indexOfAny(charArrayOf('/', '?', '#'), schemeEnd + 3).let { if (it < 0) trimmed.length else it }
    return trimmed.substring(0, hostEnd).lowercase() + trimmed.substring(hostEnd)
}
