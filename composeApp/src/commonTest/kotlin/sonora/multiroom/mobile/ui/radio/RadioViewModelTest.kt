package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.data.InMemoryRadioStore
import sonora.multiroom.mobile.domain.HubAddress
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
        script: (FakeRepository) -> Unit = { it.stationsResult = { _, offset -> page(offset) } },
    ): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        runCurrent()
        script(factory.last)
        val availability = RadioAvailability(session, backgroundScope)
        val picker = StationPicker()
        val vm = RadioViewModel(handle, session, availability, picker, store) { "UA" }
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
}
