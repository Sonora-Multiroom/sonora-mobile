package sonora.multiroom.mobile.domain

/** What the user asked to play. */
sealed interface StartWhat {
    data class Source(val id: String) : StartWhat

    /** [uri] is always a normalised, valid link (`LinkCheck.Valid`). */
    data class Link(val uri: String) : StartWhat
}

/**
 * Names captured when Play is tapped, so a failure or the hand-off to Now Playing can name things
 * after they vanish from the snapshot or the screen has closed. [source] is null for a link.
 */
data class StartNames(val source: String?, val target: String)
