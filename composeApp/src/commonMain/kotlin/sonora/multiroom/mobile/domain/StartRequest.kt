package sonora.multiroom.mobile.domain

/** What the user asked to play. */
sealed interface StartWhat {
    data class Source(val id: String) : StartWhat

    /** [uri] is always a normalised, valid link (`LinkCheck.Valid`). */
    data class Link(val uri: String) : StartWhat

    /** A radio station chosen on the radio screen (MOB-009); it starts through the hub's play request. */
    data class Station(val station: sonora.multiroom.mobile.domain.Station) : StartWhat
}

/**
 * Names captured when Play is tapped, so a failure or the hand-off to Now Playing can name things
 * after they vanish from the snapshot or the screen has closed. [source] is null for a link; for a
 * station it is the station's display name.
 */
data class StartNames(val source: String?, val target: String)
