package ai.sonora.mobile.data

import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.domain.HubSnapshot
import ai.sonora.mobile.hub.generated.apis.GroupsApi
import ai.sonora.mobile.hub.generated.apis.InputsApi
import ai.sonora.mobile.hub.generated.apis.MasterMuteApi
import ai.sonora.mobile.hub.generated.apis.OutputsApi
import ai.sonora.mobile.hub.generated.apis.RoutesApi
import ai.sonora.mobile.hub.generated.models.MuteRequest
import ai.sonora.mobile.hub.generated.models.PauseRequest
import ai.sonora.mobile.hub.generated.models.VolumeRequest
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The only class that touches the generated client (Constitution I). */
class KtorHubRepository(address: HubAddress, client: HttpClient) : HubRepository {
    private val outputs = OutputsApi(address.baseUrl, client)
    private val groups = GroupsApi(address.baseUrl, client)
    private val routes = RoutesApi(address.baseUrl, client)
    private val inputs = InputsApi(address.baseUrl, client)
    private val masterMute = MasterMuteApi(address.baseUrl, client)

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
}

class KtorHubRepositoryFactory(private val client: HttpClient) : HubRepositoryFactory {
    override fun create(address: HubAddress): HubRepository = KtorHubRepository(address, client)
}
