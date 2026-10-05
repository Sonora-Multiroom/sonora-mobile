package sonora.multiroom.mobile.ui.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsNavigatorTest {
    @Test
    fun startsOnTheRoomsTab() {
        assertEquals(SettingsTab.Rooms, SettingsNavigator().tab.value)
    }

    @Test
    fun selectRemembersTheTab() {
        val nav = SettingsNavigator()
        nav.select(SettingsTab.Groups)
        assertEquals(SettingsTab.Groups, nav.tab.value)
    }

    @Test
    fun theSheetRequestIsOneShot() {
        val nav = SettingsNavigator()
        assertFalse(nav.openSheetRequested.value)
        nav.openSheet()
        assertTrue(nav.openSheetRequested.value)
        nav.consumeSheetRequest()
        assertFalse(nav.openSheetRequested.value)
    }
}
