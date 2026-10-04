package sonora.multiroom.mobile.domain

import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * What the Settings tabs list, decided here and nowhere else (research R3): room status,
 * group rows and the configured sources. The strings are made by `ui/settings/SettingsText.kt`.
 */
data class SettingsContent(
    val rooms: List<RoomRow>,
    val groups: List<GroupRow>,
    val configuredSources: List<ConfiguredSourceRow>,
    val runtimeSources: List<RuntimeSourceRow>,
)

data class RoomRow(val id: String, val name: String, val enabled: Boolean, val status: RoomStatus)

sealed interface RoomStatus {
    /** Turned off; wins over everything else. */
    data object Off : RoomStatus

    /** Turned on, but the hardware is not connected. */
    data object NotConnected : RoomStatus

    /** At least one live route covers the room, its own or a group's; source names, hub order. */
    data class Playing(val sources: List<String>) : RoomStatus

    data class InGroups(val groups: List<String>) : RoomStatus
    data object Speaker : RoomStatus
}

/** [members] are the known rooms in the group's order; [playing] the sources of its own live routes. */
data class GroupRow(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val members: List<String>,
    val playing: List<String>,
)

/** Deliberately without any removal field: configured sources can never be removed (FR-016). */
data class ConfiguredSourceRow(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val kind: SourceKind,
    val detail: String?,
)

/** A source added at run time; [added] is the line under its name. */
data class RuntimeSourceRow(val id: String, val name: String, val enabled: Boolean, val added: AddedLine)

/** The parts of "Off · Added today 14:30 · removed when it stops". */
data class AddedLine(val off: Boolean, val at: AddedAt?, val autoRemove: Boolean)

/** When a runtime source was added, in the phone's time zone and 24 h time. */
sealed interface AddedAt {
    data class Today(val time: String) : AddedAt
    data class Yesterday(val time: String) : AddedAt
    data class Earlier(val day: Int, val month: Int, val time: String) : AddedAt
}

object SettingsBuilder {
    /** [now] and [zone] are for the added-when lines of runtime sources. */
    @Suppress("UNUSED_PARAMETER")
    fun build(snapshot: HubSnapshot, now: Instant, zone: TimeZone): SettingsContent {
        val byRoom = routesByRoom(snapshot)
        val names = sourceNames(snapshot)
        val roomsById = snapshot.rooms.associateBy { it.id }

        val rooms = snapshot.rooms.sortedWith(ByName { it.name to it.id }).map { room ->
            val playing = playingNames(byRoom[room.id].orEmpty(), names)
            RoomRow(
                id = room.id,
                name = room.name,
                enabled = room.enabled,
                status = when {
                    !room.enabled -> RoomStatus.Off
                    !room.available -> RoomStatus.NotConnected
                    playing.isNotEmpty() -> RoomStatus.Playing(playing)
                    else -> {
                        val groups = snapshot.groups.filter { room.id in it.memberIds }.sortedWith(ByName { it.name to it.id })
                        if (groups.isEmpty()) RoomStatus.Speaker else RoomStatus.InGroups(groups.map { it.name })
                    }
                },
            )
        }

        val live = liveRoutes(snapshot)
        val groups = snapshot.groups.sortedWith(ByName { it.name to it.id }).map { group ->
            GroupRow(
                id = group.id,
                name = group.name,
                enabled = group.enabled,
                members = group.memberIds.mapNotNull { roomsById[it]?.name },
                playing = if (group.enabled) playingNames(live.filter { it.target == Target.Group(group.id) }, names) else emptyList(),
            )
        }

        val configured = snapshot.sources
            .filter { it.origin != SourceOrigin.Runtime }
            .sortedWith(ByName { it.name to it.id })
            .map { ConfiguredSourceRow(it.id, it.name, it.enabled, it.kind, sourceDetail(it.kind, it.uri)) }

        return SettingsContent(rooms, groups, configured, runtimeSources = emptyList())
    }
}

/** A→Z, case-insensitive, the id as the tie-break (spec Edge Cases). */
internal class ByName<T>(private val key: (T) -> Pair<String, String>) : Comparator<T> {
    override fun compare(a: T, b: T): Int {
        val (nameA, idA) = key(a)
        val (nameB, idB) = key(b)
        val byName = nameA.lowercase().compareTo(nameB.lowercase())
        return if (byName != 0) byName else idA.compareTo(idB)
    }
}

internal fun sourceNames(snapshot: HubSnapshot): Map<String, String> = snapshot.sources.associate { it.id to it.name }

/** Source names of [routes] in order, without duplicates; an input the hub no longer lists shows its id. */
internal fun playingNames(routes: List<Route>, names: Map<String, String>): List<String> =
    routes.mapNotNull { r -> r.inputId.takeIf { it.isNotBlank() }?.let { names[it] ?: it } }.distinct()
