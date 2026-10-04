package sonora.multiroom.mobile.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppBackStackTest {
    private fun AppBackStack.contents(): List<Destination> = stack.toList()

    @Test
    fun startsAtRooms() {
        val s = AppBackStack()
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
        assertEquals(Destination.Rooms, s.currentTab)
    }

    @Test
    fun tabsReplaceTheStackAndRoomsIsAlwaysTheRoot() {
        val s = AppBackStack()
        s.selectTab(Destination.Settings)
        assertEquals(listOf(Destination.Rooms, Destination.Settings), s.contents())
        s.selectTab(Destination.Rooms)
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
    }

    @Test
    fun selectingTheCurrentTabChangesNothing() {
        val s = AppBackStack()
        s.selectTab(Destination.Rooms)
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
        s.selectTab(Destination.Settings)
        s.selectTab(Destination.Settings)
        assertEquals(listOf(Destination.Rooms, Destination.Settings), s.contents())
    }

    @Test
    fun aPushedDestinationPopsBackToWhereItCameFrom() {
        val s = AppBackStack()
        s.push(Destination.NowPlaying("r1"))
        assertEquals(listOf(Destination.Rooms, Destination.NowPlaying("r1")), s.contents())
        assertTrue(s.pop())
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
    }

    @Test
    fun backOnRoomsLeavesTheApp() {
        val s = AppBackStack()
        assertFalse(s.pop())
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
    }

    @Test
    fun backFromAnotherTabGoesToRooms() {
        val s = AppBackStack()
        s.selectTab(Destination.Settings)
        assertTrue(s.pop())
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
        assertFalse(s.pop())
    }

    @Test
    fun startPlaybackReturnsToThePreviousScreen() {
        val s = AppBackStack()
        s.selectTab(Destination.Settings)
        s.push(Destination.StartPlayback(null))
        assertTrue(s.pop())
        assertEquals(listOf(Destination.Rooms, Destination.Settings), s.contents())
    }

    @Test
    fun theCurrentTabIsDerivedFromTheStack() {
        val s = AppBackStack()
        s.push(Destination.StartPlayback("bedroom"))
        assertEquals(Destination.Rooms, s.currentTab)
        s.selectTab(Destination.Settings)
        s.push(Destination.StartPlayback(null))
        assertEquals(Destination.Settings, s.currentTab)
        s.selectTab(Destination.Rooms)
        assertEquals(Destination.Rooms, s.currentTab)
    }

    @Test
    fun onlyRoomsAndSettingsAreTabs() {
        // Sources is a tab of Settings now (004): the destination is gone.
        val s = AppBackStack(listOf(Destination.Rooms, Destination.Settings))
        assertEquals(Destination.Settings, s.currentTab)
        assertTrue(s.showsBottomBar)
    }

    @Test
    fun anOldSavedSourcesEntryRestoresToSettings() {
        assertEquals(Destination.Settings, decodeDestination("sources"))
        val restored = AppBackStack(listOf("rooms", "sources").mapNotNull(::decodeDestination))
        assertEquals(listOf(Destination.Rooms, Destination.Settings), restored.contents())
    }

    @Test
    fun everyDestinationSurvivesSaveAndRestore() {
        val all = listOf(
            Destination.Rooms,
            Destination.Settings,
            Destination.NowPlaying("route:with:colons"),
            Destination.StartPlayback(null),
            Destination.StartPlayback("bedroom"),
        )
        for (d in all) assertEquals(d, decodeDestination(encodeDestination(d)), d.toString())
    }

    @Test
    fun aRestoredStackKeepsItsScreens() {
        val saved = listOf(Destination.Rooms, Destination.Settings, Destination.StartPlayback("a"))
            .map(::encodeDestination)
        val restored = AppBackStack(saved.mapNotNull(::decodeDestination))
        assertEquals(listOf(Destination.Rooms, Destination.Settings, Destination.StartPlayback("a")), restored.contents())
        assertEquals(Destination.Settings, restored.currentTab)
    }

    @Test
    fun anEmptyOrGarbledRestoreFallsBackToRooms() {
        assertEquals(listOf<Destination>(Destination.Rooms), AppBackStack(emptyList()).contents())
        assertEquals(null, decodeDestination("nonsense"))
    }

    @Test
    fun theRoomsRootIsTheSameInstanceAfterATabRoundTrip() {
        val s = AppBackStack()
        val root = s.stack[0]
        s.selectTab(Destination.Settings)
        s.selectTab(Destination.Rooms)
        assertSame(root, s.stack[0])

        s.push(Destination.NowPlaying("r1"))
        s.selectTab(Destination.Settings)
        s.selectTab(Destination.Rooms)
        assertSame(root, s.stack[0])
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
    }

    @Test
    fun replaceTopSwapsStartPlaybackForNowPlayingAndBackReturnsToRooms() {
        val s = AppBackStack()
        s.push(Destination.StartPlayback(null))
        s.replaceTop(Destination.NowPlaying("r1", 7, "Bedroom"))
        assertEquals(listOf(Destination.Rooms, Destination.NowPlaying("r1", 7, "Bedroom")), s.contents())
        assertTrue(s.pop())
        assertEquals(listOf<Destination>(Destination.Rooms), s.contents())
    }

    @Test
    fun theFenceAndNameAreNotSavedForNowPlaying() {
        val d = Destination.NowPlaying("r1", 7, "Bedroom")
        assertEquals("now:r1", encodeDestination(d))
        assertEquals(Destination.NowPlaying("r1", null, null), decodeDestination("now:r1"))
    }
}
