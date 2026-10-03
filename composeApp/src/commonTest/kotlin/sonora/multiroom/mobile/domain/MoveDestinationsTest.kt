package sonora.multiroom.mobile.domain

import sonora.multiroom.mobile.domain.MoveDestinationNote.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The 23 cases of data-model.md "FR-025 test matrix", numbered as there. */
class MoveDestinationsTest {
    private fun room(id: String, name: String = id.replaceFirstChar { it.uppercase() }, enabled: Boolean = true, available: Boolean = true) =
        Room(id, name, 50, muted = false, enabled = enabled, available = available)

    private fun group(id: String, members: List<String>, name: String = id.replaceFirstChar { it.uppercase() }, enabled: Boolean = true) =
        Group(id, name, members, muted = false, enabled = enabled)

    private fun src(id: String, name: String) =
        Source(id, name, "http://x/$id", SourceOrigin.Configured, false, true, SourceKind.Stream)

    private fun route(id: String, input: String, target: Target, status: RouteStatus = RouteStatus.Active) =
        Route(id, input, target, status, paused = false, pauseable = false, transferable = true)

    private val sources = listOf(src("jazz", "Jazz24"), src("morning", "Morning playlist"), src("radio", "Radio Paradise"))

    private fun snap(rooms: List<Room>, groups: List<Group> = emptyList(), routes: List<Route>) =
        HubSnapshot(rooms, groups, routes, sources, masterMuted = false)

    private fun build(s: HubSnapshot, id: String = "r1") = assertNotNull(MoveDestinations.build(s, id))

    private fun MoveSheetContent.room(label: String) = rooms.first { it.label == label }
    private fun MoveSheetContent.group(label: String) = groups.first { it.label == label }

    private val five = listOf(room("living", "Living Room"), room("kitchen", "Kitchen"), room("office", "Office"), room("bedroom", "Bedroom"), room("patio", "Patio"))

    // 1
    @Test fun anIdleRoomIsIdleAndSelectable() {
        val c = build(snap(five, routes = listOf(route("r1", "radio", Target.Room("living")))))
        val d = c.room("Kitchen")
        assertEquals(Idle, d.note)
        assertTrue(d.selectable)
        assertEquals(Target.Room("kitchen"), d.target)
        assertEquals(MoveDestinationKind.Room, d.kind)
        assertEquals("Kitchen", d.ctaName)
    }

    // 2
    @Test fun aRoomWithAnotherSingleRoomRouteWillStopThatSource() {
        val c = build(snap(five, routes = listOf(
            route("r1", "radio", Target.Room("living")), route("r2", "jazz", Target.Room("kitchen")),
        )))
        assertEquals(WillStop(listOf("Jazz24")), c.room("Kitchen").note)
        assertTrue(c.room("Kitchen").selectable)
    }

    // 3
    @Test fun aRoomInAnotherGroupsRouteWillStopOnThatGroup() {
        val c = build(snap(five, groups = listOf(group("up", listOf("kitchen", "office"), "Upstairs")), routes = listOf(
            route("r1", "radio", Target.Room("living")), route("r2", "jazz", Target.Group("up")),
        )))
        assertEquals(WillStopOnGroup("Jazz24", "Upstairs"), c.room("Kitchen").note)
        assertEquals(WillStopOnGroup("Jazz24", "Upstairs"), c.room("Office").note)
    }

