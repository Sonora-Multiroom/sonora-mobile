package sonora.multiroom.mobile.domain

sealed interface NowPlayingContent {
    /** The route is absent or STOPPED. */
    data object Gone : NowPlayingContent

    data class Playback(
        val routeId: String,
        /** The source name, else the input id. */
        val sourceName: String,
        val kind: SourceKind,
        val kindLabel: String,
        /** Host, file name or address as typed; null leaves the subtitle at just [kindLabel]. */
        val addressDetail: String?,
        /** Never [CardStatus.LiveStream]: a live stream is [CardStatus.Playing] with [live]. */
        val status: CardStatus,
        val live: Boolean,
        val target: TargetLine,
        val pauseVisible: Boolean,
        val pauseEnabled: Boolean,
        /** The button reads Resume. */
        val paused: Boolean,
        val moveVisible: Boolean,
        /** Null when the target is unknown or has no known rooms: no pills, no mute. */
        val volume: VolumeSection?,
    ) : NowPlayingContent
}

data class TargetLine(
    val target: Target,
    /** The room or group name, else the id. */
    val name: String,
    /** Group only: the known members in the group's order. */
    val memberNames: List<String>,
    /** The room, or any listed member, has `available = false`. */
    val notConnected: Boolean,
)

data class VolumeSection(
    /** The room pill, or the group pill (the loudest member is what it shows). */
    val main: PillModel,
    /** Group only: one pill per known member. */
    val members: List<PillModel>,
    val mute: MuteModel,
    /** Master mute is on: "All rooms are muted". */
    val masterMuted: Boolean,
)

data class PillModel(
    /** "main" or "member:<roomId>". */
    val key: String,
    val label: String,
    /** Hub volumes of the rooms this pill covers: the base of a drag. */
    val roomVolumes: Map<String, Int>,
    /** Master mute or the target's own mute: the pill shows muted and ignores drags. */
    val muted: Boolean,
)

data class MuteModel(val target: Target, val name: String, val muted: Boolean, val enabled: Boolean)

/** Joins one route with its source, target and rooms into what Now Playing draws (data-model.md). */
object NowPlayingBuilder {
    fun build(snapshot: HubSnapshot, routeId: String): NowPlayingContent {
        val route = liveRoutes(snapshot).firstOrNull { it.id == routeId } ?: return NowPlayingContent.Gone
        val rooms = snapshot.rooms.associateBy { it.id }
        val groups = snapshot.groups.associateBy { it.id }
        val source = snapshot.sources.firstOrNull { it.id == route.inputId }
        val master = snapshot.masterMuted

        val cardStatus = cardStatus(route, source?.uri)
        val live = cardStatus == CardStatus.LiveStream
        // A live stream is shown as Playing plus the badge and the line, not as chip text (FR-006).
        val status = if (live) CardStatus.Playing else cardStatus
        val described = describeTarget(route.target, rooms, groups, master)
        val kind = source?.kind ?: inferSourceKind(SourceOrigin.Configured, null)

        return NowPlayingContent.Playback(
            routeId = route.id,
            sourceName = source?.name ?: route.inputId,
            kind = kind,
            kindLabel = kindLabel(kind),
            addressDetail = addressDetail(kind, source?.uri),
            status = status,
            live = live,
            target = TargetLine(route.target, described.title, described.memberNames, described.notConnected),
            pauseVisible = route.pauseable,
            pauseEnabled = status == CardStatus.Playing || status == CardStatus.Paused,
            paused = status == CardStatus.Paused,
            moveVisible = route.transferable && status == CardStatus.Playing && route.target !is Target.Unknown,
            volume = volumeSection(route.target, rooms, groups, master),
        )
    }

    private fun volumeSection(
        target: Target,
        rooms: Map<String, Room>,
        groups: Map<String, Group>,
        master: Boolean,
    ): VolumeSection? = when (target) {
        is Target.Room -> rooms[target.id]?.let { room ->
            val muted = master || room.muted
            VolumeSection(
                main = PillModel("main", room.name, mapOf(room.id to room.volume), muted),
                members = emptyList(),
                mute = MuteModel(target, room.name, muted, enabled = !master),
                masterMuted = master,
            )
        }

        is Target.Group -> groups[target.id]?.let { group ->
            val members = group.memberIds.mapNotNull { rooms[it] }
            if (members.isEmpty()) return@let null
            val muted = master || group.muted
            VolumeSection(
                main = PillModel("main", group.name, members.associate { it.id to it.volume }, muted),
                members = members.map { PillModel("member:${it.id}", it.name, mapOf(it.id to it.volume), master || it.muted) },
                mute = MuteModel(target, group.name, muted, enabled = !master),
                masterMuted = master,
            )
        }

        is Target.Unknown -> null
    }
}
