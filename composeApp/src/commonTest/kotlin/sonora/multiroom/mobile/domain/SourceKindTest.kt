package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceKindTest {
    private fun kind(origin: SourceOrigin, uri: String?) = inferSourceKind(origin, uri)

    @Test fun runtimeOriginWinsAndIsALink() = assertEquals(SourceKind.Link, kind(SourceOrigin.Runtime, "http://x"))

    @Test fun runtimeWithoutUriIsStillALink() = assertEquals(SourceKind.Link, kind(SourceOrigin.Runtime, null))

    @Test
    fun configuredHttpIsAStream() {
        assertEquals(SourceKind.Stream, kind(SourceOrigin.Configured, "http://x"))
        assertEquals(SourceKind.Stream, kind(SourceOrigin.Configured, "HTTPS://x"))
    }

    @Test fun unknownOriginIsTreatedLikeConfigured() = assertEquals(SourceKind.Stream, kind(SourceOrigin.Unknown, "http://x"))

    @Test
    fun fileUrisAndPathsAreFiles() {
        for (uri in listOf("file:///a.mp3", "/music/a.flac", "./a.mp3", "../a", "~/a", "C:\\a.mp3", "C:/a.mp3", "\\\\nas\\a")) {
            assertEquals(SourceKind.File, kind(SourceOrigin.Configured, uri), uri)
        }
    }

    @Test
    fun everythingElseIsLineIn() {
        for (uri in listOf("alsa:hw:1", "line-in", "", "  ", null)) {
            assertEquals(SourceKind.LineIn, kind(SourceOrigin.Configured, uri), uri.toString())
        }
    }

    @Test
    fun liveStreamIsNotPauseableAndHttp() {
        assertTrue(isLiveStream(false, "http://x"))
        assertTrue(isLiveStream(false, "HTTPS://x"))
    }

    @Test
    fun otherSourcesAreNeverLive() {
        assertFalse(isLiveStream(true, "http://x"))
        assertFalse(isLiveStream(false, "alsa:hw:1"))
        assertFalse(isLiveStream(false, "/a.flac"))
        assertFalse(isLiveStream(false, null))
    }
}
