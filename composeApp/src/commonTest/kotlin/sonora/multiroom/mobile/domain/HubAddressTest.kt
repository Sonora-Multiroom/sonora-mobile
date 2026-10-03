package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HubAddressTest {
    private fun valid(input: String): String {
        val result = HubAddress.parse(input)
        assertIs<HubAddress.ParseResult.Valid>(result, "expected '$input' to be valid but was $result")
        return result.address.baseUrl
    }

    private fun invalid(input: String): String {
        val result = HubAddress.parse(input)
        assertIs<HubAddress.ParseResult.Invalid>(result, "expected '$input' to be invalid but was $result")
        return result.message
    }

    @Test fun bareHostGetsHttpAndDefaultPort() = assertEquals("http://multiroom.lan:8080", valid("multiroom.lan"))

    @Test fun hostWithPortKeepsPort() = assertEquals("http://multiroom.lan:9000", valid("multiroom.lan:9000"))

    @Test fun httpSchemeGetsDefaultPort() = assertEquals("http://multiroom.lan:8080", valid("http://multiroom.lan"))

    @Test fun explicitPort80IsKept() = assertEquals("http://multiroom.lan:80", valid("http://multiroom.lan:80"))

    @Test fun httpsGetsItsOwnDefaultPort() = assertEquals("https://hub.example:8443", valid("https://hub.example"))

    @Test fun explicitPort443IsKept() = assertEquals("https://hub.example:443", valid("https://hub.example:443"))

    @Test fun ipAddress() = assertEquals("http://192.168.1.20:8080", valid("192.168.1.20"))

    @Test fun trimsAndRemovesTrailingSlash() = assertEquals("http://multiroom.lan:8080", valid("  multiroom.lan/  "))

    @Test fun basePathIsKeptWithoutTrailingSlash() = assertEquals("http://hub:8080/base", valid("http://hub:8080/base/"))

    @Test fun emptyInput() = assertEquals("Enter the hub's address, e.g. multiroom.lan", invalid(""))

    @Test fun blankInput() = assertEquals("Enter the hub's address, e.g. multiroom.lan", invalid("   "))

    @Test fun spacesInside() = assertEquals("The address can't contain spaces", invalid("multiroom lan"))

    @Test fun unsupportedScheme() {
        invalid("ftp://hub")
    }

    @Test fun emptyHost() {
        invalid("http://")
    }

    @Test fun queryIsRejected() = assertEquals("Enter just the address, e.g. multiroom.lan:8080", invalid("hub?x=1"))

    @Test fun fragmentIsRejected() = assertEquals("Enter just the address, e.g. multiroom.lan:8080", invalid("hub#a"))
}
