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

/**
 * The rooms [route] plays on, and the one rule every screen uses for it: the known rooms the hub
 * lists in `outputs`, a group's members first in the group's order, then any other listed room in
 * hub order. When the hub lists none it knows (no list, an empty one while starting, or only
 * unknown ids), a room target occupies its room and a group target its known members.
 */
internal fun occupiedRooms(route: Route, rooms: Map<String, Room>, groups: Map<String, Group>): List<Room> {
    val target = route.target
    val listed = route.outputs.orEmpty().mapNotNull { rooms[it] }
    if (listed.isNotEmpty()) {
        if (target !is Target.Group) return listed
        val memberIds = groups[target.id]?.memberIds.orEmpty()
        val (members, others) = listed.partition { it.id in memberIds }
        return members.sortedBy { memberIds.indexOf(it.id) } + others
    }
    return when (target) {
        is Target.Room -> listOfNotNull(rooms[target.id])
        is Target.Group -> groups[target.id]?.memberIds.orEmpty().mapNotNull { rooms[it] }
        is Target.Unknown -> emptyList()
    }
}

/**
 * [describeTarget] for a playback: the title, kind and mute come from what it was addressed to,
 * the rooms, volumes and "not connected" from the rooms it plays on ([occupiedRooms]).
 */
internal fun describeRoute(
    route: Route,
    rooms: Map<String, Room>,
    groups: Map<String, Group>,
    masterMuted: Boolean,
): TargetDescription {
    val target = describeTarget(route.target, rooms, groups, masterMuted)
    val occupied = occupiedRooms(route, rooms, groups)
    val isGroup = route.target is Target.Group
    return target.copy(
        memberNames = if (isGroup) occupied.map { it.name } else emptyList(),
        volume = when (val t = route.target) {
            is Target.Room -> rooms[t.id]?.volume ?: 0
            is Target.Group -> occupied.maxOfOrNull { it.volume } ?: 0
            is Target.Unknown -> null
        },
        adjustable = when (val t = route.target) {
            is Target.Room -> t.id in rooms
            is Target.Group -> occupied.isNotEmpty()
            is Target.Unknown -> false
        },
        memberVolumes = if (isGroup) occupied.associate { it.id to it.volume } else emptyMap(),
        notConnected = occupied.any { !it.available },
        occupies = occupied.map { it.id }.toSet(),
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
 * Room id -> the first live route occupying it. A route occupies the rooms the hub lists, else its
 * room or every known member of its group ([occupiedRooms]). Rooms and Now Playing show one
 * playback per room, so the first one wins.
 */
internal fun occupancy(snapshot: HubSnapshot): Map<String, Route> {
    val rooms = snapshot.rooms.associateBy { it.id }
    val groups = snapshot.groups.associateBy { it.id }
    val result = linkedMapOf<String, Route>()
    for (route in liveRoutes(snapshot)) {
        for (id in occupiedRooms(route, rooms, groups).map { it.id }) {
            if (id !in result) result[id] = route
        }
    }
    return result
}

/**
 * Room id -> every live route covering it, in hub order (API 0.1.21 lets a room carry several). A
 * route covers the rooms the hub lists, else its room or every known member of its group
 * ([occupiedRooms]). Start Playback and Settings use this; Rooms and Now Playing keep [occupancy].
 */
internal fun routesByRoom(snapshot: HubSnapshot): Map<String, List<Route>> {
    val rooms = snapshot.rooms.associateBy { it.id }
    val groups = snapshot.groups.associateBy { it.id }
    val result = linkedMapOf<String, MutableList<Route>>()
    for (route in liveRoutes(snapshot)) {
        for (id in occupiedRooms(route, rooms, groups).map { it.id }) {
            result.getOrPut(id) { mutableListOf() } += route
        }
    }
    return result
}

/** A playback the hub runs lowering the others instead of replacing them. `Unknown` is not one. */
internal val Route.isAnnouncement: Boolean get() = joinMode == JoinMode.Announcement
