package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.ConfiguredSourceRow
import sonora.multiroom.mobile.domain.Confirmation
import sonora.multiroom.mobile.domain.GroupRow
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.RoomStatus
import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.ui.session.Connection
import sonora.multiroom.mobile.ui.session.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTextTest {
    private val address = HubAddress("http://multiroom.lan:8080")

    @Test
    fun hubRowFollowsTheSession() {
        assertEquals(HubRow(HubStatus.Hidden, null), hubRow(SessionState.Initial))
        assertEquals(HubRow(HubStatus.NotSet, null), hubRow(SessionState.NoAddress))
        val url = address.baseUrl
        assertEquals(HubRow(HubStatus.Connecting, url), hubRow(SessionState.Connected(address, Connection.Loading)))
        assertEquals(HubRow(HubStatus.Connected, url), hubRow(SessionState.Connected(address, Connection.Live)))
        assertEquals(HubRow(HubStatus.NotConnected, url), hubRow(SessionState.Connected(address, Connection.Unreachable(null))))
    }

    @Test
    fun hubRowTexts() {
        assertEquals("Not set", hubStatusText(HubStatus.NotSet))
        assertEquals("Connecting…", hubStatusText(HubStatus.Connecting))
        assertEquals("Connected", hubStatusText(HubStatus.Connected))
        assertEquals("Not connected", hubStatusText(HubStatus.NotConnected))
        assertEquals("Set the hub address to start", hubAddressLine(hubRow(SessionState.NoAddress)))
        assertEquals("http://multiroom.lan:8080", hubAddressLine(hubRow(SessionState.Connected(address))))
    }

    @Test
    fun hubRowAccessibilityLabel() {
        assertEquals(
            "Hub connection: Connected, http://multiroom.lan:8080. Change address",
            hubRowLabel(hubRow(SessionState.Connected(address, Connection.Live))),
        )
        assertEquals("Hub connection: Not set. Change address", hubRowLabel(hubRow(SessionState.NoAddress)))
    }

    // ---- Rows and dialog (US1) ---------------------------------------------------------------

    @Test
    fun roomStatusTexts() {
        assertEquals("Off", roomStatusText(RoomStatus.Off))
        assertEquals("Not connected", roomStatusText(RoomStatus.NotConnected))
        assertEquals("Speaker", roomStatusText(RoomStatus.Speaker))
        assertEquals("Playing · Radio Paradise", roomStatusText(RoomStatus.Playing(listOf("Radio Paradise"))))
        assertEquals("Playing · A + B", roomStatusText(RoomStatus.Playing(listOf("A", "B"))))
        assertEquals("In Downstairs, Everywhere", roomStatusText(RoomStatus.InGroups(listOf("Downstairs", "Everywhere"))))
    }

    @Test
    fun groupLines() {
        assertEquals("Living Room, Kitchen", groupMembersText(listOf("Living Room", "Kitchen")))
        assertEquals("No rooms", groupMembersText(emptyList()))
        assertEquals("Playing · A + B", groupPlayingText(listOf("A", "B")))
        assertEquals(null, groupPlayingText(emptyList()))
    }

    @Test
    fun configuredLines() {
        fun row(kind: SourceKind, detail: String?) = ConfiguredSourceRow("id", "n", true, kind, detail)
        assertEquals("Stream · stream.radioparadise.com", configuredLine(row(SourceKind.Stream, "stream.radioparadise.com")))
        assertEquals("File · morning.flac", configuredLine(row(SourceKind.File, "morning.flac")))
        assertEquals("Line-in · plughw:…", configuredLine(row(SourceKind.LineIn, "plughw:…")))
        assertEquals("Line-in", configuredLine(row(SourceKind.LineIn, null)))
    }

    @Test
    fun roomDialog() {
        val one = Confirmation.TurnOffRoom("Living Room", listOf("Radio Paradise"))
        assertEquals("Turn off Living Room?", confirmTitle(one))
        assertEquals(
            "Radio Paradise is playing in Living Room. Turning the room off stops playback there.",
            confirmBody(one),
        )
        val two = Confirmation.TurnOffRoom("Living Room", listOf("A", "B"))
        assertEquals("A and B is playing in Living Room. Turning the room off stops playback there.", confirmBody(two))
    }

    @Test
    fun groupDialog() {
        val c = Confirmation.TurnOffGroup("Downstairs", listOf("Radio Paradise"), listOf("Living Room", "Kitchen"))
        assertEquals("Turn off Downstairs?", confirmTitle(c))
        assertEquals(
            "Radio Paradise is playing on Living Room, Kitchen. Turning the group off stops playback in all of these rooms.",
            confirmBody(c),
        )
    }

    @Test
    fun theKeepsPlayingMessageAndTheEmptyText() {
        assertEquals("Jazz FM is off. What's playing from it keeps playing.", keepsPlayingMessage("Jazz FM"))
        assertEquals("No sources in the hub's configuration.", CONFIGURED_EMPTY)
    }
}
