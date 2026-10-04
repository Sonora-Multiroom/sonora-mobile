package sonora.multiroom.mobile.domain

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsBuilderTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val zone = TimeZone.UTC

    private fun build(s: HubSnapshot) = SettingsBuilder.build(s, now, zone)

    private fun statusOf(s: HubSnapshot, id: String = "a") = build(s).rooms.first { it.id == id }.status

    private val radio = source("radio", "Radio Paradise")
    private val jazz = source("jazz", "Jazz FM")

    // ---- Room status (FR-009) ---------------------------------------------------------------

    @Test
    fun offWhenNotEnabled() =
        assertEquals(RoomStatus.Off, statusOf(snapshot(rooms = listOf(room("a", enabled = false)))))

    @Test
    fun notConnectedWhenUnavailable() =
        assertEquals(RoomStatus.NotConnected, statusOf(snapshot(rooms = listOf(room("a", available = false)))))

    @Test
    fun offWinsOverNotConnected() =
        assertEquals(RoomStatus.Off, statusOf(snapshot(rooms = listOf(room("a", enabled = false, available = false)))))

    @Test
    fun playingOnItsOwnRoute() {
        val s = snapshot(listOf(room("a")), routes = listOf(route("r", "radio", Target.Room("a"))), sources = listOf(radio))
        assertEquals(RoomStatus.Playing(listOf("Radio Paradise")), statusOf(s))
    }

    @Test
    fun playingThroughAGroupRoute() {
        val s = snapshot(
            rooms = listOf(room("a"), room("b")),
            groups = listOf(group("g", "Downstairs", listOf("a", "b"))),
            routes = listOf(route("r", "radio", Target.Group("g"))),
            sources = listOf(radio),
        )
        assertEquals(RoomStatus.Playing(listOf("Radio Paradise")), statusOf(s))
    }

    @Test
    fun ownAndGroupRoutesWithDifferentSourcesListBothInHubOrder() {
        val s = snapshot(
            rooms = listOf(room("a")),
            groups = listOf(group("g", members = listOf("a"))),
            routes = listOf(route("r1", "jazz", Target.Group("g")), route("r2", "radio", Target.Room("a"))),
            sources = listOf(radio, jazz),
        )
        assertEquals(RoomStatus.Playing(listOf("Jazz FM", "Radio Paradise")), statusOf(s))
    }

    @Test
    fun theSameSourceTwiceIsListedOnce() {
        val s = snapshot(
            rooms = listOf(room("a")),
            groups = listOf(group("g", members = listOf("a"))),
            routes = listOf(route("r1", "radio", Target.Group("g")), route("r2", "radio", Target.Room("a"))),
            sources = listOf(radio),
        )
        assertEquals(RoomStatus.Playing(listOf("Radio Paradise")), statusOf(s))
    }

    @Test
    fun failedStartingAndUnknownRoutesCountButStoppedDoesNot() {
        for (st in listOf(RouteStatus.Failed, RouteStatus.Starting, RouteStatus.Unknown)) {
            val s = snapshot(listOf(room("a")), routes = listOf(route("r", "radio", Target.Room("a"), st)), sources = listOf(radio))
            assertEquals(RoomStatus.Playing(listOf("Radio Paradise")), statusOf(s), st.name)
        }
        val stopped = snapshot(listOf(room("a")), routes = listOf(route("r", "radio", Target.Room("a"), RouteStatus.Stopped)), sources = listOf(radio))
        assertEquals(RoomStatus.Speaker, statusOf(stopped))
    }

    @Test
    fun offWhilePlayingIsOff() {
        val s = snapshot(listOf(room("a", enabled = false)), routes = listOf(route("r", "radio", Target.Room("a"))), sources = listOf(radio))
        assertEquals(RoomStatus.Off, statusOf(s))
    }

    @Test
    fun inGroupsAreListedAtoZ() {
        val s = snapshot(
            rooms = listOf(room("a")),
            groups = listOf(group("g1", "Everywhere", listOf("a")), group("g2", "downstairs", listOf("a")), group("g3", "Other", listOf("x"))),
        )
        assertEquals(RoomStatus.InGroups(listOf("downstairs", "Everywhere")), statusOf(s))
        assertEquals(RoomStatus.InGroups(listOf("Everywhere")), statusOf(s.copy(groups = s.groups.take(1))))
    }

    @Test
    fun aRoomInNoGroupIsASpeaker() =
        assertEquals(RoomStatus.Speaker, statusOf(snapshot(listOf(room("a")))))

    // ---- Group rows (FR-010) ----------------------------------------------------------------

    private fun groupOf(s: HubSnapshot) = build(s).groups.single()

    @Test
    fun membersFollowMemberIdsAndSkipUnknownIds() {
        val s = snapshot(
            rooms = listOf(room("a", "Alpha"), room("b", "Beta")),
            groups = listOf(group("g", members = listOf("b", "ghost", "a"))),
        )
        assertEquals(listOf("Beta", "Alpha"), groupOf(s).members)
    }

    @Test
    fun noKnownMembersIsEmpty() =
        assertEquals(emptyList(), groupOf(snapshot(groups = listOf(group("g", members = listOf("ghost"))))).members)

    @Test
    fun aGroupShowsWhatItsOwnLiveRoutePlays() {
        val s = snapshot(
            rooms = listOf(room("a")),
            groups = listOf(group("g", members = listOf("a"))),
            routes = listOf(route("r", "radio", Target.Group("g"))),
            sources = listOf(radio),
        )
        assertEquals(listOf("Radio Paradise"), groupOf(s).playing)
    }

    @Test
    fun anIdleGroupPlaysNothing() =
        assertEquals(emptyList(), groupOf(snapshot(listOf(room("a")), groups = listOf(group("g", members = listOf("a"))))).playing)

    @Test
    fun aTurnedOffGroupShowsNoPlayingLine() {
        val s = snapshot(
            rooms = listOf(room("a")),
            groups = listOf(group("g", members = listOf("a"), enabled = false)),
            routes = listOf(route("r", "radio", Target.Group("g"))),
            sources = listOf(radio),
        )
        assertEquals(emptyList(), groupOf(s).playing)
    }

    @Test
    fun membersPlayingOnTheirOwnDoNotMakeTheGroupPlay() {
        val s = snapshot(
            rooms = listOf(room("a")),
            groups = listOf(group("g", members = listOf("a"))),
            routes = listOf(route("r", "radio", Target.Room("a"))),
            sources = listOf(radio),
        )
        assertEquals(emptyList(), groupOf(s).playing)
    }

    // ---- Configured sources (FR-011) --------------------------------------------------------

    @Test
    fun configuredAndUnknownOriginsAreListedRuntimeIsNot() {
        val s = snapshot(
            sources = listOf(
                source("c", "Configured", SourceOrigin.Configured),
                source("u", "Unknown", SourceOrigin.Unknown),
                source("r", "Runtime", SourceOrigin.Runtime, "https://soundcloud.com/x"),
            ),
        )
        assertEquals(listOf("c", "u"), build(s).configuredSources.map { it.id })
    }

    @Test
    fun kindAndDetailComeFromTheSharedRules() {
        val s = snapshot(
            sources = listOf(
                source("s", "Radio", uri = "https://stream.radioparadise.com/mp3"),
                source("f", "File", uri = "file:/home/x/morning.flac"),
                source("l", "Line", uri = "alsa://plughw:CARD=X,DEV=0"),
                source("n", "NoUri", uri = null),
            ),
        )
        val rows = build(s).configuredSources.associateBy { it.id }
        assertEquals(ConfiguredSourceRow("s", "Radio", true, SourceKind.Stream, "stream.radioparadise.com"), rows["s"])
        assertEquals(ConfiguredSourceRow("f", "File", true, SourceKind.File, "morning.flac"), rows["f"])
        assertEquals(ConfiguredSourceRow("l", "Line", true, SourceKind.LineIn, "plughw:CARD=X,DEV=0"), rows["l"])
        assertEquals(ConfiguredSourceRow("n", "NoUri", true, SourceKind.LineIn, null), rows["n"])
    }

    @Test
    fun aConfiguredUnusedSourceNeverOffersRemoval() {
        // ConfiguredSourceRow has no removal field at all (FR-016): even an unused configured
        // source is only ever a switch.
        val s = snapshot(sources = listOf(source("c", "Idle one")))
        val content = build(s)
        assertEquals(listOf("c"), content.configuredSources.map { it.id })
        assertTrue(content.runtimeSources.isEmpty())
    }

    // ---- Ordering ---------------------------------------------------------------------------

    @Test
    fun listsAreSortedAtoZCaseInsensitiveWithTheIdAsTieBreak() {
        val s = snapshot(
            rooms = listOf(room("2", "beta"), room("1", "Beta"), room("3", "alpha")),
            groups = listOf(group("g2", "Beta", emptyList()), group("g1", "alpha", emptyList())),
            sources = listOf(source("s2", "Zed"), source("s1", "alpha"), source("s3", "Beta")),
        )
        val c = build(s)
        assertEquals(listOf("3", "1", "2"), c.rooms.map { it.id })
        assertEquals(listOf("g1", "g2"), c.groups.map { it.id })
        assertEquals(listOf("s1", "s3", "s2"), c.configuredSources.map { it.id })
    }
}
