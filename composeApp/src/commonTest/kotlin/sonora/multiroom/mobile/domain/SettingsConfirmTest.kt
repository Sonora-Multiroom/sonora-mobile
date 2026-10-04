package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun aSourceNeverAsks() {
        val s = base(listOf(route("r", "radio", Target.Room("living"))))
        assertNull(turnOffConfirmation(ItemKey(ItemKind.Source, "radio"), s))
    }

    @Test
    fun anUnknownItemSendsAtOnce() {
        assertNull(turnOffConfirmation(ItemKey(ItemKind.Room, "ghost"), base()))
        assertNull(turnOffConfirmation(ItemKey(ItemKind.Group, "ghost"), base()))
    }

    // ---- keepsPlaying -----------------------------------------------------------------------

    @Test
    fun keepsPlayingWhenALiveRouteUsesTheSource() {
        assertTrue(keepsPlaying("radio", base(listOf(route("r", "radio", Target.Room("living"))))))
    }

    @Test
    fun doesNotKeepPlayingWhenOnlyAStoppedRouteOrNoneUsesIt() {
        assertFalse(keepsPlaying("radio", base(listOf(route("r", "radio", Target.Room("living"), RouteStatus.Stopped)))))
        assertFalse(keepsPlaying("radio", base()))
    }
}
