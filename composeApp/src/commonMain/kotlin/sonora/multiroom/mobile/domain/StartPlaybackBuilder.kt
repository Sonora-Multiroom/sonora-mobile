package sonora.multiroom.mobile.domain

data class StartPlaybackContent(
    val sources: List<SourceOption>,
    val targets: List<TargetOption>,
)

data class SourceOption(val id: String, val name: String, val kind: SourceKind)

data class TargetOption(
    /** [Target.Room] or [Target.Group] only. */
    val target: Target,
    val name: String,
    val status: TargetStatus,
    val selectable: Boolean,
)

/** One status line per target; the wording is in `targetStatusText`. */
sealed interface TargetStatus {
    data object Idle : TargetStatus
    data class Playing(val status: CardStatus, val sources: List<String>) : TargetStatus
    data class InGroup(val group: String) : TargetStatus
    data class GroupPlaying(val sources: List<String>) : TargetStatus
    data class GroupMembers(val rooms: List<String>) : TargetStatus
    data object TurnedOff : TargetStatus
    data object NotConnected : TargetStatus
    data object NoRooms : TargetStatus
}

/**
 * What Start Playback lists (research R4). Decided here and only here; wording comes from
 * `targetStatusText`. Announcements are never shown in a status line (FR-008).
 */
object StartPlaybackBuilder {
    fun build(snapshot: HubSnapshot): StartPlaybackContent {
        val sources = snapshot.sources
            .filter { it.enabled }
            .map { SourceOption(it.id, it.name, it.kind) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

        val rooms = snapshot.rooms.associateBy { it.id }
        val groupsById = snapshot.groups.associateBy { it.id }
        val sourcesById = snapshot.sources.associateBy { it.id }
        val byRoom = routesByRoom(snapshot)
        val live = liveRoutes(snapshot).filterNot { it.isAnnouncement }

        fun sourceName(route: Route) = sourcesById[route.inputId]?.name ?: route.inputId

        val roomOptions = snapshot.rooms.map { room ->
            val own = byRoom[room.id].orEmpty().filter { !it.isAnnouncement && it.target is Target.Room }
            val viaGroup = byRoom[room.id].orEmpty().filter { !it.isAnnouncement && it.target is Target.Group }
            val status = when {
                !room.enabled -> TargetStatus.TurnedOff
                !room.available -> TargetStatus.NotConnected
                own.isNotEmpty() -> TargetStatus.Playing(
                    cardStatus(own.first(), sourcesById[own.first().inputId]?.uri),
                    own.map(::sourceName),
                )
                viaGroup.isNotEmpty() -> {
                    val id = (viaGroup.first().target as Target.Group).id
                    TargetStatus.InGroup(groupsById[id]?.name ?: id)
                }
                else -> TargetStatus.Idle
            }
            TargetOption(Target.Room(room.id), room.name, status, status.selectable)
        }

        val groupOptions = snapshot.groups.map { group ->
            val members = group.memberIds.mapNotNull { rooms[it] }
            val playable = members.filter { it.enabled && it.available }
            val own = live.filter { (it.target as? Target.Group)?.id == group.id }
            val status = when {
                !group.enabled -> TargetStatus.TurnedOff
                members.isEmpty() -> TargetStatus.NoRooms
                playable.isEmpty() ->
                    if (members.all { !it.enabled }) TargetStatus.TurnedOff else TargetStatus.NotConnected
                own.isNotEmpty() -> TargetStatus.GroupPlaying(own.map(::sourceName))
                else -> TargetStatus.GroupMembers(playable.map { it.name })
            }
            TargetOption(Target.Group(group.id), group.name, status, status.selectable)
        }

        return StartPlaybackContent(sources, orderTargets(groupOptions, roomOptions))
    }

    /** Selectable groups, selectable rooms, then every unselectable target; each part A→Z. */
    private fun orderTargets(groups: List<TargetOption>, rooms: List<TargetOption>): List<TargetOption> {
        val order = compareBy<TargetOption>({ it.name.lowercase() }, { idOf(it.target) })
        return groups.filter { it.selectable }.sortedWith(order) +
            rooms.filter { it.selectable }.sortedWith(order) +
            (groups + rooms).filter { !it.selectable }.sortedWith(order)
    }

    private fun idOf(target: Target) = when (target) {
        is Target.Room -> target.id
        is Target.Group -> target.id
        is Target.Unknown -> target.id
    }

    private val TargetStatus.selectable: Boolean
        get() = when (this) {
            TargetStatus.TurnedOff, TargetStatus.NotConnected, TargetStatus.NoRooms -> false
            else -> true
        }
}
