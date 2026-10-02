package ai.sonora.mobile.ui.nav

import androidx.compose.runtime.mutableStateListOf
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
class AppBackStack {
    val stack: SnapshotStateList<Destination> = mutableStateListOf(Destination.Rooms)

    val top: Destination get() = stack.last()

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
}
