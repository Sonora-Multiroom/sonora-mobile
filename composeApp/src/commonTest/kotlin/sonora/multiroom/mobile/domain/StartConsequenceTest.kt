package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StartConsequenceTest {
    private fun room(id: String, name: String, muted: Boolean = false, enabled: Boolean = true, available: Boolean = true) =
        Room(id, name, 50, muted, enabled, available)

    private fun source(id: String, name: String, default: JoinMode? = null, origin: SourceOrigin = SourceOrigin.Configured) =
        Source(id, name, "http://x/$id", origin, false, true, inferSourceKind(origin, "http://x/$id"), default)

    private fun route(id: String, input: String, target: Target, mode: JoinMode = JoinMode.Replace, status: RouteStatus = RouteStatus.Active) =
        Route(id, input, target, status, false, false, true, mode)

    private val bedroom = Target.Room("bedroom")
    private val downstairs = Target.Group("down")

    private fun snapshot(
        routes: List<Route> = emptyList(),
        sources: List<Source> = listOf(
            source("jazz", "Jazz24"), source("radio", "Radio Paradise"), source("playlist", "Morning playlist"),
            source("mix", "Mixer", JoinMode.Mix), source("duck", "Doorbell", JoinMode.Announcement),
            source("unk", "Odd", JoinMode.Unknown),
        ),
        rooms: List<Room> = listOf(
            room("bedroom", "Bedroom"), room("office", "Office"), room("kitchen", "Kitchen"), room("living", "Living Room"),
            room("patio", "Patio", available = false), room("garage", "Garage", enabled = false),
        ),
        groups: List<Group> = listOf(
            Group("down", "Downstairs", listOf("living", "kitchen"), muted = false, enabled = true),
            Group("mixed", "Mixed", listOf("living", "patio", "garage"), muted = false, enabled = true),
        ),
        masterMuted: Boolean = false,
    ) = HubSnapshot(rooms, groups, routes, sources, masterMuted)

    private fun of(snapshot: HubSnapshot, what: StartWhat, target: Target) = StartConsequence.of(snapshot, what, target)
    private fun line(snapshot: HubSnapshot, what: String, target: Target) = of(snapshot, StartWhat.Source(what), target).line

    // ---- Replace ---------------------------------------------------------------------------------

    @Test
    fun anIdleTargetHasNoLine() = assertEquals(null, line(snapshot(), "jazz", bedroom))

    @Test
    fun aBusyRoomNamesWhatWillStop() {
        val s = snapshot(listOf(route("r", "radio", bedroom)))
        assertEquals(ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Bedroom"))), line(s, "jazz", bedroom))
    }

    @Test
    fun aRoomInsideAGroupPlaybackIsNamedByTheGroup() {
        val s = snapshot(listOf(route("r", "radio", downstairs)))
        assertEquals(
            ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Downstairs"))),
            line(s, "jazz", Target.Room("kitchen")),
        )
    }

    @Test
    fun aGroupOverSeveralPlaybacksNamesEachInHubOrder() {
        val s = snapshot(listOf(route("r1", "playlist", Target.Room("kitchen")), route("r2", "radio", Target.Room("living"))))
        assertEquals(
            ConsequenceLine.WillStop(listOf(AffectedPlayback("Morning playlist", "Kitchen"), AffectedPlayback("Radio Paradise", "Living Room"))),
            line(s, "jazz", downstairs),
        )
    }

    @Test
    fun aGroupRouteCoveringSeveralChosenRoomsIsCountedOnce() {
        val s = snapshot(listOf(route("r", "radio", downstairs)))
        assertEquals(ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Downstairs"))), line(s, "jazz", downstairs))
    }

    @Test
    fun theSameSourceOnExactlyTheSameTargetIsAlreadyPlaying() {
        val s = snapshot(listOf(route("r", "jazz", bedroom)))
        assertEquals(ConsequenceLine.AlreadyPlaying("Jazz24", "Bedroom"), line(s, "jazz", bedroom))
        val g = snapshot(listOf(route("r", "jazz", downstairs)))
        assertEquals(ConsequenceLine.AlreadyPlaying("Jazz24", "Downstairs"), line(g, "jazz", downstairs))
    }

    @Test
    fun theSameSourceOnAGroupContainingTheChosenRoomWillStop() {
        val s = snapshot(listOf(route("r", "jazz", downstairs)))
        assertEquals(
            ConsequenceLine.WillStop(listOf(AffectedPlayback("Jazz24", "Downstairs"))),
            line(s, "jazz", Target.Room("kitchen")),
        )
    }

    @Test
    fun anAnnouncementOnTheRoomIsLeftOut() {
        val s = snapshot(listOf(route("r", "duck", bedroom, JoinMode.Announcement)))
        assertEquals(null, line(s, "jazz", bedroom))
        val both = snapshot(listOf(route("r1", "duck", bedroom, JoinMode.Announcement), route("r2", "radio", bedroom)))
        assertEquals(ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Bedroom"))), line(both, "jazz", bedroom))
    }

    @Test
    fun aStoppedRouteIsNotAffected() {
        assertEquals(null, line(snapshot(listOf(route("r", "radio", bedroom, status = RouteStatus.Stopped))), "jazz", bedroom))
    }

    // ---- Other modes -----------------------------------------------------------------------------

    @Test
    fun aMixDefaultPlaysAlongsideAndLeavesAnnouncementsOut() {
        val s = snapshot(listOf(route("r1", "radio", bedroom), route("r2", "duck", bedroom, JoinMode.Announcement)))
        assertEquals(ConsequenceLine.PlaysAlongside(listOf(AffectedPlayback("Radio Paradise", "Bedroom"))), line(s, "mix", bedroom))
        assertEquals(null, line(snapshot(), "mix", bedroom))
    }

    @Test
    fun anAnnouncementDefaultLowersEveryoneIncludingAnnouncements() {
        val s = snapshot(listOf(route("r1", "radio", bedroom), route("r2", "playlist", bedroom, JoinMode.Announcement)))
        assertEquals(
            ConsequenceLine.WillBeLowered(listOf(AffectedPlayback("Radio Paradise", "Bedroom"), AffectedPlayback("Morning playlist", "Bedroom"))),
            line(s, "duck", bedroom),
        )
        assertEquals(null, line(snapshot(), "duck", bedroom))
    }

    @Test
    fun alreadyPlayingStillWinsInEveryMode() {
        assertEquals(ConsequenceLine.AlreadyPlaying("Mixer", "Bedroom"), line(snapshot(listOf(route("r", "mix", bedroom, JoinMode.Mix))), "mix", bedroom))
        assertEquals(
            ConsequenceLine.AlreadyPlaying("Doorbell", "Bedroom"),
            line(snapshot(listOf(route("r", "duck", bedroom, JoinMode.Announcement))), "duck", bedroom),
        )
    }

    @Test
    fun aLinkAlwaysReplacesAndIsNeverAlreadyPlaying() {
        val s = snapshot(listOf(route("r", "radio", bedroom)))
        assertEquals(
            ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Bedroom"))),
            of(s, StartWhat.Link("https://x.y/z"), bedroom).line,
        )
        val same = snapshot(listOf(route("r", "jazz", bedroom)), sources = listOf(source("jazz", "Jazz24", origin = SourceOrigin.Runtime)))
        assertEquals(
            ConsequenceLine.WillStop(listOf(AffectedPlayback("Jazz24", "Bedroom"))),
            of(same, StartWhat.Link("http://x/jazz"), bedroom).line,
        )
    }

    @Test
    fun aMissingOrUnknownDefaultMeansReplaceAndNeverUnknown() {
        val s = snapshot(listOf(route("r", "radio", bedroom)))
        val stop = ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Bedroom")))
        assertEquals(stop, line(s, "jazz", bedroom))
        assertEquals(stop, line(s, "unk", bedroom))
        assertEquals(JoinMode.Replace, effectiveJoinMode(StartWhat.Source("jazz"), s))
        assertEquals(JoinMode.Replace, effectiveJoinMode(StartWhat.Source("unk"), s))
        assertEquals(JoinMode.Replace, effectiveJoinMode(StartWhat.Source("ghost"), s))
        assertEquals(JoinMode.Replace, effectiveJoinMode(StartWhat.Link("https://x.y"), s))
        assertEquals(JoinMode.Mix, effectiveJoinMode(StartWhat.Source("mix"), s))
        assertEquals(JoinMode.Announcement, effectiveJoinMode(StartWhat.Source("duck"), s))
    }

    @Test
    fun aBusyRouteWithAnUnknownJoinModeIsStillNamedAsStopping() {
        val s = snapshot(listOf(route("r", "radio", bedroom, JoinMode.Unknown)))
        assertEquals(ConsequenceLine.WillStop(listOf(AffectedPlayback("Radio Paradise", "Bedroom"))), line(s, "jazz", bedroom))
        assertNotEquals(null, line(s, "jazz", bedroom))
    }

    // ---- Notes ---------------------------------------------------------------------------------

    @Test
    fun aGroupWithUnplayableMembersSaysWhichWontPlay() {
        val c = of(snapshot(), StartWhat.Source("jazz"), Target.Group("mixed"))
        assertEquals(WontPlay(turnedOff = listOf("Garage"), notConnected = listOf("Patio")), c.wontPlay)
        assertEquals(null, of(snapshot(), StartWhat.Source("jazz"), downstairs).wontPlay)
        assertEquals(null, of(snapshot(), StartWhat.Source("jazz"), bedroom).wontPlay)
    }

    @Test
    fun aMutedTargetIsNoted() {
        val muted = snapshot(rooms = listOf(room("bedroom", "Bedroom", muted = true)))
        assertEquals(MuteNote.TargetMuted("Bedroom"), of(muted, StartWhat.Source("jazz"), bedroom).mute)
        assertEquals(null, of(snapshot(), StartWhat.Source("jazz"), bedroom).mute)
    }

    @Test
    fun aGroupMutedOrWithEveryMemberMutedIsNoted() {
        val groupMuted = snapshot(groups = listOf(Group("down", "Downstairs", listOf("living", "kitchen"), muted = true, enabled = true)))
        assertEquals(MuteNote.TargetMuted("Downstairs"), of(groupMuted, StartWhat.Source("jazz"), downstairs).mute)
        val membersMuted = snapshot(rooms = listOf(room("living", "Living Room", muted = true), room("kitchen", "Kitchen", muted = true)))
        assertEquals(MuteNote.TargetMuted("Downstairs"), of(membersMuted, StartWhat.Source("jazz"), downstairs).mute)
        val oneMuted = snapshot(rooms = listOf(room("living", "Living Room", muted = true), room("kitchen", "Kitchen")))
        assertEquals(null, of(oneMuted, StartWhat.Source("jazz"), downstairs).mute)
    }

    @Test
    fun masterMuteReplacesTheTargetMuteNote() {
        val s = snapshot(rooms = listOf(room("bedroom", "Bedroom", muted = true)), masterMuted = true)
        assertEquals(MuteNote.AllRoomsMuted, of(s, StartWhat.Source("jazz"), bedroom).mute)
        assertEquals(MuteNote.AllRoomsMuted, of(snapshot(masterMuted = true), StartWhat.Source("jazz"), bedroom).mute)
    }
}
