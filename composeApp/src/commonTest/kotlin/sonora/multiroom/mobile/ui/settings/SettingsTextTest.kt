package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.HubAddress
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
}
