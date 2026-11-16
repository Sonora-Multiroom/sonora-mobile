package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.Station
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioTextTest {
    private val full = Station("a", "radiobrowser://a", "MANGORADIO", "DE", "MP3", 128)
    private val bare = Station("b", "radiobrowser://b", null, null, null, null)

    @Test
    fun theScreensWording() {
        assertEquals("Radio stations", RadioText.TITLE)
        assertEquals("Station name", RadioText.NAME_FIELD)
        assertEquals("Clear the name", RadioText.CLEAR_NAME)
        assertEquals("Back", RadioText.BACK)
        assertEquals("STATIONS", RadioText.LIST_HEADING)
        assertEquals("Loading stations", RadioText.LOADING)
        assertEquals("Loading more stations…", RadioText.LOADING_MORE)
    }

    @Test
    fun aStationRowReadsAsOneNode() {
        assertEquals("MANGORADIO, DE · MP3 · 128 kbps", stationSpoken(full, "DE · MP3 · 128 kbps"))
        assertEquals("Unnamed station", stationSpoken(bare, null))
        assertEquals("Unnamed station", RadioText.UNNAMED)
    }

    @Test
    fun theRadioScreensSecondLineIsTheKnownPartsAlone() {
        assertEquals("DE · MP3 · 128 kbps", stationRowDetail(full))
        assertEquals(null, stationRowDetail(bare))
    }

    @Test
    fun theRecentSectionsWordingAndSpokenLabel() {
        assertEquals("RECENT", RadioText.RECENT_HEADING)
        assertEquals("Remove MANGORADIO from Recent", removeRecentLabel(full))
        assertEquals("Remove Unnamed station from Recent", removeRecentLabel(bare))
    }
}
