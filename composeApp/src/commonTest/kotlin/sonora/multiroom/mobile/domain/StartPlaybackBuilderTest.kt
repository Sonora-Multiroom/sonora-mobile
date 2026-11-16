package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartPlaybackBuilderTest {
    private fun room(id: String, name: String = id, enabled: Boolean = true, available: Boolean = true) =
        Room(id, name, 50, muted = false, enabled = enabled, available = available)

    private fun group(id: String, name: String, vararg members: String, enabled: Boolean = true) =
        Group(id, name, members.toList(), muted = false, enabled = enabled)

    private fun source(
        id: String, name: String = id, uri: String? = "http://x/$id", enabled: Boolean = true,
        origin: SourceOrigin = SourceOrigin.Configured, pauseable: Boolean = false,
    ) = Source(id, name, uri, origin, pauseable, enabled, inferSourceKind(origin, uri))

    private fun route(
        id: String, input: String, target: Target, status: RouteStatus = RouteStatus.Active,
        paused: Boolean = false, pauseable: Boolean = false, joinMode: JoinMode = JoinMode.Replace,
    ) = Route(id, input, target, status, paused, pauseable, transferable = true, joinMode = joinMode)

    private fun snapshot(
        rooms: List<Room> = emptyList(),
        groups: List<Group> = emptyList(),
        routes: List<Route> = emptyList(),
        sources: List<Source> = emptyList(),
    ) = HubSnapshot(rooms, groups, routes, sources, masterMuted = false)

    private fun statusOf(snapshot: HubSnapshot, target: Target): TargetStatus =
        StartPlaybackBuilder.build(snapshot).targets.first { it.target == target }.status

    private fun roomStatus(id: String, vararg routes: Route, rooms: List<Room> = listOf(room(id)), groups: List<Group> = emptyList(), sources: List<Source> = listOf(source("jazz", "Jazz24"), source("news", "News"))) =
        statusOf(snapshot(rooms, groups, routes.toList(), sources), Target.Room(id))

    // ---- sources --------------------------------------------------------------------------------

    @Test
    fun turnedOffSourcesAreLeftOutAndRuntimeOnesAreKept() {
        val content = StartPlaybackBuilder.build(
            snapshot(
                sources = listOf(
                    source("a", "Alpha"),
                    source("off", "Off", enabled = false),
                    source("link", "A link", uri = "https://s.example/t", origin = SourceOrigin.Runtime),
                ),
            ),
        )
        assertEquals(listOf("link", "a"), content.sources.map { it.id }) // "A link" sorts before "Alpha"
        assertEquals(SourceKind.Link, content.sources.single { it.id == "link" }.kind)
    }

    @Test
    fun sourcesAreSortedByNameIgnoringCaseWithIdAsTieBreak() {
        val content = StartPlaybackBuilder.build(
            snapshot(
                sources = listOf(
                    source("3", "Beta"), source("2", "alpha"), source("1", "alpha"), source("4", "Zulu"),
                ),
            ),
        )
        assertEquals(listOf("1", "2", "3", "4"), content.sources.map { it.id })
    }

    @Test
    fun sourceKindComesFromTheSingleInferenceFunction() {
        val content = StartPlaybackBuilder.build(
            snapshot(
                sources = listOf(
                    source("s", "S", uri = "http://x/s"),
                    source("f", "F", uri = "file:///a.mp3"),
                    source("l", "L", uri = "alsa:hw0"),
                ),
            ),
        )
        assertEquals(listOf(SourceKind.File, SourceKind.LineIn, SourceKind.Stream), content.sources.map { it.kind })
    }

    // ---- target order ---------------------------------------------------------------------------

    @Test
    fun targetsAreSelectableGroupsThenSelectableRoomsThenTheRest() {
        val content = StartPlaybackBuilder.build(
            snapshot(
                rooms = listOf(
                    room("o", "Office"), room("b", "bedroom"), room("p", "Patio", enabled = false),
                    room("k", "Kitchen", available = false),
                ),
                groups = listOf(
                    group("z", "Zone", "o", "b"), group("a", "Attic", "o"),
                    group("off", "Aaa off", "o", enabled = false), group("empty", "Empty"),
                ),
            ),
        )
        assertEquals(
            listOf("a", "z", "b", "o", "off", "empty", "k", "p").map { it },
            content.targets.map { (it.target as? Target.Room)?.id ?: (it.target as Target.Group).id },
        )
        assertEquals(
            listOf(true, true, true, true, false, false, false, false),
            content.targets.map { it.selectable },
        )
    }

    @Test
    fun unknownTargetTypesAreNotListed() {
        val content = StartPlaybackBuilder.build(
            snapshot(rooms = listOf(room("a")), routes = listOf(route("r", "jazz", Target.Unknown("z")))),
        )
        assertEquals(listOf<Target>(Target.Room("a")), content.targets.map { it.target })
    }

    // ---- room status ----------------------------------------------------------------------------

    @Test
    fun anIdleRoomReadsIdle() = assertEquals(TargetStatus.Idle, roomStatus("a"))

    @Test
    fun anOwnRouteShowsItsStateAndSource() {
        fun status(r: Route) = roomStatus("a", r)
        val own = Target.Room("a")
        assertEquals(TargetStatus.Playing(CardStatus.Playing, listOf("Jazz24")), status(route("r", "jazz", own, pauseable = true)))
        assertEquals(TargetStatus.Playing(CardStatus.Paused, listOf("Jazz24")), status(route("r", "jazz", own, paused = true, pauseable = true)))
        assertEquals(TargetStatus.Playing(CardStatus.LiveStream, listOf("Jazz24")), status(route("r", "jazz", own)))
        assertEquals(TargetStatus.Playing(CardStatus.Starting, listOf("Jazz24")), status(route("r", "jazz", own, RouteStatus.Starting)))
        assertEquals(TargetStatus.Playing(CardStatus.Stopping, listOf("Jazz24")), status(route("r", "jazz", own, RouteStatus.Stopping)))
        assertEquals(TargetStatus.Playing(CardStatus.Failed, listOf("Jazz24")), status(route("r", "jazz", own, RouteStatus.Failed)))
        assertEquals(TargetStatus.Playing(CardStatus.Unknown, listOf("Jazz24")), status(route("r", "jazz", own, RouteStatus.Unknown)))
    }

    @Test
    fun severalOwnRoutesListTheirSourcesInHubOrderWithTheFirstOnesState() {
        val own = Target.Room("a")
        val status = roomStatus(
            "a",
            route("r1", "jazz", own, RouteStatus.Starting),
            route("r2", "news", own),
        )
        assertEquals(TargetStatus.Playing(CardStatus.Starting, listOf("Jazz24", "News")), status)
    }

    @Test
    fun aRoomCoveredOnlyByAGroupRouteReadsInGroup() {
        val status = roomStatus(
            "a",
            route("r", "jazz", Target.Group("g")),
            rooms = listOf(room("a"), room("b")),
            groups = listOf(group("g", "Downstairs", "a", "b")),
        )
        assertEquals(TargetStatus.InGroup("Downstairs"), status)
    }

    @Test
    fun anOwnRouteWinsOverAGroupRoute() {
        val status = roomStatus(
            "a",
            route("r1", "jazz", Target.Group("g")),
            route("r2", "news", Target.Room("a")),
            rooms = listOf(room("a"), room("b")),
            groups = listOf(group("g", "Downstairs", "a", "b")),
        )
        assertEquals(TargetStatus.Playing(CardStatus.LiveStream, listOf("News")), status)
    }

    @Test
    fun turnedOffAndNotConnectedRooms() {
        val own = route("r", "jazz", Target.Room("a"))
        assertEquals(TargetStatus.TurnedOff, roomStatus("a", rooms = listOf(room("a", enabled = false))))
        assertEquals(TargetStatus.NotConnected, roomStatus("a", rooms = listOf(room("a", available = false))))
        assertEquals(TargetStatus.TurnedOff, roomStatus("a", rooms = listOf(room("a", enabled = false, available = false))))
        // Turned off wins even while it still plays, and it cannot be picked.
        assertEquals(TargetStatus.TurnedOff, roomStatus("a", own, rooms = listOf(room("a", enabled = false))))
        val option = StartPlaybackBuilder.build(snapshot(listOf(room("a", enabled = false)), routes = listOf(own))).targets.single()
        assertFalse(option.selectable)
    }

    @Test
    fun anAnnouncementOnARoomIsIgnored() {
        val announcement = route("r", "jazz", Target.Room("a"), joinMode = JoinMode.Announcement)
        assertEquals(TargetStatus.Idle, roomStatus("a", announcement))
    }

    @Test
    fun aRouteWithAnUnknownJoinModeStillCounts() {
        val unknown = route("r", "jazz", Target.Room("a"), joinMode = JoinMode.Unknown)
        assertEquals(TargetStatus.Playing(CardStatus.LiveStream, listOf("Jazz24")), roomStatus("a", unknown))
    }

    // ---- group status ---------------------------------------------------------------------------

    private val members = listOf(room("living", "Living Room"), room("kitchen", "Kitchen"), room("patio", "Patio"))

    private fun groupStatus(
        g: Group = group("down", "Downstairs", "living", "kitchen"),
        rooms: List<Room> = members,
        vararg routes: Route,
    ) = StartPlaybackBuilder.build(snapshot(rooms, listOf(g), routes.toList(), listOf(source("jazz", "Jazz24"), source("news", "News"))))
        .targets.single { it.target == Target.Group(g.id) }

    @Test
    fun aGroupWithItsOwnRouteReadsGroupPlaying() {
        val t = groupStatus(routes = arrayOf(route("r", "jazz", Target.Group("down"))))
        assertEquals(TargetStatus.GroupPlaying(CardStatus.Playing, listOf("Jazz24")), t.status)
        assertTrue(t.selectable)
    }

    @Test
    fun anIdleGroupListsItsPlayableMembers() {
        val t = groupStatus()
        assertEquals(TargetStatus.GroupMembers(listOf("Living Room", "Kitchen")), t.status)
    }

    @Test
    fun aGroupWithUnplayableMembersStaysSelectableOverTheRest() {
        val rooms = listOf(room("living", "Living Room"), room("kitchen", "Kitchen", enabled = false), room("patio", "Patio", available = false))
        val t = groupStatus(group("g", "G", "living", "kitchen", "patio"), rooms)
        assertEquals(TargetStatus.GroupMembers(listOf("Living Room")), t.status)
        assertTrue(t.selectable)
    }

    @Test
    fun aGroupWithNoKnownMembersReadsNoRooms() {
        val t = groupStatus(group("g", "G", "ghost"))
        assertEquals(TargetStatus.NoRooms, t.status)
        assertFalse(t.selectable)
        assertEquals(TargetStatus.NoRooms, groupStatus(group("g", "G")).status)
    }

    @Test
    fun aGroupWhoseMembersAreAllTurnedOffReadsTurnedOff() {
        val rooms = listOf(room("living", enabled = false), room("kitchen", enabled = false))
        val t = groupStatus(group("g", "G", "living", "kitchen"), rooms)
        assertEquals(TargetStatus.TurnedOff, t.status)
        assertFalse(t.selectable)
    }

    @Test
    fun aGroupWithNoPlayableMemberThatIsNotAllTurnedOffReadsNotConnected() {
        val rooms = listOf(room("living", enabled = false), room("kitchen", available = false))
        val t = groupStatus(group("g", "G", "living", "kitchen"), rooms)
        assertEquals(TargetStatus.NotConnected, t.status)
        assertFalse(t.selectable)
        val allGone = groupStatus(group("g", "G", "living", "kitchen"), listOf(room("living", available = false), room("kitchen", available = false)))
        assertEquals(TargetStatus.NotConnected, allGone.status)
    }

    @Test
    fun aTurnedOffGroupReadsTurnedOffEvenWhilePlaying() {
        val g = group("down", "Downstairs", "living", "kitchen", enabled = false)
        assertEquals(TargetStatus.TurnedOff, groupStatus(g).status)
        val playing = groupStatus(g, routes = arrayOf(route("r", "jazz", Target.Group("down"))))
        assertEquals(TargetStatus.TurnedOff, playing.status)
        assertFalse(playing.selectable)
    }

    @Test
    fun anAnnouncementOnAGroupIsIgnored() {
        val t = groupStatus(routes = arrayOf(route("r", "jazz", Target.Group("down"), joinMode = JoinMode.Announcement)))
        assertEquals(TargetStatus.GroupMembers(listOf("Living Room", "Kitchen")), t.status)
    }

    @Test
    fun aRoomInsideAGroupThatPlaysReadsInGroupInTheFullList() {
        val content = StartPlaybackBuilder.build(
            snapshot(members, listOf(group("down", "Downstairs", "living", "kitchen")), listOf(route("r", "jazz", Target.Group("down"))), listOf(source("jazz", "Jazz24"))),
        )
        assertEquals(TargetStatus.InGroup("Downstairs"), content.targets.single { it.target == Target.Room("living") }.status)
        assertEquals(TargetStatus.Idle, content.targets.single { it.target == Target.Room("patio") }.status)
    }

    @Test
    fun aGroupPlaybackCarriesItsStatusAndALiveStreamReadsPlaying() {
        val g = group("down", "Downstairs", "a", "b")
        val rooms = listOf(room("a"), room("b"))
        fun status(vararg routes: Route, sources: List<Source>) = statusOf(snapshot(rooms, listOf(g), routes.toList(), sources), Target.Group("down"))
        val paused = status(route("r1", "pl", Target.Group("down"), paused = true, pauseable = true), sources = listOf(source("pl", "Playlist", pauseable = true)))
        assertEquals(TargetStatus.GroupPlaying(CardStatus.Paused, listOf("Playlist")), paused)
        val live = status(route("r1", "jazz", Target.Group("down")), sources = listOf(source("jazz", "Jazz24")))
        assertEquals(TargetStatus.GroupPlaying(CardStatus.Playing, listOf("Jazz24")), live)
    }

    // ---- Radio rows (MOB-009) --------------------------------------------------------------------

    private fun station(uuid: String) = Station(uuid, "radiobrowser://$uuid", "Station $uuid", null, null, null)

    @Test
    fun noRadioRowsWhileRadioIsNotOffered() {
        assertEquals(null, radioRows(false, listOf(station("a")), station("b")))
    }

    @Test
    fun theFirstThreeRecentStationsAreListed() {
        val recent = listOf("a", "b", "c", "d", "e").map(::station)
        val rows = radioRows(true, recent, null)!!
        assertEquals(listOf("a", "b", "c"), rows.recent.map { it.uuid })
        assertEquals(null, rows.found)
        assertEquals(null, rows.selectedUuid)
    }

    @Test
    fun aSelectedRecentStationMarksItsRowAndIsNotFound() {
        val recent = listOf("a", "b", "c").map(::station)
        val rows = radioRows(true, recent, station("b"))!!
        assertEquals("b", rows.selectedUuid)
        assertEquals(null, rows.found)
        assertEquals(3, rows.recent.size)
    }

    @Test
    fun aSelectedStationOutsideTheRecentRowsIsFoundAndNeverListedTwice() {
        val recent = listOf("a", "b", "c", "d").map(::station)
        val outside = radioRows(true, recent, station("z"))!!
        assertEquals("z", outside.found?.uuid)
        assertEquals("z", outside.selectedUuid)
        // The fourth recent station is not among the first three, so it is found, not listed.
        val fourth = radioRows(true, recent, station("d"))!!
        assertEquals("d", fourth.found?.uuid)
        assertEquals(listOf("a", "b", "c"), fourth.recent.map { it.uuid })
    }

    private fun stationSource(id: String, uuid: String) =
        source(id, "Station $uuid", uri = "radiobrowser://$uuid", origin = SourceOrigin.Runtime)

    @Test
    fun sourcesThatAreAStationShownInTheSectionAreLeftOut() {
        val sources = listOf(source("jazz"), stationSource("l1", "a"), stationSource("l2", "z"), stationSource("l3", "q"))
        val rows = radioRows(true, listOf(station("a")), station("z"))!!
        val options = StartPlaybackBuilder.build(snapshot(sources = sources)).sources
        assertEquals(listOf("jazz", "l3"), hideListedStations(options, rows, null).map { it.id })
    }

    @Test
    fun theSelectedSourceIsNeverLeftOut() {
        val sources = listOf(stationSource("l1", "a"))
        val rows = radioRows(true, listOf(station("a")), null)!!
        val options = StartPlaybackBuilder.build(snapshot(sources = sources)).sources
        assertEquals(listOf("l1"), hideListedStations(options, rows, "l1").map { it.id })
    }

    @Test
    fun withoutRadioRowsTheListIsUnchanged() {
        val options = StartPlaybackBuilder.build(snapshot(sources = listOf(stationSource("l1", "a")))).sources
        assertEquals(options, hideListedStations(options, null, null))
    }

    @Test
    fun stationUriIsSetOnlyForAStationSource() {
        val options = StartPlaybackBuilder.build(
            snapshot(sources = listOf(source("jazz"), stationSource("l1", "a"))),
        ).sources.associateBy { it.id }
        assertEquals(null, options.getValue("jazz").stationUri)
        assertEquals("radiobrowser://a", options.getValue("l1").stationUri)
    }
}
