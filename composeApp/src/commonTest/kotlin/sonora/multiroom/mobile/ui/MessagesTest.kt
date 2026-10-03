package sonora.multiroom.mobile.ui

import sonora.multiroom.mobile.data.HubError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MessagesTest {
    private val unreachable = HubError.Unreachable
    private val notFound = HubError.Rejected(404, "urn:multiroom:error:not-found")
    private val other = HubError.Rejected(500, null)
    private val unexpected = HubError.Unexpected

    @Test
    fun volume() {
        val a = UserAction.Volume
        assertEquals("Couldn't change the volume in Kitchen. Can't reach the hub.", actionErrorMessage(a, "Kitchen", unreachable))
        assertEquals("Kitchen is no longer on the hub.", actionErrorMessage(a, "Kitchen", notFound))
        assertEquals("Couldn't change the volume in Kitchen.", actionErrorMessage(a, "Kitchen", other))
        assertEquals("Couldn't change the volume in Kitchen.", actionErrorMessage(a, "Kitchen", unexpected))
    }

    @Test
    fun stop() {
        val a = UserAction.Stop
        assertEquals("Couldn't stop Downstairs. Can't reach the hub.", actionErrorMessage(a, "Downstairs", unreachable))
        assertEquals("That playback has already ended.", actionErrorMessage(a, "Downstairs", notFound))
        assertEquals("Couldn't stop Downstairs.", actionErrorMessage(a, "Downstairs", other))
        assertEquals("Couldn't stop Downstairs.", actionErrorMessage(a, "Downstairs", unexpected))
    }

    @Test
    fun pause() {
        val a = UserAction.Pause
        assertEquals("Couldn't pause Office. Can't reach the hub.", actionErrorMessage(a, "Office", unreachable))
        assertEquals("That playback has already ended.", actionErrorMessage(a, "Office", notFound))
        assertEquals("Couldn't pause Office.", actionErrorMessage(a, "Office", other))
        assertEquals("Couldn't pause Office.", actionErrorMessage(a, "Office", unexpected))
    }

    @Test
    fun resume() {
        val a = UserAction.Resume
        assertEquals("Couldn't resume Office. Can't reach the hub.", actionErrorMessage(a, "Office", unreachable))
        assertEquals("That playback has already ended.", actionErrorMessage(a, "Office", notFound))
        assertEquals("Couldn't resume Office.", actionErrorMessage(a, "Office", other))
        assertEquals("Couldn't resume Office.", actionErrorMessage(a, "Office", unexpected))
    }

    @Test
    fun masterMute() {
        val mute = UserAction.MasterMute(on = true)
        val unmute = UserAction.MasterMute(on = false)
        assertEquals("Couldn't mute all rooms. Can't reach the hub.", actionErrorMessage(mute, "", unreachable))
        assertEquals("Couldn't unmute all rooms. Can't reach the hub.", actionErrorMessage(unmute, "", unreachable))
        // No target can disappear, so a 404 reads like any other rejection.
        assertEquals("Couldn't mute all rooms.", actionErrorMessage(mute, "", notFound))
        assertEquals("Couldn't mute all rooms.", actionErrorMessage(mute, "", other))
        assertEquals("Couldn't unmute all rooms.", actionErrorMessage(unmute, "", unexpected))
    }

    @Test
    fun hubWordingNeverReachesTheMessage() {
        val rejected = HubError.Rejected(500, "urn:multiroom:error:internal")
        for (a in listOf(UserAction.Volume, UserAction.Stop, UserAction.Pause, UserAction.Resume, UserAction.MasterMute(true))) {
            val text = actionErrorMessage(a, "X", rejected)
            assertFalse("urn:" in text, text)
        }
    }
}
