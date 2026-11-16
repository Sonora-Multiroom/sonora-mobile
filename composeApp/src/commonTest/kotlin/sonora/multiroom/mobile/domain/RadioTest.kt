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

    // ---- Judging a started station (research R8) -------------------------------------------------

    private val now = 10_000_000L
    private val mango = station("u1")

    private fun playing(
        status: RouteStatus = RouteStatus.Active,
        startedAt: Long? = null,
        input: String? = "radiobrowser://u1",
        routeId: String = "r1",
    ) = HubSnapshot(
        rooms = emptyList(),
        groups = emptyList(),
        routes = listOf(
            Route(
                routeId, "link-1", Target.Room("kitchen"), status, false, false, true,
                startedAt = startedAt?.let(kotlin.time.Instant::fromEpochMilliseconds),
            ),
        ),
        sources = listOfNotNull(
            input?.let { Source("link-1", "MANGORADIO", it, SourceOrigin.Runtime, false, true, SourceKind.Radio) },
        ),
        masterMuted = false,
    )

    private fun pending(trackedAt: Long = now - 60_000, playingSince: Long? = null) =
        PendingStation(mango, "r1", trackedAt, playingSince)

    @Test
    fun aMissingStoppedOrOtherRouteDropsTheEntry() {
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), playing(routeId = "other"), now))
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), playing(RouteStatus.Stopped), now))
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), HubSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), false), now))
    }

    @Test
    fun aMissingInputOrAnotherStationDropsTheEntry() {
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), playing(input = null), now))
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), playing(input = "radiobrowser://other"), now))
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), playing(input = "http://x/stream"), now))
    }

    @Test
    fun aFailedRouteDropsTheEntry() {
        assertEquals(RecentVerdict.Drop, judgeRecent(pending(), playing(RouteStatus.Failed), now))
    }

    @Test
    fun aStartingRouteWaitsUnchanged() {
        val p = pending()
        assertEquals(RecentVerdict.Wait(p), judgeRecent(p, playing(RouteStatus.Starting), now))
    }

    @Test
    fun theFirstActiveRefreshFixesPlayingSinceFromTheHubsStartedAt() {
        val v = judgeRecent(pending(), playing(startedAt = now - 10_000), now)
        assertEquals(RecentVerdict.Wait(pending(playingSince = now - 10_000)), v)
    }

    @Test
    fun aStationThatBeganInTheBackgroundIsRecentInTheSameCall() {
        val v = judgeRecent(pending(trackedAt = now - 400_000), playing(startedAt = now - 300_000), now)
        assertEquals(RecentVerdict.Recent, v)
    }

    @Test
    fun theCountNeverStartsBeforeTheRequestWasSent() {
        // The hub's clock runs behind: it says the audio began 100 s ago, but the request left 5 s ago.
        val v = judgeRecent(pending(trackedAt = now - 5_000), playing(startedAt = now - 100_000), now)
        assertEquals(RecentVerdict.Wait(pending(trackedAt = now - 5_000, playingSince = now - 5_000)), v)
    }

    @Test
    fun withoutAStartedAtTheCountBeginsNow() {
        val v = judgeRecent(pending(), playing(startedAt = null), now)
        assertEquals(RecentVerdict.Wait(pending(playingSince = now)), v)
    }

    @Test
    fun thirtySecondsOfPlayAddTheStation() {
        assertEquals(RecentVerdict.Recent, judgeRecent(pending(playingSince = now - 30_000), playing(), now))
        val p = pending(playingSince = now - 29_999)
        assertEquals(RecentVerdict.Wait(p), judgeRecent(p, playing(), now))
    }

    @Test
    fun timeSpentStartingDoesNotCount() {
        // Starting for 29 s, then playing for 5 s.
        val v = judgeRecent(pending(trackedAt = now - 34_000), playing(startedAt = now - 5_000), now)
        assertEquals(RecentVerdict.Wait(pending(trackedAt = now - 34_000, playingSince = now - 5_000)), v)
    }

    @Test
    fun onceFixedPlayingSinceIsNotChangedByALaterStartedAt() {
        // A move to another room may carry another startedAt; the count is not restarted or lengthened.
        val p = pending(playingSince = now - 10_000)
        assertEquals(RecentVerdict.Wait(p), judgeRecent(p, playing(startedAt = now - 100_000), now))
        val later = pending(playingSince = now - 40_000)
        assertEquals(RecentVerdict.Recent, judgeRecent(later, playing(startedAt = now - 1_000), now))
    }
}
