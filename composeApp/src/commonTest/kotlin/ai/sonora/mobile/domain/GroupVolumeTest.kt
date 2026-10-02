package ai.sonora.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GroupVolumeTest {
    @Test
    fun scalesEachMemberByNewOverLoudestAndRoundsHalfUp() =
        assertEquals(mapOf("a" to 35, "b" to 18), GroupVolume.scale(mapOf("a" to 70, "b" to 35), 35))

    @Test
    fun draggingToTheCurrentLoudestChangesNothing() =
        assertEquals(mapOf("a" to 70, "b" to 35), GroupVolume.scale(mapOf("a" to 70, "b" to 35), 70))

    @Test
    fun allMembersAtZeroAreSetToTheNewValue() =
        assertEquals(mapOf("a" to 40, "b" to 40), GroupVolume.scale(mapOf("a" to 0, "b" to 0), 40))

    @Test
    fun scalingUpKeepsTheBalance() =
        assertEquals(mapOf("a" to 100, "b" to 50), GroupVolume.scale(mapOf("a" to 50, "b" to 25), 100))

    @Test
    fun draggingToZeroSilencesEveryone() =
        assertEquals(mapOf("a" to 0, "b" to 0), GroupVolume.scale(mapOf("a" to 60, "b" to 1), 0))

    @Test
    fun draggingDownThenBackUpFromTheSameBaseRestoresTheOriginal() {
        val base = mapOf("a" to 70, "b" to 35)
        assertEquals(base, GroupVolume.scale(base, 70))
        assertEquals(mapOf("a" to 35, "b" to 18), GroupVolume.scale(base, 35))
        assertEquals(base, GroupVolume.scale(base, 70))
    }

    @Test
    fun valuesOutsideZeroToHundredAreClampedFirst() {
        val base = mapOf("a" to 50, "b" to 25)
        assertEquals(GroupVolume.scale(base, 100), GroupVolume.scale(base, 150))
        assertEquals(GroupVolume.scale(base, 0), GroupVolume.scale(base, -5))
    }

    @Test
    fun anEmptyGroupStaysEmpty() = assertEquals(emptyMap(), GroupVolume.scale(emptyMap(), 50))

    @Test
    fun aSingleRoomJustGetsTheNewValue() {
        assertEquals(mapOf("a" to 42), GroupVolume.scale(mapOf("a" to 70), 42))
        assertEquals(mapOf("a" to 42), GroupVolume.scale(mapOf("a" to 0), 42))
    }
}
