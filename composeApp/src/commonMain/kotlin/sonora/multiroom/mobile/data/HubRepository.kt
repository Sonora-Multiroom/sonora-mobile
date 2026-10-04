package sonora.multiroom.mobile.data

import sonora.multiroom.mobile.domain.HubAddress
import sonora.multiroom.mobile.domain.HubSnapshot
import sonora.multiroom.mobile.domain.Route
import sonora.multiroom.mobile.domain.Target

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

    suspend fun setRoomMute(roomId: String, muted: Boolean): HubResult<Unit>

    /** Mutes or unmutes every member of the group (idempotent on the hub). */
    suspend fun setGroupMute(groupId: String, muted: Boolean): HubResult<Unit>

    /**
     * Moves a playback. [target] is [Target.Room] or [Target.Group] (`Target.Unknown` throws
     * [IllegalArgumentException] and is never sent). Returns the hub's NEW route: the old id is
     * gone after success.
     */
    suspend fun transferRoute(routeId: String, target: Target): HubResult<Route>
}

sealed interface HubResult<out T> {
    data class Ok<T>(val value: T) : HubResult<T>
    data class Err(val error: HubError) : HubResult<Nothing>
}

sealed interface HubError {
    /** Connect failure, timeout or other IO problem. */
    data object Unreachable : HubError

    /**
     * The hub answered with an error status; [problemType] is the RFC 7807 `type` if present.
     * [reason] (e.g. `ROUTE_LIMIT_REACHED`) and [outputId] name an admission refusal (API 0.1.21).
     * They are never shown as text.
     */
    data class Rejected(
        val status: Int,
        val problemType: String?,
        val reason: String? = null,
        val outputId: String? = null,
    ) : HubError

    /** Undecodable body or anything else unexpected. */
    data object Unexpected : HubError
}

fun interface HubRepositoryFactory {
    fun create(address: HubAddress): HubRepository
}
