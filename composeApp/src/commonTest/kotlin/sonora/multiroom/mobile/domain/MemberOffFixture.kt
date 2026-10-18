package sonora.multiroom.mobile.domain

/**
 * The UI contract's "2026-10-17" fixture: Living Room = Office Speaker + Kitchen Speakers plays
 * KissFM, and Office Speaker was turned off. [Variant.A]: the hub lists the route on `kitchen`
 * only; [Variant.B]: the hub gives no list; [Variant.C]: Office Speaker is back on and listed.
 */
internal object MemberOff {
    enum class Variant { A, B, C }

    val kissFm = Source(
        "kiss", "KissFM", "http://online.kissfm.ua/KissFM", SourceOrigin.Configured,
        pauseable = false, enabled = true, kind = SourceKind.Stream,
    )

    fun rooms(variant: Variant) = listOf(
        Room("office", "Office Speaker", 40, muted = false, enabled = variant == Variant.C, available = true),
        Room("kitchen", "Kitchen Speakers", 30, muted = false, enabled = true, available = true),
        Room("bedroom", "Bedroom", 20, muted = false, enabled = true, available = true),
        Room("bathroom", "Bathroom", 10, muted = false, enabled = true, available = true),
    )

    val living = Group("living", "Living Room", listOf("office", "kitchen"), muted = false, enabled = true)

    fun route(variant: Variant) = Route(
        "r1", "kiss", Target.Group("living"), RouteStatus.Active,
        paused = false, pauseable = false, transferable = true,
        outputs = when (variant) {
            Variant.A -> listOf("kitchen")
            Variant.B -> null
            Variant.C -> listOf("kitchen", "office")
        },
    )

    fun snapshot(variant: Variant) = HubSnapshot(
        rooms = rooms(variant),
        groups = listOf(living),
        routes = listOf(route(variant)),
        sources = listOf(kissFm),
        masterMuted = false,
    )
}
