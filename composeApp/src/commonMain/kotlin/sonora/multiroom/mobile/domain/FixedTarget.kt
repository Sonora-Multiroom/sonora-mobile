package sonora.multiroom.mobile.domain

/**
 * The target Change source (MOB-008) is fixed on, as the card in Start Playback draws it.
 * [unusable] is null while a start may be sent there.
 */
data class FixedTargetCard(
    /** [Target.Room] or [Target.Group]. */
    val target: Target,
    val name: String,
    val status: TargetStatus,
    val unusable: Unusable?,
)

/** Why a fixed target cannot take a start right now. */
enum class Unusable { TurnedOff, AllRoomsTurnedOff, NotConnected, NoRooms, Removed }

/** Decided here and only here; the wording is `fixedTargetStatusText` / `fixedTargetNoteText`. */
object FixedTarget {
    /**
     * [knownName] is the name Now Playing showed (or the last one seen), used when the target is
     * not in [snapshot] any more; the id is the last resort.
     */
    fun of(snapshot: HubSnapshot, target: Target, knownName: String?): FixedTargetCard {
        val statuses = TargetStatuses(snapshot)
        return when (target) {
            is Target.Room -> {
                val room = snapshot.rooms.firstOrNull { it.id == target.id }
                    ?: return removed(target, knownName ?: target.id)
                val unusable = when {
                    !room.enabled -> Unusable.TurnedOff
                    !room.available -> Unusable.NotConnected
                    else -> null
                }
                FixedTargetCard(target, room.name, statuses.of(room), unusable)
            }

            is Target.Group -> {
                val group = snapshot.groups.firstOrNull { it.id == target.id }
                    ?: return removed(target, knownName ?: target.id)
                val members = group.memberIds.mapNotNull { id -> snapshot.rooms.firstOrNull { it.id == id } }
                val unusable = when {
                    !group.enabled -> Unusable.TurnedOff
                    members.isEmpty() -> Unusable.NoRooms
                    members.all { !it.enabled } -> Unusable.AllRoomsTurnedOff
                    members.none { it.enabled && it.available } -> Unusable.NotConnected
                    else -> null
                }
                FixedTargetCard(target, group.name, statuses.of(group), unusable)
            }

            // Never passed (Change source is not offered for an unknown target type).
            is Target.Unknown -> removed(target, knownName ?: target.id)
        }
    }

    private fun removed(target: Target, name: String) =
        FixedTargetCard(target, name, TargetStatus.Removed, Unusable.Removed)
}
