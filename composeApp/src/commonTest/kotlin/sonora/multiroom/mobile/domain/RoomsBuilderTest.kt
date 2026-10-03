package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomsBuilderTest {
    private fun room(
        id: String,
        name: String = id,
        volume: Int = 50,
        muted: Boolean = false,
        enabled: Boolean = true,
        available: Boolean = true,
    ) = Room(id, name, volume, muted, enabled, available)

    private fun group(id: String, members: List<String>, name: String = id, muted: Boolean = false) =
        Group(id, name, members, muted, enabled = true)

    private fun source(
        id: String,
        uri: String?,
        name: String = id,
        origin: SourceOrigin = SourceOrigin.Configured,
        pauseable: Boolean = false,
    ) = Source(id, name, uri, origin, pauseable, enabled = true, kind = inferSourceKind(origin, uri))

    private fun route(
        id: String,
        inputId: String,
        target: Target,
        status: RouteStatus = RouteStatus.Active,
        paused: Boolean = false,
        pauseable: Boolean = false,
    ) = Route(id, inputId, target, status, paused, pauseable, transferable = false)

    private fun snapshot(
        rooms: List<Room> = emptyList(),
        groups: List<Group> = emptyList(),
        routes: List<Route> = emptyList(),
        sources: List<Source> = emptyList(),
        masterMuted: Boolean = false,
    ) = HubSnapshot(rooms, groups, routes, sources, masterMuted)

    private fun build(s: HubSnapshot): RoomsContent.Rooms {
        val content = RoomsBuilder.build(s)
        assertIs<RoomsContent.Rooms>(content)
        return content
    }

    private val radio = source("radio", "http://radio.example/stream", name = "Radio Paradise")
    private val playlist = source("playlist", "file:///music/morning.m3u", name = "Morning playlist", pauseable = true)

    /** The design sample (design/screens/Main.dc.html). */
    private val sample = snapshot(
        rooms = listOf(
            room("living", "Living Room", volume = 70),
            room("kitchen", "Kitchen", volume = 55),
            room("office", "Office", volume = 40),
            room("bedroom", "Bedroom"),
            room("patio", "Patio", enabled = false),
        ),
        groups = listOf(group("downstairs", listOf("living", "kitchen"), name = "Downstairs")),
        sources = listOf(radio, playlist),
        routes = listOf(
            route("r1", "radio", Target.Group("downstairs")),
            route("r2", "playlist", Target.Room("office"), paused = true, pauseable = true),
        ),
    )

    @Test
    fun designSampleHasTwoSortedCardsAndThreeOfFiveInUse() {
        val c = build(sample)
        assertEquals(listOf("Downstairs", "Office"), c.cards.map { it.title })
        assertEquals(3, c.inUse)
        assertEquals(5, c.total)
        assertFalse(c.masterMuted)
    }

    @Test
    fun groupCardInDesignSample() {
        val card = build(sample).cards.first { it.title == "Downstairs" }
        assertTrue(card.isGroup)
        assertEquals(listOf("Living Room", "Kitchen"), card.memberNames)
        assertEquals("Radio Paradise", card.sourceName)
        assertEquals(SourceKind.Stream, card.kind)
        assertEquals(70, card.volume)
        assertEquals(mapOf("living" to 70, "kitchen" to 55), card.memberVolumes)
        assertEquals(CardStatus.LiveStream, card.status)
        assertEquals(CardAction.Stop, card.action)
        assertTrue(card.actionEnabled)
        assertTrue(card.volumeAdjustable)
        assertEquals("r1", card.key)
    }

    @Test
    fun pausedSingleRoomCardOffersResume() {
        val card = build(sample).cards.first { it.title == "Office" }
        assertFalse(card.isGroup)
        assertEquals(SourceKind.File, card.kind)
        assertEquals(40, card.volume)
        assertEquals(CardStatus.Paused, card.status)
        assertEquals(CardAction.Resume, card.action)
        assertTrue(card.actionEnabled)
    }

    @Test
    fun idleRowsAreBedroomAndPatio() {
        val c = build(sample)
        assertEquals(
            listOf(IdleRow("bedroom", "Bedroom", IdleState.NothingPlaying), IdleRow("patio", "Patio", IdleState.TurnedOff)),
            c.idle,
        )
    }

    @Test
    fun stoppedRoutesAreIgnored() {
        val c = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Room("a"), status = RouteStatus.Stopped)),
            ),
        )
        assertEquals(emptyList(), c.cards)
        assertEquals(listOf("a"), c.idle.map { it.roomId })
        assertEquals(0, c.inUse)
    }

    @Test
    fun failedRouteHasACardWithStopAndOccupiesTheRoom() {
        val c = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Room("a"), status = RouteStatus.Failed, pauseable = true)),
            ),
        )
        val card = c.cards.single()
        assertEquals(CardStatus.Failed, card.status)
        assertEquals(CardAction.Stop, card.action)
        assertTrue(card.actionEnabled)
        assertEquals(emptyList(), c.idle)
        assertEquals(1, c.inUse)
    }

    @Test
    fun pauseIsDisabledWhileUnknownStartingOrStopping() {
        for ((status, expected) in listOf(
            RouteStatus.Unknown to CardStatus.Unknown,
            RouteStatus.Starting to CardStatus.Starting,
            RouteStatus.Stopping to CardStatus.Stopping,
        )) {
            val card = build(
                snapshot(
                    rooms = listOf(room("a")),
                    sources = listOf(playlist),
                    routes = listOf(route("r", "playlist", Target.Room("a"), status = status, pauseable = true)),
                ),
            ).cards.single()
            assertEquals(expected, card.status)
            assertEquals(CardAction.Pause, card.action, status.name)
            assertFalse(card.actionEnabled, status.name)
        }
    }

    @Test
    fun activePauseableNotPausedOffersEnabledPause() {
        val card = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(playlist),
                routes = listOf(route("r", "playlist", Target.Room("a"), pauseable = true)),
            ),
        ).cards.single()
        assertEquals(CardStatus.Playing, card.status)
        assertEquals(CardAction.Pause, card.action)
        assertTrue(card.actionEnabled)
    }

    @Test
    fun nonPauseableLineInIsPlayingNotLive() {
        val card = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(source("line", "alsa:hw:1", name = "Turntable")),
                routes = listOf(route("r", "line", Target.Room("a"))),
            ),
        ).cards.single()
        assertEquals(SourceKind.LineIn, card.kind)
        assertEquals(CardStatus.Playing, card.status)
        assertEquals(CardAction.Stop, card.action)
    }

    @Test
    fun runtimeHttpsLinkIsKindLinkButLive() {
        val card = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(source("dyn", "https://radio.example/s", origin = SourceOrigin.Runtime)),
                routes = listOf(route("r", "dyn", Target.Room("a"))),
            ),
        ).cards.single()
        assertEquals(SourceKind.Link, card.kind)
        assertEquals(CardStatus.LiveStream, card.status)
        assertEquals(CardAction.Stop, card.action)
    }

    @Test
    fun routeToAnUnlistedGroupIsDrawnWithItsIdAndOccupiesNothing() {
        val c = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Group("ghost"))),
                masterMuted = true,
            ),
        )
        val card = c.cards.single()
        assertEquals("ghost", card.title)
        assertTrue(card.isGroup)
        assertEquals(emptyList(), card.memberNames)
        assertEquals(0, card.volume)
        assertFalse(card.volumeAdjustable)
        assertTrue(card.muted)
        assertFalse(card.notConnected)
        assertEquals(listOf("a"), c.idle.map { it.roomId })
        assertEquals(0, c.inUse)
    }

    @Test
    fun groupWithAnUnlistedMemberSkipsIt() {
        val card = build(
            snapshot(
                rooms = listOf(room("a", "A", volume = 30)),
                groups = listOf(group("g", listOf("a", "ghost"))),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Group("g"))),
            ),
        ).cards.single()
        assertEquals(listOf("A"), card.memberNames)
        assertEquals(mapOf("a" to 30), card.memberVolumes)
        assertEquals(30, card.volume)
    }

    @Test
    fun routeToAnUnlistedRoomIsDrawnWithItsIdAndOccupiesNothing() {
        val c = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Room("ghost"))),
                masterMuted = true,
            ),
        )
        val card = c.cards.single()
        assertEquals("ghost", card.title)
        assertFalse(card.isGroup)
        assertEquals(0, card.volume)
        assertFalse(card.volumeAdjustable)
        assertTrue(card.muted)
        assertFalse(card.notConnected)
        assertEquals(0, c.inUse)
        assertEquals(listOf("a"), c.idle.map { it.roomId })
    }

    @Test
    fun listedRoomsAndGroupsWithKnownMembersAreAdjustable() {
        val cards = build(sample).cards
        assertTrue(cards.all { it.volumeAdjustable })
    }

    @Test
    fun groupWithNoKnownMembersIsNotAdjustable() {
        val card = build(
            snapshot(
                rooms = listOf(room("a")),
                groups = listOf(group("g", listOf("ghost"))),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Group("g"))),
            ),
        ).cards.single()
        assertEquals(0, card.volume)
        assertFalse(card.volumeAdjustable)
    }

    @Test
    fun routeWithUnlistedInputUsesTheInputIdAndLineIn() {
        val card = build(
            snapshot(
                rooms = listOf(room("a")),
                routes = listOf(route("r", "gone-input", Target.Room("a"))),
            ),
        ).cards.single()
        assertEquals("gone-input", card.sourceName)
        assertEquals(SourceKind.LineIn, card.kind)
    }

    @Test
    fun unknownTargetTypeKeepsACardWithoutVolumeAndOccupiesNothing() {
        val c = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Unknown("z1"))),
            ),
        )
        val card = c.cards.single()
        assertEquals("z1", card.title)
        assertFalse(card.isGroup)
        assertNull(card.volume)
        assertFalse(card.volumeAdjustable)
        assertEquals(CardAction.Stop, card.action)
        assertEquals(listOf("a"), c.idle.map { it.roomId })
        assertEquals(0, c.inUse)
    }

    @Test
    fun unknownOriginWithHttpIsAStream() {
        val card = build(
            snapshot(
                rooms = listOf(room("a")),
                sources = listOf(source("x", "http://x", origin = SourceOrigin.Unknown)),
                routes = listOf(route("r", "x", Target.Room("a"))),
            ),
        ).cards.single()
        assertEquals(SourceKind.Stream, card.kind)
    }

    @Test
    fun roomInTwoGroupsOnlyOneRoutedIsOccupiedAndCountedOnce() {
        val c = build(
            snapshot(
                rooms = listOf(room("a"), room("b"), room("c")),
                groups = listOf(group("g1", listOf("a", "b")), group("g2", listOf("a", "c"))),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Group("g1"))),
            ),
        )
        assertEquals(2, c.inUse)
        assertEquals(listOf("c"), c.idle.map { it.roomId })
    }

    @Test
    fun idleStatePrecedence() {
        val c = build(
            snapshot(
                rooms = listOf(
                    room("both", "Both", enabled = false, available = false),
                    room("unplugged", "Unplugged", available = false),
                ),
            ),
        )
        assertEquals(
            listOf(
                IdleRow("both", "Both", IdleState.TurnedOff),
                IdleRow("unplugged", "Unplugged", IdleState.NotConnected),
            ),
            c.idle,
        )
    }

    @Test
    fun singleRoomCardIsNotConnectedWhenTheRoomIsUnavailableAndDisabledRoomsKeepTheirCard() {
        val card = build(
            snapshot(
                rooms = listOf(room("a", available = false, enabled = false)),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Room("a"))),
            ),
        ).cards.single()
        assertTrue(card.notConnected)
    }

    @Test
    fun groupCardIsNotConnectedWhenAnyMemberIsUnavailable() {
        fun card(secondAvailable: Boolean) = build(
            snapshot(
                rooms = listOf(room("a"), room("b", available = secondAvailable)),
                groups = listOf(group("g", listOf("a", "b"))),
                sources = listOf(radio),
                routes = listOf(route("r", "radio", Target.Group("g"))),
            ),
        ).cards.single()
        assertTrue(card(false).notConnected)
        assertFalse(card(true).notConnected)
    }

    @Test
    fun mutedFollowsRoomGroupAndMasterMute() {
        fun cards(roomMuted: Boolean = false, groupMuted: Boolean = false, master: Boolean = false) = build(
            snapshot(
                rooms = listOf(room("a", muted = roomMuted), room("b"), room("c")),
                groups = listOf(group("g", listOf("b", "c"), muted = groupMuted)),
                sources = listOf(radio),
                routes = listOf(
                    route("r1", "radio", Target.Room("a")),
                    route("r2", "radio", Target.Group("g")),
                ),
                masterMuted = master,
            ),
        ).cards.associate { it.key to it.muted }

        assertEquals(mapOf("r1" to false, "r2" to false), cards())
        assertEquals(mapOf("r1" to true, "r2" to false), cards(roomMuted = true))
        assertEquals(mapOf("r1" to false, "r2" to true), cards(groupMuted = true))
        assertEquals(mapOf("r1" to true, "r2" to true), cards(master = true))
    }

    @Test
    fun sortingIsCaseInsensitiveWithIdTiebreak() {
        val c = build(
            snapshot(rooms = listOf(room("z1", "Kitchen"), room("a1", "Kitchen"), room("b", "bedroom"))),
        )
        assertEquals(listOf("b", "a1", "z1"), c.idle.map { it.roomId })
    }

    @Test
    fun noRoomsIsItsOwnState() {
        assertEquals(RoomsContent.NoRooms, RoomsBuilder.build(snapshot()))
    }
}
