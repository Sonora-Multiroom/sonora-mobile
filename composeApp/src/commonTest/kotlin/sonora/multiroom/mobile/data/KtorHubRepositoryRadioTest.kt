package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.Country
import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.Language
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.domain.StationOrder
import sonora.multiroom.mobile.domain.StationPage
import sonora.multiroom.mobile.domain.StationQuery
import sonora.multiroom.mobile.domain.Tag
import sonora.multiroom.mobile.domain.TagPage
import sonora.multiroom.mobile.domain.Target
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorHubRepositoryRadioTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problem = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private class Recorded(val method: HttpMethod, val path: String, val query: Map<String, List<String>>, val body: String)

    private fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}",
        headers: io.ktor.http.Headers = json,
        log: MutableList<Recorded> = mutableListOf(),
    ): Pair<KtorHubRepository, MutableList<Recorded>> {
        val engine = MockEngine { request: HttpRequestData ->
            log += Recorded(
                request.method,
                request.url.encodedPath,
                request.url.parameters.entries().associate { it.key to it.value },
                request.body.toByteArray().decodeToString(),
            )
            respond(body, status, headers)
        }
        return KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine)) to log
    }

    private fun station(extra: String = "") = """{"uuid":"u1","uri":"radiobrowser://u1","languages":[],"tags":[],"hls":false,
        "lastCheckOk":true,"votes":1,"clickCount":2$extra}"""

    private val emptyPage = """{"stations":[],"nextOffset":null}"""
    private val query = StationQuery(null, null, null, null, StationOrder.MostVoted)

    // 1
    @Test
    fun aPlainSearchSendsOnlyLimitOffsetAndOrder() = runTest {
        val (repo, log) = repository(body = emptyPage)
        assertEquals(HubResult.Ok(StationPage(emptyList(), null)), repo.searchStations(query, 0))
        val r = log.single()
        assertEquals(HttpMethod.Get, r.method)
        assertEquals("/api/radio/stations", r.path)
        assertEquals(mapOf("limit" to listOf("50"), "offset" to listOf("0"), "order" to listOf("votes")), r.query)
    }

    // 2
    @Test
    fun everyFilterIsSentOnceAndOrderIsMapped() = runTest {
        val (repo, log) = repository(body = emptyPage)
        repo.searchStations(StationQuery("mango", "DE", "jazz", "german", StationOrder.Trending), 50)
        assertEquals(
            mapOf(
                "name" to listOf("mango"), "countryCode" to listOf("DE"), "tag" to listOf("jazz"),
                "language" to listOf("german"), "order" to listOf("clickCount"), "offset" to listOf("50"),
                "limit" to listOf("50"),
            ),
            log.single().query,
        )
        repo.searchStations(StationQuery(null, null, null, null, StationOrder.NameAZ), 0)
        assertEquals(listOf("name"), log.last().query["order"])
        for (never in listOf("direction", "codec", "bitrateMin")) assertNull(log.last().query[never])
    }

    // 3
    @Test
    fun aNameWithSpacesAmpersandAndNonAsciiIsPercentEncoded() = runTest {
        val (repo, log) = repository(body = emptyPage)
        repo.searchStations(StationQuery("Хіт FM & co", null, null, null, StationOrder.MostVoted), 0)
        assertEquals(listOf("Хіт FM & co"), log.single().query["name"])
    }

    // 4
    @Test
    fun aPageMapsToStations() = runTest {
        val body = """{"stations":[
          {"uuid":"a","uri":"radiobrowser://a","name":"Kiss FM","countryCode":"UA","codec":"MP3","bitrate":128,
           "languages":["ukrainian"],"tags":["pop"],"hls":false,"lastCheckOk":true,"votes":10,"clickCount":20},
          {"uuid":"b","uri":"radiobrowser://b","name":"Radio NV","countryCode":"UA","codec":"AAC","bitrate":64,
           "languages":[],"tags":[],"hls":false,"lastCheckOk":true,"votes":1,"clickCount":2},
          {"uuid":"c","uri":"radiobrowser://c","name":"Hit FM","countryCode":"UA","codec":"MP3","bitrate":192,
           "languages":[],"tags":[],"hls":false,"lastCheckOk":true,"votes":1,"clickCount":2}],"nextOffset":50}"""
        val (repo, _) = repository(body = body)
        val page = (repo.searchStations(query, 0) as HubResult.Ok).value
        assertEquals(50, page.nextOffset)
        assertEquals(
            listOf(
                Station("a", "radiobrowser://a", "Kiss FM", "UA", "MP3", 128),
                Station("b", "radiobrowser://b", "Radio NV", "UA", "AAC", 64),
                Station("c", "radiobrowser://c", "Hit FM", "UA", "MP3", 192),
            ),
            page.stations,
        )
    }

    // 5
    @Test
    fun unknownFieldsBecomeNullAndABlankUuidIsDropped() = runTest {
        val body = """{"stations":[
          {"uuid":"a","uri":"radiobrowser://a","name":null,"countryCode":null,"codec":"UNKNOWN","bitrate":null,
           "languages":[],"tags":[],"hls":false,"lastCheckOk":true,"votes":1,"clickCount":2},
          {"uuid":"b","uri":"radiobrowser://b","name":"  ","countryCode":" ","codec":"unknown","bitrate":0,
           "languages":[],"tags":[],"hls":false,"lastCheckOk":true,"votes":1,"clickCount":2},
          {"uuid":" ","uri":"radiobrowser://x","name":"Dropped",
           "languages":[],"tags":[],"hls":false,"lastCheckOk":true,"votes":1,"clickCount":2}],"nextOffset":null}"""
        val (repo, _) = repository(body = body)
        val page = (repo.searchStations(query, 0) as HubResult.Ok).value
        assertEquals(
            listOf(
                Station("a", "radiobrowser://a", null, null, null, null),
                Station("b", "radiobrowser://b", null, null, null, null),
            ),
            page.stations,
        )
    }

    // 6
    @Test
    fun unknownExtraFieldsAreIgnored() = runTest {
        val body = """{"stations":[${station(""","extra":{"x":1},"name":"N"""")}],"nextOffset":null,"total":9}"""
        val (repo, _) = repository(body = body)
        val page = (repo.searchStations(query, 0) as HubResult.Ok).value
        assertEquals("N", page.stations.single().name)
    }

    // 7
    @Test
    fun nextOffsetNullOrMissingIsNull() = runTest {
        val (a, _) = repository(body = """{"stations":[${station()}],"nextOffset":null}""")
        assertNull((a.searchStations(query, 0) as HubResult.Ok).value.nextOffset)
        val (b, _) = repository(body = """{"stations":[${station()}]}""")
        assertNull((b.searchStations(query, 0) as HubResult.Ok).value.nextOffset)
    }

    // 8
    @Test
    fun countriesAndLanguagesAreMapped() = runTest {
        val (countries, log) = repository(
            body = """[{"code":"UA","name":"Ukraine","stationCount":2000},{"code":"","name":"No code","stationCount":1},
                {"code":"DE","name":"Germany","stationCount":6965}]""",
        )
        assertEquals(
            HubResult.Ok(listOf(Country("UA", "Ukraine", 2000), Country("DE", "Germany", 6965))),
            countries.radioCountries(),
        )
        assertEquals("/api/radio/countries", log.single().path)
        assertTrue(log.single().query.isEmpty())

        val (languages, log2) = repository(
            body = """[{"name":"german","code":"de","stationCount":500},{"name":" ","stationCount":3}]""",
        )
        assertEquals(HubResult.Ok(listOf(Language("german", 500))), languages.radioLanguages())
        assertEquals("/api/radio/languages", log2.single().path)
        assertTrue(log2.single().query.isEmpty())
    }

    // 9
    @Test
    fun tagsSendLimitOffsetAndTheNameOnlyWhenGiven() = runTest {
        val (repo, log) = repository(body = """{"tags":[{"name":"jazz","stationCount":7},{"name":"","stationCount":1}],"nextOffset":50}""")
        assertEquals(HubResult.Ok(TagPage(listOf(Tag("jazz", 7)), 50)), repo.radioTags(null, 0))
        assertEquals("/api/radio/tags", log.single().path)
        assertEquals(mapOf("limit" to listOf("50"), "offset" to listOf("0")), log.single().query)
        repo.radioTags("jazz", 50)
        assertEquals(mapOf("limit" to listOf("50"), "offset" to listOf("50"), "name" to listOf("jazz")), log.last().query)
    }

    // 10
    @Test
    fun radioProblemsKeepOnlyTheirType() = runTest {
        for ((status, type) in listOf(
            HttpStatusCode.ServiceUnavailable to "urn:multiroom:radio:error:catalogue-unavailable",
            HttpStatusCode.BadGateway to "urn:multiroom:radio:error:catalogue-bad-answer",
            HttpStatusCode.BadRequest to "urn:multiroom:radio:error:invalid-parameter",
        )) {
            val (repo, _) = repository(
                status = status,
                headers = problem,
                body = """{"type":"$type","title":"T","status":${status.value},"detail":"D","parameter":"name"}""",
            )
            assertEquals(
                HubResult.Err(HubError.Rejected(status.value, type, null, null)),
                repo.searchStations(query, 0),
                type,
            )
        }
    }

    // 11
    @Test
    fun aPlain404HasNoType() = runTest {
        val (repo, _) = repository(
            status = HttpStatusCode.NotFound,
            body = """{"timestamp":"2026-11-16T10:00:00Z","status":404,"error":"Not Found","path":"/api/radio/stations"}""",
        )
        assertEquals(HubResult.Err(HubError.Rejected(404, null, null, null)), repo.searchStations(query, 0))
    }

    // 12
    @Test
    fun aRadioNotFoundKeepsItsType() = runTest {
        val type = "urn:multiroom:radio:error:not-found"
        val (repo, _) = repository(status = HttpStatusCode.NotFound, headers = problem, body = """{"type":"$type","status":404}""")
        assertEquals(HubResult.Err(HubError.Rejected(404, type, null, null)), repo.radioCountries())
    }

    // 13
    @Test
    fun aBodyThatDoesNotDecodeIsUnexpected() = runTest {
        val html = headersOf(HttpHeaders.ContentType, "text/html")
        val (a, _) = repository(body = "<html>hi</html>", headers = html)
        assertEquals(HubResult.Err(HubError.Unexpected), a.searchStations(query, 0))
        val (b, _) = repository(body = """{"unexpected":true}""")
        assertEquals(HubResult.Err(HubError.Unexpected), b.searchStations(query, 0))
    }

    // 14
    @Test
    fun aRadioAnswerMayTakeUpToSixSeconds() = runTest {
        val slow = MockEngine { delay(5_500); respond(emptyPage, HttpStatusCode.OK, json) }
        val ok = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(slow))
        assertIs<HubResult.Ok<StationPage>>(ok.searchStations(query, 0))

        val tooSlow = MockEngine { delay(6_500); respond(emptyPage, HttpStatusCode.OK, json) }
        val late = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(tooSlow))
        assertEquals(HubResult.Err(HubError.Unreachable), late.searchStations(query, 0))
    }

    @Test
    fun aSnapshotStillTimesOutAtThreeAndAHalfSeconds() = runTest {
        val engine = MockEngine { delay(3_500); respond("[]", HttpStatusCode.OK, json) }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        assertEquals(HubResult.Err(HubError.Unreachable), repo.snapshot())
    }

    // 15
    @Test
    fun aNetworkFailureIsUnreachableOnEveryCall() = runTest {
        val engine = MockEngine { throw kotlinx.io.IOException("down") }
        val repo = KtorHubRepository(HubAddress("http://hub:8080"), createHubHttpClient(engine))
        val down = HubResult.Err(HubError.Unreachable)
        assertEquals(down, repo.searchStations(query, 0))
        assertEquals(down, repo.radioCountries())
        assertEquals(down, repo.radioLanguages())
        assertEquals(down, repo.radioTags(null, 0))
    }

    // 16
    @Test
    fun aStationStartPostsOnlyTheAddressAndTheTarget() = runTest {
        val route = """{"routeId":"r1","inputId":"i1","targetId":"kitchen","targetType":"SINGLE_OUTPUT","status":"STARTING"}"""
        val (repo, log) = repository(body = """{"inputId":"i1","message":"ok","route":$route}""")
        val uri = "radiobrowser://9617a958-0601-11e8-ae97-52543be04c81"
        assertIs<HubResult.Ok<*>>(repo.playLink(uri, Target.Room("kitchen")))
        val r = log.single()
        assertEquals("/api/v2/play", r.path)
        assertEquals("""{"uri":"$uri","targetId":"kitchen","targetType":"SINGLE_OUTPUT"}""", r.body)
    }
}
