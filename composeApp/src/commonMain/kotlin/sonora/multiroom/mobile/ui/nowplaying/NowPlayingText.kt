package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.NowPlayingContent
import sonora.multiroom.mobile.domain.TargetLine

/** "Stream · stream.radioparadise.com", or just "Stream" when there is no detail (research R7). */
internal fun subtitle(content: NowPlayingContent.Playback): String =
    content.addressDetail?.let { "${content.kindLabel} · $it" } ?: content.kindLabel

/** "on Bedroom", "on Downstairs · Living Room + Kitchen", plus " · Not connected" (FR-007). */
internal fun targetText(line: TargetLine): String {
    val base = if (line.memberNames.isEmpty()) "on ${line.name}" else "on ${line.name} · ${line.memberNames.joinToString(" + ")}"
    return if (line.notConnected) "$base · Not connected" else base
}
