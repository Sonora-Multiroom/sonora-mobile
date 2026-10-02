package ai.sonora.mobile.domain

sealed interface RoomsContent {
    data object NoRooms : RoomsContent

    data class Rooms(
        val inUse: Int,
        val total: Int,
        val masterMuted: Boolean,
        val cards: List<NowPlayingCard>,
        val idle: List<IdleRow>,
    ) : RoomsContent
}

enum class CardStatus { Playing, Paused, LiveStream, Starting, Stopping, Failed, Unknown }

/** The single action a card offers. */
enum class CardAction { Stop, Pause, Resume }

enum class IdleState { NothingPlaying, TurnedOff, NotConnected }

data class NowPlayingCard(
    /** The route id. */
    val key: String,
    val title: String,
    val isGroup: Boolean,
    val memberNames: List<String>,
    val sourceName: String,
    val kind: SourceKind,
    val status: CardStatus,
    /** The loudest member for a group; null when the target type is unknown (no pill then). */
    val volume: Int?,
    /** Whether the pill has something it can set; false shows the value but ignores drags. */
    val volumeAdjustable: Boolean,
    /** Group only: the known members' volumes, the base for scaling a drag. */
    val memberVolumes: Map<String, Int>,
    val muted: Boolean,
    val notConnected: Boolean,
    val action: CardAction,
    val actionEnabled: Boolean,
    /** A single listed room's id, the target of its volume; null for groups and unknown targets. */
    val roomId: String? = null,
)

data class IdleRow(val roomId: String, val name: String, val state: IdleState)

/** Joins outputs + groups + routes + inputs into what the Rooms screen draws (data-model.md). */
object RoomsBuilder {
    fun build(snapshot: HubSnapshot): RoomsContent {
        if (snapshot.rooms.isEmpty()) return RoomsContent.NoRooms

        val rooms = snapshot.rooms.associateBy { it.id }
        val groups = snapshot.groups.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.id }
        val master = snapshot.masterMuted

        // STOPPED routes are history; FAILED and Unknown ones still occupy their rooms.
        val live = snapshot.routes.filter { it.status != RouteStatus.Stopped }

        val occupied = mutableSetOf<String>()
        val cards = live.map { route ->
            val source = sources[route.inputId]
            val (status, action, actionEnabled) = statusAndAction(route, source?.uri)
            val target = describe(route.target, rooms, groups, master)
            occupied += target.occupies
            NowPlayingCard(
                key = route.id,
                title = target.title,
                isGroup = target.isGroup,
                memberNames = target.memberNames,
                sourceName = source?.name ?: route.inputId,
                kind = source?.kind ?: inferSourceKind(SourceOrigin.Configured, null),
                status = status,
                volume = target.volume,
                volumeAdjustable = target.adjustable,
                memberVolumes = target.memberVolumes,
                muted = target.muted,
                notConnected = target.notConnected,
                action = action,
                actionEnabled = actionEnabled,
                roomId = target.roomId,
            )
        }.sortedWith(compareBy({ it.title.lowercase() }, { it.key }))

        val idle = snapshot.rooms
            .filter { it.id !in occupied }
            .map { room ->
                val state = when {
                    !room.enabled -> IdleState.TurnedOff
                    !room.available -> IdleState.NotConnected
                    else -> IdleState.NothingPlaying
                }
                IdleRow(room.id, room.name, state)
            }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.roomId }))

        return RoomsContent.Rooms(
            inUse = occupied.size,
            total = snapshot.rooms.size,
            masterMuted = master,
            cards = cards,
            idle = idle,
        )
    }

    private data class Described(
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

    private fun describe(
        target: Target,
        rooms: Map<String, Room>,
        groups: Map<String, Group>,
        masterMuted: Boolean,
    ): Described = when (target) {
        is Target.Room -> {
            val room = rooms[target.id]
            Described(
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
            Described(
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
        is Target.Unknown -> Described(
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

    private fun statusAndAction(route: Route, uri: String?): Triple<CardStatus, CardAction, Boolean> {
        val status = when (route.status) {
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
        return when {
            status == CardStatus.Failed || !route.pauseable -> Triple(status, CardAction.Stop, true)
            status == CardStatus.Paused -> Triple(status, CardAction.Resume, true)
            status == CardStatus.Playing || status == CardStatus.LiveStream -> Triple(status, CardAction.Pause, true)
            // Starting, Stopping, Unknown: the button shows but cannot be used yet (FR-016).
            else -> Triple(status, CardAction.Pause, false)
        }
    }
}
