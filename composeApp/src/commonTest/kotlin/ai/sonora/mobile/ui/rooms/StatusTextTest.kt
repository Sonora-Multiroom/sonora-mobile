package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.domain.CardAction
import ai.sonora.mobile.domain.CardStatus
import ai.sonora.mobile.domain.NowPlayingCard
import ai.sonora.mobile.domain.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTextTest {
    private fun card(
        status: CardStatus,
        kind: SourceKind = SourceKind.File,
        members: List<String> = emptyList(),
        isGroup: Boolean = members.isNotEmpty(),
        notConnected: Boolean = false,
    ) = NowPlayingCard(
        key = "r", title = "T", isGroup = isGroup, memberNames = members, sourceName = "S", kind = kind,
        status = status, volume = 50, volumeAdjustable = true, memberVolumes = emptyMap(), muted = false,
        notConnected = notConnected, action = CardAction.Stop, actionEnabled = true,
    )

    @Test fun singleRoomPaused() = assertEquals("File · Paused", statusLine(card(CardStatus.Paused)))

    @Test fun singleRoomPlayingUsesTheKindLabel() {
        assertEquals("Stream · Playing", statusLine(card(CardStatus.Playing, SourceKind.Stream)))
        assertEquals("Line-in · Playing", statusLine(card(CardStatus.Playing, SourceKind.LineIn)))
        assertEquals("Link · Playing", statusLine(card(CardStatus.Playing, SourceKind.Link)))
    }

    @Test fun singleRoomLiveStream() = assertEquals("Live stream", statusLine(card(CardStatus.LiveStream, SourceKind.Stream)))

    @Test
    fun singleRoomTransientAndErrorStates() {
        assertEquals("Starting…", statusLine(card(CardStatus.Starting)))
        assertEquals("Stopping…", statusLine(card(CardStatus.Stopping)))
        assertEquals("Couldn't play", statusLine(card(CardStatus.Failed)))
        assertEquals("Unknown", statusLine(card(CardStatus.Unknown)))
    }

    @Test
    fun groupListsItsMembersBeforeTheStatus() {
        val members = listOf("Living Room", "Kitchen")
        assertEquals("Living Room + Kitchen · Live stream", statusLine(card(CardStatus.LiveStream, members = members)))
        assertEquals("Living Room + Kitchen · Paused", statusLine(card(CardStatus.Paused, members = members)))
        assertEquals("Living Room + Kitchen · Playing", statusLine(card(CardStatus.Playing, members = members)))
    }

    @Test
    fun notConnectedIsAppended() {
        val members = listOf("Living Room", "Kitchen")
        assertEquals(
            "Living Room + Kitchen · Live stream · Not connected",
            statusLine(card(CardStatus.LiveStream, members = members, notConnected = true)),
        )
        assertEquals("File · Paused · Not connected", statusLine(card(CardStatus.Paused, notConnected = true)))
    }

    @Test
    fun aGroupWithoutKnownMembersShowsJustTheStatusWord() {
        assertEquals("Playing", statusLine(card(CardStatus.Playing, isGroup = true)))
    }
}
