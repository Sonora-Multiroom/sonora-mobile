package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.MoveDestinationNote.*
import sonora.multiroom.mobile.domain.warning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationTextTest {
    @Test
    fun everyRowOfTheCopyTable() {
        assertEquals("Idle", destinationNoteText(Idle))
        assertEquals("Jazz24 will stop", destinationNoteText(WillStop(listOf("Jazz24"))))
        assertEquals("Jazz24 and Morning playlist will stop", destinationNoteText(WillStop(listOf("Jazz24", "Morning playlist"))))
        assertEquals("Jazz24 will stop on Downstairs", destinationNoteText(WillStopOnGroup("Jazz24", "Downstairs")))
        assertEquals("Kitchen stops", destinationNoteText(OthersStop(listOf("Kitchen"))))
        assertEquals("Kitchen and Patio stop", destinationNoteText(OthersStop(listOf("Kitchen", "Patio"))))
        assertEquals("Office, Kitchen and Patio stop", destinationNoteText(OthersStop(listOf("Office", "Kitchen", "Patio"))))
        assertEquals("Living Room + Kitchen", destinationNoteText(Members(listOf("Living Room", "Kitchen"))))
        assertEquals(
            "Living Room + Kitchen · Patio not connected",
            destinationNoteText(Members(listOf("Living Room", "Kitchen"), notConnected = listOf("Patio"))),
        )
        assertEquals(
            "Jazz24 will stop · Patio and Office not connected",
            destinationNoteText(WillStop(listOf("Jazz24"), notConnected = listOf("Patio", "Office"))),
        )
        assertEquals(
            "Living Room + Kitchen · Patio turned off",
            destinationNoteText(Members(listOf("Living Room", "Kitchen"), turnedOff = listOf("Patio"))),
        )
        assertEquals(
            "Living Room + Kitchen · Patio turned off · Garden not connected",
            destinationNoteText(Members(listOf("Living Room", "Kitchen"), turnedOff = listOf("Patio"), notConnected = listOf("Garden"))),
        )
        assertEquals("Turned off", destinationNoteText(TurnedOff))
        assertEquals("Not connected", destinationNoteText(NotConnected))
        assertEquals("No rooms", destinationNoteText(NoRooms))
    }

    @Test
    fun onlyTheWillStopNotesAreWarnings() {
        assertTrue(WillStop(listOf("a")).warning)
        assertTrue(WillStopOnGroup("a", "g").warning)
        for (n in listOf(Idle, OthersStop(listOf("a")), Members(listOf("a")), TurnedOff, NotConnected, NoRooms)) {
            assertFalse(n.warning, n.toString())
        }
    }
}
