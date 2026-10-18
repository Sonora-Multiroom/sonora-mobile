package sonora.multiroom.mobile.ui

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.domain.StartNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun mute() {
        val on = UserAction.Mute(on = true)
        assertEquals("Couldn't mute Kitchen. Can't reach the hub.", actionErrorMessage(on, "Kitchen", unreachable))
        assertEquals("Kitchen is no longer on the hub.", actionErrorMessage(on, "Kitchen", notFound))
        assertEquals("Couldn't mute Kitchen.", actionErrorMessage(on, "Kitchen", other))
        assertEquals("Couldn't mute Kitchen.", actionErrorMessage(on, "Kitchen", unexpected))
        val off = UserAction.Mute(on = false)
        assertEquals("Couldn't unmute Kitchen. Can't reach the hub.", actionErrorMessage(off, "Kitchen", unreachable))
        assertEquals("Kitchen is no longer on the hub.", actionErrorMessage(off, "Kitchen", notFound))
        assertEquals("Couldn't unmute Kitchen.", actionErrorMessage(off, "Kitchen", other))
    }

    @Test
    fun move() {
        val a = UserAction.Move("Kitchen")
        assertEquals("Couldn't move Jazz24 to Kitchen. Can't reach the hub.", actionErrorMessage(a, "Jazz24", unreachable))
        assertEquals("Couldn't move Jazz24 to Kitchen.", actionErrorMessage(a, "Jazz24", notFound))
        assertEquals("Couldn't move Jazz24 to Kitchen.", actionErrorMessage(a, "Jazz24", other))
        assertEquals("Couldn't move Jazz24 to Kitchen.", actionErrorMessage(a, "Jazz24", unexpected))
    }

    // ---- Start playback (003, research R6) ---------------------------------------------------

    private val names = StartNames("Jazz24", "Bedroom")
    private val roomName: (String) -> String? = { mapOf("kitchen" to "Kitchen", "office" to "Office Speaker")[it] }
    private fun source(error: HubError) = startFailure(StartKind.Source, error, names, roomName)

    @Test
    fun aReasonDecidesWhateverTheStatus() {
        for (status in listOf(409, 422, 400)) {
            assertEquals(
                StartFailure.RoomFull("Kitchen"),
                source(HubError.Rejected(status, null, "ROUTE_LIMIT_REACHED", "kitchen")),
            )
            assertEquals(
                StartFailure.AlreadyThere("Jazz24", "Kitchen"),
                source(HubError.Rejected(status, null, "INPUT_ALREADY_ON_OUTPUT", "kitchen")),
            )
        }
    }

    @Test
    fun theRoomFallsBackToTheChosenTargetWhenMissingOrUnknown() {
        assertEquals(StartFailure.RoomFull("Bedroom"), source(HubError.Rejected(409, null, "ROUTE_LIMIT_REACHED", null)))
        assertEquals(StartFailure.RoomFull("Bedroom"), source(HubError.Rejected(409, null, "ROUTE_LIMIT_REACHED", "ghost")))
        assertEquals(StartFailure.AlreadyThere("Jazz24", "Bedroom"), source(HubError.Rejected(409, null, "INPUT_ALREADY_ON_OUTPUT", "ghost")))
    }

    @Test
    fun aSourceStartWithoutAReasonIsOtherExceptFor404() {
        assertEquals(StartFailure.Other, source(HubError.Rejected(400, null)))
        assertEquals(StartFailure.Other, source(HubError.Rejected(422, null)))
        assertEquals(StartFailure.Other, source(HubError.Rejected(502, null)))
        assertEquals(StartFailure.Other, source(HubError.Rejected(500, null, "SOMETHING_NEW", null)))
        assertIs<StartFailure.NoLongerOnHub>(source(HubError.Rejected(404, "urn:multiroom:error:not-found")))
        assertEquals(StartFailure.HubUnreachable, source(HubError.Unreachable))
        assertEquals(StartFailure.Other, source(HubError.Unexpected))
    }

    @Test
    fun startFailureCopy() {
        assertEquals("Kitchen can't play more at once", startFailureMessage(StartFailure.RoomFull("Kitchen")))
        assertEquals("Jazz24 is already playing in Office", startFailureMessage(StartFailure.AlreadyThere("Jazz24", "Office")))
        assertEquals("The hub couldn't play this link", startFailureMessage(StartFailure.LinkUnusable))
        assertEquals("Couldn't reach that link", startFailureMessage(StartFailure.LinkUnreachable))
        assertEquals("That service isn't available right now. Try again later.", startFailureMessage(StartFailure.ServiceDown))
        assertEquals("Bedroom is no longer on the hub", startFailureMessage(StartFailure.NoLongerOnHub("Bedroom")))
        assertEquals("Couldn't reach the hub", startFailureMessage(StartFailure.HubUnreachable))
        assertEquals("Couldn't start playback", startFailureMessage(StartFailure.Other))
    }

    private val linkNames = StartNames(null, "Bedroom")
    private fun link(error: HubError) = startFailure(StartKind.Link, error, linkNames, roomName)

    @Test
    fun linkFailuresFollowTheStatus() {
        assertEquals(StartFailure.LinkUnusable, link(HubError.Rejected(400, null)))
        assertEquals(StartFailure.LinkUnusable, link(HubError.Rejected(422, null)))
        assertEquals(StartFailure.LinkUnreachable, link(HubError.Rejected(502, null)))
        assertEquals(StartFailure.ServiceDown, link(HubError.Rejected(503, null)))
        assertEquals(StartFailure.NoLongerOnHub("Bedroom"), link(HubError.Rejected(404, null)))
        assertEquals(StartFailure.Other, link(HubError.Rejected(500, null)))
        assertEquals(StartFailure.HubUnreachable, link(HubError.Unreachable))
        assertEquals(StartFailure.Other, link(HubError.Unexpected))
    }

    @Test
    fun aLinkRefusalReasonStillWinsOverTheStatus() {
        assertEquals(StartFailure.RoomFull("Kitchen"), link(HubError.Rejected(422, null, "ROUTE_LIMIT_REACHED", "kitchen")))
        // A link has no source name yet, so this one is not named.
        assertEquals(StartFailure.Other, link(HubError.Rejected(409, null, "INPUT_ALREADY_ON_OUTPUT", "kitchen")))
    }

    // ---- Turned off (006) ---------------------------------------------------------------------

    private fun turnedOff(reason: String, outputId: String? = null) = HubError.Rejected(409, null, reason, outputId)
    private val groupNames = StartNames("Jazz24", "Living Room")
    private val groupLinkNames = StartNames(null, "Living Room")

    private fun startText(kind: StartKind, error: HubError, n: StartNames) =
        startFailureMessage(startFailure(kind, error, n, roomName))

    @Test
    fun aStartRefusedForSomethingTurnedOffNamesIt() {
        for (kind in StartKind.entries) {
            val n = if (kind == StartKind.Source) names else linkNames
            val g = if (kind == StartKind.Source) groupNames else groupLinkNames
            assertEquals("Office Speaker is turned off", startText(kind, turnedOff("OUTPUT_DISABLED", "office"), n), kind.name)
            assertEquals("Bedroom is turned off", startText(kind, turnedOff("OUTPUT_DISABLED", "ghost"), n), kind.name)
            assertEquals("Bedroom is turned off", startText(kind, turnedOff("OUTPUT_DISABLED"), n), kind.name)
            assertEquals("Living Room is turned off", startText(kind, turnedOff("GROUP_DISABLED"), g), kind.name)
            assertEquals("All rooms in Living Room are turned off", startText(kind, turnedOff("ALL_MEMBERS_DISABLED"), g), kind.name)
            assertEquals("Couldn't start playback", startText(kind, turnedOff("SOMETHING_NEW"), n), kind.name)
        }
        assertEquals("Jazz24 is turned off", startText(StartKind.Source, turnedOff("INPUT_DISABLED"), names))
        // A link has no source name to give.
        assertEquals("Couldn't start playback", startText(StartKind.Link, turnedOff("INPUT_DISABLED"), linkNames))
    }

    @Test
    fun theReasonDecidesATurnedOffRefusalWhateverTheStatus() {
        for (status in listOf(400, 409, 422)) {
            assertEquals(
                StartFailure.TurnedOff(TurnedOff.Room("Office Speaker")),
                source(HubError.Rejected(status, null, "OUTPUT_DISABLED", "office")),
            )
        }
    }

    @Test
    fun onlyATurnedOffRefusalAsksForARefresh() {
        val turnedOffFailures = listOf(
            source(turnedOff("OUTPUT_DISABLED", "office")), source(turnedOff("GROUP_DISABLED")),
            source(turnedOff("ALL_MEMBERS_DISABLED")), source(turnedOff("INPUT_DISABLED")),
            link(turnedOff("OUTPUT_DISABLED")),
        )
        for (f in turnedOffFailures) {
            assertIs<StartFailure.TurnedOff>(f)
            assertTrue(f.needsRefresh, f.toString())
        }
        val others = listOf(
            StartFailure.RoomFull("Kitchen"), StartFailure.AlreadyThere("Jazz24", "Kitchen"), StartFailure.LinkUnusable,
            StartFailure.LinkUnreachable, StartFailure.ServiceDown, StartFailure.NoLongerOnHub("Bedroom"),
            StartFailure.HubUnreachable, StartFailure.Other,
        )
        for (f in others) assertFalse(f.needsRefresh, f.toString())
    }

    private fun moveText(error: HubError) = actionErrorMessage(UserAction.Move("Bedroom"), "KissFM", error, roomName)

    @Test
    fun aMoveRefusedForSomethingTurnedOffNamesIt() {
        assertEquals("Office Speaker is turned off", moveText(turnedOff("OUTPUT_DISABLED", "office")))
        assertEquals("Bedroom is turned off", moveText(turnedOff("OUTPUT_DISABLED", "ghost")))
        assertEquals("Bedroom is turned off", moveText(turnedOff("GROUP_DISABLED")))
        assertEquals("All rooms in Bedroom are turned off", moveText(turnedOff("ALL_MEMBERS_DISABLED")))
        assertEquals("KissFM is turned off", moveText(turnedOff("INPUT_DISABLED")))
        assertEquals("Couldn't move KissFM to Bedroom.", moveText(turnedOff("SOMETHING_NEW")))
        assertEquals("Couldn't move KissFM to Bedroom. Try again in a moment.", moveText(turnedOff("ROUTE_CHANGING_OUTPUTS")))
        assertEquals("Couldn't move KissFM to Bedroom. Can't reach the hub.", moveText(unreachable))
    }

    @Test
    fun aRefusalsWordingNeverReachesTheMessage() {
        // The error carries the problem's type only; every message is a fixed string around names.
        val withType = HubError.Rejected(409, "urn:multiroom:error:conflict", "OUTPUT_DISABLED", "office")
        assertFalse("urn:" in moveText(withType))
        assertFalse("urn:" in startText(StartKind.Source, withType, names))
    }

    // ---- Settings (004, FR-013) --------------------------------------------------------------

    @Test
    fun turningSomethingOnOrOff() {
        val off = SettingsAction.Turn(on = false)
        val on = SettingsAction.Turn(on = true)
        val gone = settingsFailure(off, "Kitchen", notFound)
        assertEquals("Kitchen is no longer on the hub", settingsFailureMessage(gone!!))
        assertEquals(true, gone.needsRefresh)
        assertEquals("Couldn't reach the hub", settingsFailureMessage(settingsFailure(off, "Kitchen", unreachable)!!))
        assertEquals(false, settingsFailure(off, "Kitchen", unreachable)!!.needsRefresh)
        for (e in listOf(HubError.Rejected(400, null), other, unexpected)) {
            assertEquals("Couldn't turn Kitchen off", settingsFailureMessage(settingsFailure(off, "Kitchen", e)!!))
            assertEquals("Couldn't turn Kitchen on", settingsFailureMessage(settingsFailure(on, "Kitchen", e)!!))
        }
    }

    // ---- Removing a source (FR-018) ----------------------------------------------------------

    @Test
    fun removingASource() {
        val a = SettingsAction.Remove
        assertEquals(
            "Radio comes from the hub's configuration and can't be removed",
            settingsFailureMessage(settingsFailure(a, "Radio", HubError.Rejected(400, null))!!),
        )
        assertEquals("Couldn't reach the hub", settingsFailureMessage(settingsFailure(a, "Radio", unreachable)!!))
        assertEquals("Couldn't remove Radio", settingsFailureMessage(settingsFailure(a, "Radio", other)!!))
        assertEquals("Couldn't remove Radio", settingsFailureMessage(settingsFailure(a, "Radio", unexpected)!!))
        // Already gone is a success for the caller.
        assertEquals(null, settingsFailure(a, "Radio", notFound))
    }
}
