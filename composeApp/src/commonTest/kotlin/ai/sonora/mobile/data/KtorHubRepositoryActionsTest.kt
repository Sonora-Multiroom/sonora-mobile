package ai.sonora.mobile.data

import ai.sonora.mobile.domain.HubAddress
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
}
