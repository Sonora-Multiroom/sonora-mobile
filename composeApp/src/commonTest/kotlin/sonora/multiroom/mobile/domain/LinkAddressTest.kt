package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkAddressTest {
    private fun valid(uri: String) = LinkCheck.Valid(uri)

    @Test
    fun blankTextIsEmpty() {
        assertEquals(LinkCheck.Empty, checkLink(""))
        assertEquals(LinkCheck.Empty, checkLink("   "))
    }

    @Test
    fun aMissingSchemeGetsHttps() {
        assertEquals(valid("https://soundcloud.com/artist/track"), checkLink("soundcloud.com/artist/track"))
        assertEquals(valid("https://radio.lan:8000/live"), checkLink("radio.lan:8000/live"))
    }

    @Test
    fun theTextIsTrimmedAndAnExistingSchemeKept() {
        assertEquals(valid("https://x.y/a"), checkLink(" https://x.y/a "))
        assertEquals(valid("http://radio.lan:8000/live"), checkLink("http://radio.lan:8000/live"))
        assertEquals(valid("HTTPS://X.Y"), checkLink("HTTPS://X.Y"))
    }

    @Test
    fun aSingleLabelHostIsAccepted() {
        assertEquals(valid("https://jazz"), checkLink("jazz"))
        assertEquals(valid("http://hub:8080"), checkLink("http://hub:8080"))
    }

    @Test
    fun anIpv6LiteralIsAccepted() {
        assertEquals(valid("https://[fe80::1]:8000/x"), checkLink("https://[fe80::1]:8000/x"))
        assertEquals(valid("http://[::1]/"), checkLink("http://[::1]/"))
    }

    @Test
    fun queriesAndFragmentsAreKept() {
        assertEquals(valid("https://youtu.be/abc?t=3#x"), checkLink("youtu.be/abc?t=3#x"))
        assertEquals(valid("https://x.y?a=b"), checkLink("https://x.y?a=b"))
    }

    @Test
    fun otherSchemesHostlessAddressesAndWhitespaceAreInvalid() {
        for (text in listOf("ftp://x.y", "https://", "https:///path", "hello world", "https://a b.com", "https://x.y/a b", "mailto:x@y.z")) {
            assertEquals(LinkCheck.Invalid, checkLink(text), text)
        }
    }

    @Test
    fun malformedHostsAndPortsAreInvalid() {
        for (text in listOf("https://a..b", "https://.a", "https://x.y:abc", "https://x.y:", "https://[fe80::1", "https://ex_ample.com", "://x.y")) {
            assertEquals(LinkCheck.Invalid, checkLink(text), text)
        }
    }
}
