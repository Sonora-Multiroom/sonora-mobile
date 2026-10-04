package sonora.multiroom.mobile.ui

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.domain.StartNames

/** What the user was doing when a request failed. */
sealed interface UserAction {
    data object Volume : UserAction
    data object Stop : UserAction
    data object Pause : UserAction
    data object Resume : UserAction

    /** [on] is the state being asked for: true = mute everything. */
    data class MasterMute(val on: Boolean) : UserAction

    /** Mute or unmute one room or group; [on] is the state being asked for. */
    data class Mute(val on: Boolean) : UserAction

    /** Move a playback; the target name passed to [actionErrorMessage] is the source's name. */
    data class Move(val destination: String) : UserAction
}

/**
 * The only place error copy is written (contracts/hub-repository.md "User-facing messages"). It
 * never includes anything the hub said, so hub wording cannot reach the screen (Constitution V).
 */
fun actionErrorMessage(action: UserAction, targetName: String, error: HubError): String {
    val unreachable = error is HubError.Unreachable
    val gone = error is HubError.Rejected && error.status == 404
    val reason = " Can't reach the hub."
    return when (action) {
        UserAction.Volume -> when {
            unreachable -> "Couldn't change the volume in $targetName.$reason"
            gone -> "$targetName is no longer on the hub."
            else -> "Couldn't change the volume in $targetName."
        }

        UserAction.Stop -> verbMessage("stop", targetName, unreachable, gone)
        UserAction.Pause -> verbMessage("pause", targetName, unreachable, gone)
        UserAction.Resume -> verbMessage("resume", targetName, unreachable, gone)

        is UserAction.MasterMute -> {
            val verb = if (action.on) "mute" else "unmute"
            if (unreachable) "Couldn't $verb all rooms.$reason" else "Couldn't $verb all rooms."
        }

        is UserAction.Mute -> {
            val verb = if (action.on) "mute" else "unmute"
            when {
                unreachable -> "Couldn't $verb $targetName.$reason"
                gone -> "$targetName is no longer on the hub."
                else -> "Couldn't $verb $targetName."
            }
        }

        // A 404 cannot tell a vanished playback from a vanished destination: general text.
        is UserAction.Move ->
            if (unreachable) "Couldn't move $targetName to ${action.destination}.$reason"
            else "Couldn't move $targetName to ${action.destination}."
    }
}

private fun verbMessage(verb: String, targetName: String, unreachable: Boolean, gone: Boolean): String = when {
    unreachable -> "Couldn't $verb $targetName. Can't reach the hub."
    gone -> "That playback has already ended."
    else -> "Couldn't $verb $targetName."
}

/** Why starting playback failed (research R6); the copy is in [startFailureMessage]. */
sealed interface StartFailure {
    data class RoomFull(val room: String) : StartFailure
    data class AlreadyThere(val source: String, val room: String) : StartFailure
    data object LinkUnusable : StartFailure
    data object LinkUnreachable : StartFailure
    data object ServiceDown : StartFailure
    data class NoLongerOnHub(val name: String) : StartFailure
    data object HubUnreachable : StartFailure
    data object Other : StartFailure
}

enum class StartKind { Source, Link }

/**
 * Maps a hub answer to a [StartFailure]. A `reason` decides whatever the status (the contract does
 * not document the status of admission refusals). [roomName] looks an `outputId` up in the latest
 * snapshot; a missing or unknown one falls back to the chosen target. A 404 cannot tell which of
 * source and target vanished, so the caller refines the name from a fresh snapshot (research R7).
 * `Unreachable` is only the answer when recovery (research R8) found nothing.
 */
fun startFailure(
    kind: StartKind,
    error: HubError,
    names: StartNames,
    roomName: (outputId: String) -> String?,
): StartFailure = when (error) {
    HubError.Unreachable -> StartFailure.HubUnreachable
    HubError.Unexpected -> StartFailure.Other
    is HubError.Rejected -> {
        val room = error.outputId?.let(roomName) ?: names.target
        val source = names.source
        when {
            error.reason == "ROUTE_LIMIT_REACHED" -> StartFailure.RoomFull(room)
            error.reason == "INPUT_ALREADY_ON_OUTPUT" ->
                if (kind == StartKind.Source && source != null) StartFailure.AlreadyThere(source, room) else StartFailure.Other
            error.status == 404 ->
                StartFailure.NoLongerOnHub(if (kind == StartKind.Source) source ?: names.target else names.target)
            kind == StartKind.Link -> when (error.status) {
                400, 422 -> StartFailure.LinkUnusable
                502 -> StartFailure.LinkUnreachable
                503 -> StartFailure.ServiceDown
                else -> StartFailure.Other
            }
            else -> StartFailure.Other
        }
    }
}

fun startFailureMessage(failure: StartFailure): String = when (failure) {
    is StartFailure.RoomFull -> "${failure.room} can't play more at once"
    is StartFailure.AlreadyThere -> "${failure.source} is already playing in ${failure.room}"
    StartFailure.LinkUnusable -> "The hub couldn't play this link"
    StartFailure.LinkUnreachable -> "Couldn't reach that link"
    StartFailure.ServiceDown -> "That service isn't available right now. Try again later."
    is StartFailure.NoLongerOnHub -> "${failure.name} is no longer on the hub"
    StartFailure.HubUnreachable -> "Couldn't reach the hub"
    StartFailure.Other -> "Couldn't start playback"
}
