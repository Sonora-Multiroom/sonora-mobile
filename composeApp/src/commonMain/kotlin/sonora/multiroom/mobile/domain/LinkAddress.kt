package sonora.multiroom.mobile.domain

sealed interface LinkCheck {
    /** Blank after trimming. */
    data object Empty : LinkCheck

    /** Not an `http(s)` address with a valid host. */
    data object Invalid : LinkCheck

    /** [uri] is trimmed, with `https://` prepended when the text had no scheme. */
    data class Valid(val uri: String) : LinkCheck
}

/**
 * Normalises and validates the pasted link (research R11). Hand-written because `commonMain` has no
 * `java.net.URI` (Constitution III). Single-label hosts are valid: LAN stream servers have them.
 */
fun checkLink(text: String): LinkCheck {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return LinkCheck.Empty
    if (trimmed.any { it.isWhitespace() }) return LinkCheck.Invalid

    val schemeEnd = trimmed.indexOf("://")
    val uri = if (schemeEnd < 0) "https://$trimmed" else trimmed
    val scheme = uri.substringBefore("://")
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return LinkCheck.Invalid

    val afterScheme = uri.substringAfter("://")
    val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    if (!validAuthority(authority)) return LinkCheck.Invalid
    return LinkCheck.Valid(uri)
}

/** `host` or `host:port`, where host is a bracketed IPv6 literal or dot-separated labels. */
private fun validAuthority(authority: String): Boolean {
    if (authority.isEmpty()) return false
    val host: String
    val port: String?
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return false
        val literal = authority.substring(1, close)
        if (literal.isEmpty() || !literal.all { it.isLetterOrDigit() || it == ':' || it == '.' }) return false
        val rest = authority.substring(close + 1)
        if (rest.isNotEmpty() && !rest.startsWith(":")) return false
        return rest.isEmpty() || validPort(rest.substring(1))
    }
    host = authority.substringBefore(':')
    port = if (':' in authority) authority.substringAfter(':') else null
    if (port != null && !validPort(port)) return false
    return host.isNotEmpty() && host.split('.').all { label ->
        label.isNotEmpty() && label.all { it.isAsciiLetterOrDigit() || it == '-' }
    }
}

private fun validPort(port: String) = port.isNotEmpty() && port.all { it in '0'..'9' }

private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
