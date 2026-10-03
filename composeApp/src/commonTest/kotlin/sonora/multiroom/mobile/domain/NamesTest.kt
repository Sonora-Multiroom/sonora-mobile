package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class NamesTest {
    @Test
    fun joinsNamesTheWayTheCopyReads() {
        assertEquals("", joinNames(emptyList()))
        assertEquals("A", joinNames(listOf("A")))
        assertEquals("A and B", joinNames(listOf("A", "B")))
        assertEquals("A, B and C", joinNames(listOf("A", "B", "C")))
        assertEquals("A, B, C and D", joinNames(listOf("A", "B", "C", "D")))
    }
}
