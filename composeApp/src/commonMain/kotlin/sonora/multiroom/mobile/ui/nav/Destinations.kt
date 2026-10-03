package sonora.multiroom.mobile.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList

sealed interface Destination {
    data object Rooms : Destination
    data object Sources : Destination
    data object Settings : Destination

    /** Placeholder in this feature; [routeId] is the card's route. */
    data class NowPlaying(val routeId: String) : Destination

    /** Placeholder in this feature; [targetId] is the room to play in, if any. */
    data class StartPlayback(val targetId: String?) : Destination
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
        get() = stack.last { it == Destination.Rooms || it == Destination.Sources || it == Destination.Settings }

    /** Only the three tabs show the bottom bar; detail destinations cover it. */
    val showsBottomBar: Boolean
        get() = top == Destination.Rooms || top == Destination.Sources || top == Destination.Settings

    fun push(destination: Destination) {
        stack.add(destination)
    }

    /** Pops one destination; false when the root is already showing (the app should exit). */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Switches to a top-level tab: Rooms is the root, the other tabs sit directly on it. */
    fun selectTab(tab: Destination) {
        require(tab == Destination.Rooms || tab == Destination.Sources || tab == Destination.Settings)
        if (stack.size == 1 && stack.single() == tab) return
        if (stack.size == 2 && stack.last() == tab && tab != Destination.Rooms) return
        stack.clear()
        stack.add(Destination.Rooms)
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
    Destination.Sources -> "sources"
    Destination.Settings -> "settings"
    is Destination.NowPlaying -> "now:${d.routeId}"
    is Destination.StartPlayback -> if (d.targetId == null) "play" else "play:${d.targetId}"
}

internal fun decodeDestination(s: String): Destination? = when {
    s == "rooms" -> Destination.Rooms
    s == "sources" -> Destination.Sources
    s == "settings" -> Destination.Settings
    s.startsWith("now:") -> Destination.NowPlaying(s.removePrefix("now:"))
    s == "play" -> Destination.StartPlayback(null)
    s.startsWith("play:") -> Destination.StartPlayback(s.removePrefix("play:"))
    else -> null
}
