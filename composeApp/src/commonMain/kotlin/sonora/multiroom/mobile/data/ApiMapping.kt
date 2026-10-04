package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.Extension
import sonora.multiroom.mobile.domain.ExtensionConnection
import sonora.multiroom.mobile.domain.ExtensionInventory
import sonora.multiroom.mobile.domain.ExtensionStatus
import sonora.multiroom.mobile.domain.Group
import sonora.multiroom.mobile.domain.JoinMode
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Source
import sonora.multiroom.mobile.domain.SourceOrigin
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.domain.inferSourceKind
import sonora.multiroom.mobile.hub.generated.models.GroupResponse
import sonora.multiroom.mobile.hub.generated.models.ExtensionInventory as ExtensionInventoryResponse
import sonora.multiroom.mobile.hub.generated.models.Extension as ExtensionResponse
import sonora.multiroom.mobile.hub.generated.models.InputResponse
import sonora.multiroom.mobile.hub.generated.models.OutputResponse
import sonora.multiroom.mobile.hub.generated.models.RouteResponse
import kotlin.time.Instant

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
        // HubJson coerces an unrecognised value to null, so "unknown" and "none" look the same here.
        defaultJoinMode = when (defaultJoinMode) {
            InputResponse.DefaultJoinMode.REPLACE -> JoinMode.Replace
            InputResponse.DefaultJoinMode.MIX -> JoinMode.Mix
            InputResponse.DefaultJoinMode.DUCK_OTHERS -> JoinMode.Announcement
            null -> null
        },
        autoRemove = autoRemove ?: false,
        // A date the app cannot read is "undated", never a failed refresh (research R10).
        createdAt = createdAt?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
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
        joinMode = when (joinMode) {
            RouteResponse.JoinMode.REPLACE -> JoinMode.Replace
            RouteResponse.JoinMode.MIX -> JoinMode.Mix
            RouteResponse.JoinMode.DUCK_OTHERS -> JoinMode.Announcement
            null -> JoinMode.Unknown
        },
    )
}

/**
 * Missing or unrecognised values (coerced to null by `HubJson`) become `Unknown`; a blank name is
 * the id, and an entry with neither is dropped. `rejectionReason`, version and directory are
 * never mapped: the app shows none of them (FR-019).
 */
internal fun ExtensionResponse.toExtension(): Extension? {
    val key = id.idOrNull()
    val label = name.idOrNull() ?: key ?: return null
    return Extension(
        id = key ?: label,
        name = label,
        status = when (status) {
            ExtensionResponse.Status.ACTIVE -> ExtensionStatus.Active
            ExtensionResponse.Status.DISABLED -> ExtensionStatus.Disabled
            ExtensionResponse.Status.REJECTED -> ExtensionStatus.Rejected
            ExtensionResponse.Status.INERT -> ExtensionStatus.Inactive
            null -> ExtensionStatus.Unknown
        },
        connection = when (connectionState) {
            ExtensionResponse.ConnectionState.CONNECTED -> ExtensionConnection.Connected
            ExtensionResponse.ConnectionState.DISCONNECTED -> ExtensionConnection.Disconnected
            ExtensionResponse.ConnectionState.NOT_APPLICABLE -> ExtensionConnection.NotApplicable
            null -> ExtensionConnection.Unknown
        },
    )
}

internal fun ExtensionInventoryResponse.toInventory(): ExtensionInventory = ExtensionInventory(
    loadingEnabled = loadingEnabled ?: true,
    extensions = extensions.orEmpty().mapNotNull { it.toExtension() },
)
