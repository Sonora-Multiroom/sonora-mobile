package sonora.multiroom.mobile.domain

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

        val live = liveRoutes(snapshot)

        val occupied = mutableSetOf<String>()
        val cards = live.map { route ->
            val source = sources[route.inputId]
            val (status, action, actionEnabled) = statusAndAction(route, source?.uri)
            val target = describeTarget(route.target, rooms, groups, master)
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

    private fun statusAndAction(route: Route, uri: String?): Triple<CardStatus, CardAction, Boolean> {
        val status = cardStatus(route, uri)
        return when {
            status == CardStatus.Failed || !route.pauseable -> Triple(status, CardAction.Stop, true)
            status == CardStatus.Paused -> Triple(status, CardAction.Resume, true)
            status == CardStatus.Playing || status == CardStatus.LiveStream -> Triple(status, CardAction.Pause, true)
            // Starting, Stopping, Unknown: the button shows but cannot be used yet (FR-016).
            else -> Triple(status, CardAction.Pause, false)
        }
    }
}
