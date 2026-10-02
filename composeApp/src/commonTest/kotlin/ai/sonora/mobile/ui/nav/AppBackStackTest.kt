package ai.sonora.mobile.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        s.selectTab(Destination.Sources)
        assertEquals(listOf(Destination.Rooms, Destination.Sources), s.contents())
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
        s.selectTab(Destination.Sources)
        s.push(Destination.StartPlayback(null))
        assertTrue(s.pop())
        assertEquals(listOf(Destination.Rooms, Destination.Sources), s.contents())
    }

    @Test
    fun theCurrentTabIsDerivedFromTheStack() {
        val s = AppBackStack()
        s.push(Destination.StartPlayback("bedroom"))
        assertEquals(Destination.Rooms, s.currentTab)
        s.selectTab(Destination.Settings)
        s.push(Destination.StartPlayback(null))
        assertEquals(Destination.Settings, s.currentTab)
        s.selectTab(Destination.Sources)
        assertEquals(Destination.Sources, s.currentTab)
    }
}
