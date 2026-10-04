package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.AffectedPlayback
import sonora.multiroom.mobile.domain.CardStatus
import sonora.multiroom.mobile.domain.ConsequenceLine
import sonora.multiroom.mobile.domain.MuteNote
import sonora.multiroom.mobile.domain.TargetStatus
import sonora.multiroom.mobile.domain.WontPlay
import kotlin.test.Test
import kotlin.test.assertEquals

class StartPlaybackTextTest {
    @Test
    fun roomStatusLines() {
        assertEquals("Idle", targetStatusText(TargetStatus.Idle))
        val jazz = listOf("Jazz24")
        assertEquals("Playing · Jazz24", targetStatusText(TargetStatus.Playing(CardStatus.Playing, jazz)))
        assertEquals("Playing · Jazz24", targetStatusText(TargetStatus.Playing(CardStatus.LiveStream, jazz)))
        assertEquals("Paused · Morning playlist", targetStatusText(TargetStatus.Playing(CardStatus.Paused, listOf("Morning playlist"))))
        assertEquals("Starting… · Jazz24", targetStatusText(TargetStatus.Playing(CardStatus.Starting, jazz)))
        assertEquals("Stopping… · Jazz24", targetStatusText(TargetStatus.Playing(CardStatus.Stopping, jazz)))
        assertEquals("Couldn't play · Jazz24", targetStatusText(TargetStatus.Playing(CardStatus.Failed, jazz)))
        assertEquals("Unknown · Jazz24", targetStatusText(TargetStatus.Playing(CardStatus.Unknown, jazz)))
        assertEquals("Playing · Jazz24 + Doorbell", targetStatusText(TargetStatus.Playing(CardStatus.Playing, listOf("Jazz24", "Doorbell"))))
    }

    @Test
    fun groupAndUnavailableStatusLines() {
        assertEquals("In Downstairs", targetStatusText(TargetStatus.InGroup("Downstairs")))
        assertEquals("Group · Radio Paradise", targetStatusText(TargetStatus.GroupPlaying(listOf("Radio Paradise"))))
        assertEquals("Group · Kitchen + Patio", targetStatusText(TargetStatus.GroupMembers(listOf("Kitchen", "Patio"))))
        assertEquals("Turned off", targetStatusText(TargetStatus.TurnedOff))
        assertEquals("Not connected", targetStatusText(TargetStatus.NotConnected))
        assertEquals("No rooms", targetStatusText(TargetStatus.NoRooms))
    }

    @Test
    fun playLabels() {
        assertEquals("Play", playLabelText(PlayLabel.Play))
        assertEquals("Play Jazz24 in Bedroom", playLabelText(PlayLabel.PlaySource("Jazz24", "Bedroom")))
        assertEquals("Starting…", playLabelText(PlayLabel.Starting))
    }

    // ---- consequence -------------------------------------------------------------------------

    private fun a(source: String, where: String) = AffectedPlayback(source, where)

    @Test
    fun willStopLines() {
        assertEquals(
            "Radio Paradise will stop in Bedroom",
            consequenceLineText(ConsequenceLine.WillStop(listOf(a("Radio Paradise", "Bedroom")))),
        )
        assertEquals(
            "Jazz24 will stop in Office and Morning playlist in Kitchen",
            consequenceLineText(ConsequenceLine.WillStop(listOf(a("Jazz24", "Office"), a("Morning playlist", "Kitchen")))),
        )
        assertEquals(
            "A will stop in X, B in Y and C in Z",
            consequenceLineText(ConsequenceLine.WillStop(listOf(a("A", "X"), a("B", "Y"), a("C", "Z")))),
        )
    }

    @Test
    fun otherConsequenceLines() {
        assertEquals("Plays alongside Jazz24 in Bedroom", consequenceLineText(ConsequenceLine.PlaysAlongside(listOf(a("Jazz24", "Bedroom")))))
        assertEquals(
            "Plays alongside Jazz24 in Bedroom and Radio in Kitchen",
            consequenceLineText(ConsequenceLine.PlaysAlongside(listOf(a("Jazz24", "Bedroom"), a("Radio", "Kitchen")))),
        )
        assertEquals(
            "Jazz24 will be lowered in Bedroom while it plays",
            consequenceLineText(ConsequenceLine.WillBeLowered(listOf(a("Jazz24", "Bedroom")))),
        )
        assertEquals("Jazz24 is already playing in Bedroom", consequenceLineText(ConsequenceLine.AlreadyPlaying("Jazz24", "Bedroom")))
    }

    @Test
    fun wontPlayAndMuteNotes() {
        assertEquals("Won't play in Patio (not connected)", wontPlayText(WontPlay(emptyList(), listOf("Patio"))))
        assertEquals("Won't play in Garage (turned off)", wontPlayText(WontPlay(listOf("Garage"), emptyList())))
        assertEquals(
            "Won't play in Garage (turned off), Patio and Deck (not connected)",
            wontPlayText(WontPlay(listOf("Garage"), listOf("Patio", "Deck"))),
        )
        assertEquals("Bedroom is muted", muteNoteText(MuteNote.TargetMuted("Bedroom")))
        assertEquals("All rooms are muted", muteNoteText(MuteNote.AllRoomsMuted))
    }
}
