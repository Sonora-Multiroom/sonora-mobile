package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddressDetailTest {
    @Test
    fun kindLabels() {
        assertEquals("Stream", kindLabel(SourceKind.Stream))
        assertEquals("Line-in", kindLabel(SourceKind.LineIn))
        assertEquals("File", kindLabel(SourceKind.File))
        assertEquals("Link", kindLabel(SourceKind.Link))
    }

    @Test
    fun aStreamShowsOnlyTheHost() {
        assertEquals("stream.radioparadise.com", addressDetail(SourceKind.Stream, "https://stream.radioparadise.com/aac-320"))
        assertEquals("host", addressDetail(SourceKind.Stream, "http://u:p@host:8000/x"))
        assertEquals("::1", addressDetail(SourceKind.Stream, "http://[::1]:8000/"))
        assertEquals("radio.example", addressDetail(SourceKind.Stream, "HTTP://radio.example?x=1#y"))
    }

    @Test
    fun aLinkShowsTheHostForHttpAndTheAddressAsTypedOtherwise() {
        assertEquals("soundcloud.com", addressDetail(SourceKind.Link, "https://soundcloud.com/a/b"))
        assertEquals("spotify:track:123", addressDetail(SourceKind.Link, "  spotify:track:123 "))
    }

    @Test
    fun aFileShowsItsLastPathSegmentWithoutDecoding() {
        assertEquals("Morning.mp3", addressDetail(SourceKind.File, "file:///music/Morning.mp3"))
        assertEquals("a b.flac", addressDetail(SourceKind.File, "/music/a b.flac"))
        assertEquals("x.wav", addressDetail(SourceKind.File, "C:\\music\\x.wav"))
        assertEquals("a%20b.mp3", addressDetail(SourceKind.File, "file:///m/a%20b.mp3"))
    }

    @Test
    fun aLineInHasNoDetail() {
        assertNull(addressDetail(SourceKind.LineIn, "hw:1,0"))
        assertNull(addressDetail(SourceKind.LineIn, null))
    }

    @Test
    fun blankOrMissingIsNull() {
        for (kind in SourceKind.entries) {
            assertNull(addressDetail(kind, null))
            assertNull(addressDetail(kind, "   "))
        }
    }

    @Test
    fun anUnparseableAddressIsShownAsTyped() {
        assertEquals("http://", addressDetail(SourceKind.Stream, " http:// "))
        assertEquals("/music/", addressDetail(SourceKind.File, "/music/"))
    }
}
