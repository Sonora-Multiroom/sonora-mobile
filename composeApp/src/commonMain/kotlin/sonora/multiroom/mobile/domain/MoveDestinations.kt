package sonora.multiroom.mobile.domain

data class MoveSheetContent(
    val sourceName: String,
    /** The room or group name only, for "<source> · now on <target>". */
    val currentTargetName: String,
    val rooms: List<MoveDestination>,
    /** Empty hides the Groups section. */
    val groups: List<MoveDestination>,
)

data class MoveDestination(
    /** [Target.Room] or [Target.Group] only. */
    val target: Target,
    /** "Kitchen", "Kitchen only", "Downstairs". */
    val label: String,
    /** The name in "Move to <name>": the room's own name also for "Kitchen only". */
    val ctaName: String,
    val kind: MoveDestinationKind,
    val note: MoveDestinationNote,
    val selectable: Boolean,
)

enum class MoveDestinationKind { Room, MemberOnly, Group }

sealed interface MoveDestinationNote {
    data object Idle : MoveDestinationNote

    /** Moving here stops [sources]. Group rows also list members that cannot play. */
    data class WillStop(
        val sources: List<String>,
        val turnedOff: List<String> = emptyList(),
        val notConnected: List<String> = emptyList(),
    ) : MoveDestinationNote

    data class WillStopOnGroup(val source: String, val group: String) : MoveDestinationNote

    /** "<Room> only": the other members of the current group stop. */
    data class OthersStop(val rooms: List<String>) : MoveDestinationNote

    /** A free group: the rooms it would play on, plus the members that cannot play. */
    data class Members(
        val rooms: List<String>,
        val turnedOff: List<String> = emptyList(),
        val notConnected: List<String> = emptyList(),
    ) : MoveDestinationNote

    data object TurnedOff : MoveDestinationNote
    data object NotConnected : MoveDestinationNote
    data object NoRooms : MoveDestinationNote
}

/** Notes that say what will stop are drawn as warnings. */
val MoveDestinationNote.warning: Boolean
    get() = this is MoveDestinationNote.WillStop || this is MoveDestinationNote.WillStopOnGroup

/**
 * What the Move playback sheet lists for one route (research R8). Decided here and only here; the
 * wording comes from `destinationNoteText`.
 */
object MoveDestinations {
    /** Null when the route is gone. A route with an unknown target has nothing to move to. */
    fun build(snapshot: HubSnapshot, routeId: String): MoveSheetContent? {
        val route = liveRoutes(snapshot).firstOrNull { it.id == routeId } ?: return null
        val rooms = snapshot.rooms.associateBy { it.id }
        val groups = snapshot.groups.associateBy { it.id }
        val sourceNames = snapshot.sources.associate { it.id to it.name }
        fun sourceName(r: Route) = sourceNames[r.inputId] ?: r.inputId

        val current = describeTarget(route.target, rooms, groups, masterMuted = false)
        val header = MoveSheetContent(sourceName(route), current.title, emptyList(), emptyList())
        if (route.target is Target.Unknown) return header

        // Who else occupies which room: the current route is excluded, so the rooms it plays on
        // never count as "will stop".
        val others = occupancy(snapshot.copy(routes = snapshot.routes.filter { it.id != route.id }))

        val currentGroup = (route.target as? Target.Group)?.let { groups[it.id] }
        val currentMembers = current.occupies

        fun occupiedNote(room: Room): MoveDestinationNote {
            val other = others[room.id] ?: return MoveDestinationNote.Idle
            val target = other.target
            return if (target is Target.Group) {
                MoveDestinationNote.WillStopOnGroup(sourceName(other), groups[target.id]?.name ?: target.id)
            } else {
                MoveDestinationNote.WillStop(listOf(sourceName(other)))
            }
        }

        fun roomNote(room: Room): MoveDestinationNote = when {
            !room.enabled -> MoveDestinationNote.TurnedOff
            !room.available -> MoveDestinationNote.NotConnected
            else -> occupiedNote(room)
        }

        val roomRows = mutableListOf<MoveDestination>()
        for (room in snapshot.rooms) {
            if (room.id in currentMembers) continue
            val note = roomNote(room)
            roomRows += MoveDestination(
                target = Target.Room(room.id),
                label = room.name,
                ctaName = room.name,
                kind = MoveDestinationKind.Room,
                note = note,
                selectable = note.selectable,
            )
        }
        if (currentGroup != null) {
            val members = currentGroup.memberIds.mapNotNull { rooms[it] }
            for (member in members) {
                val note = when {
                    !member.enabled -> MoveDestinationNote.TurnedOff
                    !member.available -> MoveDestinationNote.NotConnected
                    else -> MoveDestinationNote.OthersStop(members.filter { it.id != member.id }.map { it.name })
                }
                roomRows += MoveDestination(
                    target = Target.Room(member.id),
                    label = "${member.name} only",
                    ctaName = member.name,
                    kind = MoveDestinationKind.MemberOnly,
                    note = note,
                    selectable = note.selectable,
                )
            }
        }

        val groupRows = snapshot.groups
            .filter { (route.target as? Target.Group)?.id != it.id }
            .map { group ->
                val members = group.memberIds.mapNotNull { rooms[it] }
                val playable = members.filter { it.enabled && it.available }
                val note: MoveDestinationNote = when {
                    !group.enabled -> MoveDestinationNote.TurnedOff
                    members.isEmpty() -> MoveDestinationNote.NoRooms
                    playable.isEmpty() ->
                        if (members.all { !it.enabled }) MoveDestinationNote.TurnedOff else MoveDestinationNote.NotConnected
                    else -> {
                        val turnedOff = members.filter { !it.enabled }.map { it.name }
                        val notConnected = members.filter { it.enabled && !it.available }.map { it.name }
                        val stopping = playable.mapNotNull { others[it.id] }.map(::sourceName).distinct()
                        if (stopping.isNotEmpty()) {
                            MoveDestinationNote.WillStop(stopping, turnedOff, notConnected)
                        } else {
                            MoveDestinationNote.Members(playable.map { it.name }, turnedOff, notConnected)
                        }
                    }
                }
                MoveDestination(
                    target = Target.Group(group.id),
                    label = group.name,
                    ctaName = group.name,
                    kind = MoveDestinationKind.Group,
                    note = note,
                    selectable = note.selectable,
                )
            }

        return header.copy(rooms = sortRooms(roomRows), groups = sortGroups(groupRows))
    }

    private val MoveDestinationNote.selectable: Boolean
        get() = when (this) {
            MoveDestinationNote.TurnedOff, MoveDestinationNote.NotConnected, MoveDestinationNote.NoRooms -> false
            else -> true
        }

    private fun key(d: MoveDestination): Pair<String, String> {
        val id = when (val t = d.target) {
            is Target.Room -> t.id
            is Target.Group -> t.id
            is Target.Unknown -> t.id
        }
        return d.label.lowercase() to id
    }

    /** Selectable non-members, then "only" options, then the unselectable ones. */
    private fun sortRooms(rows: List<MoveDestination>): List<MoveDestination> {
        fun bucket(d: MoveDestination) = when {
            !d.selectable -> 2
            d.kind == MoveDestinationKind.MemberOnly -> 1
            else -> 0
        }
        return rows.sortedWith(compareBy<MoveDestination>({ bucket(it) }, { key(it).first }, { key(it).second }))
    }

    private fun sortGroups(rows: List<MoveDestination>): List<MoveDestination> =
        rows.sortedWith(compareBy<MoveDestination>({ !it.selectable }, { key(it).first }, { key(it).second }))
}
