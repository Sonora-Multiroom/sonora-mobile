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

    /** Turns a room (output) on or off. The body of the answer is ignored; the next refresh confirms. */
    suspend fun setRoomEnabled(roomId: String, enabled: Boolean): HubResult<Unit>

    suspend fun setGroupEnabled(groupId: String, enabled: Boolean): HubResult<Unit>

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): HubResult<Unit>

    /** Removes a runtime source. 404 stays Rejected(404) here; the caller treats it as removed. */
    suspend fun removeSource(sourceId: String): HubResult<Unit>

    /**
     * The connection test: how many rooms the hub at this repository's address lists, on or off.
     * A reply that is not a list of outputs is Unexpected.
     */
    suspend fun countRooms(): HubResult<Int>

    /**
     * Moves a playback. [target] is [Target.Room] or [Target.Group] (`Target.Unknown` throws
     * [IllegalArgumentException] and is never sent). Returns the hub's NEW route: the old id is
     * gone after success.
     */
    suspend fun transferRoute(routeId: String, target: Target): HubResult<Route>

    /**
     * Starts a configured source on a room or group. No join mode is sent, so the hub applies the
     * source's default, else replace. Returns the hub's route, which may be an existing one when
     * the source already plays on exactly that target. [target] is [Target.Room] or [Target.Group]
     * (`Target.Unknown` throws [IllegalArgumentException] and is never sent).
     */
    suspend fun startSource(inputId: String, target: Target): HubResult<Route>

    /**
     * Plays a link (an http/https address, already normalised) on a room or group; the hub adds it
     * as a runtime source. No name, volume or join mode is sent. Allows 30 s (the hub resolves the
     * link first), unlike every other call.
     */
    suspend fun playLink(uri: String, target: Target): HubResult<Route>
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
