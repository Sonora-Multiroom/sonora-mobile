package sonora.multiroom.mobile.domain

/**
 * The hub has no "kind" field on inputs (a known hub gap), so kind is inferred here and **only**
 * here. [origin] `Runtime` means the input was added at run time (a played link) and wins; an
 * unknown origin is treated like a configured one. Then the address decides: http(s) is a stream,
 * `file:` or a filesystem path is a file, anything else (including no address) is a line-in.
 */
fun inferSourceKind(origin: SourceOrigin, uri: String?): SourceKind {
    if (origin == SourceOrigin.Runtime) return SourceKind.Link
    val address = uri?.trim().orEmpty()
    return when {
        address.isHttp() -> SourceKind.Stream
        address.startsWith("file:", ignoreCase = true) || address.isPath() -> SourceKind.File
        else -> SourceKind.LineIn
    }
}

/**
 * The only place a route is judged live: it cannot be paused and its input address is http(s),
 * whatever the origin. Line-in and files are never labelled live.
 */
fun isLiveStream(pauseable: Boolean, uri: String?): Boolean =
    !pauseable && uri?.trim().orEmpty().isHttp()

fun kindLabel(kind: SourceKind): String = when (kind) {
    SourceKind.Stream -> "Stream"
    SourceKind.LineIn -> "Line-in"
    SourceKind.File -> "File"
    SourceKind.Link -> "Link"
}

/**
 * The second half of Now Playing's subtitle ("Stream · stream.radioparadise.com"), derived here
 * and only here (research R7). Stream/Link with an http(s) address: the host only. File: the last
 * path segment (no percent-decoding). Line-in and blank: null. Anything else, or an address that
 * cannot be parsed: the address as typed.
 */
fun addressDetail(kind: SourceKind, uri: String?): String? {
    val address = uri?.trim().orEmpty()
    if (address.isEmpty() || kind == SourceKind.LineIn) return null
    return when (kind) {
        SourceKind.File -> lastSegment(address) ?: address
        else -> if (address.isHttp()) hostOf(address) ?: address else address
    }
}

/**
 * The Settings row detail (research R11); Now Playing keeps [addressDetail]. Stream and file as
 * there, a line-in as its address without `scheme://` (or as typed when it has none), blank
 * `null`. A link is never asked for: runtime rows show the added line instead.
 */
fun sourceDetail(kind: SourceKind, uri: String?): String? {
    val address = uri?.trim().orEmpty()
    if (address.isEmpty()) return null
    return when (kind) {
        SourceKind.LineIn -> address.substringAfter("://", missingDelimiterValue = address).takeIf { it.isNotEmpty() }
        else -> addressDetail(kind, address)
    }
}

private fun hostOf(address: String): String? {
    val afterScheme = address.substringAfter("://")
    val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    val hostPort = authority.substringAfterLast('@')
    val host = if (hostPort.startsWith("[")) {
        hostPort.removePrefix("[").substringBefore(']')
    } else {
        hostPort.substringBefore(':')
    }
    return host.takeIf { it.isNotEmpty() }
}

private fun lastSegment(address: String): String? {
    val path = if (address.startsWith("file:", ignoreCase = true)) address.substring(5) else address
    return path.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotEmpty() }
}

private fun String.isHttp(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun String.isPath(): Boolean =
    startsWith("/") || startsWith("./") || startsWith("../") || startsWith("~/") ||
        startsWith("\\\\") || (length >= 3 && this[0].isLetter() && this[1] == ':' && (this[2] == '\\' || this[2] == '/'))
