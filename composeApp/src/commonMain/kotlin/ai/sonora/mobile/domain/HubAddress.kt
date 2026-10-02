package ai.sonora.mobile.domain

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import kotlin.jvm.JvmInline

/** The hub's normalised base URL, e.g. `http://multiroom.lan:8080`. */
@JvmInline
value class HubAddress(val baseUrl: String) {
    sealed interface ParseResult {
        data class Valid(val address: HubAddress) : ParseResult
        data class Invalid(val message: String) : ParseResult
    }

    companion object {
        private const val DEFAULT_HTTP_PORT = 8080
        private const val DEFAULT_HTTPS_PORT = 8443
        private const val EMPTY = "Enter the hub's address, e.g. multiroom.lan"
        private const val SPACES = "The address can't contain spaces"
        private const val JUST_ADDRESS = "Enter just the address, e.g. multiroom.lan:8080"

        /**
         * Normalises what the user typed (research R6): scheme defaults to http, a missing port
         * becomes 8080 (http) or 8443 (https), an explicit port is always kept, the trailing slash
         * goes and a path stays as a base-path prefix.
         */
        fun parse(input: String): ParseResult {
            val text = input.trim()
            if (text.isEmpty()) return ParseResult.Invalid(EMPTY)
            if (text.any { it.isWhitespace() }) return ParseResult.Invalid(SPACES)
            if ('?' in text || '#' in text) return ParseResult.Invalid(JUST_ADDRESS)

            val schemeEnd = text.indexOf("://")
            val scheme = if (schemeEnd >= 0) text.substring(0, schemeEnd).lowercase() else "http"
            if (scheme != "http" && scheme != "https") return ParseResult.Invalid(EMPTY)
            val rest = if (schemeEnd >= 0) text.substring(schemeEnd + 3) else text

            // Ktor fills an empty host with "localhost", so reject it before parsing.
            val authority = rest.substringBefore('/')
            if (authority.isEmpty() || authority.startsWith(":")) return ParseResult.Invalid(EMPTY)

            val url = try {
                URLBuilder("$scheme://$rest").build()
            } catch (e: Exception) {
                return ParseResult.Invalid(EMPTY)
            }
            if (url.host.isEmpty()) return ParseResult.Invalid(EMPTY)

            val port = if (url.specifiedPort > 0) {
                url.specifiedPort
            } else if (url.protocol == URLProtocol.HTTPS) {
                DEFAULT_HTTPS_PORT
            } else {
                DEFAULT_HTTP_PORT
            }
            val path = url.encodedPath.trimEnd('/')
            return ParseResult.Valid(HubAddress("$scheme://${url.host}:$port$path"))
        }
    }
}
