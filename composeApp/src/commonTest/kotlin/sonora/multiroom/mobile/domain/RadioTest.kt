package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadioTest {
    private fun station(
        uuid: String,
        name: String? = "Name $uuid",
        country: String? = null,
        codec: String? = null,
        bitrate: Int? = null,
    ) = Station(uuid, "radiobrowser://$uuid", name, country, codec, bitrate)

    // ---- Names typed by the user ------------------------------------------------------------

    @Test
    fun stationNameRemovesPercentTrimsAndCuts() {
        assertEquals("mango", stationName("  mango  "))
        assertEquals("mango", stationName("man%go%"))
        assertEquals("a/b", stationName("a/b"))
        assertEquals(100, stationName("x".repeat(150))?.length)
    }

    @Test
    fun stationNameIsNullWhenEmptyOrBlank() {
        assertNull(stationName(""))
        assertNull(stationName("   "))
        assertNull(stationName("%%"))
        assertNull(stationName(" % "))
    }

    @Test
    fun tagNameAlsoRemovesSlash() {
        assertEquals("jazz", tagName("ja/zz%"))
        assertNull(tagName("/ %"))
        assertEquals(100, tagName("y".repeat(120))?.length)
    }

    // ---- What a row says --------------------------------------------------------------------

    @Test
    fun displayNameFallsBack() {
        assertEquals("MANGORADIO", stationDisplayName(station("a", name = "MANGORADIO")))
        assertEquals("Unnamed station", stationDisplayName(station("a", name = null)))
    }

    @Test
    fun detailJoinsTheKnownParts() {
        assertEquals("DE · MP3 · 128 kbps", stationDetail(station("a", country = "DE", codec = "MP3", bitrate = 128)))
        assertEquals("MP3", stationDetail(station("a", codec = "MP3")))
        assertEquals("DE · 64 kbps", stationDetail(station("a", country = "DE", bitrate = 64)))
        assertNull(stationDetail(station("a")))
    }

    // ---- Paging -----------------------------------------------------------------------------

    @Test
    fun mergePageDropsAnAlreadyListedUuidAndKeepsPageOrder() {
        val listed = listOf(station("a"), station("b"))
        val page = StationPage(listOf(station("c"), station("a"), station("d")), 150)
        assertEquals(listOf("a", "b", "c", "d"), mergePage(listed, page).map { it.uuid })
    }

    @Test
    fun mergePageOfOnlyDuplicatesChangesNothing() {
        val listed = listOf(station("a"))
        assertEquals(listed, mergePage(listed, StationPage(listOf(station("a")), 100)))
    }

    @Test
    fun canLoadMoreAndLimitReached() {
        assertTrue(canLoadMore(50))
        assertTrue(canLoadMore(9950))
        assertFalse(canLoadMore(10_000))
        assertFalse(canLoadMore(null))
        assertTrue(limitReached(10_000))
        assertTrue(limitReached(10_050))
        assertFalse(limitReached(9950))
        assertFalse(limitReached(null))
    }

    // ---- Filters ----------------------------------------------------------------------------

    @Test
    fun regionCountryMatchesTheCodeIgnoringCase() {
        val countries = listOf(Country("UA", "Ukraine", 100), Country("DE", "Germany", 90))
        assertEquals("Germany", regionCountry("de", countries)?.name)
        assertEquals("Ukraine", regionCountry("UA", countries)?.name)
        assertNull(regionCountry("FR", countries))
        assertNull(regionCountry("", countries))
    }

    @Test
    fun narrowKeepsNamesContainingTheTextIgnoringCase() {
        val tags = listOf(Tag("Jazz", 5), Tag("smooth jazz", 3), Tag("rock", 9))
        assertEquals(listOf("Jazz", "smooth jazz"), narrow(tags, " JAZ ") { it.name }.map { it.name })
        assertEquals(tags, narrow(tags, "  ") { it.name })
    }

    @Test
    fun groupThousandsInsertsCommas() {
        assertEquals("6,965", groupThousands(6965))
        assertEquals("12", groupThousands(12))
        assertEquals("1,234,567", groupThousands(1_234_567))
        assertEquals("0", groupThousands(0))
    }

    // ---- Recent -----------------------------------------------------------------------------

    @Test
    fun addRecentPutsTheStationFirstAndRemovesTheOlderEntry() {
        val list = listOf(station("a"), station("b"), station("c"))
        assertEquals(listOf("b", "a", "c"), addRecent(list, station("b")).map { it.uuid })
        assertEquals(listOf("z", "a", "b", "c"), addRecent(list, station("z")).map { it.uuid })
    }

    @Test
    fun addRecentKeepsTen() {
        val list = (1..10).map { station("s$it") }
        val next = addRecent(list, station("new"))
        assertEquals(RECENT_LIMIT, next.size)
        assertEquals("new", next.first().uuid)
        assertEquals("s9", next.last().uuid)
    }

    // ---- Availability -----------------------------------------------------------------------

    private fun inventory(vararg e: Pair<String, ExtensionStatus>) = ExtensionInventory(
        loadingEnabled = true,
        extensions = e.map { Extension(it.first, it.first, it.second, ExtensionConnection.Unknown) },
    )

    @Test
    fun radioIsOfferedOnlyForAnActiveRadioExtension() {
        assertTrue(radioOffered(inventory("radio" to ExtensionStatus.Active)))
        assertTrue(radioOffered(inventory("Radio" to ExtensionStatus.Active)))
        assertTrue(radioOffered(inventory("tts" to ExtensionStatus.Active, "RADIO" to ExtensionStatus.Active)))
        assertFalse(radioOffered(inventory("radio" to ExtensionStatus.Disabled)))
        assertFalse(radioOffered(inventory("radio" to ExtensionStatus.Rejected)))
        assertFalse(radioOffered(inventory("radio" to ExtensionStatus.Inactive)))
        assertFalse(radioOffered(inventory("radio" to ExtensionStatus.Unknown)))
        assertFalse(radioOffered(inventory("tts" to ExtensionStatus.Active)))
        assertFalse(radioOffered(inventory()))
    }

    // ---- Filters to a query -----------------------------------------------------------------

    @Test
    fun filtersBuildAQuery() {
        val filters = RadioFilters(Country("DE", "Germany", 0), "jazz", "german", StationOrder.Trending)
        assertEquals(StationQuery("mango", "DE", "jazz", "german", StationOrder.Trending), filters.toQuery("mango"))
        assertEquals(StationQuery(null, null, null, null, StationOrder.MostVoted), RadioFilters().toQuery(null))
    }
}
