package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.domain.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationPickerTest {
    private fun station(uuid: String) = Station(uuid, "radiobrowser://$uuid", uuid, null, null, null)

    @Test
    fun pickPublishesTheStationAndConsumeClearsIt() {
        val picker = StationPicker()
        assertNull(picker.picked.value)
        picker.pick(station("a"))
        assertEquals(station("a"), picker.picked.value)
        picker.consumePicked()
        assertNull(picker.picked.value)
    }

    @Test
    fun aSecondPickReplacesTheFirst() {
        val picker = StationPicker()
        picker.pick(station("a"))
        picker.pick(station("b"))
        assertEquals(station("b"), picker.picked.value)
    }

    @Test
    fun goneIsReportedAndConsumed() {
        val picker = StationPicker()
        assertFalse(picker.gone.value)
        picker.reportGone()
        assertTrue(picker.gone.value)
        picker.consumeGone()
        assertFalse(picker.gone.value)
    }

    @Test
    fun consumingOneDoesNotTouchTheOther() {
        val picker = StationPicker()
        picker.pick(station("a"))
        picker.reportGone()
        picker.consumePicked()
        assertTrue(picker.gone.value)
        picker.pick(station("b"))
        picker.consumeGone()
        assertEquals(station("b"), picker.picked.value)
    }
}
