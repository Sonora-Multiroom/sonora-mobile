package ai.sonora.mobile.data

import ai.sonora.mobile.hub.generated.apis.OutputsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class HttpClientsTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problem = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private suspend fun call(handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HubResult<*> {
        val client = createHubHttpClient(MockEngine { handler(it) })
        return hubCall { OutputsApi("http://hub:8080", client).listOutputs(includeDisabled = true) }
    }

    @Test
    fun ioFailureIsUnreachable() = runTest {
        val result = call { throw IOException("connection refused") }
        assertEquals(HubResult.Err(HubError.Unreachable), result)
    }

    @Test
    fun connectTimeoutIsUnreachable() = runTest {
        val result = call { throw ConnectTimeoutException("connect timeout") }
        assertEquals(HubResult.Err(HubError.Unreachable), result)
    }

    @Test
    fun requestTimeoutIsUnreachable() = runTest {
        val result = call { throw HttpRequestTimeoutException("http://hub:8080", 3000) }
        assertEquals(HubResult.Err(HubError.Unreachable), result)
    }

    @Test
    fun problemDetailsBecomeRejectedWithType() = runTest {
        val body = """{"type":"urn:multiroom:error:not-found","title":"Not Found","status":404,"detail":"x"}"""
        val result = call { respond(body, HttpStatusCode.NotFound, problem) }
        assertEquals(HubResult.Err(HubError.Rejected(404, "urn:multiroom:error:not-found")), result)
    }

    @Test
    fun nonJsonErrorBodyIsRejectedWithoutType() = runTest {
        val result = call { respond("<html>oops</html>", HttpStatusCode.InternalServerError) }
        assertEquals(HubResult.Err(HubError.Rejected(500, null)), result)
    }

    @Test
    fun undecodableSuccessBodyIsUnexpected() = runTest {
        val result = call { respond("{not json", HttpStatusCode.OK, json) }
        assertIs<HubResult.Err>(result)
        assertEquals(HubError.Unexpected, result.error)
    }

    @Test
    fun successDecodes() = runTest {
        val result = call { respond("""[{"outputId":"a","volume":5}]""", HttpStatusCode.OK, json) }
        assertIs<HubResult.Ok<*>>(result)
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        assertFailsWith<CancellationException> {
            call { throw CancellationException("cancelled") }
        }
    }
}
