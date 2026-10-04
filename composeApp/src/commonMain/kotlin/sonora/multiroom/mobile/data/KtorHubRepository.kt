package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.Target
import sonora.multiroom.mobile.hub.generated.apis.GroupsApi
import sonora.multiroom.mobile.hub.generated.apis.InputsApi
import sonora.multiroom.mobile.hub.generated.apis.MasterMuteApi
import sonora.multiroom.mobile.hub.generated.apis.OutputsApi
import sonora.multiroom.mobile.hub.generated.apis.PlaybackApi
import sonora.multiroom.mobile.hub.generated.apis.RoutesApi
import sonora.multiroom.mobile.hub.generated.models.CreateRouteRequest
import sonora.multiroom.mobile.hub.generated.models.MuteRequest
import sonora.multiroom.mobile.hub.generated.models.PauseRequest
import sonora.multiroom.mobile.hub.generated.models.PlaybackRequest
import sonora.multiroom.mobile.hub.generated.models.TransferRequest
import sonora.multiroom.mobile.hub.generated.models.VolumeRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The only class that touches the generated client (Constitution I). */
class KtorHubRepository(address: HubAddress, client: HttpClient) : HubRepository {
    private val outputs = OutputsApi(address.baseUrl, client)
    private val groups = GroupsApi(address.baseUrl, client)
    private val routes = RoutesApi(address.baseUrl, client)
    private val inputs = InputsApi(address.baseUrl, client)
    private val masterMute = MasterMuteApi(address.baseUrl, client)

    // Same engine, longer request/socket timeouts; connecting still fails fast.
    private val playback = PlaybackApi(
        address.baseUrl,
        client.config {
            install(HttpTimeout) {
                requestTimeoutMillis = LINK_TIMEOUT_MILLIS
                socketTimeoutMillis = LINK_TIMEOUT_MILLIS
                connectTimeoutMillis = HUB_TIMEOUT_MILLIS
            }
        },
    )

    override suspend fun snapshot(): HubResult<HubSnapshot> = coroutineScope {
        // The hub omits disabled items by default; Rooms must show them as "Off".
        val o = async { hubCall { outputs.listOutputs(includeDisabled = true) } }
        val g = async { hubCall { groups.listGroups(includeDisabled = true) } }
        val r = async { hubCall { routes.listRoutes() } }
        val i = async { hubCall { inputs.listInputs(includeDisabled = true) } }
        val m = async { hubCall { masterMute.getMasterMute() } }
        val results = listOf(o.await(), g.await(), r.await(), i.await(), m.await())

        // A partial snapshot would misplace rooms, so any failure fails the whole refresh.
        val failure = results.filterIsInstance<HubResult.Err>().firstOrNull()
        if (failure != null) return@coroutineScope failure

        @Suppress("UNCHECKED_CAST")
        HubResult.Ok(
            HubSnapshot(
                rooms = (o.await() as HubResult.Ok).value.mapNotNull { it.toRoom() },
                groups = (g.await() as HubResult.Ok).value.mapNotNull { it.toGroup() },
                routes = (r.await() as HubResult.Ok).value.mapNotNull { it.toRoute() },
                sources = (i.await() as HubResult.Ok).value.mapNotNull { it.toSource() },
                masterMuted = (m.await() as HubResult.Ok).value.muted ?: false,
            ),
        )
    }

    // Response bodies are ignored: the next refresh is the confirmation. There is deliberately no
    // call to GroupsApi.setGroupVolume anywhere (it would flatten the balance between rooms).

    override suspend fun setRoomVolume(roomId: String, volume: Int): HubResult<Unit> =
        hubCallUnit { outputs.setOutputVolume(roomId, VolumeRequest(volume.coerceIn(0, 100))) }

    override suspend fun stopRoute(routeId: String): HubResult<Unit> =
        hubCallUnit { routes.deleteRoute(routeId) }

    override suspend fun setRoutePaused(routeId: String, paused: Boolean): HubResult<Unit> =
        hubCallUnit { routes.setPauseState(routeId, PauseRequest(paused)) }

    override suspend fun setMasterMute(muted: Boolean): HubResult<Unit> =
        hubCallUnit { masterMute.setMasterMute(MuteRequest(muted)) }

    override suspend fun setRoomMute(roomId: String, muted: Boolean): HubResult<Unit> =
        hubCallUnit { outputs.setOutputMute(roomId, MuteRequest(muted)) }

    override suspend fun setGroupMute(groupId: String, muted: Boolean): HubResult<Unit> =
        hubCallUnit { groups.setGroupMute(groupId, MuteRequest(muted)) }

    override suspend fun startSource(inputId: String, target: Target): HubResult<Route> {
        val request = when (target) {
            is Target.Room -> CreateRouteRequest(inputId, target.id, CreateRouteRequest.TargetType.SINGLE_OUTPUT)
            is Target.Group -> CreateRouteRequest(inputId, target.id, CreateRouteRequest.TargetType.OUTPUT_GROUP)
            is Target.Unknown -> throw IllegalArgumentException("Cannot start playback on an unknown target type")
        }
        // joinMode stays null: the hub applies the source's default, else replace.
        return when (val result = hubCall { routes.createRoute(request) }) {
            is HubResult.Err -> result
            is HubResult.Ok -> result.value.toRoute()?.let { HubResult.Ok(it) } ?: HubResult.Err(HubError.Unexpected)
        }
    }

    override suspend fun playLink(uri: String, target: Target): HubResult<Route> {
        val request = when (target) {
            is Target.Room -> PlaybackRequest(uri, target.id, PlaybackRequest.TargetType.SINGLE_OUTPUT)
            is Target.Group -> PlaybackRequest(uri, target.id, PlaybackRequest.TargetType.OUTPUT_GROUP)
            is Target.Unknown -> throw IllegalArgumentException("Cannot play a link on an unknown target type")
        }
        // No displayName, volume or joinMode: the hub names the link and each room keeps its volume.
        return when (val result = hubCall { playback.playback(request) }) {
            is HubResult.Err -> result
            is HubResult.Ok -> result.value.route?.toRoute()?.let { HubResult.Ok(it) } ?: HubResult.Err(HubError.Unexpected)
        }
    }

    override suspend fun transferRoute(routeId: String, target: Target): HubResult<Route> {
        val request = when (target) {
            is Target.Room -> TransferRequest(target.id, TransferRequest.TargetType.SINGLE_OUTPUT)
            is Target.Group -> TransferRequest(target.id, TransferRequest.TargetType.OUTPUT_GROUP)
            is Target.Unknown -> throw IllegalArgumentException("Cannot move a playback to an unknown target type")
        }
        return when (val result = hubCall { routes.transferRoute(routeId, request) }) {
            is HubResult.Err -> result
            // The hub answers with the NEW route; one the app cannot read is unexpected.
            is HubResult.Ok -> result.value.toRoute()?.let { HubResult.Ok(it) } ?: HubResult.Err(HubError.Unexpected)
        }
    }
}

class KtorHubRepositoryFactory(private val client: HttpClient) : HubRepositoryFactory {
    override fun create(address: HubAddress): HubRepository = KtorHubRepository(address, client)
}
