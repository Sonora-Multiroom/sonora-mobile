package ai.sonora.mobile.ui

import ai.sonora.mobile.data.HubError

/** What the user was doing when a request failed. */
sealed interface UserAction {
    data object Volume : UserAction
    data object Stop : UserAction
    data object Pause : UserAction
    data object Resume : UserAction

    /** [on] is the state being asked for: true = mute everything. */
    data class MasterMute(val on: Boolean) : UserAction
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
    }
}

private fun verbMessage(verb: String, targetName: String, unreachable: Boolean, gone: Boolean): String = when {
    unreachable -> "Couldn't $verb $targetName. Can't reach the hub."
    gone -> "That playback has already ended."
    else -> "Couldn't $verb $targetName."
}
