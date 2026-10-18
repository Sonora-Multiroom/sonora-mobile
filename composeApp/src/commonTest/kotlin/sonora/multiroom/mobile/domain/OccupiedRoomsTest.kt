package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** The one rule deciding which rooms a playback occupies (data-model.md, "Occupancy"). */
class OccupiedRoomsTest {
    private val rooms = listOf("office", "kitchen", "bedroom")
        .associateWith { Room(it, it, 50, muted = false, enabled = true, available = true) }
    private val groups = mapOf("g" to Group("g", "G", listOf("office", "kitchen"), muted = false, enabled = true))

    private fun occupied(target: Target, outputs: List<String>?): List<String> =
        occupiedRooms(
            Route("r1", "in", target, RouteStatus.Active, paused = false, pauseable = false, transferable = true, outputs = outputs),
            rooms,
            groups,
        ).map { it.id }

    private val g = Target.Group("g")

    @Test
    fun aGroupPlayingEverywhereKeepsTheGroupsOrder() =
        assertEquals(listOf("office", "kitchen"), occupied(g, listOf("kitchen", "office")))

    @Test
    fun aGroupWithAMemberLeftOutOccupiesOnlyTheListedOne() =
        assertEquals(listOf("kitchen"), occupied(g, listOf("kitchen")))

    @Test
    fun aGroupWithoutAListOccupiesEveryKnownMember() =
        assertEquals(listOf("office", "kitchen"), occupied(g, null))

    @Test
    fun aGroupWithAnEmptyListOccupiesEveryKnownMember() =
        assertEquals(listOf("office", "kitchen"), occupied(g, emptyList()))

    @Test
    fun unknownIdsInTheListAreSkipped() =
        assertEquals(listOf("kitchen"), occupied(g, listOf("ghost", "kitchen")))

    @Test
    fun aListOfOnlyUnknownIdsFallsBackToTheMembers() =
        assertEquals(listOf("office", "kitchen"), occupied(g, listOf("ghost")))

    @Test
    fun aListedRoomOutsideTheGroupComesAfterTheMembers() =
        assertEquals(listOf("kitchen", "bedroom"), occupied(g, listOf("bedroom", "kitchen")))

    @Test
    fun aListedRoomOutsideTheGroupIsKept() =
        assertEquals(listOf("kitchen", "bedroom"), occupied(g, listOf("kitchen", "bedroom")))

    @Test
    fun aRoomTargetOccupiesItsRoomWithOrWithoutAList() {
        assertEquals(listOf("bedroom"), occupied(Target.Room("bedroom"), listOf("bedroom")))
        assertEquals(listOf("bedroom"), occupied(Target.Room("bedroom"), null))
    }

    @Test
    fun anUnknownRoomTargetWithoutAListOccupiesNothing() =
        assertEquals(emptyList(), occupied(Target.Room("ghost"), null))

    @Test
    fun anUnknownTargetOccupiesOnlyWhatTheHubLists() {
        assertEquals(listOf("kitchen"), occupied(Target.Unknown("z"), listOf("kitchen")))
        assertEquals(emptyList(), occupied(Target.Unknown("z"), null))
    }
}
