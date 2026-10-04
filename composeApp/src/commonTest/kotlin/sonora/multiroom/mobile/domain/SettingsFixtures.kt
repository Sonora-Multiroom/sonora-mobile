package sonora.multiroom.mobile.domain

/** Short builders for the Settings domain tests. */
internal fun room(id: String, name: String = id, enabled: Boolean = true, available: Boolean = true) =
    Room(id, name, volume = 30, muted = false, enabled = enabled, available = available)

internal fun group(id: String, name: String = id, members: List<String>, enabled: Boolean = true) =
    Group(id, name, members, muted = false, enabled = enabled)

internal fun source(
    id: String,
    name: String = id,
    origin: SourceOrigin = SourceOrigin.Configured,
    uri: String? = "http://radio.example/$id",
    enabled: Boolean = true,
    autoRemove: Boolean = false,
    createdAt: kotlin.time.Instant? = null,
) = Source(
    id, name, uri, origin, pauseable = false, enabled = enabled, kind = inferSourceKind(origin, uri),
    autoRemove = autoRemove, createdAt = createdAt,
)

internal fun route(id: String, input: String, target: Target, status: RouteStatus = RouteStatus.Active) =
    Route(id, input, target, status, paused = false, pauseable = false, transferable = true)

internal fun snapshot(
    rooms: List<Room> = emptyList(),
    groups: List<Group> = emptyList(),
    routes: List<Route> = emptyList(),
    sources: List<Source> = emptyList(),
) = HubSnapshot(rooms, groups, routes, sources, masterMuted = false)
