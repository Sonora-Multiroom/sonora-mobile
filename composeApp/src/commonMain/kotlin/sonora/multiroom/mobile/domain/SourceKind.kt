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

private fun String.isHttp(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun String.isPath(): Boolean =
    startsWith("/") || startsWith("./") || startsWith("../") || startsWith("~/") ||
        startsWith("\\\\") || (length >= 3 && this[0].isLetter() && this[1] == ':' && (this[2] == '\\' || this[2] == '/'))
