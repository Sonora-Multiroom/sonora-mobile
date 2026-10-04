package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.CardStatus
import sonora.multiroom.mobile.domain.NowPlayingContent
import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.domain.TargetLine
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingTextTest {
    private fun content(detail: String?) = NowPlayingContent.Playback(
        routeId = "r1", sourceName = "Radio", kind = SourceKind.Stream, kindLabel = "Stream", addressDetail = detail,
        status = CardStatus.Playing, live = true, target = TargetLine(Target.Room("a"), "A", emptyList(), false),
        pauseVisible = false, pauseEnabled = true, paused = false, moveVisible = true, volume = null,
    )

    @Test
    fun theSubtitleJoinsKindAndDetailOrIsJustTheKind() {
        assertEquals("Stream · stream.radioparadise.com", subtitle(content("stream.radioparadise.com")))
        assertEquals("Stream", subtitle(content(null)))
    }

    @Test
    fun theTargetLineNamesMembersAndConnection() {
        assertEquals("on Bedroom", targetText(TargetLine(Target.Room("b"), "Bedroom", emptyList(), false)))
        assertEquals(
            "on Downstairs · Living Room + Kitchen",
            targetText(TargetLine(Target.Group("g"), "Downstairs", listOf("Living Room", "Kitchen"), false)),
        )
        assertEquals(
            "on Downstairs · Living Room + Kitchen · Not connected",
            targetText(TargetLine(Target.Group("g"), "Downstairs", listOf("Living Room", "Kitchen"), true)),
        )
        assertEquals("on Patio · Not connected", targetText(TargetLine(Target.Room("p"), "Patio", emptyList(), true)))
    }
}
