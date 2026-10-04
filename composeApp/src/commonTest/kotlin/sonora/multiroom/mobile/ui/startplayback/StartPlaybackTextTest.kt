package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.CardStatus
import sonora.multiroom.mobile.domain.TargetStatus
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
}
