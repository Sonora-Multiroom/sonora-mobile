package sonora.multiroom.mobile.ui.session

import sonora.multiroom.mobile.data.HubError
import sonora.multiroom.mobile.data.HubResult
import sonora.multiroom.mobile.data.InMemoryHubAddressStore
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.ui.rooms.FakeFactory
import sonora.multiroom.mobile.ui.rooms.FakeRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VolumeDragControllerTest {
    private val address = HubAddress("http://hub:8080")

    private fun room(id: String, volume: Int) = Room(id, id, volume, muted = false, enabled = true, available = true)

    private fun snapshot(living: Int = 70, kitchen: Int = 35, office: Int = 40) = HubSnapshot(
        rooms = listOf(room("living", living), room("kitchen", kitchen), room("office", office)),
        groups = emptyList(),
        routes = emptyList(),
        sources = emptyList(),
        masterMuted = false,
    )

    private class Setup(
        val session: HubSession,
        val controller: VolumeDragController,
        val factory: FakeFactory,
        val errors: MutableList<Pair<String, HubError>>,
    ) {
        val repo: FakeRepository get() = factory.last
        fun volumeCalls() = repo.calls.filter { it.name == "setRoomVolume" }
        fun lastPerRoom() = volumeCalls().groupBy { it.args[0] }.mapValues { (_, v) -> v.last().args[1] }
    }

    private fun TestScope.setup(snapshot: HubSnapshot = snapshot()): Setup {
        val factory = FakeFactory { currentTime }
        val session = HubSession(InMemoryHubAddressStore(address), factory, backgroundScope, now = { currentTime })
        runCurrent()
        factory.last.snapshotResult = { HubResult.Ok(snapshot) }
        val errors = mutableListOf<Pair<String, HubError>>()
        val controller = VolumeDragController(backgroundScope, session, onError = { name, e -> errors += name to e })
        return Setup(session, controller, factory, errors)
    }

    private val group = mapOf("living" to 70, "kitchen" to 35)

    @Test
    fun fortyDragEventsOverTwoSecondsSendAtMostFourPerSecondAndEndWithTheFinalValue() = runTest {
        val s = setup()
        s.controller.start("main", "Office", mapOf("office" to 40))
        for (i in 1..40) {
            s.controller.drag("main", 40 + i)
            advanceTimeBy(50); runCurrent()
        }
        s.controller.end("main", 80)
        runCurrent()

        val calls = s.volumeCalls()
        assertTrue(calls.isNotEmpty())
        for (c in calls) {
            val inWindow = calls.count { it.at >= c.at && it.at < c.at + 1000 }
            assertTrue(inWindow <= 4 + 1, "too many sends in a second from ${c.at}: $inWindow") // +1: the final send
        }
        assertTrue(calls.size <= 8 + 1, "sent ${calls.size} times")
        assertEquals(listOf<Any>("office", 80), calls.last().args)
        assertEquals(1, calls.count { it.args[1] == 80 })
    }

    @Test
    fun theThrottleAloneNeverExceedsFourSendsPerSecond() = runTest {
        val s = setup()
        s.controller.start("main", "Office", mapOf("office" to 40))
        for (i in 1..40) {
            s.controller.drag("main", 40 + i)
            advanceTimeBy(50); runCurrent()
        }
        val calls = s.volumeCalls()
        for (c in calls) assertTrue(calls.count { it.at >= c.at && it.at < c.at + 1000 } <= 4, "from ${c.at}")
    }

    @Test
    fun aValueAlreadySentIsNotSentAgain() = runTest {
        val s = setup()
        s.controller.start("main", "Downstairs", group)
        s.controller.end("main", 70) // same as the loudest: nothing changes
        runCurrent()
        assertEquals(emptyList(), s.volumeCalls())
    }

    @Test
    fun draggingBackToTheStartRestoresTheHubValues() = runTest {
        val s = setup()
        s.controller.start("main", "Downstairs", group)
        s.controller.drag("main", 35)
        runCurrent()
        advanceTimeBy(300); runCurrent()
        s.controller.drag("main", 70)
        s.controller.end("main", 70)
        runCurrent()
        assertEquals(mapOf<Any, Any>("living" to 70, "kitchen" to 35), s.lastPerRoom())
    }

    @Test
    fun aDragRightAfterAReleaseStartsFromTheSettledTargetsAndQueuesBehindIt() = runTest {
        val s = setup(snapshot(living = 40, kitchen = 80))
        s.repo.snapshotDelayMs = 60_000 // the confirming refresh does not arrive in time
        s.session.acquire()
        runCurrent()
        s.controller.start("main", "Downstairs", mapOf("living" to 40, "kitchen" to 80))
        s.controller.end("main", 40) // 40/80 -> 20/40
        runCurrent()
        assertEquals(mapOf("living" to 20, "kitchen" to 40), s.controller.pending.value)

        // The next drag's base is the settled targets (pending overlaid on the hub values).
        s.controller.start("main", "Downstairs", s.controller.pending.value)
        s.controller.end("main", 80) // loudest back to 80 over the settled 20/40 base: 40/80
        runCurrent()
        assertEquals(mapOf<Any, Any>("living" to 40, "kitchen" to 80), s.lastPerRoom())
    }

    @Test
    fun pendingValuesBeatRefreshedHubValuesUntilARefreshThatStartedAfterTheFinalSendSucceeds() = runTest {
        val s = setup()
        // Not polling: startedSeq stays 0, which is what the final send captures.
        s.controller.start("main", "Office", mapOf("office" to 40))
        s.controller.end("main", 60)
        runCurrent()
        assertEquals(mapOf("office" to 60), s.controller.pending.value)

        // A refresh that was already running at the final send carries refreshSeq == captured: no drop.
        s.controller.onRefresh(0, setOf("living", "kitchen", "office"))
        assertEquals(mapOf("office" to 60), s.controller.pending.value)

        // A refresh that started after the send confirms it.
        s.controller.onRefresh(1, setOf("living", "kitchen", "office"))
        assertEquals(emptyMap(), s.controller.pending.value)
    }

    @Test
    fun aPendingValueIsDroppedWhenItsRoomDisappears() = runTest {
        val s = setup()
        s.controller.start("main", "Office", mapOf("office" to 40))
        s.controller.drag("main", 55)
        s.controller.end("main", 55)
        runCurrent()
        s.controller.onRefresh(s.session.startedSeq, setOf("living", "kitchen"))
        assertEquals(emptyMap(), s.controller.pending.value)
    }

    @Test
    fun aFailedSendCallsTheErrorCallbackOnceAndClearsThePendingValues() = runTest {
        val s = setup()
        s.repo.actionResult = { HubResult.Err(HubError.Rejected(404, null)) }
        s.controller.start("main", "Downstairs", group)
        s.controller.end("main", 35)
        runCurrent()
        assertEquals(listOf<Pair<String, HubError>>("Downstairs" to HubError.Rejected(404, null)), s.errors)
        assertEquals(emptyMap(), s.controller.pending.value)
    }

    @Test
    fun aMemberDragChangesOnlyThatRoomAndTheGroupMaxFollows() = runTest {
        val s = setup()
        val hub = group
        s.controller.start("member:kitchen", "Kitchen", mapOf("kitchen" to 35))
        s.controller.drag("member:kitchen", 90)
        runCurrent()
        assertEquals(setOf<List<Any>>(listOf("kitchen", 90)), s.volumeCalls().map { it.args }.toSet())
        assertEquals(mapOf("kitchen" to 90), s.controller.pending.value)
        // The group max (living 70, kitchen pending 90) moves once kitchen passes living.
        assertEquals(90, s.controller.shown(hub.keys, hub))
        s.controller.drag("member:kitchen", 50)
        assertEquals(70, s.controller.shown(hub.keys, hub))
    }

    @Test
    fun aGroupDragScalesEveryMemberByTheNewOverTheOldLoudest() = runTest {
        val s = setup()
        s.controller.start("main", "Downstairs", group)
        s.controller.drag("main", 35)
        runCurrent()
        assertEquals(setOf<List<Any>>(listOf("living", 35), listOf("kitchen", 18)), s.volumeCalls().map { it.args }.toSet())
        assertEquals(mapOf("living" to 35, "kitchen" to 18), s.controller.pending.value)
    }

    @Test
    fun aGroupWithAllMembersAtZeroSetsEveryoneToTheNewValue() = runTest {
        val s = setup()
        s.controller.start("main", "Downstairs", mapOf("living" to 0, "kitchen" to 0))
        s.controller.end("main", 40)
        runCurrent()
        assertEquals(setOf<List<Any>>(listOf("living", 40), listOf("kitchen", 40)), s.volumeCalls().map { it.args }.toSet())
    }

    @Test
    fun aMemberDragRightAfterAGroupReleaseUsesTheGroupsSettledTargetAsItsBase() = runTest {
        val s = setup()
        s.session.acquire()
        runCurrent()
        s.repo.snapshotDelayMs = 60_000
        s.controller.start("main", "Downstairs", group)
        s.controller.end("main", 35) // living 35, kitchen 18
        runCurrent()
        // The member pill's base is its hub value overlaid with the pending target.
        val base = mapOf("kitchen" to s.controller.pending.value.getValue("kitchen"))
        s.controller.start("member:kitchen", "Kitchen", base)
        s.controller.end("member:kitchen", 18) // already sent: nothing new to send
        runCurrent()
        assertEquals(mapOf<Any, Any>("living" to 35, "kitchen" to 18), s.lastPerRoom())
        assertEquals(1, s.volumeCalls().count { it.args[0] == "kitchen" })
    }

    @Test
    fun noCodePathRequestsTheGroupVolumeEndpoint() = runTest {
        val s = setup()
        s.controller.start("main", "Downstairs", group)
        s.controller.drag("main", 35)
        s.controller.end("main", 50)
        runCurrent()
        assertTrue(s.repo.calls.none { it.name.contains("Group", ignoreCase = true) })
    }

    @Test
    fun clearDropsEverythingForANewHub() = runTest {
        val s = setup()
        s.controller.start("main", "Office", mapOf("office" to 40))
        s.controller.end("main", 60)
        runCurrent()
        s.controller.clear()
        assertEquals(emptyMap(), s.controller.pending.value)
    }
}
