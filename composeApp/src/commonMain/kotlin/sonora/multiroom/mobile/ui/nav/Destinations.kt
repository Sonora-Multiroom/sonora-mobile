package sonora.multiroom.mobile.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import sonora.multiroom.mobile.domain.Target

sealed interface Destination {
    data object Rooms : Destination
    data object Settings : Destination

    /**
     * [routeId] is the card's route. Start Playback sets [startedAfterSeq] (the session's
     * `startedSeq` when the playback began) and [targetName], so Now Playing does not announce "ended"
     * before a snapshot that can contain the new playback. Neither is saved (research R10).
     */
    data class NowPlaying(
        val routeId: String,
        val startedAfterSeq: Long? = null,
        val targetName: String? = null,
    ) : Destination

    /**
     * [targetId] is the room to preselect (Rooms' "Play in room"); null for "Play something".
     * [fixedTarget] is the addressed `Target.Room` or `Target.Group` of the Now Playing it was
     * opened from ("Change source…", MOB-008): there is no picker then. [fixedName] is the name
     * Now Playing showed; it is not saved, the view model keeps it.
     */
    data class StartPlayback(
        val targetId: String?,
        val fixedTarget: Target? = null,
        val fixedName: String? = null,
    ) : Destination
}

/**
 * The app's back stack, owned by the app so it is plain, testable state. Top-level tabs replace the
 * stack; detail destinations are pushed on top.
 */
class AppBackStack(initial: List<Destination> = listOf(Destination.Rooms)) {
    val stack: SnapshotStateList<Destination> =
        mutableStateListOf<Destination>().also { it.addAll(initial.ifEmpty { listOf(Destination.Rooms) }) }

    val top: Destination get() = stack.last()

    /** The top-level tab the user is in: the last tab destination on the stack. */
    val currentTab: Destination
        get() = stack.last { it == Destination.Rooms || it == Destination.Settings }

    /** Only the two tab roots show the bottom bar (Sources is a tab of Settings); detail destinations cover it. */
    val showsBottomBar: Boolean
        get() = top == Destination.Rooms || top == Destination.Settings

    fun push(destination: Destination) {
        stack.add(destination)
    }

    /** Swaps the top destination, e.g. Start Playback for the Now Playing it started. */
    fun replaceTop(destination: Destination) {
        stack[stack.lastIndex] = destination
    }

    /** Removes the top two entries and pushes [destination]; the root is never removed. */
    fun replaceTopTwo(destination: Destination) {
        if (stack.size >= 3) stack.removeAt(stack.lastIndex)
        replaceTop(destination)
    }

    /**
     * Shows the playback a Start Playback started. A fixed (Change source) Start Playback over a
     * Now Playing replaces both, so Back leads to where that Now Playing came from; any other
     * Start Playback is replaced alone. Nothing changes when it already left.
     */
    fun showStarted(started: Destination.NowPlaying) {
        val top = stack.last() as? Destination.StartPlayback ?: return
        val below = stack.getOrNull(stack.lastIndex - 1)
        if (top.fixedTarget != null && below is Destination.NowPlaying) replaceTopTwo(started) else replaceTop(started)
    }

    /** Pops one destination; false when the root is already showing (the app should exit). */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Switches to a top-level tab: Rooms is the root, the other tabs sit directly on it. */
    fun selectTab(tab: Destination) {
        require(tab == Destination.Rooms || tab == Destination.Settings)
        if (stack.size == 1 && stack.single() == tab) return
        if (stack.size == 2 && stack.last() == tab && tab != Destination.Rooms) return
        // The Rooms root is never removed, so its entry (and view model) survives tab switches.
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (tab != Destination.Rooms) stack.add(tab)
    }

    companion object {
        /** Survives rotation and other activity recreation (used with `rememberSaveable`). */
        val Saver: Saver<AppBackStack, Any> = listSaver(
            save = { backStack -> backStack.stack.map(::encodeDestination) },
            restore = { saved -> AppBackStack(saved.mapNotNull(::decodeDestination)) },
        )
    }
}

internal fun encodeDestination(d: Destination): String = when (d) {
    Destination.Rooms -> "rooms"
    Destination.Settings -> "settings"
    is Destination.NowPlaying -> "now:${d.routeId}"
    is Destination.StartPlayback -> when (val t = d.fixedTarget) {
        is Target.Room -> "change:room:${t.id}"
        is Target.Group -> "change:group:${t.id}"
        else -> if (d.targetId == null) "play" else "play:${d.targetId}"
    }
}

internal fun decodeDestination(s: String): Destination? = when {
    s == "rooms" -> Destination.Rooms
    // "sources" was a destination before 004: an old saved stack restores to Settings.
    s == "settings" || s == "sources" -> Destination.Settings
    s.startsWith("now:") -> Destination.NowPlaying(s.removePrefix("now:"))
    s.startsWith("change:room:") -> Destination.StartPlayback(null, Target.Room(s.removePrefix("change:room:")))
    s.startsWith("change:group:") -> Destination.StartPlayback(null, Target.Group(s.removePrefix("change:group:")))
    s == "play" -> Destination.StartPlayback(null)
    s.startsWith("play:") -> Destination.StartPlayback(s.removePrefix("play:"))
    else -> null
}
