package ai.sonora.mobile.data

import ai.sonora.mobile.domain.HubAddress
import ai.sonora.mobile.domain.HubSnapshot

/**
 * The only boundary between the app and the hub (Constitution I). There is deliberately no group
 * volume method: `PUT /api/v2/groups/{id}/volume` must never be called.
 */
interface HubRepository {
    /** The five GETs, run concurrently; [HubResult.Ok] only if all succeed. */
    suspend fun snapshot(): HubResult<HubSnapshot>

    /** [volume] is clamped to 0..100. */
    suspend fun setRoomVolume(roomId: String, volume: Int): HubResult<Unit>

    suspend fun stopRoute(routeId: String): HubResult<Unit>

    suspend fun setRoutePaused(routeId: String, paused: Boolean): HubResult<Unit>

    suspend fun setMasterMute(muted: Boolean): HubResult<Unit>
}

sealed interface HubResult<out T> {
    data class Ok<T>(val value: T) : HubResult<T>
    data class Err(val error: HubError) : HubResult<Nothing>
}

sealed interface HubError {
    /** Connect failure, timeout or other IO problem. */
    data object Unreachable : HubError

    /** The hub answered with an error status; [problemType] is the RFC 7807 `type` if present. */
    data class Rejected(val status: Int, val problemType: String?) : HubError

    /** Undecodable body or anything else unexpected. */
    data object Unexpected : HubError
}

fun interface HubRepositoryFactory {
    fun create(address: HubAddress): HubRepository
}
