package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.HubAddress
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
import kotlin.test.assertTrue

/** Contract tests 1–8 of specs/004-settings-sources/contracts/hub-repository.md. */
class KtorHubRepositorySettingsTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problem = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private class Recorded(val method: HttpMethod, val path: String, val query: String, val body: String)

    private fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        headers: io.ktor.http.Headers = json,
        log: MutableList<Recorded> = mutableListOf(),
    ): Pair<KtorHubRepository, MutableList<Recorded>> {
        val engine = MockEngine { request: HttpRequestData ->
            log += Recorded(request.method, request.url.encodedPath, request.url.encodedQuery, request.body.toByteArray().decodeToString())
            respond(body, status, headers)
        }
        return KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine)) to log
    }

    private suspend fun KtorHubRepository.enableCalls(id: String, enabled: Boolean) = listOf(
        setRoomEnabled(id, enabled),
        setGroupEnabled(id, enabled),
        setSourceEnabled(id, enabled),
    )

    // ---- 1: the three PUTs ------------------------------------------------------------------

    @Test
    fun theEnabledCallsPutExactlyTheEnabledFlag() = runTest {
        for (enabled in listOf(false, true)) {
            val (repo, log) = repository(body = """{"outputId":"a","enabled":$enabled}""")
            assertEquals(List(3) { HubResult.Ok(Unit) }, repo.enableCalls("a", enabled))
            assertEquals(listOf("/api/v2/outputs/a/enabled", "/api/v2/groups/a/enabled", "/api/v2/inputs/a/enabled"), log.map { it.path })
            for (r in log) {
                assertEquals(HttpMethod.Put, r.method)
                assertEquals("""{"enabled":$enabled}""", r.body)
            }
        }
    }

    @Test
    fun anEmptyBodyIsStillOk() = runTest {
        val (repo, _) = repository(status = HttpStatusCode.NoContent, body = "")
        assertEquals(List(3) { HubResult.Ok(Unit) }, repo.enableCalls("a", true))
    }

    // ---- 2: encoding ------------------------------------------------------------------------

    @Test
    fun anIdNeedingEncodingArrivesEncoded() = runTest {
        val (repo, log) = repository()
        // The generated client splits its path on "/", so a slash in an id is not supported (hub ids
        // are slugs); spaces are encoded.
        repo.enableCalls("a b", true)
        assertEquals(
            listOf("/api/v2/outputs/a%20b/enabled", "/api/v2/groups/a%20b/enabled", "/api/v2/inputs/a%20b/enabled"),
            log.map { it.path },
        )
    }

    // ---- 6: errors --------------------------------------------------------------------------

    @Test
    fun problemDetailsBecomeRejected() = runTest {
        val (repo, _) = repository(status = HttpStatusCode.NotFound, body = Fixtures.PROBLEM_NOT_FOUND, headers = problem)
        val expected = HubResult.Err(HubError.Rejected(404, "urn:multiroom:error:not-found", null, null))
        assertEquals(List(3) { expected }, repo.enableCalls("a", false))
    }

    @Test
    fun anIoFailureIsUnreachable() = runTest {
        val engine = MockEngine { throw kotlinx.io.IOException("down") }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(List(3) { HubResult.Err(HubError.Unreachable) }, repo.enableCalls("a", false))
    }

    // ---- 7: never group volume --------------------------------------------------------------

    @Test
    fun neverCallsGroupVolume() = runTest {
        val (repo, log) = repository()
        repo.enableCalls("g", false)
        assertTrue(log.none { it.path.endsWith("/volume") })
    }
}
