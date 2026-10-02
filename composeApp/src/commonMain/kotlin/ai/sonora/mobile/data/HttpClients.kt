package ai.sonora.mobile.data

import ai.sonora.mobile.hub.generated.infrastructure.HttpResponse
import ai.sonora.mobile.hub.generated.models.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

/** The engine for this platform: OkHttp on Android, Darwin on iOS. */
expect fun httpEngine(): HttpClientEngineFactory<*>

/** Lenient so one unrecognised value from a newer hub never fails a whole response (R4). */
internal val HubJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

internal const val HUB_TIMEOUT_MILLIS = 3000L

/** No retries (the poll loop is the retry) and no auth; status codes are mapped explicitly. */
fun createHubHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = HUB_TIMEOUT_MILLIS
        connectTimeoutMillis = HUB_TIMEOUT_MILLIS
        socketTimeoutMillis = HUB_TIMEOUT_MILLIS
    }
    install(ContentNegotiation) { json(HubJson) }
}

/** Runs a generated call and returns its decoded body. */
internal suspend fun <R : Any> hubCall(block: suspend () -> HttpResponse<R>): HubResult<R> =
    guarded {
        val response = block()
        if (response.success) HubResult.Ok(response.body()) else HubResult.Err(response.rejection())
    }

/** Like [hubCall] for actions whose response body is ignored: the next refresh confirms them. */
internal suspend fun hubCallUnit(block: suspend () -> HttpResponse<*>): HubResult<Unit> =
    guarded {
        val response = block()
        if (response.success) HubResult.Ok(Unit) else HubResult.Err(response.rejection())
    }

private suspend fun <T> guarded(block: suspend () -> HubResult<T>): HubResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        HubResult.Err(HubError.Unreachable)
    } catch (e: IOException) {
        // Connect/socket timeouts, refused, reset and unresolved hosts all land here.
        HubResult.Err(HubError.Unreachable)
    } catch (e: Exception) {
        HubResult.Err(HubError.Unexpected)
    }

/**
 * The problem's `type` is kept when the body is RFC 7807; its `title`/`detail` never leave this
 * function, so hub wording cannot reach the UI (Constitution V).
 */
private suspend fun HttpResponse<*>.rejection(): HubError.Rejected {
    val type = try {
        typedBody<ErrorResponse>(io.ktor.util.reflect.typeInfo<ErrorResponse>()).type
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    return HubError.Rejected(status, type)
}
