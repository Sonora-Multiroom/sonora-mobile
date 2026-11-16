package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.data.InMemoryRadioStore
import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.Language
import sonora.multiroom.mobile.domain.RadioFilters
import sonora.multiroom.mobile.domain.Tag
import sonora.multiroom.mobile.domain.TagPage
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationOrder
import sonora.multiroom.mobile.domain.StationPage
import sonora.multiroom.mobile.domain.StationQuery
import sonora.multiroom.mobile.runViewModelTest
import sonora.multiroom.mobile.ui.RadioFailure
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.FakeRepository
import sonora.multiroom.mobile.ui.rooms.unreachable
import sonora.multiroom.mobile.ui.session.HubSession
import sonora.multiroom.mobile.ui.session.RadioAvailability
import sonora.multiroom.mobile.ui.session.RadioOffer
import sonora.multiroom.mobile.ui.session.StationPicker
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadioViewModelTest {
    private val address = HubAddress("http://hub:8080")
    private val everything = StationQuery(null, null, null, null, StationOrder.MostVoted)

    private fun station(id: String) = Station(id, "radiobrowser://$id", "Station $id", null, null, null)

    /** [count] stations named "<prefix><offset+i>", with [next] as the next offset. */
    private fun page(offset: Int, count: Int = 50, next: Int? = offset + count, prefix: String = "s") =
        HubResult.Ok(StationPage((0 until count).map { station("$prefix${offset + it}") }, next))

    private class Setup(
        val vm: RadioViewModel,
        val session: HubSession,
        val factory: FakeFactory,
        val availability: RadioAvailability,
        val picker: StationPicker,
        val store: InMemoryRadioStore,
        val handle: SavedStateHandle,
    ) {
        val repo get() = factory.last
        val ui get() = vm.state.value
        val list get() = vm.state.value.list
    }

    private fun TestScope.live(
        handle: SavedStateHandle = SavedStateHandle(),
        store: InMemoryRadioStore = InMemoryRadioStore(),
        visible: Boolean = true,
        region: String = "UA",
        script: (FakeRepository) -> Unit = { it.stationsResult = { _, offset -> page(offset) } },
    ): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        runCurrent()
        script(factory.last)
        val availability = RadioAvailability(session, backgroundScope)
        val picker = StationPicker()
        val vm = RadioViewModel(handle, session, availability, picker, store) { region }
        if (visible) vm.onVisible()
        runCurrent()
        return Setup(vm, session, factory, availability, picker, store, handle)
    }

    // ---- the first page and the name ------------------------------------------------------------

    @Test
    fun theListLoadsWithoutTypingWithNoFilterAndMostVotedOrder() = runViewModelTest {
        val s = live()
        assertEquals(listOf(everything to 0), s.repo.searches)
        assertEquals(ListPhase.Loaded, s.list.phase)
        assertEquals(50, s.list.stations.size)
        assertEquals(50, s.list.nextOffset)
        s.vm.onHidden()
    }

    @Test
    fun aTypedNameLoadsHalfASecondAfterTheLastChange() = runViewModelTest {
        val s = live()
        s.vm.onNameChange("mango"); runCurrent()
        advanceTimeBy(499); runCurrent()
        assertEquals(1, s.repo.searches.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(everything.copy(name = "mango") to 0, s.repo.searches.last())
        s.vm.onHidden()
    }

    @Test
    fun aPercentSignIsDroppedFromTheFieldAndNeverSent() = runViewModelTest {
        val s = live()
        s.vm.onNameChange("man%go"); runCurrent()
        assertEquals("mango", s.ui.nameText)
        advanceTimeBy(500); runCurrent()
        assertEquals("mango", s.repo.searches.last().first.name)
        s.vm.onNameChange("%"); runCurrent()
        assertEquals("", s.ui.nameText)
        s.vm.onHidden()
    }

    @Test
    fun theFieldStopsAtOneHundredCharacters() = runViewModelTest {
        val s = live()
        s.vm.onNameChange("x".repeat(130)); runCurrent()
        assertEquals(100, s.ui.nameText.length)
        s.vm.onHidden()
    }

    @Test
    fun onlyTheLatestTextDecidesTheList() = runViewModelTest {
        val s = live { repo ->
            repo.radioDelayMs = 1_000
            repo.stationsResult = { q, _ -> HubResult.Ok(StationPage(listOf(station(q.name ?: "all")), null)) }
        }
        advanceTimeBy(1_000); runCurrent()
        s.vm.onNameChange("a"); runCurrent()
        advanceTimeBy(500); runCurrent() // "a" is out, its answer is still on its way
        s.vm.onNameChange("ab"); runCurrent()
        advanceTimeBy(500); runCurrent()
        advanceTimeBy(1_500); runCurrent()
        assertEquals(listOf("ab"), s.list.stations.map { it.uuid })
        assertEquals(ListPhase.Loaded, s.list.phase)
        s.vm.onHidden()
    }

    @Test
    fun aBlankNameCountsAsEmptyAndATrailingSpaceLoadsNothingAgain() = runViewModelTest {
        val s = live()
        s.vm.onNameChange("   "); runCurrent()
        advanceTimeBy(600); runCurrent()
        assertEquals(1, s.repo.searches.size)
        s.vm.onNameChange("mango"); runCurrent()
        advanceTimeBy(600); runCurrent()
        assertEquals(2, s.repo.searches.size)
        s.vm.onNameChange("mango "); runCurrent()
        advanceTimeBy(600); runCurrent()
        assertEquals(2, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun clearingTheNameLoadsTheFullListAgain() = runViewModelTest {
        val s = live()
        s.vm.onNameChange("mango"); runCurrent()
        advanceTimeBy(500); runCurrent()
        s.vm.onClearName(); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals("", s.ui.nameText)
        assertNull(s.repo.searches.last().first.name)
        s.vm.onHidden()
    }

    @Test
    fun theTypedNameIsKeptInSavedStateAndLoadedAgain() = runViewModelTest {
        val handle = SavedStateHandle()
        val first = live(handle)
        first.vm.onNameChange("mango"); runCurrent()
        assertEquals("mango", handle.get<String>("name"))
        first.vm.onHidden()
        val second = live(handle)
        assertEquals("mango", second.ui.nameText)
        assertEquals("mango", second.repo.searches.first().first.name)
        second.vm.onHidden()
    }

    @Test
    fun noStationsMatchForAnEmptyLoadedList() = runViewModelTest {
        val s = live { it.stationsResult = { _, _ -> HubResult.Ok(StationPage(emptyList(), null)) } }
        assertTrue(s.list.empty)
        assertNull(s.list.problem)
        s.vm.onHidden()
    }

    // ---- paging ---------------------------------------------------------------------------------

    @Test
    fun theNextPageIsRequestedWithinTenRowsOfTheEnd() = runViewModelTest {
        val s = live()
        s.vm.onListPosition(lastVisible = 38); runCurrent()
        assertEquals(1, s.repo.searches.size)
        s.vm.onListPosition(lastVisible = 40); runCurrent()
        assertEquals(everything to 50, s.repo.searches.last())
        assertEquals(100, s.list.stations.size)
        assertEquals(100, s.list.nextOffset)
        s.vm.onHidden()
    }

    @Test
    fun aStationAlreadyListedIsDroppedFromALaterPage() = runViewModelTest {
        val s = live {
            it.stationResults += page(0)
            it.stationResults += HubResult.Ok(StationPage((45 until 95).map { i -> station("s$i") }, 100))
        }
        s.vm.onListPosition(45); runCurrent()
        assertEquals(95, s.list.stations.size)
        assertEquals(95, s.list.stations.map { it.uuid }.toSet().size)
        s.vm.onHidden()
    }

    @Test
    fun aPageOfOnlyDuplicatesIsFollowedByTheNextRequestAtOnce() = runViewModelTest {
        val s = live {
            it.stationResults += page(0)
            it.stationResults += page(0, next = 100) // the same fifty again
            it.stationResults += page(100, next = 150)
        }
        s.vm.onListPosition(45); runCurrent()
        assertEquals(listOf(0, 50, 100), s.repo.searches.map { it.second })
        assertEquals(100, s.list.stations.size)
        s.vm.onHidden()
    }

    @Test
    fun pagingStopsWhenThereIsNoNextOffset() = runViewModelTest {
        val s = live { it.stationResults += page(0, count = 20, next = null) }
        s.vm.onListPosition(19); runCurrent()
        assertEquals(1, s.repo.searches.size)
        assertFalse(s.list.limitReached)
        s.vm.onHidden()
    }

    @Test
    fun pagingStopsAtTenThousandAndSaysSo() = runViewModelTest {
        val s = live { it.stationResults += page(9950, next = 10_000) }
        s.vm.onListPosition(45); runCurrent()
        assertTrue(s.list.limitReached)
        val calls = s.repo.searches.size
        s.vm.onListPosition(49); runCurrent()
        assertEquals(calls, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun aNextPageStillInFlightWhenTheQueryChangesIsNeverUsed() = runViewModelTest {
        val s = live { repo ->
            repo.stationsResult = { q, offset ->
                if (q.name == null) page(offset, prefix = "old") else page(offset, count = 3, next = null, prefix = "new")
            }
        }
        s.repo.radioDelayMs = 1_000
        s.vm.onListPosition(45); runCurrent() // the next page is out
        s.vm.onNameChange("x"); runCurrent()
        advanceTimeBy(500); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf("new0", "new1", "new2"), s.list.stations.map { it.uuid })
        s.vm.onHidden()
    }

    // ---- choosing and leaving --------------------------------------------------------------------

    @Test
    fun choosingAStationHandsItBackAndExits() = runViewModelTest {
        val s = live()
        val chosen = s.list.stations[3]
        s.vm.onChoose(chosen)
        assertEquals(chosen, s.picker.picked.value)
        assertEquals(RadioExit.Chosen, s.ui.exit)
        s.vm.consumeExit()
        assertNull(s.ui.exit)
        s.vm.onHidden()
    }

    @Test
    fun nothingChangesAfterTheViewModelIsCleared() = runViewModelTest {
        val s = live { it.radioDelayMs = 1_000; it.stationsResult = { _, o -> page(o) } }
        val before = s.ui
        s.vm.viewModelScope.cancel()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(before.list, s.ui.list)
        s.vm.onHidden()
    }

    // ---- when the catalogue fails -----------------------------------------------------------------

    private val catalogueDown = HubResult.Err(HubError.Rejected(503, "urn:multiroom:radio:error:catalogue-unavailable"))

    @Test
    fun aCatalogueFailureOfTheFirstPageShowsTheProblemAndRetryLoadsAgain() = runViewModelTest {
        val s = live { it.stationResults += catalogueDown; it.stationsResult = { _, o -> page(o) } }
        assertEquals(RadioFailure.Catalogue, s.list.problem)
        assertEquals(ListPhase.Loaded, s.list.phase)
        assertTrue(s.list.stations.isEmpty())
        assertFalse(s.list.empty)
        s.vm.retry(); runCurrent()
        assertNull(s.list.problem)
        assertEquals(50, s.list.stations.size)
        s.vm.onHidden()
    }

    @Test
    fun aFailedNextPageKeepsTheRowsAndRetryRepeatsThatPage() = runViewModelTest {
        val s = live { it.stationResults += page(0); it.stationResults += catalogueDown }
        s.vm.onListPosition(45); runCurrent()
        assertEquals(RadioFailure.Catalogue, s.list.problem)
        assertEquals(50, s.list.stations.size)
        // A failed page is not asked for again by scrolling; only Retry repeats it.
        s.vm.onListPosition(49); runCurrent()
        assertEquals(2, s.repo.searches.size)
        s.repo.stationResults += page(9950, next = 10_000)
        s.vm.retry(); runCurrent()
        assertEquals(listOf(0, 50, 50), s.repo.searches.map { it.second })
        assertNull(s.list.problem)
        assertTrue(s.list.limitReached)
        s.vm.onHidden()
    }

    @Test
    fun aRadioFailureWithTheHubAnsweringIsTheCatalogueMessage() = runViewModelTest {
        // The radio call got no answer, but the hub itself answers the refresh.
        val s = live { it.stationResults += unreachable; it.stationsResult = { _, o -> page(o) } }
        advanceTimeBy(100); runCurrent()
        assertEquals(RadioFailure.Catalogue, s.list.problem)
        s.vm.onHidden()
    }

    @Test
    fun anUnreachableHubIsDecidedByAFreshRefreshAndTheRequestIsRepeatedOnceItAnswers() = runViewModelTest {
        val s = live {
            it.stationResults += unreachable
            it.stationsResult = { _, o -> page(o) }
            it.snapshotResult = { unreachable } // the following refresh fails too
        }
        advanceTimeBy(20_000); runCurrent()
        assertNull(s.list.problem)
        assertEquals(1, s.repo.searches.size)
        s.repo.snapshotResult = { HubResult.Ok(sonora.multiroom.mobile.ui.rooms.oneRoomSnapshot) }
        advanceTimeBy(10_000); runCurrent()
        assertEquals(2, s.repo.searches.size)
        assertNull(s.list.problem)
        assertEquals(50, s.list.stations.size)
        s.vm.onHidden()
    }

    @Test
    fun withNoRefreshAtAllTheFailedRequestIsNotRepeatedHoweverLongItWaits() = runViewModelTest {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        runCurrent()
        factory.last.stationResults += unreachable
        factory.last.stationsResult = { _, o -> page(o) }
        // The session was live once and still says so, but no screen holds it now.
        session.acquire(); runCurrent(); session.release()
        val vm = RadioViewModel(
            SavedStateHandle(), session, RadioAvailability(session, backgroundScope), StationPicker(), InMemoryRadioStore(),
        ) { "" }
        runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, factory.last.searches.size)
        assertNull(vm.state.value.list.problem)
    }

    @Test
    fun aQueryChangeDuringTheWaitCancelsIt() = runViewModelTest {
        val s = live {
            it.stationResults += unreachable
            it.stationsResult = { q, _ -> HubResult.Ok(StationPage(listOf(station(q.name ?: "all")), null)) }
            it.snapshotResult = { unreachable }
        }
        advanceTimeBy(6_000); runCurrent()
        s.vm.onNameChange("x"); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf("x"), s.list.stations.map { it.uuid })
        s.repo.snapshotResult = { HubResult.Ok(sonora.multiroom.mobile.ui.rooms.oneRoomSnapshot) }
        advanceTimeBy(10_000); runCurrent()
        // The old request is not repeated behind the new one's back.
        assertEquals(2, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun aMissingRadioExtensionClosesTheScreenAndSaysSo() = runViewModelTest {
        val s = live { it.stationResults += HubResult.Err(HubError.Rejected(404, null)) }
        assertEquals(RadioExit.Gone, s.ui.exit)
        assertEquals(RadioOffer.NotOffered, s.availability.state.value)
        assertTrue(s.picker.gone.value)
        s.vm.onHidden()
    }

    // ---- Recent (MOB-009) ------------------------------------------------------------------------

    private fun recent(vararg ids: String) = ids.map { station(it) }

    @Test
    fun allRecentStationsShowWhileTheNameIsEmptyWhateverTheFilters() = runViewModelTest {
        val ten = recent(*(1..10).map { "r$it" }.toTypedArray())
        val filters = sonora.multiroom.mobile.domain.RadioFilters(tag = "jazz", order = StationOrder.Trending)
        val s = live(store = InMemoryRadioStore(recent = ten, filters = filters))
        assertEquals(ten, s.ui.recent)
        s.vm.onHidden()
    }

    @Test
    fun recentStationsShowWhenNoStationMatchesAndWhenTheCatalogueDoesNotAnswer() = runViewModelTest {
        val empty = live(store = InMemoryRadioStore(recent = recent("a")), script = { it.stationsResult = { _, _ -> HubResult.Ok(StationPage(emptyList(), null)) } })
        assertTrue(empty.list.empty)
        assertEquals(recent("a"), empty.ui.recent)
        empty.vm.onHidden()
        val down = live(
            store = InMemoryRadioStore(recent = recent("a")),
            script = { it.stationResults += HubResult.Err(HubError.Rejected(503, "urn:multiroom:radio:error:catalogue-unavailable")) },
        )
        assertEquals(RadioFailure.Catalogue, down.list.problem)
        assertEquals(recent("a"), down.ui.recent)
        down.vm.onHidden()
    }

    @Test
    fun recentStationsAreHiddenWhileANameIsTypedAndComeBackWhenItIsCleared() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(recent = recent("a", "b")))
        s.vm.onNameChange("man"); runCurrent()
        assertTrue(s.ui.recent.isEmpty())
        s.vm.onNameChange("  "); runCurrent()
        assertEquals(recent("a", "b"), s.ui.recent)
        s.vm.onHidden()
    }

    @Test
    fun withNoRecentStationNothingIsShown() = runViewModelTest {
        val s = live()
        assertTrue(s.ui.recent.isEmpty())
        s.vm.onHidden()
    }

    @Test
    fun removingARecentStationIsImmediateAndTouchesNoOtherEntry() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(recent = recent("a", "b", "c")))
        s.vm.removeRecent("b"); runCurrent()
        assertEquals(recent("a", "c"), s.ui.recent)
        s.vm.onHidden()
    }

    // ---- Filters (MOB-009) -----------------------------------------------------------------------

    private val ukraine = Country("UA", "Ukraine", 1_200)
    private val germany = Country("DE", "Germany", 6_965)
    private val france = Country("FR", "France", 900)
    private val countries = listOf(germany, ukraine, france)

    private suspend fun Setup.storedFilters() = store.filters.first()

    private val someFilters = RadioFilters(germany, "jazz", "german", StationOrder.Trending)

    @Test
    fun theFirstVisitChoosesTheCountryOfThePhonesRegionIgnoringCaseStoresItAndThenLoads() = runViewModelTest {
        val s = live(region = "ua") { it.countriesResult = { HubResult.Ok(countries) }; it.stationsResult = { _, o -> page(o) } }
        assertEquals(RadioFilters(country = ukraine), s.ui.filters)
        assertEquals(RadioFilters(country = ukraine), s.storedFilters())
        assertEquals(listOf(everything.copy(countryCode = "UA") to 0), s.repo.searches)
        assertEquals(1, s.repo.countriesCalls)
        s.vm.onHidden()
    }

    @Test
    fun aRegionTheCatalogueDoesNotListStoresNoCountry() = runViewModelTest {
        val s = live(region = "ZZ") { it.countriesResult = { HubResult.Ok(countries) }; it.stationsResult = { _, o -> page(o) } }
        assertEquals(RadioFilters(), s.storedFilters())
        assertEquals(listOf(everything to 0), s.repo.searches)
        s.vm.onHidden()
        val unknown = live(region = "") { it.countriesResult = { HubResult.Ok(countries) }; it.stationsResult = { _, o -> page(o) } }
        assertEquals(RadioFilters(), unknown.storedFilters())
        unknown.vm.onHidden()
    }

    @Test
    fun ifTheCountryListFailsNothingIsStoredTheCatalogueMessageShowsAndRetryTriesAgain() = runViewModelTest {
        var up = false
        val s = live {
            it.countriesResult = { if (up) HubResult.Ok(countries) else catalogueDown }
            it.stationsResult = { _, o -> page(o) }
        }
        assertEquals(RadioFailure.Catalogue, s.list.problem)
        assertNull(s.storedFilters())
        assertTrue(s.repo.searches.isEmpty())
        up = true
        s.vm.retry(); runCurrent()
        assertNull(s.list.problem)
        assertEquals(RadioFilters(country = ukraine), s.storedFilters())
        assertEquals(1, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun withTheHubUnreachableTheFirstVisitShowsNoMessageAndLooksUpAgainByItself() = runViewModelTest {
        var up = false
        val s = live {
            it.countriesResult = { if (up) HubResult.Ok(countries) else unreachable }
            it.stationsResult = { _, o -> page(o) }
            it.snapshotResult = { unreachable }
        }
        advanceTimeBy(20_000); runCurrent()
        assertNull(s.list.problem)
        assertNull(s.storedFilters())
        up = true
        s.repo.snapshotResult = { HubResult.Ok(sonora.multiroom.mobile.ui.rooms.oneRoomSnapshot) }
        advanceTimeBy(10_000); runCurrent()
        assertEquals(RadioFilters(country = ukraine), s.storedFilters())
        assertEquals(1, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun aMissingRadioExtensionDuringTheFirstVisitClosesTheScreen() = runViewModelTest {
        val s = live { it.countriesResult = { HubResult.Err(HubError.Rejected(404, null)) } }
        assertEquals(RadioExit.Gone, s.ui.exit)
        assertNull(s.storedFilters())
        s.vm.onHidden()
    }

    @Test
    fun storedFiltersAndOrderOpenTheScreenWithoutAskingForCountriesAndNoNameIsKept() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = someFilters))
        assertEquals(someFilters, s.ui.filters)
        assertEquals(0, s.repo.countriesCalls)
        assertEquals(listOf(StationQuery(null, "DE", "jazz", "german", StationOrder.Trending) to 0), s.repo.searches)
        assertEquals("", s.ui.nameText)
        s.vm.onHidden()
    }

    @Test
    fun severalFiltersAndANameAreSentTogether() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = someFilters))
        s.vm.onNameChange("mango"); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertEquals(StationQuery("mango", "DE", "jazz", "german", StationOrder.Trending) to 0, s.repo.searches.last())
        s.vm.onHidden()
    }

    @Test
    fun removingAChipReloadsAtOnceStoresItAndKeepsTheRest() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = someFilters))
        s.vm.onRemoveFilter(FilterSheetState.Kind.Tag); runCurrent()
        val expected = someFilters.copy(tag = null)
        assertEquals(expected, s.ui.filters)
        assertEquals(expected, s.storedFilters())
        assertEquals(StationQuery(null, "DE", null, "german", StationOrder.Trending) to 0, s.repo.searches.last())
        s.vm.onRemoveFilter(FilterSheetState.Kind.Country); runCurrent()
        s.vm.onRemoveFilter(FilterSheetState.Kind.Language); runCurrent()
        assertEquals(RadioFilters(order = StationOrder.Trending), s.storedFilters())
        assertEquals(StationQuery(null, null, null, null, StationOrder.Trending) to 0, s.repo.searches.last())
        s.vm.onHidden()
    }

    @Test
    fun clearFiltersRemovesTheThreeKeepsOrderAndNameAndIsOfferedOnlyWhenOneIsChosen() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = someFilters))
        s.vm.onNameChange("mango"); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertTrue(s.ui.filters.narrowed)
        s.vm.onClearFilters(); runCurrent()
        assertEquals(RadioFilters(order = StationOrder.Trending), s.ui.filters)
        assertEquals(RadioFilters(order = StationOrder.Trending), s.storedFilters())
        assertEquals(StationQuery("mango", null, null, null, StationOrder.Trending) to 0, s.repo.searches.last())
        assertFalse(s.ui.filters.narrowed)
        val calls = s.repo.searches.size
        s.vm.onClearFilters(); runCurrent()
        assertEquals(calls, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun aStoredValueTheCatalogueNoLongerListsIsStillAppliedAndShown() = runViewModelTest {
        val gone = Country("XX", "Atlantis", 0)
        val s = live(store = InMemoryRadioStore(filters = RadioFilters(country = gone, tag = "lost"))) {
            it.countriesResult = { HubResult.Ok(countries) }
            it.stationsResult = { _, o -> page(o) }
        }
        assertEquals(gone, s.ui.filters.country)
        assertEquals(StationQuery(null, "XX", "lost", null, StationOrder.MostVoted) to 0, s.repo.searches.first())
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        assertTrue(s.ui.sheet!!.rows.none { it.selected })
        assertEquals(gone, s.ui.filters.country)
        s.vm.onHidden()
    }

    // ---- The sheets ------------------------------------------------------------------------------

    private val kinds = FilterSheetState.Kind.entries

    private fun withSheetData(repo: FakeRepository) {
        repo.countriesResult = { HubResult.Ok(countries) }
        repo.languagesResult = { HubResult.Ok(listOf(Language("german", 7_000), Language("English", 5_000), Language("ukrainian", 800))) }
        repo.stationsResult = { _, o -> page(o) }
    }

    @Test
    fun countryAndLanguageListsLoadOnceWhenTheSheetFirstOpensAndAreKeptWhileTheScreenLives() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters()), script = ::withSheetData)
        assertEquals(0, s.repo.countriesCalls)
        s.vm.onOpenSheet(FilterSheetState.Kind.Country)
        assertTrue(s.ui.sheet!!.loading)
        runCurrent()
        assertEquals(listOf("Germany", "Ukraine", "France"), s.ui.sheet!!.rows.map { it.name })
        assertEquals(listOf<Long?>(6_965, 1_200, 900), s.ui.sheet!!.rows.map { it.count })
        assertEquals(listOf("DE", "UA", "FR"), s.ui.sheet!!.rows.map { it.code })
        s.vm.onCloseSheet()
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        assertFalse(s.ui.sheet!!.loading)
        assertEquals(1, s.repo.countriesCalls)
        s.vm.onCloseSheet()
        s.vm.onOpenSheet(FilterSheetState.Kind.Language); runCurrent()
        s.vm.onCloseSheet()
        s.vm.onOpenSheet(FilterSheetState.Kind.Language); runCurrent()
        assertEquals(1, s.repo.languagesCalls)
        s.vm.onHidden()
    }

    @Test
    fun theFieldNarrowsCountriesAndLanguagesOnThePhoneIgnoringCase() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters()), script = ::withSheetData)
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        s.vm.onSheetSearch("AN"); runCurrent()
        assertEquals(listOf("Germany", "France"), s.ui.sheet!!.rows.map { it.name })
        s.vm.onSheetSearch("ger"); runCurrent()
        assertEquals(listOf("Germany"), s.ui.sheet!!.rows.map { it.name })
        assertEquals("ger", s.ui.sheet!!.searchText)
        assertEquals(1, s.repo.countriesCalls)
        s.vm.onCloseSheet()
        s.vm.onOpenSheet(FilterSheetState.Kind.Language); runCurrent()
        s.vm.onSheetSearch("ENG"); runCurrent()
        assertEquals(listOf("English"), s.ui.sheet!!.rows.map { it.name })
        s.vm.onHidden()
    }

    @Test
    fun theFieldStopsAtOneHundredCharactersAndTheTagFieldDropsPercentAndSlash() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters()), script = ::withSheetData)
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        s.vm.onSheetSearch("x".repeat(130))
        assertEquals(100, s.ui.sheet!!.searchText.length)
        s.vm.onCloseSheet()
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        s.vm.onSheetSearch("ja%z/z")
        assertEquals("jazz", s.ui.sheet!!.searchText)
        s.vm.onHidden()
    }

    private fun tagPage(offset: Int, count: Int = 50, next: Int? = offset + count) =
        HubResult.Ok(TagPage((0 until count).map { Tag("tag${offset + it}", (1000 - it).toLong()) }, next))

    @Test
    fun theTagSheetPagesFiftyAtATimeAndStopsWhenThereIsNoNextOffset() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters())) {
            it.stationsResult = { _, o -> page(o) }
            it.tagsResult = { _, o -> if (o < 100) tagPage(o) else tagPage(o, count = 10, next = null) }
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        assertEquals(null to 0, s.repo.tagSearches.first())
        assertEquals(50, s.ui.sheet!!.rows.size)
        s.vm.onSheetListPosition(10); runCurrent()
        assertEquals(1, s.repo.tagSearches.size)
        s.vm.onSheetListPosition(45); runCurrent()
        assertEquals(100, s.ui.sheet!!.rows.size)
        s.vm.onSheetListPosition(95); runCurrent()
        assertEquals(110, s.ui.sheet!!.rows.size)
        s.vm.onSheetListPosition(109); runCurrent()
        assertEquals(listOf(0, 50, 100), s.repo.tagSearches.map { it.second })
        s.vm.onHidden()
    }

    @Test
    fun theTagSheetNeverRequestsAnOffsetOfTenThousand() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters())) {
            it.stationsResult = { _, o -> page(o) }
            it.tagResults += tagPage(0, next = 9_950)
            it.tagResults += tagPage(9_950, next = 10_000)
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        s.vm.onSheetListPosition(45); runCurrent()
        s.vm.onSheetListPosition(99); runCurrent()
        s.vm.onSheetListPosition(99); runCurrent()
        assertEquals(listOf(0, 9_950), s.repo.tagSearches.map { it.second })
        s.vm.onHidden()
    }

    @Test
    fun theTagFieldAsksTheHubHalfASecondAfterTheLastChangeWithPercentAndSlashRemoved() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters())) {
            it.stationsResult = { _, o -> page(o) }
            it.tagsResult = { name, _ -> HubResult.Ok(TagPage(listOf(Tag(name ?: "all", 5)), null)) }
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        s.vm.onSheetSearch("ja"); runCurrent()
        advanceTimeBy(400); runCurrent()
        s.vm.onSheetSearch("ja%z/z"); runCurrent()
        advanceTimeBy(499); runCurrent()
        assertEquals(1, s.repo.tagSearches.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(null to 0, s.repo.tagSearches.first())
        assertEquals("jazz" to 0, s.repo.tagSearches.last())
        assertEquals(listOf("jazz"), s.ui.sheet!!.rows.map { it.name })
        s.vm.onHidden()
    }

    @Test
    fun aChosenTagIsTheFirstRowAndIsLeftOutOfItsOwnPlace() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters(tag = "tag3"))) {
            it.stationsResult = { _, o -> page(o) }
            it.tagResults += tagPage(0, count = 5, next = null)
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        val rows = s.ui.sheet!!.rows
        assertEquals(listOf("tag3", "tag0", "tag1", "tag2", "tag4"), rows.map { it.name })
        assertTrue(rows[0].selected)
        assertEquals(997L, rows[0].count)
        assertTrue(rows.drop(1).none { it.selected })
        assertEquals(0, s.ui.sheet!!.chosenIndex)
        s.vm.onHidden()
    }

    @Test
    fun aChosenTagNotYetLoadedShowsFirstWithoutACount() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters(tag = "deep"))) {
            it.stationsResult = { _, o -> page(o) }
            it.tagResults += tagPage(0, count = 3, next = 3)
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        assertEquals("deep", s.ui.sheet!!.rows.first().name)
        assertNull(s.ui.sheet!!.rows.first().count)
        assertEquals(4, s.ui.sheet!!.rows.size)
        s.vm.onHidden()
    }

    @Test
    fun theOrderSheetHasThreeRowsAndReportsTheChosenOneToShow() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters(order = StationOrder.NameAZ)), script = ::withSheetData)
        s.vm.onOpenSheet(FilterSheetState.Kind.Order)
        val sheet = s.ui.sheet!!
        assertFalse(sheet.loading)
        assertEquals(listOf("Most voted", "Trending", "Name A–Z"), sheet.rows.map { it.name })
        assertTrue(sheet.rows.all { it.count == null })
        assertEquals(listOf(true, true, false), sheet.rows.map { it.detail != null })
        assertEquals(2, sheet.chosenIndex)
        s.vm.onHidden()
    }

    @Test
    fun choosingARowClosesTheSheetAppliesStoresAndReloadsFromTheFirstPage() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters()), script = ::withSheetData)
        s.vm.onOpenSheet(FilterSheetState.Kind.Order)
        s.vm.onSheetChoose(s.ui.sheet!!.rows[1]); runCurrent()
        assertNull(s.ui.sheet)
        assertEquals(StationOrder.Trending, s.ui.filters.order)
        assertEquals(StationOrder.Trending, s.storedFilters()!!.order)
        assertEquals(everything.copy(order = StationOrder.Trending) to 0, s.repo.searches.last())

        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        s.vm.onSheetChoose(s.ui.sheet!!.rows.first { it.code == "DE" }); runCurrent()
        assertEquals(germany, s.ui.filters.country)
        assertEquals(germany, s.storedFilters()!!.country)
        assertEquals("DE", s.repo.searches.last().first.countryCode)

        s.vm.onOpenSheet(FilterSheetState.Kind.Language); runCurrent()
        s.vm.onSheetChoose(s.ui.sheet!!.rows.first { it.name == "English" }); runCurrent()
        assertEquals("English", s.storedFilters()!!.language)

        s.repo.tagsResult = { _, _ -> tagPage(0, count = 3, next = null) }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        s.vm.onSheetChoose(s.ui.sheet!!.rows.first()); runCurrent()
        assertEquals(s.storedFilters()!!.tag, s.repo.searches.last().first.tag)
        assertEquals(50, s.list.stations.size)
        s.vm.onHidden()
    }

    @Test
    fun cancelScrimAndBackChangeNothing() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = someFilters), script = ::withSheetData)
        val calls = s.repo.searches.size
        for (kind in kinds) {
            s.vm.onOpenSheet(kind); runCurrent()
            s.vm.onCloseSheet(); runCurrent()
            assertNull(s.ui.sheet)
        }
        assertEquals(someFilters, s.ui.filters)
        assertEquals(someFilters, s.storedFilters())
        assertEquals(calls, s.repo.searches.size)
        s.vm.onHidden()
    }

    @Test
    fun aSheetThatCannotLoadShowsTheCatalogueMessageInsideItselfAndLeavesTheListAlone() = runViewModelTest {
        var up = false
        val s = live(store = InMemoryRadioStore(filters = RadioFilters())) {
            it.stationsResult = { _, o -> page(o) }
            it.countriesResult = { if (up) HubResult.Ok(countries) else catalogueDown }
        }
        val before = s.list
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        assertEquals(RadioFailure.Catalogue, s.ui.sheet!!.problem)
        assertFalse(s.ui.sheet!!.loading)
        assertEquals(before, s.list)
        up = true
        s.vm.retrySheet(); runCurrent()
        assertNull(s.ui.sheet!!.problem)
        assertEquals(3, s.ui.sheet!!.rows.size)
        assertEquals(before, s.list)
        s.vm.onHidden()
    }

    @Test
    fun aFailedTagPageKeepsTheRowsAndRetryRepeatsThatPage() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters())) {
            it.stationsResult = { _, o -> page(o) }
            it.tagResults += tagPage(0)
            it.tagResults += catalogueDown
            it.tagResults += tagPage(50, count = 5, next = null)
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Tag); runCurrent()
        s.vm.onSheetListPosition(45); runCurrent()
        assertEquals(RadioFailure.Catalogue, s.ui.sheet!!.problem)
        assertEquals(50, s.ui.sheet!!.rows.size)
        s.vm.retrySheet(); runCurrent()
        assertNull(s.ui.sheet!!.problem)
        assertEquals(55, s.ui.sheet!!.rows.size)
        s.vm.onHidden()
    }

    @Test
    fun withTheHubUnreachableASheetShowsNoMessageAndLoadsAgainByItself() = runViewModelTest {
        var up = false
        val s = live(store = InMemoryRadioStore(filters = RadioFilters())) {
            it.stationsResult = { _, o -> page(o) }
            it.countriesResult = { if (up) HubResult.Ok(countries) else unreachable }
            it.snapshotResult = { unreachable }
        }
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        advanceTimeBy(20_000); runCurrent()
        assertNull(s.ui.sheet!!.problem)
        assertTrue(s.ui.sheet!!.loading)
        up = true
        s.repo.snapshotResult = { HubResult.Ok(sonora.multiroom.mobile.ui.rooms.oneRoomSnapshot) }
        advanceTimeBy(10_000); runCurrent()
        assertNull(s.ui.sheet!!.problem)
        assertEquals(3, s.ui.sheet!!.rows.size)
        s.vm.onHidden()
    }

    @Test
    fun anAnswerForASheetThatWasClosedIsNeverUsed() = runViewModelTest {
        val s = live(store = InMemoryRadioStore(filters = RadioFilters()), script = ::withSheetData)
        s.repo.radioDelayMs = 1_000
        s.vm.onOpenSheet(FilterSheetState.Kind.Country); runCurrent()
        s.vm.onCloseSheet()
        advanceTimeBy(2_000); runCurrent()
        assertNull(s.ui.sheet)
        s.vm.onHidden()
    }
}
