package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceDetailTest {
    @Test
    fun streamIsTheHost() =
        assertEquals("stream.radioparadise.com", sourceDetail(SourceKind.Stream, "https://stream.radioparadise.com/mp3-192"))

    @Test
    fun fileIsTheFileName() =
        assertEquals("morning.flac", sourceDetail(SourceKind.File, "file:/home/x/morning.flac"))

    @Test
    fun lineInDropsTheScheme() {
        assertEquals("plughw:CARD=ReceiverSolid,DEV=0", sourceDetail(SourceKind.LineIn, "alsa://plughw:CARD=ReceiverSolid,DEV=0"))
        assertEquals("sine?frequency=440", sourceDetail(SourceKind.LineIn, "signal://sine?frequency=440"))
    }

    @Test
    fun lineInWithoutASchemeIsAsTyped() = assertEquals("hw:1,0", sourceDetail(SourceKind.LineIn, "hw:1,0"))

    @Test
    fun blankOrNullIsNull() {
        assertNull(sourceDetail(SourceKind.LineIn, null))
        assertNull(sourceDetail(SourceKind.LineIn, "  "))
        assertNull(sourceDetail(SourceKind.Stream, ""))
    }

    @Test
    fun nowPlayingsAddressDetailIsUnchanged() {
        assertNull(addressDetail(SourceKind.LineIn, "alsa://plughw:CARD=X,DEV=0"))
        assertEquals("stream.radioparadise.com", addressDetail(SourceKind.Stream, "https://stream.radioparadise.com/mp3-192"))
    }
}
