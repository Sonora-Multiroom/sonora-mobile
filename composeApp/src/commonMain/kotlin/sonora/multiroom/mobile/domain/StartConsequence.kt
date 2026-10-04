package sonora.multiroom.mobile.domain

/** A playback a start touches: [source] playing in [where], its own room or group. */
data class AffectedPlayback(val source: String, val where: String)

sealed interface ConsequenceLine {
    /** Warning colour: starting stops these. */
    data class WillStop(val items: List<AffectedPlayback>) : ConsequenceLine

    data class PlaysAlongside(val items: List<AffectedPlayback>) : ConsequenceLine
    data class WillBeLowered(val items: List<AffectedPlayback>) : ConsequenceLine
    data class AlreadyPlaying(val source: String, val target: String) : ConsequenceLine
}

/** Rooms of a chosen group that will stay silent. */
data class WontPlay(val turnedOff: List<String>, val notConnected: List<String>)

sealed interface MuteNote {
    data object AllRoomsMuted : MuteNote
    data class TargetMuted(val name: String) : MuteNote
}

/** What starting will do, shown above the Play button (FR-010, FR-011). */
data class Consequence(
    val line: ConsequenceLine?,
    val wontPlay: WontPlay?,
    val mute: MuteNote?,
)

/**
 * The mode the hub will apply, since the app names none (FR-010): a link replaces; a source uses
 * its declared default; none or an unknown one replaces, so the user is never told less than may
 * stop. Never [JoinMode.Unknown].
 */
fun effectiveJoinMode(what: StartWhat, snapshot: HubSnapshot): JoinMode = when (what) {
    is StartWhat.Link -> JoinMode.Replace
    is StartWhat.Source -> when (val mode = snapshot.sources.firstOrNull { it.id == what.id }?.defaultJoinMode) {
        JoinMode.Mix, JoinMode.Announcement -> mode
        else -> JoinMode.Replace
    }
}

/** Decided here and only here (research R4); the wording is `consequenceLineText` and friends. */
object StartConsequence {
    fun of(snapshot: HubSnapshot, what: StartWhat, target: Target): Consequence {
        val rooms = snapshot.rooms.associateBy { it.id }
        val groups = snapshot.groups.associateBy { it.id }
        val sourceNames = snapshot.sources.associate { it.id to it.name }
        val chosen = describeTarget(target, rooms, groups, snapshot.masterMuted)

        // Everything covering a room of the target. A group's playback stops as a whole, so every
        // known member counts, turned off or not connected ones included.
        val byRoom = routesByRoom(snapshot)
        val affectedIds = chosen.occupies.flatMap { byRoom[it].orEmpty() }.map { it.id }.toSet()
        val affected = liveRoutes(snapshot).filter { it.id in affectedIds }

        fun playback(route: Route): AffectedPlayback {
            val where = describeTarget(route.target, rooms, groups, masterMuted = false).title
            return AffectedPlayback(sourceNames[route.inputId] ?: route.inputId, where)
        }

        val alreadyThere = (what as? StartWhat.Source)?.let { w ->
            liveRoutes(snapshot).any { it.inputId == w.id && it.target == target }
        } == true

        val withoutAnnouncements = affected.filterNot { it.isAnnouncement }.map(::playback)
        val line: ConsequenceLine? = when {
            alreadyThere -> ConsequenceLine.AlreadyPlaying(
                (what as StartWhat.Source).let { sourceNames[it.id] ?: it.id },
                chosen.title,
            )
            else -> when (effectiveJoinMode(what, snapshot)) {
                JoinMode.Mix -> withoutAnnouncements.takeIf { it.isNotEmpty() }?.let(ConsequenceLine::PlaysAlongside)
                JoinMode.Announcement -> affected.map(::playback).takeIf { it.isNotEmpty() }?.let(ConsequenceLine::WillBeLowered)
                else -> withoutAnnouncements.takeIf { it.isNotEmpty() }?.let(ConsequenceLine::WillStop)
            }
        }

        val members = (target as? Target.Group)?.let { g -> groups[g.id]?.memberIds.orEmpty().mapNotNull { rooms[it] } }.orEmpty()
        val turnedOff = members.filter { !it.enabled }.map { it.name }
        val notConnected = members.filter { it.enabled && !it.available }.map { it.name }
        val wontPlay = if (turnedOff.isEmpty() && notConnected.isEmpty()) null else WontPlay(turnedOff, notConnected)

        val mute = when {
            snapshot.masterMuted -> MuteNote.AllRoomsMuted
            target is Target.Room && rooms[target.id]?.muted == true -> MuteNote.TargetMuted(chosen.title)
            target is Target.Group && (groups[target.id]?.muted == true || (members.isNotEmpty() && members.all { it.muted })) ->
                MuteNote.TargetMuted(chosen.title)
            else -> null
        }

        return Consequence(line, wontPlay, mute)
    }
}
