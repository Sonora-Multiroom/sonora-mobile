package sonora.multiroom.mobile.domain

// Rules shared by the Rooms and Now Playing builders, so the two screens cannot disagree about
// status, live streams, mute or "not connected" (research R6).

/** STOPPED routes are history; FAILED and Unknown ones still occupy their rooms. */
internal fun liveRoutes(snapshot: HubSnapshot): List<Route> =
    snapshot.routes.filter { it.status != RouteStatus.Stopped }

internal data class TargetDescription(
    val title: String,
    val isGroup: Boolean,
    val memberNames: List<String>,
    val volume: Int?,
    val adjustable: Boolean,
    val memberVolumes: Map<String, Int>,
    val muted: Boolean,
    val notConnected: Boolean,
    val occupies: Set<String>,
    val roomId: String? = null,
)

internal fun describeTarget(
    target: Target,
    rooms: Map<String, Room>,
    groups: Map<String, Group>,
    masterMuted: Boolean,
): TargetDescription = when (target) {
    is Target.Room -> {
        val room = rooms[target.id]
        TargetDescription(
            title = room?.name ?: target.id,
            isGroup = false,
            memberNames = emptyList(),
            volume = room?.volume ?: 0,
            adjustable = room != null,
            memberVolumes = emptyMap(),
            muted = masterMuted || room?.muted == true,
            notConnected = room?.available == false,
            occupies = if (room != null) setOf(room.id) else emptySet(),
            roomId = room?.id,
        )
    }

    is Target.Group -> {
        val group = groups[target.id]
        val members = group?.memberIds.orEmpty().mapNotNull { rooms[it] }
        TargetDescription(
            title = group?.name ?: target.id,
            isGroup = true,
            memberNames = members.map { it.name },
            volume = members.maxOfOrNull { it.volume } ?: 0,
            adjustable = members.isNotEmpty(),
            memberVolumes = members.associate { it.id to it.volume },
            muted = masterMuted || group?.muted == true,
            notConnected = members.any { !it.available },
            occupies = members.map { it.id }.toSet(),
        )
    }

    // The app cannot tell which rooms this covers, so it occupies none and has no volume.
    is Target.Unknown -> TargetDescription(
        title = target.id,
        isGroup = false,
        memberNames = emptyList(),
        volume = null,
        adjustable = false,
        memberVolumes = emptyMap(),
        muted = masterMuted,
        notConnected = false,
        occupies = emptySet(),
    )
}

/** The status the Rooms card and Now Playing share; a live stream is [CardStatus.LiveStream]. */
internal fun cardStatus(route: Route, uri: String?): CardStatus = when (route.status) {
    RouteStatus.Starting -> CardStatus.Starting
    RouteStatus.Stopping -> CardStatus.Stopping
    RouteStatus.Failed -> CardStatus.Failed
    RouteStatus.Active -> when {
        route.pauseable && route.paused -> CardStatus.Paused
        isLiveStream(route.pauseable, uri) -> CardStatus.LiveStream
        else -> CardStatus.Playing
    }
    RouteStatus.Unknown, RouteStatus.Stopped -> CardStatus.Unknown
}

/**
 * Room id -> the live route occupying it. A group route occupies every known member; a route with
 * an unknown target occupies none. An output plays at most one route, so the first one wins.
 */
internal fun occupancy(snapshot: HubSnapshot): Map<String, Route> {
    val rooms = snapshot.rooms.associateBy { it.id }
    val groups = snapshot.groups.associateBy { it.id }
    val result = linkedMapOf<String, Route>()
    for (route in liveRoutes(snapshot)) {
        for (id in describeTarget(route.target, rooms, groups, masterMuted = false).occupies) {
            result.putIfAbsent(id, route)
        }
    }
    return result
}
