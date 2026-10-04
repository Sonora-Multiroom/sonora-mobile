package sonora.multiroom.mobile.domain

/** A speaker output ("room" in the UI). */
data class Room(
    val id: String,
    val name: String,
    val volume: Int,
    val muted: Boolean,
    val enabled: Boolean,
    val available: Boolean,
)

data class Group(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val muted: Boolean,
    val enabled: Boolean,
)

enum class SourceOrigin { Configured, Runtime, Unknown }

enum class SourceKind { Stream, LineIn, File, Link }

/**
 * How a playback joined its target (API 0.1.21). [Announcement] is the hub's `DUCK_OTHERS`:
 * it lowers the others instead of replacing them. [Unknown] is a value this app does not know.
 */
enum class JoinMode { Replace, Mix, Announcement, Unknown }

data class Source(
    val id: String,
    val name: String,
    val uri: String?,
    val origin: SourceOrigin,
    val pauseable: Boolean,
    val enabled: Boolean,
    val kind: SourceKind,
    /** The mode the hub applies to a start that names none; null when the source declares none. */
    val defaultJoinMode: JoinMode? = null,
    /** A runtime source the hub removes by itself once its playback stops. */
    val autoRemove: Boolean = false,
    /** When the hub added a runtime source; null when it did not say or the value was unreadable. */
    val createdAt: kotlin.time.Instant? = null,
)

sealed interface Target {
    data class Room(val id: String) : Target
    data class Group(val id: String) : Target

    /** The hub named a target type this app does not know. */
    data class Unknown(val id: String) : Target
}

enum class RouteStatus { Starting, Active, Stopping, Stopped, Failed, Unknown }

data class Route(
    val id: String,
    val inputId: String,
    val target: Target,
    val status: RouteStatus,
    val paused: Boolean,
    val pauseable: Boolean,
    val transferable: Boolean,
    val joinMode: JoinMode = JoinMode.Replace,
)

/** One successful refresh of everything the Rooms screen needs. */
data class HubSnapshot(
    val rooms: List<Room>,
    val groups: List<Group>,
    val routes: List<Route>,
    val sources: List<Source>,
    val masterMuted: Boolean,
)
