package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.JoinMode
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Target
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorHubRepositoryActionsTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problem = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private class Recorded(val method: HttpMethod, val path: String, val body: String)

    private fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"ok":true}""",
        headers: io.ktor.http.Headers = json,
        log: MutableList<Recorded> = mutableListOf(),
    ): Pair<KtorHubRepository, MutableList<Recorded>> {
        val engine = MockEngine { request: HttpRequestData ->
            log += Recorded(request.method, request.url.encodedPath, request.body.toByteArray().decodeToString())
            respond(body, status, headers)
        }
        return KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine)) to log
    }

    @Test
    fun setRoomVolumePutsTheVolume() = runTest {
        val (repo, log) = repository(body = """{"outputId":"kitchen","volume":42}""")
        assertEquals(HubResult.Ok(Unit), repo.setRoomVolume("kitchen", 42))
        val r = log.single()
        assertEquals(HttpMethod.Put, r.method)
        assertEquals("/api/v2/outputs/kitchen/volume", r.path)
        assertEquals("""{"volume":42}""", r.body)
    }

    @Test
    fun setRoomVolumeClampsTo100() = runTest {
        val (repo, log) = repository(body = """{"outputId":"x","volume":100}""")
        repo.setRoomVolume("x", 130)
        assertEquals("""{"volume":100}""", log.single().body)
    }

    @Test
    fun setRoomVolumeClampsToZero() = runTest {
        val (repo, log) = repository(body = """{"outputId":"x","volume":0}""")
        repo.setRoomVolume("x", -4)
        assertEquals("""{"volume":0}""", log.single().body)
    }

    @Test
    fun stopRouteDeletesTheRoute() = runTest {
        val (repo, log) = repository(status = HttpStatusCode.NoContent, body = "")
        assertEquals(HubResult.Ok(Unit), repo.stopRoute("r1"))
        val r = log.single()
        assertEquals(HttpMethod.Delete, r.method)
        assertEquals("/api/v2/routes/r1", r.path)
    }

    @Test
    fun setRoutePausedPutsThePauseState() = runTest {
        val (repo, log) = repository(body = """{"routeId":"r1","paused":true}""")
        assertEquals(HubResult.Ok(Unit), repo.setRoutePaused("r1", true))
        val r = log.single()
        assertEquals(HttpMethod.Put, r.method)
        assertEquals("/api/v2/routes/r1/pause", r.path)
        assertEquals("""{"paused":true}""", r.body)
    }

    @Test
    fun setMasterMutePutsTheMuteState() = runTest {
        val (repo, log) = repository(body = """{"muted":true}""")
        assertEquals(HubResult.Ok(Unit), repo.setMasterMute(true))
        val r = log.single()
        assertEquals(HttpMethod.Put, r.method)
        assertEquals("/api/v2/master-mute", r.path)
        assertEquals("""{"muted":true}""", r.body)
    }

    @Test
    fun problemDetailsBecomeRejectedForEveryAction() = runTest {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.UnprocessableEntity)) {
            val (repo, _) = repository(status = status, body = Fixtures.PROBLEM_NOT_FOUND, headers = problem)
            val expected = HubResult.Err(HubError.Rejected(status.value, "urn:multiroom:error:not-found"))
            assertEquals(expected, repo.setRoomVolume("a", 1))
            assertEquals(expected, repo.stopRoute("r"))
            assertEquals(expected, repo.setRoutePaused("r", true))
            assertEquals(expected, repo.setMasterMute(true))
        }
    }

    @Test
    fun nonJsonErrorBodyIsRejectedWithoutType() = runTest {
        val (repo, _) = repository(status = HttpStatusCode.BadGateway, body = "<html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        assertEquals(HubResult.Err(HubError.Rejected(502, null)), repo.stopRoute("r"))
    }

    // ---- Mute and transfer (002, contracts/hub-repository.md) -------------------------------

    @Test
    fun setRoomMutePutsTheMuteStateForTrueAndFalse() = runTest {
        for (muted in listOf(true, false)) {
            val (repo, log) = repository(body = """{"outputId":"bedroom","muted":$muted}""")
            assertEquals(HubResult.Ok(Unit), repo.setRoomMute("bedroom", muted))
            val r = log.single()
            assertEquals(HttpMethod.Put, r.method)
            assertEquals("/api/v2/outputs/bedroom/mute", r.path)
            assertEquals("""{"muted":$muted}""", r.body)
        }
    }

    @Test
    fun setGroupMutePutsTheMuteStateForTrueAndFalse() = runTest {
        for (muted in listOf(true, false)) {
            val (repo, log) = repository(body = """{"groupId":"downstairs","muted":$muted}""")
            assertEquals(HubResult.Ok(Unit), repo.setGroupMute("downstairs", muted))
            val r = log.single()
            assertEquals(HttpMethod.Put, r.method)
            assertEquals("/api/v2/groups/downstairs/mute", r.path)
            assertEquals("""{"muted":$muted}""", r.body)
        }
    }

    private val movedRoute = """{"routeId":"r2","inputId":"radio","targetId":"kitchen","targetType":"SINGLE_OUTPUT",
        "status":"ACTIVE","transferable":true,"pauseable":false,"paused":false}"""

    @Test
    fun transferToARoomSendsSingleOutputAndReturnsTheNewRoute() = runTest {
        val (repo, log) = repository(body = movedRoute)
        val result = repo.transferRoute("r1", Target.Room("kitchen"))
        assertEquals(
            HubResult.Ok(Route("r2", "radio", Target.Room("kitchen"), RouteStatus.Active, false, false, true, JoinMode.Unknown)),
            result,
        )
        val r = log.single()
        assertEquals(HttpMethod.Post, r.method)
        assertEquals("/api/v2/routes/r1/transfer", r.path)
        assertEquals("""{"targetId":"kitchen","targetType":"SINGLE_OUTPUT"}""", r.body)
    }

    @Test
    fun transferToAGroupSendsOutputGroup() = runTest {
        val (repo, log) = repository(body = movedRoute.replace("kitchen", "downstairs").replace("SINGLE_OUTPUT", "OUTPUT_GROUP"))
        val result = repo.transferRoute("r1", Target.Group("downstairs"))
        assertEquals(Target.Group("downstairs"), (result as HubResult.Ok).value.target)
        assertEquals("""{"targetId":"downstairs","targetType":"OUTPUT_GROUP"}""", log.single().body)
    }

    @Test
    fun transferProblemDetailsBecomeRejected() = runTest {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.NotFound, HttpStatusCode.UnprocessableEntity)) {
            val (repo, _) = repository(status = status, body = Fixtures.PROBLEM_NOT_FOUND, headers = problem)
            assertEquals(
                HubResult.Err(HubError.Rejected(status.value, "urn:multiroom:error:not-found")),
                repo.transferRoute("r1", Target.Room("kitchen")),
            )
        }
    }

    @Test
    fun transferWithAnIoFailureIsUnreachable() = runTest {
        val engine = MockEngine { throw kotlinx.io.IOException("down") }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(HubResult.Err(HubError.Unreachable), repo.transferRoute("r1", Target.Room("kitchen")))
    }

    @Test
    fun transferWithAGarbageBodyIsUnexpected() = runTest {
        val (repo, _) = repository(body = "not json at all")
        assertEquals(HubResult.Err(HubError.Unexpected), repo.transferRoute("r1", Target.Room("kitchen")))
    }

    @Test
    fun transferToAnUnknownTargetThrowsAndSendsNothing() = runTest {
        val (repo, log) = repository(body = movedRoute)
        assertFailsWith<IllegalArgumentException> { repo.transferRoute("r1", Target.Unknown("x")) }
        assertTrue(log.isEmpty())
    }

    // ---- Admission refusals (003, research R6) ----------------------------------------------

    @Test
    fun admissionRefusalsKeepReasonAndOutputIdOn409And422() = runTest {
        val body = """{"type":"urn:multiroom:error:conflict","status":409,"reason":"ROUTE_LIMIT_REACHED","outputId":"kitchen"}"""
        for (status in listOf(HttpStatusCode.Conflict, HttpStatusCode.UnprocessableEntity)) {
            val (repo, _) = repository(status = status, body = body, headers = problem)
            assertEquals(
                HubResult.Err(HubError.Rejected(status.value, "urn:multiroom:error:conflict", "ROUTE_LIMIT_REACHED", "kitchen")),
                repo.transferRoute("r1", Target.Room("kitchen")),
            )
        }
    }

    @Test
    fun problemWithoutReasonHasNullReasonAndOutputId() = runTest {
        val (repo, _) = repository(status = HttpStatusCode.NotFound, body = Fixtures.PROBLEM_NOT_FOUND, headers = problem)
        assertEquals(
            HubResult.Err(HubError.Rejected(404, "urn:multiroom:error:not-found", null, null)),
            repo.transferRoute("r1", Target.Room("kitchen")),
        )
    }

    @Test
    fun nonJsonErrorBodyHasNoReasonEither() = runTest {
        val (repo, _) = repository(status = HttpStatusCode.Conflict, body = "<html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        assertEquals(HubResult.Err(HubError.Rejected(409, null, null, null)), repo.transferRoute("r1", Target.Room("kitchen")))
    }

    // ---- Start a configured source (003, contract tests 1, 3, 4, 5, 6) -----------------------

    private val startedRoute = """{"routeId":"r9","inputId":"jazz","targetId":"bedroom","targetType":"SINGLE_OUTPUT",
        "status":"STARTING","transferable":true,"pauseable":false,"paused":false,"joinMode":"REPLACE"}"""

    private val r9 = Route("r9", "jazz", Target.Room("bedroom"), RouteStatus.Starting, false, false, true, JoinMode.Replace)

    @Test
    fun startSourceOnARoomSendsExactlyTheDocumentedRequest() = runTest {
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.OK)) {
            val (repo, log) = repository(status = status, body = startedRoute)
            assertEquals(HubResult.Ok(r9), repo.startSource("jazz", Target.Room("bedroom")))
            val r = log.single()
            assertEquals(HttpMethod.Post, r.method)
            assertEquals("/api/v2/routes", r.path)
            assertEquals("""{"inputId":"jazz","targetId":"bedroom","targetType":"SINGLE_OUTPUT"}""", r.body)
        }
    }

    @Test
    fun startSourceOnAGroupSendsOutputGroup() = runTest {
        val (repo, log) = repository(status = HttpStatusCode.Created, body = startedRoute.replace("bedroom", "down").replace("SINGLE_OUTPUT", "OUTPUT_GROUP"))
        val result = repo.startSource("jazz", Target.Group("down"))
        assertEquals(Target.Group("down"), (result as HubResult.Ok).value.target)
        assertEquals("""{"inputId":"jazz","targetId":"down","targetType":"OUTPUT_GROUP"}""", log.single().body)
    }

    @Test
    fun startSourceProblemDetailsBecomeRejectedWithReasonAndOutputId() = runTest {
        val refusal = """{"type":"urn:multiroom:error:conflict","status":409,"reason":"INPUT_ALREADY_ON_OUTPUT","outputId":"bedroom"}"""
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.NotFound, HttpStatusCode.UnprocessableEntity)) {
            val (repo, _) = repository(status = status, body = Fixtures.PROBLEM_NOT_FOUND, headers = problem)
            assertEquals(
                HubResult.Err(HubError.Rejected(status.value, "urn:multiroom:error:not-found")),
                repo.startSource("jazz", Target.Room("bedroom")),
            )
        }
        val (repo, _) = repository(status = HttpStatusCode.UnprocessableEntity, body = refusal, headers = problem)
        assertEquals(
            HubResult.Err(HubError.Rejected(422, "urn:multiroom:error:conflict", "INPUT_ALREADY_ON_OUTPUT", "bedroom")),
            repo.startSource("jazz", Target.Room("bedroom")),
        )
    }

    @Test
    fun startSourceWithAnIoFailureIsUnreachable() = runTest {
        val engine = MockEngine { throw kotlinx.io.IOException("down") }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(HubResult.Err(HubError.Unreachable), repo.startSource("jazz", Target.Room("bedroom")))
    }

    @Test
    fun startSourceAnsweringAfterTheShortTimeoutIsUnreachable() = runTest {
        val engine = MockEngine {
            kotlinx.coroutines.delay(4_000)
            respond(startedRoute, HttpStatusCode.Created, json)
        }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(HubResult.Err(HubError.Unreachable), repo.startSource("jazz", Target.Room("bedroom")))
    }

    @Test
    fun startSourceWithAGarbageBodyIsUnexpected() = runTest {
        val (repo, _) = repository(status = HttpStatusCode.Created, body = "not json at all")
        assertEquals(HubResult.Err(HubError.Unexpected), repo.startSource("jazz", Target.Room("bedroom")))
    }

    @Test
    fun startSourceOnAnUnknownTargetThrowsAndSendsNothing() = runTest {
        val (repo, log) = repository(body = startedRoute)
        assertFailsWith<IllegalArgumentException> { repo.startSource("jazz", Target.Unknown("x")) }
        assertTrue(log.isEmpty())
    }

    // ---- Play a link (003, contract tests 2, 3, 4, 5) ------------------------------------------

    private val playedLink = """{"inputId":"link-1","message":"ok","route":$startedRoute}"""

    @Test
    fun playLinkSendsOnlyUriTargetIdAndTargetType() = runTest {
        val (repo, log) = repository(body = playedLink)
        assertEquals(HubResult.Ok(r9), repo.playLink("https://soundcloud.com/a/b", Target.Room("bedroom")))
        val r = log.single()
        assertEquals(HttpMethod.Post, r.method)
        assertEquals("/api/v2/play", r.path)
        assertEquals("""{"uri":"https://soundcloud.com/a/b","targetId":"bedroom","targetType":"SINGLE_OUTPUT"}""", r.body)
    }

    @Test
    fun playLinkOnAGroupSendsOutputGroup() = runTest {
        val (repo, log) = repository(body = playedLink)
        repo.playLink("https://x.y", Target.Group("down"))
        assertEquals("""{"uri":"https://x.y","targetId":"down","targetType":"OUTPUT_GROUP"}""", log.single().body)
    }

    @Test
    fun playLinkWithoutARouteIsUnexpected() = runTest {
        val (repo, _) = repository(body = """{"inputId":"link-1","message":"ok"}""")
        assertEquals(HubResult.Err(HubError.Unexpected), repo.playLink("https://x.y", Target.Room("bedroom")))
    }

    @Test
    fun playLinkProblemDetailsBecomeRejected() = runTest {
        for (status in listOf(400, 404, 422, 502, 503)) {
            val (repo, _) = repository(status = HttpStatusCode.fromValue(status), body = Fixtures.PROBLEM_NOT_FOUND, headers = problem)
            assertEquals(
                HubResult.Err(HubError.Rejected(status, "urn:multiroom:error:not-found")),
                repo.playLink("https://x.y", Target.Room("bedroom")),
                status.toString(),
            )
        }
        val refusal = """{"type":"urn:multiroom:error:conflict","reason":"ROUTE_LIMIT_REACHED","outputId":"kitchen"}"""
        val (repo, _) = repository(status = HttpStatusCode.Conflict, body = refusal, headers = problem)
        assertEquals(
            HubResult.Err(HubError.Rejected(409, "urn:multiroom:error:conflict", "ROUTE_LIMIT_REACHED", "kitchen")),
            repo.playLink("https://x.y", Target.Room("bedroom")),
        )
    }

    @Test
    fun playLinkWaitsLongerThanTheShortTimeout() = runTest {
        val engine = MockEngine {
            kotlinx.coroutines.delay(10_000)
            respond(playedLink, HttpStatusCode.OK, json)
        }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(HubResult.Ok(r9), repo.playLink("https://x.y", Target.Room("bedroom")))
    }

    @Test
    fun playLinkGivesUpAfterThirtySeconds() = runTest {
        val engine = MockEngine {
            kotlinx.coroutines.delay(31_000)
            respond(playedLink, HttpStatusCode.OK, json)
        }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(HubResult.Err(HubError.Unreachable), repo.playLink("https://x.y", Target.Room("bedroom")))
    }

    @Test
    fun playLinkOnAnUnknownTargetThrowsAndSendsNothing() = runTest {
        val (repo, log) = repository(body = playedLink)
        assertFailsWith<IllegalArgumentException> { repo.playLink("https://x.y", Target.Unknown("x")) }
        assertTrue(log.isEmpty())
    }
}
