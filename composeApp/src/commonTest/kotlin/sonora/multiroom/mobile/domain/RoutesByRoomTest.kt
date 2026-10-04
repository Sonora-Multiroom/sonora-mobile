package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesByRoomTest {
    private fun room(id: String) = Room(id, id, 50, muted = false, enabled = true, available = true)
    private fun route(
        id: String,
        target: Target,
        status: RouteStatus = RouteStatus.Active,
        joinMode: JoinMode = JoinMode.Replace,
    ) = Route(id, "in-$id", target, status, paused = false, pauseable = false, transferable = true, joinMode = joinMode)

    private fun snapshot(vararg routes: Route) = HubSnapshot(
        rooms = listOf(room("a"), room("b"), room("c")),
        groups = listOf(Group("g", "G", listOf("a", "b", "ghost"), muted = false, enabled = true)),
        routes = routes.toList(),
        sources = emptyList(),
        masterMuted = false,
    )

    @Test
    fun aRoomRouteIsListedUnderItsRoom() {
        val r = route("r1", Target.Room("a"))
        assertEquals(mapOf("a" to listOf(r)), routesByRoom(snapshot(r)))
    }

    @Test
    fun aGroupRouteIsListedUnderEveryKnownMemberAndUnknownMembersAreSkipped() {
        val r = route("r1", Target.Group("g"))
        assertEquals(mapOf("a" to listOf(r), "b" to listOf(r)), routesByRoom(snapshot(r)))
    }

    @Test
    fun routesOnOneRoomKeepHubOrder() {
        val r1 = route("r1", Target.Room("a"))
        val r2 = route("r2", Target.Group("g"))
        val r3 = route("r3", Target.Room("a"), joinMode = JoinMode.Mix)
        assertEquals(listOf(r1, r2, r3), routesByRoom(snapshot(r1, r2, r3))["a"])
        assertEquals(listOf(r2), routesByRoom(snapshot(r1, r2, r3))["b"])
    }

    @Test
    fun stoppedRoutesAreIgnoredButFailedAndUnknownAreKept() {
        val stopped = route("r1", Target.Room("a"), RouteStatus.Stopped)
        val failed = route("r2", Target.Room("b"), RouteStatus.Failed)
        val unknown = route("r3", Target.Room("c"), RouteStatus.Unknown)
        assertEquals(mapOf("b" to listOf(failed), "c" to listOf(unknown)), routesByRoom(snapshot(stopped, failed, unknown)))
    }

    @Test
    fun anUnknownTargetCoversNothing() {
        assertEquals(emptyMap(), routesByRoom(snapshot(route("r1", Target.Unknown("a")))))
    }

    @Test
    fun onlyTheAnnouncementModeIsAnAnnouncement() {
        assertTrue(route("r", Target.Room("a"), joinMode = JoinMode.Announcement).isAnnouncement)
        for (mode in listOf(JoinMode.Replace, JoinMode.Mix, JoinMode.Unknown)) {
            assertFalse(route("r", Target.Room("a"), joinMode = mode).isAnnouncement, mode.name)
        }
    }
}
