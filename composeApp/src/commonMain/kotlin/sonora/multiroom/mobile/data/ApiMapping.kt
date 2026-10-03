package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.Group
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Source
import sonora.multiroom.mobile.domain.SourceOrigin
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.domain.inferSourceKind
import sonora.multiroom.mobile.hub.generated.models.GroupResponse
import sonora.multiroom.mobile.hub.generated.models.InputResponse
import sonora.multiroom.mobile.hub.generated.models.OutputResponse
import sonora.multiroom.mobile.hub.generated.models.RouteResponse

// Generated wire types -> domain types, applying the rules of specs/001-*/data-model.md.
// Every field of the wire types is optional, so each rule says what a missing value becomes.

private fun String?.idOrNull(): String? = this?.takeIf { it.isNotBlank() }

internal fun OutputResponse.toRoom(): Room? {
    val id = outputId.idOrNull() ?: return null
    return Room(
        id = id,
        name = displayName.idOrNull() ?: id,
        volume = (volume ?: 0).coerceIn(0, 100),
        muted = muted ?: false,
        enabled = enabled ?: true,
        available = available ?: true,
    )
}

internal fun GroupResponse.toGroup(): Group? {
    val id = groupId.idOrNull() ?: return null
    return Group(
        id = id,
        name = displayName.idOrNull() ?: id,
        memberIds = outputIds.orEmpty().filter { it.isNotBlank() }.distinct(),
        muted = muted ?: false,
        enabled = enabled ?: true,
    )
}

internal fun InputResponse.toSource(): Source? {
    val id = inputId.idOrNull() ?: return null
    val origin = when (source) {
        InputResponse.Source.STATIC -> SourceOrigin.Configured
        InputResponse.Source.EPHEMERAL -> SourceOrigin.Runtime
        null -> SourceOrigin.Unknown
    }
    return Source(
        id = id,
        name = displayName.idOrNull() ?: id,
        uri = uri,
        origin = origin,
        pauseable = pauseable ?: false,
        enabled = enabled ?: true,
        kind = inferSourceKind(origin, uri),
    )
}

internal fun RouteResponse.toRoute(): Route? {
    val id = routeId.idOrNull() ?: return null
    val target = targetId.orEmpty()
    return Route(
        id = id,
        inputId = inputId.orEmpty(),
        target = when (targetType) {
            RouteResponse.TargetType.SINGLE_OUTPUT -> Target.Room(target)
            RouteResponse.TargetType.OUTPUT_GROUP -> Target.Group(target)
            null -> Target.Unknown(target)
        },
        status = when (status) {
            RouteResponse.Status.STARTING -> RouteStatus.Starting
            RouteResponse.Status.ACTIVE -> RouteStatus.Active
            RouteResponse.Status.STOPPING -> RouteStatus.Stopping
            RouteResponse.Status.STOPPED -> RouteStatus.Stopped
            RouteResponse.Status.FAILED -> RouteStatus.Failed
            null -> RouteStatus.Unknown
        },
        paused = paused ?: false,
        pauseable = pauseable ?: false,
        transferable = transferable ?: false,
    )
}
