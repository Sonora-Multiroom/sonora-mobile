package sonora.multiroom.mobile.domain

enum class ItemKind { Room, Group, Source }

data class ItemKey(val kind: ItemKind, val id: String)

/** What a change would stop; the dialog text is made from it (`ui/settings/SettingsText.kt`). */
sealed interface Confirmation {
    data class TurnOffRoom(val room: String, val sources: List<String>) : Confirmation
    data class TurnOffGroup(val group: String, val sources: List<String>, val members: List<String>) : Confirmation
}

/**
 * Null means "send at once". A room that is on and covered by any live route, its own or a group's,
 * or a group that is on and has its own live route, asks first (research R4); turning on, a source
 * (FR-014) and anything the hub no longer lists never do. The rules are those of [SettingsBuilder].
 */
fun turnOffConfirmation(key: ItemKey, snapshot: HubSnapshot): Confirmation? {
    val names = sourceNames(snapshot)
    return when (key.kind) {
        ItemKind.Room -> {
            val room = snapshot.rooms.firstOrNull { it.id == key.id }?.takeIf { it.enabled } ?: return null
            val sources = playingNames(routesByRoom(snapshot)[room.id].orEmpty(), names)
            if (sources.isEmpty()) null else Confirmation.TurnOffRoom(room.name, sources)
        }

        ItemKind.Group -> {
            val group = snapshot.groups.firstOrNull { it.id == key.id }?.takeIf { it.enabled } ?: return null
            val own = liveRoutes(snapshot).filter { it.target == Target.Group(group.id) }
            val sources = playingNames(own, names)
            if (sources.isEmpty()) return null
            val byId = snapshot.rooms.associateBy { it.id }
            Confirmation.TurnOffGroup(group.name, sources, group.memberIds.mapNotNull { byId[it]?.name })
        }

        ItemKind.Source -> null
    }
}

/** A live route uses the source, so turning it off leaves that playback running (FR-014). */
fun keepsPlaying(sourceId: String, snapshot: HubSnapshot): Boolean =
    liveRoutes(snapshot).any { it.inputId == sourceId }
