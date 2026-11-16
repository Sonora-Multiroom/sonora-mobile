package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationOrder
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
    fun theChipsSpokenRemovals() {
        assertEquals("Remove the country Ukraine", removeFilterLabel(FilterSheetState.Kind.Country, "Ukraine"))
        assertEquals("Remove the tag jazz", removeFilterLabel(FilterSheetState.Kind.Tag, "jazz"))
        assertEquals("Remove the language german", removeFilterLabel(FilterSheetState.Kind.Language, "german"))
    }

    @Test
    fun theChipsUnchosenWords() {
        assertEquals("Country", RadioText.sheetTitle(FilterSheetState.Kind.Country))
        assertEquals("Tag", RadioText.sheetTitle(FilterSheetState.Kind.Tag))
        assertEquals("Language", RadioText.sheetTitle(FilterSheetState.Kind.Language))
        assertEquals("Order", RadioText.sheetTitle(FilterSheetState.Kind.Order))
    }

    @Test
    fun theOrderControlAndTheListHeading() {
        assertEquals("Order: Most voted. Change the order", orderControlLabel(StationOrder.MostVoted))
        assertEquals("Order: Name A–Z. Change the order", orderControlLabel(StationOrder.NameAZ))
        assertEquals("STATIONS IN UKRAINE", listHeading(Country("UA", "Ukraine", 1)))
        assertEquals("STATIONS", listHeading(null))
    }

    @Test
    fun theSheetFieldLabelsAndOrderRows() {
        assertEquals("Search countries", RadioText.searchLabel(FilterSheetState.Kind.Country))
        assertEquals("Search tags", RadioText.searchLabel(FilterSheetState.Kind.Tag))
        assertEquals("Search languages", RadioText.searchLabel(FilterSheetState.Kind.Language))
        assertEquals(null, RadioText.searchLabel(FilterSheetState.Kind.Order))
        assertEquals("Most voted", orderName(StationOrder.MostVoted))
        assertEquals("Trending", orderName(StationOrder.Trending))
        assertEquals("Name A–Z", orderName(StationOrder.NameAZ))
        assertEquals("The catalogue's all-time favourites", orderDetail(StationOrder.MostVoted))
        assertEquals("Most plays in the last 24 hours", orderDetail(StationOrder.Trending))
        assertEquals(null, orderDetail(StationOrder.NameAZ))
        assertEquals("Cancel", RadioText.CANCEL)
        assertEquals("Loading more tags…", RadioText.LOADING_MORE_TAGS)
    }

    @Test
    fun theRecentSectionsWordingAndSpokenLabel() {
        assertEquals("RECENT", RadioText.RECENT_HEADING)
        assertEquals("Remove MANGORADIO from Recent", removeRecentLabel(full))
        assertEquals("Remove Unnamed station from Recent", removeRecentLabel(bare))
    }
}
