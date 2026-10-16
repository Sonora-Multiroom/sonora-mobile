package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsConfirmTest {
    private val radio = source("radio", "Radio Paradise")
    private val jazz = source("jazz", "Jazz FM")

    private fun base(
        routes: List<Route> = emptyList(),
        roomEnabled: Boolean = true,
        groupEnabled: Boolean = true,
    ) = snapshot(
        rooms = listOf(
            room("living", "Living Room", enabled = roomEnabled),
            room("kitchen", "Kitchen"),
            room("bedroom", "Bedroom"),
        ),
        groups = listOf(group("g", "Downstairs", listOf("living", "kitchen"), enabled = groupEnabled)),
        routes = routes,
        sources = listOf(radio, jazz),
    )

    private val living = ItemKey(ItemKind.Room, "living")
    private val downstairs = ItemKey(ItemKind.Group, "g")

    // ---- Turn off (research R4) -------------------------------------------------------------

    @Test
    fun aPlayingRoomAsksWhatWillStop() {
        val s = base(listOf(route("r", "radio", Target.Room("living"))))
        assertEquals(Confirmation.TurnOffRoom("Living Room", listOf("Radio Paradise")), turnOffConfirmation(living, s))
    }

    @Test
    fun aRoomPlayingOnlyThroughAGroupAsks() {
        val s = base(listOf(route("r", "radio", Target.Group("g"))))
        assertEquals(Confirmation.TurnOffRoom("Living Room", listOf("Radio Paradise")), turnOffConfirmation(living, s))
    }

    @Test
    fun twoSourcesOnTheRoomAreBothNamed() {
        val s = base(listOf(route("r1", "radio", Target.Room("living")), route("r2", "jazz", Target.Group("g"))))
        assertEquals(
            Confirmation.TurnOffRoom("Living Room", listOf("Radio Paradise", "Jazz FM")),
            turnOffConfirmation(living, s),
        )
    }

    @Test
    fun anIdleRoomOrOneThatIsOffSendsAtOnce() {
        assertNull(turnOffConfirmation(living, base()))
        val off = base(listOf(route("r", "radio", Target.Room("living"))), roomEnabled = false)
        assertNull(turnOffConfirmation(living, off))
    }

    @Test
    fun aGroupWithItsOwnRouteAsksAndNamesItsMembers() {
        val s = base(listOf(route("r", "radio", Target.Group("g"))))
        assertEquals(
            Confirmation.TurnOffGroup("Downstairs", listOf("Radio Paradise"), listOf("Living Room", "Kitchen")),
            turnOffConfirmation(downstairs, s),
        )
    }

    @Test
    fun aGroupWhoseMembersPlayOnTheirOwnSendsAtOnce() {
        val s = base(listOf(route("r", "radio", Target.Room("living"))))
        assertNull(turnOffConfirmation(downstairs, s))
    }

    @Test
    fun anIdleOrTurnedOffGroupSendsAtOnce() {
        assertNull(turnOffConfirmation(downstairs, base()))
        val off = base(listOf(route("r", "radio", Target.Group("g"))), groupEnabled = false)
        assertNull(turnOffConfirmation(downstairs, off))
    }

    private val radioKey = ItemKey(ItemKind.Source, "radio")

    @Test
    fun aSourcePlayingInARoomAsksWhereItStops() {
        val s = base(listOf(route("r", "radio", Target.Room("living"))))
        assertEquals(Confirmation.TurnOffSource("Radio Paradise", listOf("Living Room")), turnOffConfirmation(radioKey, s))
    }

    @Test
    fun aSourcePlayingOnAGroupNamesTheGroup() {
        val s = base(listOf(route("r", "radio", Target.Group("g"))))
        assertEquals(Confirmation.TurnOffSource("Radio Paradise", listOf("Downstairs")), turnOffConfirmation(radioKey, s))
    }

    @Test
    fun aSourceInSeveralPlacesNamesEachOnce() {
        val s = base(
            listOf(
                route("r1", "radio", Target.Group("g")),
                route("r2", "radio", Target.Room("bedroom")),
                route("r3", "radio", Target.Room("bedroom")),
            ),
        )
        assertEquals(
            Confirmation.TurnOffSource("Radio Paradise", listOf("Downstairs", "Bedroom")),
            turnOffConfirmation(radioKey, s),
        )
    }

    @Test
    fun aSourceOnlyPausedStillAsks() {
        val s = base(listOf(route("r", "radio", Target.Room("bedroom")).copy(paused = true)))
        assertEquals(Confirmation.TurnOffSource("Radio Paradise", listOf("Bedroom")), turnOffConfirmation(radioKey, s))
    }

    @Test
    fun anIdleSourceSendsAtOnce() {
        assertNull(turnOffConfirmation(radioKey, base()))
        assertNull(turnOffConfirmation(radioKey, base(listOf(route("r", "radio", Target.Room("living"), RouteStatus.Stopped)))))
    }

    @Test
    fun aSourceThatIsOffOrUnknownSendsAtOnce() {
        val playing = base(listOf(route("r", "radio", Target.Room("living"))))
        val off = playing.copy(sources = listOf(source("radio", "Radio Paradise", enabled = false), jazz))
        assertNull(turnOffConfirmation(radioKey, off))
        assertNull(turnOffConfirmation(ItemKey(ItemKind.Source, "ghost"), playing))
    }

    @Test
    fun anUnknownItemSendsAtOnce() {
        assertNull(turnOffConfirmation(ItemKey(ItemKind.Room, "ghost"), base()))
        assertNull(turnOffConfirmation(ItemKey(ItemKind.Group, "ghost"), base()))
    }

    // ---- Where a source plays -----------------------------------------------------------------

    @Test
    fun aRouteToARoomIsNamedByTheRoom() =
        assertEquals(listOf("Bedroom"), sourcePlaces("radio", base(listOf(route("r", "radio", Target.Room("bedroom"))))))

    @Test
    fun aRouteToAGroupIsNamedByTheGroupNotItsMembers() =
        assertEquals(listOf("Downstairs"), sourcePlaces("radio", base(listOf(route("r", "radio", Target.Group("g"))))))

    @Test
    fun severalPlacesInHubOrderEachOnce() {
        val s = base(
            listOf(
                route("r1", "radio", Target.Room("bedroom")),
                route("r2", "radio", Target.Group("g")),
                route("r3", "radio", Target.Room("bedroom")),
            ),
        )
        assertEquals(listOf("Bedroom", "Downstairs"), sourcePlaces("radio", s))
    }

    @Test
    fun pausedStartingFailedAndUnknownRoutesCount() {
        val paused = route("r", "radio", Target.Room("bedroom")).copy(paused = true)
        assertEquals(listOf("Bedroom"), sourcePlaces("radio", base(listOf(paused))))
        for (status in listOf(RouteStatus.Starting, RouteStatus.Failed, RouteStatus.Unknown)) {
            assertEquals(listOf("Bedroom"), sourcePlaces("radio", base(listOf(route("r", "radio", Target.Room("bedroom"), status)))), "$status")
        }
    }

    @Test
    fun stoppedRoutesAndOtherSourcesDoNotCount() {
        val s = base(
            listOf(
                route("r1", "radio", Target.Room("bedroom"), RouteStatus.Stopped),
                route("r2", "jazz", Target.Room("kitchen")),
            ),
        )
        assertEquals(emptyList<String>(), sourcePlaces("radio", s))
        assertEquals(emptyList<String>(), sourcePlaces("radio", base()))
    }

    // ---- Remove (US2) -------------------------------------------------------------------------

    private val link = source("link", "A link", SourceOrigin.Runtime, "https://soundcloud.com/x")

    private fun withLink(vararg routes: Route) = base(routes.toList()).copy(sources = listOf(radio, jazz, link))

    @Test
    fun anUnusedRuntimeSourceIsRemovedAtOnce() = assertNull(removeConfirmation("link", withLink()))

    @Test
    fun aSourceUsedByARoomRouteAsksWhereItStops() =
        assertEquals(Confirmation.Remove("A link", listOf("Bedroom")), removeConfirmation("link", withLink(route("r", "link", Target.Room("bedroom")))))

    @Test
    fun aSourceUsedByAGroupRouteNamesTheGroup() =
        assertEquals(Confirmation.Remove("A link", listOf("Downstairs")), removeConfirmation("link", withLink(route("r", "link", Target.Group("g")))))

    @Test
    fun usedByBothListsBothInHubOrderWithoutDuplicates() {
        val s = withLink(
            route("r1", "link", Target.Group("g")),
            route("r2", "link", Target.Room("bedroom")),
            route("r3", "link", Target.Group("g")),
        )
        assertEquals(Confirmation.Remove("A link", listOf("Downstairs", "Bedroom")), removeConfirmation("link", s))
    }

    @Test
    fun aStoppedRouteDoesNotCount() =
        assertNull(removeConfirmation("link", withLink(route("r", "link", Target.Room("bedroom"), RouteStatus.Stopped))))
}
