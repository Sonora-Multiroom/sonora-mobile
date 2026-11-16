package sonora.multiroom.mobile.domain

data class StartPlaybackContent(
    val sources: List<SourceOption>,
    val targets: List<TargetOption>,
)

data class SourceOption(
    val id: String,
    val name: String,
    val kind: SourceKind,
    /** The source's address when it is a radio station (MOB-009); null for any other source. */
    val stationUri: String? = null,
)

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
    /** [status] is the group playback's state; a live stream reads [CardStatus.Playing] as for rooms. */
    data class GroupPlaying(val status: CardStatus, val sources: List<String>) : TargetStatus
    data class GroupMembers(val rooms: List<String>) : TargetStatus
    data object TurnedOff : TargetStatus
    data object NotConnected : TargetStatus
    data object NoRooms : TargetStatus

    /** Only for a fixed target (Change source) that is not in the snapshot any more; never in the picker. */
    data object Removed : TargetStatus
}

/** The one status per room or group, shared by the picker's tiles and the fixed target card. */
internal class TargetStatuses(snapshot: HubSnapshot) {
    private val rooms = snapshot.rooms.associateBy { it.id }
    private val groupsById = snapshot.groups.associateBy { it.id }
    private val sourcesById = snapshot.sources.associateBy { it.id }
    private val byRoom = routesByRoom(snapshot)
    private val live = liveRoutes(snapshot).filterNot { it.isAnnouncement }

    private fun sourceName(route: Route) = sourcesById[route.inputId]?.name ?: route.inputId

    fun of(room: Room): TargetStatus {
        val own = byRoom[room.id].orEmpty().filter { !it.isAnnouncement && it.target is Target.Room }
        val viaGroup = byRoom[room.id].orEmpty().filter { !it.isAnnouncement && it.target is Target.Group }
        return when {
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
    }

    fun of(group: Group): TargetStatus {
        val members = group.memberIds.mapNotNull { rooms[it] }
        val playable = members.filter { it.enabled && it.available }
        val own = live.filter { (it.target as? Target.Group)?.id == group.id }
        return when {
            !group.enabled -> TargetStatus.TurnedOff
            members.isEmpty() -> TargetStatus.NoRooms
            playable.isEmpty() ->
                if (members.all { !it.enabled }) TargetStatus.TurnedOff else TargetStatus.NotConnected
            own.isNotEmpty() -> {
                val first = own.first()
                val state = cardStatus(first, sourcesById[first.inputId]?.uri)
                TargetStatus.GroupPlaying(if (state == CardStatus.LiveStream) CardStatus.Playing else state, own.map(::sourceName))
            }
            else -> TargetStatus.GroupMembers(playable.map { it.name })
        }
    }
}

/**
 * What Start Playback lists (research R4). Decided here and only here; wording comes from
 * `targetStatusText`. Announcements are never shown in a status line (FR-008).
 */
object StartPlaybackBuilder {
    fun build(snapshot: HubSnapshot): StartPlaybackContent {
        val sources = snapshot.sources
            .filter { it.enabled }
            .map { SourceOption(it.id, it.name, it.kind, it.uri?.trim()?.takeIf(::isStationAddress)) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))

        val statuses = TargetStatuses(snapshot)
        val roomOptions = snapshot.rooms.map { room ->
            val status = statuses.of(room)
            TargetOption(Target.Room(room.id), room.name, status, status.selectable)
        }
        val groupOptions = snapshot.groups.map { group ->
            val status = statuses.of(group)
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
            TargetStatus.TurnedOff, TargetStatus.NotConnected, TargetStatus.NoRooms, TargetStatus.Removed -> false
            else -> true
        }
}

/**
 * The radio part of Start Playback (MOB-009). [found] is the selected station when it is not one of
 * the [recent] rows; it then takes the place of the "Find a radio station" row. [selectedUuid]
 * marks the selected row among [recent].
 */
data class RadioRows(val found: Station?, val recent: List<Station>, val selectedUuid: String?)

/** Null when radio is not offered. [selected] is the effective station (data-model.md). */
fun radioRows(offered: Boolean, recent: List<Station>, selected: Station?): RadioRows? {
    if (!offered) return null
    val shown = recent.take(RECENT_IN_START_PLAYBACK)
    val found = selected?.takeIf { s -> shown.none { it.uuid == s.uuid } }
    return RadioRows(found, shown, selected?.uuid)
}

/**
 * A station shown in the radio section is a source on the hub while it plays; it must not appear
 * twice. The selected source is never left out.
 */
fun hideListedStations(sources: List<SourceOption>, rows: RadioRows?, selectedSourceId: String?): List<SourceOption> {
    if (rows == null) return sources
    val shown = listOfNotNull(rows.found) + rows.recent
    if (shown.isEmpty()) return sources
    return sources.filterNot { option ->
        option.id != selectedSourceId && option.stationUri != null && shown.any { sameStation(option.stationUri, it.uri) }
    }
}
