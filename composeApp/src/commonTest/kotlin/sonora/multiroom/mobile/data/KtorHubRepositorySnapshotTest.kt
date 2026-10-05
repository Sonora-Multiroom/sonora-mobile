package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.Group
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.JoinMode
import sonora.multiroom.mobile.domain.Room
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.RouteStatus
import sonora.multiroom.mobile.domain.Source
import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.domain.SourceOrigin
import sonora.multiroom.mobile.domain.Target
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorHubRepositorySnapshotTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val address = HubAddress("http://hub:8080")

    private class Hub(
        var outputs: String = Fixtures.OUTPUTS,
        var groups: String = Fixtures.GROUPS,
        var routes: String = Fixtures.ROUTES,
        var inputs: String = Fixtures.INPUTS,
        var masterMute: String = Fixtures.MASTER_MUTE,
        var failPath: String? = null,
        var failWith: (() -> Nothing)? = null,
        var failStatus: HttpStatusCode? = null,
    ) {
        val requests = mutableListOf<HttpRequestData>()
    }

    private fun repository(hub: Hub): KtorHubRepository {
        val headers = jsonHeaders
        val engine = MockEngine { request ->
            hub.requests += request
            val path = request.url.encodedPath
            if (path == hub.failPath) {
                hub.failWith?.invoke()
                return@MockEngine respond("oops", hub.failStatus ?: HttpStatusCode.InternalServerError)
            }
            val body = when (path) {
                "/api/v2/outputs" -> hub.outputs
                "/api/v2/groups" -> hub.groups
                "/api/v2/routes" -> hub.routes
                "/api/v2/inputs" -> hub.inputs
                "/api/v2/master-mute" -> hub.masterMute
                else -> error("unexpected request $path")
            }
            respond(body, HttpStatusCode.OK, headers)
        }
        return KtorHubRepository(address, createHubHttpClient(engine))
    }

    private suspend fun snapshotOf(hub: Hub): HubSnapshot {
        val result = repository(hub).snapshot()
        assertIs<HubResult.Ok<HubSnapshot>>(result, result.toString())
        return result.value
    }

    @Test
    fun decodesTheFullSnapshot() = runTest {
        val s = snapshotOf(Hub())
        assertEquals(
            listOf(
                Room("living", "Living Room", 70, muted = false, enabled = true, available = true),
                Room("kitchen", "Kitchen", 55, muted = true, enabled = true, available = true),
                Room("patio", "Patio", 10, muted = false, enabled = false, available = false),
            ),
            s.rooms,
        )
        assertEquals(listOf(Group("downstairs", "Downstairs", listOf("living", "kitchen"), muted = false, enabled = true)), s.groups)
        assertEquals(
            listOf(
                Route("r1", "radio", Target.Group("downstairs"), RouteStatus.Active, paused = false, pauseable = false, transferable = true, joinMode = JoinMode.Unknown),
                Route("r2", "playlist", Target.Room("patio"), RouteStatus.Starting, paused = true, pauseable = true, transferable = false, joinMode = JoinMode.Unknown),
            ),
            s.routes,
        )
        assertEquals(
            listOf(
                Source("radio", "Radio Paradise", "http://radio.example/stream", SourceOrigin.Configured, false, true, SourceKind.Stream),
                Source("playlist", "Morning playlist", "file:///music/morning.m3u", SourceOrigin.Runtime, true, true, SourceKind.Link),
            ),
            s.sources,
        )
        assertEquals(true, s.masterMuted)
    }

    @Test
    fun requestsExactlyTheFiveV2GetsWithDisabledItemsIncluded() = runTest {
        val hub = Hub()
        snapshotOf(hub)
        val seen = hub.requests.map { it.method to it.url.encodedPath }.toSet()
        assertEquals(
            setOf(
                HttpMethod.Get to "/api/v2/outputs",
                HttpMethod.Get to "/api/v2/groups",
                HttpMethod.Get to "/api/v2/routes",
                HttpMethod.Get to "/api/v2/inputs",
                HttpMethod.Get to "/api/v2/master-mute",
            ),
            seen,
        )
        assertEquals(5, hub.requests.size)
        for (path in listOf("/api/v2/outputs", "/api/v2/groups", "/api/v2/inputs")) {
            assertEquals("true", hub.requests.first { it.url.encodedPath == path }.url.parameters["includeDisabled"], path)
        }
    }

    @Test
    fun unknownFieldsAreIgnored() = runTest {
        val s = snapshotOf(Hub(outputs = """[{"outputId":"a","displayName":"A","volume":1,"futureField":{"x":1}}]"""))
        assertEquals("a", s.rooms.single().id)
    }

    @Test
    fun unknownRouteStatusBecomesUnknown() = runTest {
        val s = snapshotOf(
            Hub(routes = """[{"routeId":"r","inputId":"i","targetId":"a","targetType":"SINGLE_OUTPUT","status":"PAUSING"}]"""),
        )
        assertEquals(RouteStatus.Unknown, s.routes.single().status)
    }

    @Test
    fun unknownOrMissingTargetTypeKeepsTheRouteAsUnknownTarget() = runTest {
        val s = snapshotOf(
            Hub(
                routes = """[
                  {"routeId":"r1","inputId":"i","targetId":"z1","targetType":"ZONE","status":"ACTIVE"},
                  {"routeId":"r2","inputId":"i","targetId":"z2","status":"ACTIVE"}
                ]""",
            ),
        )
        assertEquals(listOf(Target.Unknown("z1"), Target.Unknown("z2")), s.routes.map { it.target })
    }

    @Test
    fun unknownInputSourceBecomesUnknownOrigin() = runTest {
        val s = snapshotOf(Hub(inputs = """[{"inputId":"i","displayName":"I","uri":"http://x","source":"DYNAMIC"}]"""))
        assertEquals(SourceOrigin.Unknown, s.sources.single().origin)
        assertEquals(SourceKind.Stream, s.sources.single().kind)
    }

    @Test
    fun missingOptionalFieldsTakeTheirDefaults() = runTest {
        val s = snapshotOf(
            Hub(
                outputs = """[{"outputId":"a"},{"outputId":"b","volume":150},{"outputId":" "},{"displayName":"nameless"}]""",
                groups = """[{"groupId":"g"}]""",
                routes = """[{"routeId":"r","targetId":"a","targetType":"SINGLE_OUTPUT"}]""",
                inputs = """[{"inputId":"i"}]""",
                masterMute = """{}""",
            ),
        )
        assertEquals(Room("a", "a", 0, muted = false, enabled = true, available = true), s.rooms[0])
        assertEquals(100, s.rooms[1].volume)
        assertEquals(listOf("a", "b"), s.rooms.map { it.id })
        assertEquals(Group("g", "g", emptyList(), muted = false, enabled = true), s.groups.single())
        assertEquals(Route("r", "", Target.Room("a"), RouteStatus.Unknown, paused = false, pauseable = false, transferable = false, joinMode = JoinMode.Unknown), s.routes.single())
        assertEquals(Source("i", "i", null, SourceOrigin.Unknown, false, true, SourceKind.LineIn), s.sources.single())
        assertEquals(false, s.masterMuted)
    }

    @Test
    fun oneFailingGetFailsTheWholeSnapshot() = runTest {
        for (path in listOf("/api/v2/outputs", "/api/v2/groups", "/api/v2/routes", "/api/v2/inputs", "/api/v2/master-mute")) {
            val http500 = repository(Hub(failPath = path)).snapshot()
            assertEquals(HubResult.Err(HubError.Rejected(500, null)), http500, path)
            val io = repository(Hub(failPath = path, failWith = { throw IOException("down") })).snapshot()
            assertEquals(HubResult.Err(HubError.Unreachable), io, path)
        }
    }

    @Test
    fun routeJoinModesMapAndUnknownValuesNeverFailTheSnapshot() = runTest {
        fun route(id: String, mode: String?) =
            """{"routeId":"$id","inputId":"i","targetId":"a","targetType":"SINGLE_OUTPUT","status":"ACTIVE"${mode?.let { ""","joinMode":"$it"""" } ?: ""}}"""
        val s = snapshotOf(
            Hub(
                routes = "[" + listOf(
                    route("1", "REPLACE"), route("2", "MIX"), route("3", "DUCK_OTHERS"), route("4", null), route("5", "SOMETHING_NEW"),
                ).joinToString(",") + "]",
            ),
        )
        assertEquals(
            listOf(JoinMode.Replace, JoinMode.Mix, JoinMode.Announcement, JoinMode.Unknown, JoinMode.Unknown),
            s.routes.map { it.joinMode },
        )
    }

    @Test
    fun sourceDefaultJoinModesMapAndUnknownValuesBecomeNull() = runTest {
        fun input(id: String, mode: String?) =
            """{"inputId":"$id","displayName":"$id","uri":"http://x"${mode?.let { ""","defaultJoinMode":"$it"""" } ?: ""}}"""
        val s = snapshotOf(
            Hub(
                inputs = "[" + listOf(
                    input("1", "REPLACE"), input("2", "MIX"), input("3", "DUCK_OTHERS"), input("4", null), input("5", "SOMETHING_NEW"),
                ).joinToString(",") + "]",
            ),
        )
        assertEquals(
            listOf(JoinMode.Replace, JoinMode.Mix, JoinMode.Announcement, null, null),
            s.sources.map { it.defaultJoinMode },
        )
    }

    // ---- 004: autoRemove and createdAt (contract test 8) -------------------------------------

    private suspend fun sourceFrom(extra: String): Source {
        val hub = Hub(inputs = """[{"inputId":"x","displayName":"X","uri":"http://x","source":"EPHEMERAL"$extra}]""")
        return snapshotOf(hub).sources.single()
    }

    @Test
    fun autoRemoveIsReadAndMissingMeansFalse() = kotlinx.coroutines.test.runTest {
        assertEquals(true, sourceFrom(""","autoRemove":true""").autoRemove)
        assertEquals(false, sourceFrom(""","autoRemove":false""").autoRemove)
        assertEquals(false, sourceFrom("").autoRemove)
    }

    @Test
    fun createdAtIsParsedAndAnythingUnreadableIsNull() = kotlinx.coroutines.test.runTest {
        assertEquals(kotlin.time.Instant.parse("2026-10-04T12:30:00Z"), sourceFrom(""","createdAt":"2026-10-04T12:30:00Z"""").createdAt)
        for (extra in listOf("", ""","createdAt":null""", ",\"createdAt\":\"\"", ",\"createdAt\":\"yesterday\"")) {
            assertEquals(null, sourceFrom(extra).createdAt, extra)
        }
    }
}
