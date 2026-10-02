package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.data.HubError
import ai.sonora.mobile.data.HubResult
import ai.sonora.mobile.data.InMemoryHubAddressStore
import ai.sonora.mobile.data.KtorHubRepositoryFactory
import ai.sonora.mobile.data.Fixtures
import ai.sonora.mobile.data.createHubHttpClient
import ai.sonora.mobile.domain.CardAction
import ai.sonora.mobile.domain.Group
import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.domain.HubSnapshot
import ai.sonora.mobile.domain.Room
import ai.sonora.mobile.domain.RoomsContent
import ai.sonora.mobile.domain.Route
import ai.sonora.mobile.domain.RouteStatus
import ai.sonora.mobile.domain.Source
import ai.sonora.mobile.domain.SourceKind
import ai.sonora.mobile.domain.SourceOrigin
import ai.sonora.mobile.domain.Target
import ai.sonora.mobile.runViewModelTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomsViewModelControlsTest {
    private val address = HubAddress("http://hub:8080")

    private fun room(id: String, name: String, volume: Int, muted: Boolean = false) =
        Room(id, name, volume, muted, enabled = true, available = true)

    /** Group "Downstairs" (living 70, kitchen 35) playing live radio; Office playing a playlist. */
    private fun snapshot(
        officeVolume: Int = 40,
        officeMuted: Boolean = false,
        groupMuted: Boolean = false,
        masterMuted: Boolean = false,
        livingVolume: Int = 70,
        kitchenVolume: Int = 35,
        officePaused: Boolean = false,
        officeStatus: RouteStatus = RouteStatus.Active,
        extraRoutes: List<Route> = emptyList(),
    ) = HubSnapshot(
        rooms = listOf(
            room("living", "Living Room", livingVolume),
            room("kitchen", "Kitchen", kitchenVolume),
            room("office", "Office", officeVolume, officeMuted),
        ),
        groups = listOf(Group("g", "Downstairs", listOf("living", "kitchen"), muted = groupMuted, enabled = true)),
        routes = listOf(
            Route("r1", "radio", Target.Group("g"), RouteStatus.Active, paused = false, pauseable = false, transferable = false),
            Route("r2", "playlist", Target.Room("office"), officeStatus, paused = officePaused, pauseable = true, transferable = false),
        ) + extraRoutes,
        sources = listOf(
            Source("radio", "Radio Paradise", "http://radio.example/s", SourceOrigin.Configured, false, true, SourceKind.Stream),
            Source("playlist", "Morning playlist", "file:///m.m3u", SourceOrigin.Configured, true, true, SourceKind.File),
        ),
        masterMuted = masterMuted,
    )

    private class Setup(val vm: RoomsViewModel, val factory: FakeFactory) {
        val repo get() = factory.last
    }

    /** A view model that has loaded [snapshot] and is polling. */
    private fun TestScope.live(snapshot: HubSnapshot = snapshot()): Setup {
        val factory = FakeFactory { currentTime }
        val vm = RoomsViewModel(InMemoryHubAddressStore(address), factory, now = { currentTime })
        runCurrent()
        factory.last.snapshotResult = { HubResult.Ok(snapshot) }
        vm.startPolling()
        runCurrent()
        return Setup(vm, factory)
    }

    private fun Setup.connected(): RoomsUiState.Connected {
        val s = vm.state.value
        assertIs<RoomsUiState.Connected>(s)
        return s
    }

    private fun Setup.rooms() = connected().content as RoomsContent.Rooms

    private fun volumeCalls() = { s: Setup -> s.repo.calls.filter { it.name == "setRoomVolume" } }

    // ---- Drag ownership (FR-014, US3-2) -----------------------------------------------------

    @Test
    fun aRefreshDuringADragDoesNotChangeTheDisplayedVolumeAndTheOverrideClearsAfterwards() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("r2")
        s.vm.onVolumeDrag("r2", 60)
        // The hub reports a different volume meanwhile.
        s.repo.snapshotResult = { HubResult.Ok(snapshot(officeVolume = 10)) }
        advanceTimeBy(2500); runCurrent()
        assertEquals(10, s.rooms().cards.first { it.key == "r2" }.volume)
        assertEquals(60, s.connected().volumeOverrides["r2"])

        s.vm.onVolumeDragEnd("r2", 60)
        val before = s.repo.snapshotCalls
        runCurrent()
        assertTrue(s.connected().volumeOverrides.isEmpty())
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.vm.stopPolling()
    }

    // ---- Throttle ------------------------------------------------------------------------

    @Test
    fun fortyDragEventsOverTwoSecondsSendAtMostFourPerSecondAndEndWithTheFinalValue() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("r2")
        for (i in 1..40) {
            s.vm.onVolumeDrag("r2", 40 + i)
            advanceTimeBy(50); runCurrent()
        }
        s.vm.onVolumeDragEnd("r2", 80)
        runCurrent()

        val calls = s.repo.calls.filter { it.name == "setRoomVolume" }
        assertTrue(calls.isNotEmpty())
        for (c in calls) {
            val inWindow = calls.count { it.at >= c.at && it.at < c.at + 1000 }
            assertTrue(inWindow <= 4 + 1, "too many sends in a second from ${c.at}: $inWindow") // +1: the final send
        }
        // Only throttled sends plus the final one: never one per event.
        assertTrue(calls.size <= 8 + 1, "sent ${calls.size} times")
        assertEquals(listOf<Any>("office", 80), calls.last().args)
        assertEquals(1, calls.count { it.args[1] == 80 })
        s.vm.stopPolling()
    }

    @Test
    fun theThrottleAloneNeverExceedsFourSendsPerSecond() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("r2")
        for (i in 1..40) {
            s.vm.onVolumeDrag("r2", 40 + i)
            advanceTimeBy(50); runCurrent()
        }
        val calls = s.repo.calls.filter { it.name == "setRoomVolume" }
        for (c in calls) {
            assertTrue(calls.count { it.at >= c.at && it.at < c.at + 1000 } <= 4, "from ${c.at}")
        }
        s.vm.stopPolling()
    }

    // ---- Group drag ------------------------------------------------------------------------

    @Test
    fun aGroupDragScalesEveryMemberByTheNewOverTheOldLoudest() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("r1")
        s.vm.onVolumeDrag("r1", 35)
        runCurrent()
        val sent = s.repo.calls.filter { it.name == "setRoomVolume" }.map { it.args.toList() }.toSet()
        assertEquals(setOf<List<Any>>(listOf("living", 35), listOf("kitchen", 18)), sent)
        s.vm.stopPolling()
    }

    @Test
    fun aGroupWithAllMembersAtZeroSetsEveryoneToTheNewValue() = runViewModelTest {
        val s = live(snapshot(livingVolume = 0, kitchenVolume = 0))
        s.vm.onVolumeDragStart("r1")
        s.vm.onVolumeDragEnd("r1", 40)
        runCurrent()
        val sent = s.repo.calls.filter { it.name == "setRoomVolume" }.map { it.args.toList() }.toSet()
        assertEquals(setOf<List<Any>>(listOf("living", 40), listOf("kitchen", 40)), sent)
        s.vm.stopPolling()
    }

    @Test
    fun aGroupDragBackToTheStartRestoresTheHubBecauseMembersAreComparedWithWhatWasLastSent() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("r1")
        s.vm.onVolumeDrag("r1", 35)
        runCurrent()
        advanceTimeBy(300); runCurrent()
        s.vm.onVolumeDrag("r1", 70)
        s.vm.onVolumeDragEnd("r1", 70)
        runCurrent()
        val calls = s.repo.calls.filter { it.name == "setRoomVolume" }
        val lastPerRoom = calls.groupBy { it.args[0] }.mapValues { (_, v) -> v.last().args[1] }
        assertEquals(mapOf<Any, Any>("living" to 70, "kitchen" to 35), lastPerRoom)
        s.vm.stopPolling()
    }

    @Test
    fun aMemberAlreadyAtTheTargetIsNotSentAgain() = runViewModelTest {
        val s = live()
        s.vm.onVolumeDragStart("r1")
        s.vm.onVolumeDragEnd("r1", 70) // same as the loudest: nothing changes
        runCurrent()
        assertEquals(emptyList(), s.repo.calls.filter { it.name == "setRoomVolume" })
        s.vm.stopPolling()
    }

    // ---- Guards ----------------------------------------------------------------------------

    @Test
    fun aDragOnACardWhoseTargetIsNotAdjustableIsIgnored() = runViewModelTest {
        val ghost = Route("rg", "radio", Target.Room("ghost"), RouteStatus.Active, false, false, false)
        val s = live(snapshot(extraRoutes = listOf(ghost)))
        s.vm.onVolumeDragStart("rg")
        s.vm.onVolumeDrag("rg", 50)
        s.vm.onVolumeDragEnd("rg", 50)
        advanceTimeBy(1000); runCurrent()
        assertEquals(emptyList(), s.repo.calls)
        assertNull(s.connected().volumeOverrides["rg"])
        s.vm.stopPolling()
    }

    @Test
    fun aDragOnAMutedCardIsIgnored() = runViewModelTest {
        for (snap in listOf(snapshot(officeMuted = true), snapshot(groupMuted = true), snapshot(masterMuted = true))) {
            val s = live(snap)
            for (key in listOf("r1", "r2")) {
                val muted = s.rooms().cards.first { it.key == key }.muted
                if (!muted) continue
                s.vm.onVolumeDragStart(key)
                s.vm.onVolumeDrag(key, 55)
                s.vm.onVolumeDragEnd(key, 55)
                advanceTimeBy(1000); runCurrent()
            }
            assertEquals(emptyList(), s.repo.calls)
            s.vm.stopPolling()
        }
    }

    @Test
    fun everyControlIsIgnoredWhileTheHubIsUnreachable() = runViewModelTest {
        val s = live()
        s.repo.snapshotResult = { unreachable }
        advanceTimeBy(2500); runCurrent()
        assertIs<Connection.Unreachable>(s.connected().connection)
        assertTrue(s.connected().content != null)

        s.vm.onVolumeDragStart("r2"); s.vm.onVolumeDrag("r2", 70); s.vm.onVolumeDragEnd("r2", 70)
        s.vm.onCardAction("r1")
        s.vm.onCardAction("r2")
        s.vm.onMasterMuteToggle()
        advanceTimeBy(1000); runCurrent()
        assertEquals(emptyList(), s.repo.calls)
        s.vm.stopPolling()
    }

    @Test
    fun tappingStopTwiceWhileThePlaybackIsStoppingSendsOneRequest() = runViewModelTest {
        val s = live()
        s.repo.actionDelayMs = 1000
        s.vm.onCardAction("r1")
        runCurrent()
        assertTrue(ActionKey.Card("r1", CardAction.Stop) in s.connected().inFlight)
        s.vm.onCardAction("r1")
        runCurrent()
        assertEquals(1, s.repo.calls.count { it.name == "stopRoute" })
        advanceTimeBy(1000); runCurrent()
        assertTrue(s.connected().inFlight.isEmpty())
        s.vm.stopPolling()
    }

    @Test
    fun anActionOnACardWithADisabledActionIsIgnored() = runViewModelTest {
        val s = live(snapshot(officeStatus = RouteStatus.Starting))
        assertFalse(s.rooms().cards.first { it.key == "r2" }.actionEnabled)
        s.vm.onCardAction("r2")
        runCurrent()
        assertEquals(emptyList(), s.repo.calls)
        s.vm.stopPolling()
    }

    // ---- The actions -----------------------------------------------------------------------

    @Test
    fun stopPauseResumeAndMasterMuteCallTheRightMethods() = runViewModelTest {
        val s = live()
        s.vm.onCardAction("r1")
        s.vm.onCardAction("r2") // pauseable and playing: Pause
        runCurrent()
        s.vm.onMasterMuteToggle()
        runCurrent()
        assertEquals(
            listOf(
                listOf<Any>("stopRoute", "r1"),
                listOf<Any>("setRoutePaused", "r2", true),
                listOf<Any>("setMasterMute", true),
            ),
            s.repo.calls.map { listOf(it.name) + it.args },
        )

        // Now the hub reports Office paused and everything muted: Resume and Unmute.
        s.repo.calls.clear()
        s.repo.snapshotResult = { HubResult.Ok(snapshot(officePaused = true, masterMuted = true)) }
        advanceTimeBy(2500); runCurrent()
        s.vm.onCardAction("r2")
        runCurrent()
        s.vm.onMasterMuteToggle()
        runCurrent()
        assertEquals(
            listOf(listOf<Any>("setRoutePaused", "r2", false), listOf<Any>("setMasterMute", false)),
            s.repo.calls.map { listOf(it.name) + it.args },
        )
        s.vm.stopPolling()
    }

    // ---- Failure ---------------------------------------------------------------------------

    @Test
    fun aFailedActionShowsAPlainMessageClearsInFlightAndRefreshes() = runViewModelTest {
        val s = live()
        s.repo.actionResult = { HubResult.Err(HubError.Unreachable) }
        val before = s.repo.snapshotCalls
        s.vm.onCardAction("r1")
        runCurrent()
        assertEquals("Couldn't stop Downstairs. Can't reach the hub.", s.connected().message)
        assertTrue(s.connected().inFlight.isEmpty())
        assertEquals(before + 1, s.repo.snapshotCalls)
        s.vm.consumeMessage()
        assertNull(s.connected().message)
        s.vm.stopPolling()
    }

    @Test
    fun aFailedGroupVolumeNamesTheGroup() = runViewModelTest {
        val s = live()
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(404, null)) }
        s.vm.onVolumeDragStart("r1")
        s.vm.onVolumeDragEnd("r1", 35)
        runCurrent()
        assertEquals("Downstairs is no longer on the hub.", s.connected().message)
        s.vm.stopPolling()
    }

    // ---- Refresh after an action (I1) ------------------------------------------------------

    @Test
    fun anActionCompletingDuringARefreshNeverOverlapsAndAFreshRefreshFollowsRightAfter() = runViewModelTest {
        val s = live()
        s.repo.snapshotDelayMs = 2000
        advanceTimeBy(2500); runCurrent()      // a refresh runs 2500..4500
        advanceTimeBy(500)                       // t = 3000
        s.repo.actionDelayMs = 1000
        s.vm.onCardAction("r1")                 // completes at 4000, mid-refresh
        runCurrent()
        advanceTimeBy(1500); runCurrent()       // t = 4500: refresh ends, the signal starts one right away
        assertEquals(1, s.repo.maxConcurrentSnapshots)
        assertEquals(4500L, s.repo.snapshotTimes.last())
        s.vm.stopPolling()
    }

    @Test
    fun anActionCompletingAfterPollingStoppedStartsNoRefresh() = runViewModelTest {
        val s = live()
        s.vm.stopPolling()
        val before = s.repo.snapshotCalls
        s.repo.actionDelayMs = 100
        s.vm.onCardAction("r1")
        advanceUntilIdle()
        assertEquals(before, s.repo.snapshotCalls)
    }

    // ---- Never the group-volume endpoint (contract test 7) --------------------------------

    @Test
    fun dragAGroupThroughTheRealRepositoryNeverTouchesTheGroupVolumeEndpoint() = runViewModelTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val body = when (request.url.encodedPath) {
                "/api/v2/outputs" -> Fixtures.OUTPUTS
                "/api/v2/groups" -> Fixtures.GROUPS
                "/api/v2/routes" -> Fixtures.ROUTES
                "/api/v2/inputs" -> Fixtures.INPUTS
                "/api/v2/master-mute" -> """{"muted":false}"""
                else -> """{"outputId":"x","volume":1}"""
            }
            respond(body, HttpStatusCode.OK, json)
        }
        val vm = RoomsViewModel(
            InMemoryHubAddressStore(address),
            KtorHubRepositoryFactory(createHubHttpClient(engine)),
            now = { currentTime },
        )
        // MockEngine answers on a real thread, so wait in real time for each step.
        suspend fun settle(until: () -> Boolean) {
            repeat(500) {
                runCurrent()
                if (until()) return
                withContext(Dispatchers.Default) { delay(10) }
            }
        }
        settle { (vm.state.value as? RoomsUiState.Connected) != null }
        vm.startPolling()
        settle { ((vm.state.value as? RoomsUiState.Connected)?.connection) == Connection.Live }

        vm.onVolumeDragStart("r1")
        vm.onVolumeDrag("r1", 35)
        settle { requests.any { it.first == HttpMethod.Put } }
        advanceTimeBy(300)
        vm.onVolumeDrag("r1", 70)
        vm.onVolumeDragEnd("r1", 70)
        settle { requests.count { it.first == HttpMethod.Put } >= 4 }
        vm.stopPolling()

        val puts = requests.filter { it.first == HttpMethod.Put }.map { it.second }
        assertTrue(puts.isNotEmpty(), "the drag should have sent member volumes")
        assertTrue(puts.all { Regex("/api/v2/outputs/[^/]+/volume").matches(it) }, puts.toString())
        assertTrue(requests.none { Regex("/api/v2/groups/.+/volume").matches(it.second) })
    }
}
