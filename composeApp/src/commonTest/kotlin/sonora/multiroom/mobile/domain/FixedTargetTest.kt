package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FixedTargetTest {
    private fun room(id: String, name: String = id, enabled: Boolean = true, available: Boolean = true) =
        Room(id, name, 50, muted = false, enabled = enabled, available = available)

    private fun group(id: String, name: String, vararg members: String, enabled: Boolean = true) =
        Group(id, name, members.toList(), muted = false, enabled = enabled)

    private val jazz = Source("jazz", "Jazz24", "http://x/jazz", SourceOrigin.Configured, false, true, SourceKind.Stream)
    private val morning = Source("morning", "Morning playlist", "file:///m.m3u", SourceOrigin.Configured, true, true, SourceKind.File)

    private fun route(id: String, input: String, target: Target, paused: Boolean = false) =
        Route(id, input, target, RouteStatus.Active, paused, pauseable = paused, transferable = true)

    private fun snapshot(
        rooms: List<Room> = listOf(room("kitchen", "Kitchen"), room("office", "Office")),
        groups: List<Group> = listOf(group("down", "Downstairs", "kitchen", "office")),
        routes: List<Route> = emptyList(),
    ) = HubSnapshot(rooms, groups, routes, listOf(jazz, morning), masterMuted = false)

    private fun roomCard(s: HubSnapshot, known: String? = null) = FixedTarget.of(s, Target.Room("kitchen"), known)
    private fun groupCard(s: HubSnapshot, known: String? = null) = FixedTarget.of(s, Target.Group("down"), known)

    // ---- rooms ---------------------------------------------------------------------------------

    @Test
    fun anIdleRoomIsUsable() {
        val card = roomCard(snapshot())
        assertEquals(TargetStatus.Idle, card.status)
        assertNull(card.unusable)
        assertEquals("Kitchen", card.name)
        assertEquals(Target.Room("kitchen"), card.target)
    }

    @Test
    fun aRoomPlayingItsOwnSourceIsUsableAndPlaying() {
        val card = roomCard(snapshot(routes = listOf(route("r1", "jazz", Target.Room("kitchen")))))
        // A live stream, as in the picker's tile (whose text reads it plain "Playing").
        assertEquals(TargetStatus.Playing(CardStatus.LiveStream, listOf("Jazz24")), card.status)
        assertNull(card.unusable)
    }

    @Test
    fun aPausedRoomPlaybackReadsPaused() {
        val card = roomCard(snapshot(routes = listOf(route("r1", "morning", Target.Room("kitchen"), paused = true))))
        assertEquals(TargetStatus.Playing(CardStatus.Paused, listOf("Morning playlist")), card.status)
    }

    @Test
    fun aRoomPlayingViaAGroupReadsInGroup() {
        val card = roomCard(snapshot(routes = listOf(route("r1", "jazz", Target.Group("down")))))
        assertEquals(TargetStatus.InGroup("Downstairs"), card.status)
        assertNull(card.unusable)
    }

    @Test
    fun aRoomTurnedOffOrNotConnectedIsUnusable() {
        val off = roomCard(snapshot(rooms = listOf(room("kitchen", "Kitchen", enabled = false))))
        assertEquals(TargetStatus.TurnedOff, off.status)
        assertEquals(Unusable.TurnedOff, off.unusable)
        val gone = roomCard(snapshot(rooms = listOf(room("kitchen", "Kitchen", available = false))))
        assertEquals(TargetStatus.NotConnected, gone.status)
        assertEquals(Unusable.NotConnected, gone.unusable)
    }

    @Test
    fun aRoomThatIsNotInTheSnapshotIsRemovedAndKeepsItsKnownName() {
        val s = snapshot(rooms = listOf(room("office", "Office")))
        val card = roomCard(s, known = "Kitchen speakers")
        assertEquals(TargetStatus.Removed, card.status)
        assertEquals(Unusable.Removed, card.unusable)
        assertEquals("Kitchen speakers", card.name)
        assertEquals("kitchen", roomCard(s).name)
    }

    @Test
    fun theSnapshotNameWinsOverTheKnownOne() {
        assertEquals("Kitchen", roomCard(snapshot(), known = "Old name").name)
    }

    // ---- groups --------------------------------------------------------------------------------

    @Test
    fun aGroupWithPlayableMembersIsUsable() {
        val card = groupCard(snapshot())
        assertEquals(TargetStatus.GroupMembers(listOf("Kitchen", "Office")), card.status)
        assertNull(card.unusable)
    }

    @Test
    fun aGroupPlayingIsGroupPlayingAndPausedStaysPaused() {
        val playing = groupCard(snapshot(routes = listOf(route("r1", "jazz", Target.Group("down")))))
        assertEquals(TargetStatus.GroupPlaying(CardStatus.Playing, listOf("Jazz24")), playing.status)
        val paused = groupCard(snapshot(routes = listOf(route("r1", "morning", Target.Group("down"), paused = true))))
        assertEquals(TargetStatus.GroupPlaying(CardStatus.Paused, listOf("Morning playlist")), paused.status)
    }

    @Test
    fun aGroupTurnedOffHasNoMembersOrNoPlayableOnesIsUnusable() {
        val off = groupCard(snapshot(groups = listOf(group("down", "Downstairs", "kitchen", "office", enabled = false))))
        assertEquals(Unusable.TurnedOff, off.unusable)

        val empty = groupCard(snapshot(groups = listOf(group("down", "Downstairs"))))
        assertEquals(TargetStatus.NoRooms, empty.status)
        assertEquals(Unusable.NoRooms, empty.unusable)

        val allOff = groupCard(snapshot(rooms = listOf(room("kitchen", enabled = false), room("office", enabled = false))))
        assertEquals(Unusable.AllRoomsTurnedOff, allOff.unusable)

        val none = groupCard(snapshot(rooms = listOf(room("kitchen", available = false), room("office", enabled = false))))
        assertEquals(Unusable.NotConnected, none.unusable)
    }

    @Test
    fun aGroupWithOneMemberOffIsStillUsable() {
        val card = groupCard(snapshot(rooms = listOf(room("kitchen", "Kitchen"), room("office", "Office", enabled = false))))
        assertNull(card.unusable)
    }

    @Test
    fun aGroupThatIsNotInTheSnapshotIsRemoved() {
        val card = groupCard(snapshot(groups = emptyList()), known = "Downstairs")
        assertEquals(TargetStatus.Removed, card.status)
        assertEquals(Unusable.Removed, card.unusable)
        assertEquals("Downstairs", card.name)
    }
}
