package ai.sonora.mobile.ui.settings

import ai.sonora.mobile.data.InMemoryHubAddressStore
import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.runViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    @Test
    fun initialTextIsEmptyWhenNothingIsSaved() = runViewModelTest {
        val vm = SettingsViewModel(InMemoryHubAddressStore())
        advanceUntilIdle()
        assertEquals("", vm.state.value.text)
        assertNull(vm.state.value.savedAddress)
    }

    @Test
    fun initialTextIsTheSavedAddress() = runViewModelTest {
        val vm = SettingsViewModel(InMemoryHubAddressStore(HubAddress("http://multiroom.lan:8080")))
        advanceUntilIdle()
        assertEquals("http://multiroom.lan:8080", vm.state.value.text)
        assertEquals(HubAddress("http://multiroom.lan:8080"), vm.state.value.savedAddress)
    }

    @Test
    fun savingValidInputStoresTheNormalisedAddress() = runViewModelTest {
        val store = InMemoryHubAddressStore()
        val vm = SettingsViewModel(store)
        advanceUntilIdle()
        vm.onTextChange("multiroom.lan")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(HubAddress("http://multiroom.lan:8080"), store.address.first())
        assertEquals("http://multiroom.lan:8080", vm.state.value.text)
        assertTrue(vm.state.value.saved)
        assertNull(vm.state.value.error)
    }

    @Test
    fun savingInvalidInputStoresNothingAndShowsTheMessage() = runViewModelTest {
        val store = InMemoryHubAddressStore()
        val vm = SettingsViewModel(store)
        advanceUntilIdle()
        vm.onTextChange("multiroom lan")
        vm.onSave()
        advanceUntilIdle()
        assertNull(store.address.first())
        assertEquals("The address can't contain spaces", vm.state.value.error)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun editingClearsErrorAndSaved() = runViewModelTest {
        val vm = SettingsViewModel(InMemoryHubAddressStore())
        advanceUntilIdle()
        vm.onTextChange("bad address")
        vm.onSave()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        vm.onTextChange("good")
        assertNull(vm.state.value.error)
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        vm.onTextChange("good2")
        assertFalse(vm.state.value.saved)
    }
}
