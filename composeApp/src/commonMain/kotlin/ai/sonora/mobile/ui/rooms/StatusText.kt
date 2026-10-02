package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.domain.CardStatus
import ai.sonora.mobile.domain.NowPlayingCard
import ai.sonora.mobile.domain.SourceKind

fun kindLabel(kind: SourceKind): String = when (kind) {
    SourceKind.Stream -> "Stream"
    SourceKind.LineIn -> "Line-in"
    SourceKind.File -> "File"
    SourceKind.Link -> "Link"
}

private fun statusWord(status: CardStatus): String = when (status) {
    CardStatus.Playing -> "Playing"
    CardStatus.Paused -> "Paused"
    CardStatus.LiveStream -> "Live stream"
    CardStatus.Starting -> "Starting…"
    CardStatus.Stopping -> "Stopping…"
    CardStatus.Failed -> "Couldn't play"
    CardStatus.Unknown -> "Unknown"
}

/**
 * The card's third line (data-model.md "CardStatus"). A single room prefixes the kind to the plain
 * "Playing"/"Paused" states ("File · Paused"); a group prefixes its members instead
 * ("Living Room + Kitchen · Live stream"). A card needing attention appends "Not connected".
 */
fun statusLine(card: NowPlayingCard): String {
    val word = statusWord(card.status)
    val line = if (card.isGroup) {
        (card.memberNames.joinToString(" + ").takeIf { it.isNotEmpty() }?.let { "$it · $word" }) ?: word
    } else if (card.status == CardStatus.Playing || card.status == CardStatus.Paused) {
        "${kindLabel(card.kind)} · $word"
    } else {
        word
    }
    return if (card.notConnected) "$line · Not connected" else line
}