    // 4
    @Test fun aGroupRouteWithTwoMembersOffersEachOnlyWithTheOtherStopping() {
        val s = snap(listOf(room("living", "Living Room"), room("kitchen", "Kitchen"), room("office", "Office")),
            groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Group("down"))))
        val c = build(s)
        val d = c.room("Living Room only")
        assertEquals(OthersStop(listOf("Kitchen")), d.note)
        assertEquals(MoveDestinationKind.MemberOnly, d.kind)
        assertEquals("Living Room", d.ctaName)
        assertEquals(Target.Room("living"), d.target)
        assertTrue(d.selectable)
        // The members themselves are not listed as plain rooms.
        assertEquals(listOf("Office", "Kitchen only", "Living Room only"), c.rooms.map { it.label })
    }

    // 5
    @Test fun aGroupRouteWithThreeMembersListsTheTwoOthers() {
        val s = snap(listOf(room("a", "A"), room("b", "B"), room("c", "C")),
            groups = listOf(group("g", listOf("a", "b", "c"), "G")), routes = listOf(route("r1", "radio", Target.Group("g"))))
        assertEquals(OthersStop(listOf("B", "C")), build(s).room("A only").note)
    }

    // 6
    @Test fun aDisabledRoomIsTurnedOffAndUnselectable() {
        val c = build(snap(listOf(room("living", "Living Room"), room("patio", "Patio", enabled = false)),
            routes = listOf(route("r1", "radio", Target.Room("living")))))
        assertEquals(TurnedOff, c.room("Patio").note)
        assertFalse(c.room("Patio").selectable)
    }

    // 7
    @Test fun anUnavailableRoomIsNotConnectedAndDisabledWinsOverUnavailable() {
        val c = build(snap(listOf(room("living", "Living Room"), room("patio", "Patio", available = false), room("garden", "Garden", enabled = false, available = false)),
            routes = listOf(route("r1", "radio", Target.Room("living")))))
        assertEquals(NotConnected, c.room("Patio").note)
        assertFalse(c.room("Patio").selectable)
        assertEquals(TurnedOff, c.room("Garden").note)
    }

    // 8
    @Test fun roomsAreOrderedSelectableThenOnlyThenUnselectableCaseInsensitively() {
        val s = snap(listOf(room("a", "alpha"), room("b", "Beta"), room("c", "Charlie", enabled = false), room("d", "delta"), room("m1", "Mia"), room("m2", "Max")),
            groups = listOf(group("g", listOf("m1", "m2"), "G")), routes = listOf(route("r1", "radio", Target.Group("g"))))
        val c = build(s)
        assertEquals(listOf("alpha", "Beta", "delta", "Max only", "Mia only", "Charlie"), c.rooms.map { it.label })
    }

    // 9
    @Test fun aFreeGroupListsItsMembers() {
        val s = snap(five, groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Room("office"))))
        val d = build(s).group("Downstairs")
        assertEquals(Members(listOf("Living Room", "Kitchen")), d.note)
        assertTrue(d.selectable)
        assertEquals(MoveDestinationKind.Group, d.kind)
        assertEquals(Target.Group("down"), d.target)
    }

    // 10
    @Test fun aGroupWithOneOtherRouteOnAMemberWillStopIt() {
        val s = snap(five, groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Room("office")), route("r2", "jazz", Target.Room("kitchen"))))
        assertEquals(WillStop(listOf("Jazz24")), build(s).group("Downstairs").note)
    }

    // 11
    @Test fun aGroupWithTwoOtherRoutesListsBothSourcesInMemberOrder() {
        val s = snap(five,
            groups = listOf(group("down", listOf("living", "kitchen", "bedroom"), "Downstairs"), group("up", listOf("kitchen", "patio"), "Upstairs")),
            routes = listOf(
                route("r1", "radio", Target.Room("office")),
                route("r2", "jazz", Target.Room("living")),
                route("r3", "morning", Target.Group("up")),
            ))
        // Kitchen is on Upstairs' route: the note names sources only, never the other group.
        assertEquals(WillStop(listOf("Jazz24", "Morning playlist")), build(s).group("Downstairs").note)
    }

    // 12
    @Test fun aGroupOverlappingTheCurrentTargetOnlyHasNothingToStop() {
        val s = snap(five,
            groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs"), group("both", listOf("kitchen", "office"), "Both")),
            routes = listOf(route("r1", "radio", Target.Group("down"))))
        assertEquals(Members(listOf("Kitchen", "Office")), build(s).group("Both").note)
    }

    // 13
    @Test fun aDisabledGroupIsTurnedOff() {
        val s = snap(five, groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs", enabled = false)),
            routes = listOf(route("r1", "radio", Target.Room("office"))))
        val d = build(s).group("Downstairs")
        assertEquals(TurnedOff, d.note)
        assertFalse(d.selectable)
    }

    // 14
    @Test fun unavailableMembersAreNamedButTheGroupStaysSelectable() {
        val rooms = listOf(room("a", "A"), room("b", "B"), room("p", "P", available = false), room("q", "Q", available = false), room("o", "Office"))
        val one = snap(rooms, groups = listOf(group("g", listOf("a", "b", "p"), "G")), routes = listOf(route("r1", "radio", Target.Room("o"))))
        assertEquals(Members(listOf("A", "B"), notConnected = listOf("P")), build(one).group("G").note)
        assertTrue(build(one).group("G").selectable)
        val two = snap(rooms, groups = listOf(group("g", listOf("a", "p", "q"), "G")), routes = listOf(route("r1", "radio", Target.Room("o"))))
        assertEquals(Members(listOf("A"), notConnected = listOf("P", "Q")), build(two).group("G").note)
    }

    // 15
    @Test fun theCurrentGroupIsExcludedAndASingleRoomRouteListsEveryGroup() {
        val groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs"), group("up", listOf("office", "bedroom"), "Upstairs"))
        val onGroup = build(snap(five, groups, listOf(route("r1", "radio", Target.Group("down")))))
        assertEquals(listOf("Upstairs"), onGroup.groups.map { it.label })
        val onRoom = build(snap(five, groups, listOf(route("r1", "radio", Target.Room("patio")))))
        assertEquals(listOf("Downstairs", "Upstairs"), onRoom.groups.map { it.label })
    }

    // 16
    @Test fun groupsAreOrderedSelectableThenUnselectableAlphabetically() {
        val groups = listOf(
            group("z", listOf("living"), "Zed"), group("a", listOf("kitchen"), "alpha", enabled = false),
            group("m", listOf("office"), "Middle"), group("b", listOf("bedroom"), "Beta", enabled = false),
        )
        val c = build(snap(five, groups, listOf(route("r1", "radio", Target.Room("patio")))))
        assertEquals(listOf("Middle", "Zed", "alpha", "Beta"), c.groups.map { it.label })
    }

    // 17
    @Test fun theCurrentRoomIsExcludedForASingleRoomRoute() {
        val c = build(snap(five, routes = listOf(route("r1", "radio", Target.Room("living")))))
        assertTrue(c.rooms.none { it.target == Target.Room("living") })
        assertEquals(4, c.rooms.size)
    }

    // 18
    @Test fun aGroupWithNoKnownMembersReadsNoRoomsAndIsUnselectable() {
        val s = snap(five, groups = listOf(group("g", listOf("ghost"), "Ghosts"), group("e", emptyList(), "Empty")),
            routes = listOf(route("r1", "radio", Target.Room("living"))))
        for (label in listOf("Ghosts", "Empty")) {
            assertEquals(NoRooms, build(s).group(label).note)
            assertFalse(build(s).group(label).selectable)
        }
    }

    // 19
    @Test fun aTurnedOffOrNotConnectedMemberOnlyOptionReadsThatNoteAndSortsLast() {
        val s = snap(listOf(room("a", "Alpha"), room("b", "Bravo", enabled = false), room("c", "Charlie", available = false), room("z", "Zulu")),
            groups = listOf(group("g", listOf("a", "b", "c"), "G")), routes = listOf(route("r1", "radio", Target.Group("g"))))
        val c = build(s)
        assertEquals(TurnedOff, c.room("Bravo only").note)
        assertEquals(NotConnected, c.room("Charlie only").note)
        assertFalse(c.room("Bravo only").selectable)
        assertFalse(c.room("Charlie only").selectable)
        assertEquals(OthersStop(listOf("Bravo", "Charlie")), c.room("Alpha only").note)
        assertEquals(listOf("Zulu", "Alpha only", "Bravo only", "Charlie only"), c.rooms.map { it.label })
        assertEquals("Bravo", c.room("Bravo only").ctaName)
    }

    // 20
    @Test fun aGroupWithTurnedOffMembersStaysSelectableAndNamesThem() {
        val rooms = listOf(room("a", "A"), room("b", "B"), room("p", "P", enabled = false), room("q", "Q", available = false), room("both", "Both", enabled = false, available = false), room("o", "Office"))
        val s = snap(rooms, groups = listOf(group("g", listOf("a", "b", "p", "q", "both"), "G")), routes = listOf(route("r1", "radio", Target.Room("o"))))
        val d = build(s).group("G")
        // A member both disabled and unavailable counts as turned off.
        assertEquals(Members(listOf("A", "B"), turnedOff = listOf("P", "Both"), notConnected = listOf("Q")), d.note)
        assertTrue(d.selectable)
    }

    // 21
    @Test fun aGroupWhoseMembersAreAllUnavailableOrAMixOfOffAndUnavailableIsNotConnected() {
        val rooms = listOf(room("p", "P", available = false), room("q", "Q", available = false), room("r", "R", enabled = false), room("o", "Office"))
        val s = snap(rooms, groups = listOf(group("all", listOf("p", "q"), "All"), group("mix", listOf("p", "r"), "Mix")),
            routes = listOf(route("r1", "radio", Target.Room("o"))))
        for (label in listOf("All", "Mix")) {
            assertEquals(NotConnected, build(s).group(label).note)
            assertFalse(build(s).group(label).selectable)
        }
    }

    // 22
    @Test fun aGroupWhoseMembersAreAllDisabledIsTurnedOffAndPrecedenceHolds() {
        val rooms = listOf(room("p", "P", enabled = false), room("q", "Q", enabled = false), room("o", "Office"))
        val s = snap(rooms,
            groups = listOf(group("all", listOf("p", "q"), "All"), group("off", listOf("p"), "Off", enabled = false), group("none", listOf("ghost"), "None")),
            routes = listOf(route("r1", "radio", Target.Room("o"))))
        val c = build(s)
        assertEquals(TurnedOff, c.group("All").note)
        assertFalse(c.group("All").selectable)
        assertEquals(TurnedOff, c.group("Off").note)
        assertEquals(NoRooms, c.group("None").note)
    }

    // 23
    @Test fun aRouteWithAnUnknownTargetHasNoDestinations() {
        val c = build(snap(five, routes = listOf(route("r1", "radio", Target.Unknown("weird")))))
        assertEquals(emptyList(), c.rooms)
        assertEquals(emptyList(), c.groups)
        assertEquals("weird", c.currentTargetName)
    }

    @Test fun aGoneRouteIsNull() {
        assertNull(MoveDestinations.build(snap(five, routes = emptyList()), "r1"))
        assertNull(MoveDestinations.build(snap(five, routes = listOf(route("r1", "radio", Target.Room("living"), RouteStatus.Stopped))), "r1"))
    }

    @Test fun sourceAndCurrentTargetNames() {
        val s = snap(five, groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Group("down")), route("r2", "missing", Target.Room("office"))))
        assertEquals("Radio Paradise", build(s).sourceName)
        assertEquals("Downstairs", build(s).currentTargetName)
        assertEquals("missing", build(s, "r2").sourceName)
    }

    @Test fun stoppedRoutesDoNotOccupyRooms() {
        val s = snap(five, routes = listOf(
            route("r1", "radio", Target.Room("living")), route("r2", "jazz", Target.Room("kitchen"), RouteStatus.Stopped),
        ))
        assertEquals(Idle, build(s).room("Kitchen").note)
    }
}
