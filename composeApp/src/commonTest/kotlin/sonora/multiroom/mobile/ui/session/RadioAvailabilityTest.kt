package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.Extension
import sonora.multiroom.mobile.domain.ExtensionConnection
import sonora.multiroom.mobile.domain.ExtensionInventory
import sonora.multiroom.mobile.domain.ExtensionStatus
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioAvailabilityTest {
    private val address = HubAddress("http://hub:8080")

    private fun inventory(status: ExtensionStatus, id: String = "radio") =
        ExtensionInventory(true, listOf(Extension(id, id, status, ExtensionConnection.Unknown)))

    private class Setup(val availability: RadioAvailability, val factory: FakeFactory, val store: InMemoryHubAddressStore)

    private fun TestScope.setup(): Setup {
        val factory = FakeFactory { currentTime }
        val store = InMemoryHubAddressStore(address)
        val session = HubSession(store, factory, backgroundScope)
        val availability = RadioAvailability(session, backgroundScope)
        runCurrent()
        return Setup(availability, factory, store)
    }

    private fun Setup.answer(result: HubResult<ExtensionInventory>) {
        factory.last.extensionsResult = { result }
    }

    @Test
    fun startsUnknown() = runTest {
        assertEquals(RadioOffer.Unknown, setup().availability.state.value)
    }

    @Test
    fun anActiveRadioExtensionIsOffered() = runTest {
        val s = setup()
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Active)))
        s.availability.refresh()
        assertEquals(RadioOffer.Offered, s.availability.state.value)
    }

    @Test
    fun anyOtherReadableInventoryIsNotOffered() = runTest {
        val s = setup()
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Disabled)))
        s.availability.refresh()
        assertEquals(RadioOffer.NotOffered, s.availability.state.value)
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Active, id = "tts")))
        s.availability.refresh()
        assertEquals(RadioOffer.NotOffered, s.availability.state.value)
    }

    @Test
    fun anErrorAnswerTurnsUnknownIntoNotOffered() = runTest {
        for (error in listOf(HubError.Rejected(500, null), HubError.Unexpected)) {
            val s = setup()
            s.answer(HubResult.Err(error))
            s.availability.refresh()
            assertEquals(RadioOffer.NotOffered, s.availability.state.value, error.toString())
        }
    }

    @Test
    fun anErrorAnswerLeavesOfferedAsItIs() = runTest {
        val s = setup()
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Active)))
        s.availability.refresh()
        for (error in listOf(HubError.Rejected(500, null), HubError.Unexpected)) {
            s.answer(HubResult.Err(error))
            s.availability.refresh()
            assertEquals(RadioOffer.Offered, s.availability.state.value, error.toString())
        }
    }

    @Test
    fun anUnreachableHubLeavesAnyStateAsItWas() = runTest {
        val s = setup()
        s.answer(HubResult.Err(HubError.Unreachable))
        s.availability.refresh()
        assertEquals(RadioOffer.Unknown, s.availability.state.value)
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Active)))
        s.availability.refresh()
        s.answer(HubResult.Err(HubError.Unreachable))
        s.availability.refresh()
        assertEquals(RadioOffer.Offered, s.availability.state.value)
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Disabled)))
        s.availability.refresh()
        s.answer(HubResult.Err(HubError.Unreachable))
        s.availability.refresh()
        assertEquals(RadioOffer.NotOffered, s.availability.state.value)
    }

    @Test
    fun anAnswerForARepositoryThatIsNoLongerTheSessionsIsDropped() = runTest {
        val s = setup()
        val first = s.factory.last
        first.extensionsDelayMs = 1_000
        first.extensionsResult = { HubResult.Ok(inventory(ExtensionStatus.Active)) }
        backgroundScope.launch { s.availability.refresh() }
        runCurrent()
        // The hub address changes while the request is out.
        s.store.save(HubAddress("http://other:8080"))
        runCurrent()
        delay(1_500)
        assertEquals(RadioOffer.Unknown, s.availability.state.value)
    }

    @Test
    fun aChangedHubAddressReturnsToUnknown() = runTest {
        val s = setup()
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Active)))
        s.availability.refresh()
        assertEquals(RadioOffer.Offered, s.availability.state.value)
        s.store.save(HubAddress("http://other:8080"))
        runCurrent()
        assertEquals(RadioOffer.Unknown, s.availability.state.value)
    }

    @Test
    fun markGoneIsNotOffered() = runTest {
        val s = setup()
        s.answer(HubResult.Ok(inventory(ExtensionStatus.Active)))
        s.availability.refresh()
        s.availability.markGone()
        assertEquals(RadioOffer.NotOffered, s.availability.state.value)
    }
}
