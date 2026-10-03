package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NowPlayingBuilderTest {
    private fun room(
        id: String,
        name: String = id,
        volume: Int = 50,
        muted: Boolean = false,
        enabled: Boolean = true,
        available: Boolean = true,
    ) = Room(id, name, volume, muted, enabled, available)

    private fun group(id: String, members: List<String>, name: String = id, muted: Boolean = false) =
        Group(id, name, members, muted, enabled = true)

    private fun source(
        id: String,
        uri: String?,
        name: String = id,
        origin: SourceOrigin = SourceOrigin.Configured,
        pauseable: Boolean = false,
    ) = Source(id, name, uri, origin, pauseable, enabled = true, kind = inferSourceKind(origin, uri))

    private fun route(
        id: String,
        inputId: String,
        target: Target,
        status: RouteStatus = RouteStatus.Active,
        paused: Boolean = false,
        pauseable: Boolean = false,
        transferable: Boolean = true,
    ) = Route(id, inputId, target, status, paused, pauseable, transferable)

    private fun snapshot(
        rooms: List<Room> = listOf(room("living", "Living Room", 70), room("kitchen", "Kitchen", 35)),
        groups: List<Group> = listOf(group("down", listOf("living", "kitchen"), "Downstairs")),
        routes: List<Route> = emptyList(),
        sources: List<Source> = listOf(radio, playlist, lineIn),
        masterMuted: Boolean = false,
    ) = HubSnapshot(rooms, groups, routes, sources, masterMuted)

    private val radio = source("radio", "https://stream.radioparadise.com/aac-320", "Radio Paradise")
    private val playlist = source("playlist", "file:///music/Morning.mp3", "Morning playlist", pauseable = true)
    private val lineIn = source("line", "hw:1,0", "Turntable")

    private fun playback(s: HubSnapshot, id: String = "r1"): NowPlayingContent.Playback {
        val c = NowPlayingBuilder.build(s, id)
        assertIs<NowPlayingContent.Playback>(c)
        return c
    }

    // ---- Content (US1) -------------------------------------------------------------------------

    @Test
    fun anAbsentOrStoppedRouteIsGone() {
        assertEquals(NowPlayingContent.Gone, NowPlayingBuilder.build(snapshot(), "r1"))
        val stopped = snapshot(routes = listOf(route("r1", "radio", Target.Room("living"), RouteStatus.Stopped)))
        assertEquals(NowPlayingContent.Gone, NowPlayingBuilder.build(stopped, "r1"))
    }

    @Test
    fun aFailedRouteOffersStopOnly() {
        val p = playback(snapshot(routes = listOf(route("r1", "playlist", Target.Room("living"), RouteStatus.Failed, pauseable = true))))
        assertEquals(CardStatus.Failed, p.status)
        assertFalse(p.moveVisible)
        assertFalse(p.pauseEnabled)
        assertFalse(p.live)
    }

    @Test
    fun aLiveStreamIsPlayingAndLive() {
        val p = playback(snapshot(routes = listOf(route("r1", "radio", Target.Group("down")))))
        assertEquals(CardStatus.Playing, p.status)
        assertTrue(p.live)
        assertFalse(p.pauseVisible)
        assertEquals(SourceKind.Stream, p.kind)
        assertEquals("Stream", p.kindLabel)
        assertEquals("stream.radioparadise.com", p.addressDetail)
    }

    @Test
    fun aLineInIsNeverLive() {
        val p = playback(snapshot(routes = listOf(route("r1", "line", Target.Room("living")))))
        assertFalse(p.live)
        assertFalse(p.pauseVisible)
        assertEquals(CardStatus.Playing, p.status)
        assertEquals(SourceKind.LineIn, p.kind)
        assertNull(p.addressDetail)
    }

    @Test
    fun aPausedPauseableRouteReadsResumeAndCannotMove() {
        val p = playback(snapshot(routes = listOf(route("r1", "playlist", Target.Room("living"), paused = true, pauseable = true))))
        assertEquals(CardStatus.Paused, p.status)
        assertTrue(p.paused)
        assertTrue(p.pauseVisible)
        assertTrue(p.pauseEnabled)
        assertFalse(p.moveVisible)
        assertEquals("Morning.mp3", p.addressDetail)
    }

    @Test
    fun aPlayingPauseableRouteCanPauseAndMoveWhenTransferable() {
        val s = { transferable: Boolean ->
            playback(snapshot(routes = listOf(route("r1", "playlist", Target.Room("living"), pauseable = true, transferable = transferable))))
        }
        val yes = s(true)
        assertEquals(CardStatus.Playing, yes.status)
        assertTrue(yes.pauseEnabled)
        assertFalse(yes.paused)
        assertTrue(yes.moveVisible)
        assertFalse(s(false).moveVisible)
    }

    @Test
    fun aTransferableRouteWithAnUnknownTargetCannotMove() {
        val p = playback(snapshot(routes = listOf(route("r1", "radio", Target.Unknown("weird")))))
        assertFalse(p.moveVisible)
        assertEquals("weird", p.target.name)
    }

    @Test
    fun startingStoppingAndUnknownDisablePauseAndMove() {
        for (status in listOf(RouteStatus.Starting, RouteStatus.Stopping, RouteStatus.Unknown)) {
            val p = playback(snapshot(routes = listOf(route("r1", "playlist", Target.Room("living"), status, pauseable = true))))
            assertFalse(p.pauseEnabled, status.name)
            assertFalse(p.moveVisible, status.name)
        }
    }

    @Test
    fun aMissingSourceFallsBackToTheInputId() {
        val p = playback(snapshot(routes = listOf(route("r1", "gone", Target.Room("living"))), sources = emptyList()))
        assertEquals("gone", p.sourceName)
        assertNull(p.addressDetail)
    }

    @Test
    fun theTargetLineNamesRoomsGroupsAndMembers() {
        val room = playback(snapshot(routes = listOf(route("r1", "radio", Target.Room("living")))))
        assertEquals("Living Room", room.target.name)
        assertEquals(emptyList(), room.target.memberNames)

        val group = playback(snapshot(routes = listOf(route("r1", "radio", Target.Group("down")))))
        assertEquals("Downstairs", group.target.name)
        assertEquals(listOf("Living Room", "Kitchen"), group.target.memberNames)
    }

    @Test
    fun memberNamesFollowTheGroupOrderAndSkipUnknownIds() {
        val s = snapshot(
            groups = listOf(group("down", listOf("kitchen", "ghost", "living"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Group("down"))),
        )
        assertEquals(listOf("Kitchen", "Living Room"), playback(s).target.memberNames)
    }

    @Test
    fun aMissingRoomOrGroupIsNamedByItsId() {
        assertEquals("ghost", playback(snapshot(routes = listOf(route("r1", "radio", Target.Room("ghost"))))).target.name)
        assertEquals("gg", playback(snapshot(routes = listOf(route("r1", "radio", Target.Group("gg"))))).target.name)
    }

    @Test
    fun notConnectedWhenTheRoomOrAnyMemberIsUnavailable() {
        val s = snapshot(
            rooms = listOf(room("living", "Living Room"), room("kitchen", "Kitchen", available = false)),
            routes = listOf(route("r1", "radio", Target.Group("down")), route("r2", "playlist", Target.Room("kitchen"))),
        )
        assertTrue(playback(s, "r1").target.notConnected)
        assertTrue(playback(s, "r2").target.notConnected)
        val ok = snapshot(routes = listOf(route("r1", "radio", Target.Group("down"))))
        assertFalse(playback(ok).target.notConnected)
    }

    // ---- Volume (US2) --------------------------------------------------------------------------

    @Test
    fun aSingleRoomHasOneMainPillAndRoomMute() {
        val v = playback(snapshot(routes = listOf(route("r1", "radio", Target.Room("living"))))).volume!!
        assertEquals(PillModel("main", "Living Room", mapOf("living" to 70), muted = false), v.main)
        assertEquals(emptyList(), v.members)
        assertEquals(MuteModel(Target.Room("living"), "Living Room", muted = false, enabled = true), v.mute)
        assertFalse(v.masterMuted)
    }

    @Test
    fun aGroupHasAMainPillOverAllMembersAndOnePillPerMember() {
        val v = playback(snapshot(routes = listOf(route("r1", "radio", Target.Group("down"))))).volume!!
        assertEquals("main", v.main.key)
        assertEquals("Downstairs", v.main.label)
        assertEquals(mapOf("living" to 70, "kitchen" to 35), v.main.roomVolumes)
        assertEquals(
            listOf(
                PillModel("member:living", "Living Room", mapOf("living" to 70), false),
                PillModel("member:kitchen", "Kitchen", mapOf("kitchen" to 35), false),
            ),
            v.members,
        )
        assertEquals(Target.Group("down"), v.mute.target)
    }

    @Test
    fun memberPillsSkipUnknownIdsInGroupOrder() {
        val s = snapshot(
            groups = listOf(group("down", listOf("kitchen", "ghost", "living"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Group("down"))),
        )
        assertEquals(listOf("member:kitchen", "member:living"), playback(s).volume!!.members.map { it.key })
    }

    @Test
    fun noKnownMembersOrAnUnknownTargetHasNoVolumeSection() {
        val empty = snapshot(
            groups = listOf(group("down", listOf("ghost"), "Downstairs")),
            routes = listOf(route("r1", "radio", Target.Group("down"))),
        )
        assertNull(playback(empty).volume)
        assertNull(playback(snapshot(routes = listOf(route("r1", "radio", Target.Unknown("x"))))).volume)
        assertNull(playback(snapshot(routes = listOf(route("r1", "radio", Target.Room("ghost"))))).volume)
        assertNull(playback(snapshot(routes = listOf(route("r1", "radio", Target.Group("nope"))))).volume)
    }

    @Test
    fun masterMuteMutesEveryPillAndDisablesTheMuteButton() {
        val v = playback(snapshot(masterMuted = true, routes = listOf(route("r1", "radio", Target.Group("down"))))).volume!!
        assertTrue(v.masterMuted)
        assertTrue(v.main.muted)
        assertTrue(v.members.all { it.muted })
        assertTrue(v.mute.muted)
        assertFalse(v.mute.enabled)
    }

    @Test
    fun aMutedRoomMutesItsPillAndTheRoomMute() {
        val s = snapshot(
            rooms = listOf(room("living", "Living Room", muted = true), room("kitchen", "Kitchen")),
            routes = listOf(route("r1", "radio", Target.Room("living"))),
        )
        val v = playback(s).volume!!
        assertTrue(v.main.muted)
        assertTrue(v.mute.muted)
        assertTrue(v.mute.enabled)
    }

    @Test
    fun theGroupMuteComesOnlyFromTheHubsGroupFlag() {
        val s = snapshot(
            rooms = listOf(room("living", "Living Room", muted = true), room("kitchen", "Kitchen")),
            routes = listOf(route("r1", "radio", Target.Group("down"))),
        )
        val v = playback(s).volume!!
        // One muted member out of two: the group is not muted, that member's pill is.
        assertFalse(v.mute.muted)
        assertFalse(v.main.muted)
        assertEquals(listOf(true, false), v.members.map { it.muted })

        val muted = snapshot(groups = listOf(group("down", listOf("living", "kitchen"), "Downstairs", muted = true)),
            routes = listOf(route("r1", "radio", Target.Group("down"))))
        val m = playback(muted).volume!!
        assertTrue(m.mute.muted)
        assertTrue(m.main.muted)
    }
}
