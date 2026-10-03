# Contract: Hub repository (additions in 002)

Extends [001 hub-repository](../../001-rooms-screen-foundation/contracts/hub-repository.md). Error
mapping, HTTP client configuration and the 001 methods are unchanged.

## New methods

```kotlin
interface HubRepository {
    // ... 001 methods unchanged ...

    suspend fun setRoomMute(roomId: String, muted: Boolean): HubResult<Unit>
    suspend fun setGroupMute(groupId: String, muted: Boolean): HubResult<Unit>

    /**
     * Moves a playback. [target] is Target.Room or Target.Group (Target.Unknown → IllegalArgumentException,
     * never sent). Returns the hub's NEW route: the old id is gone after success.
     */
    suspend fun transferRoute(routeId: String, target: Target): HubResult<Route>
}
```

Still **absent**: group volume (`PUT /api/v2/groups/{id}/volume`, never called, FR-015 / SC-005),
play, create route, enabled toggles, input delete.

## Endpoint mapping

| Method | Generated call | HTTP | Request body | Success |
|---|---|---|---|---|
| `setRoomMute` | `OutputsApi.setOutputMute` | `PUT /api/v2/outputs/{id}/mute` | `{"muted": b}` | 200 `OutputMuteResponse` (ignored) |
| `setGroupMute` | `GroupsApi.setGroupMute` | `PUT /api/v2/groups/{id}/mute` | `{"muted": b}` | 200 `GroupMuteResponse` (ignored) |
| `transferRoute` | `RoutesApi.transferRoute` | `POST /api/v2/routes/{id}/transfer` | `{"targetId": "…", "targetType": "SINGLE_OUTPUT" \| "OUTPUT_GROUP"}` | 200 `RouteResponse` → `Route` via the existing `toRoute()` |

`transferRoute` errors in `openapi.json` 0.1.20: 400 (validation / not transferable), 404 (route
or target not found), 422 (transfer failed). All of them map to `Rejected(status, type)`. A 200 whose
body does not decode, or that `toRoute()` rejects, maps to `Unexpected`.

If the generator names operations differently, the HTTP column is binding (as in 001).

## User-facing messages (extend `ui/Messages.kt`)

New `UserAction`s: `Mute(on: Boolean)` (room or group) and `Move(destination: String)`. Target name X
is the room or group name for Mute, and the source name for Move.

| Action | `Unreachable` | `Rejected` 404 | other `Rejected` / `Unexpected` |
|---|---|---|---|
| Mute X | "Couldn't mute X. Can't reach the hub." / "Couldn't unmute X. …" | "X is no longer on the hub." | "Couldn't mute X." / "Couldn't unmute X." |
| Move X to D | "Couldn't move X to D. Can't reach the hub." | "Couldn't move X to D." | "Couldn't move X to D." |

The "ended" notice is not an error and is not in `actionErrorMessage`. It is posted by
`NowPlayingViewModel` as **"Playback on <target> ended"** (FR-002, research R4).

## Contract tests (`MockEngine`, `KtorHubRepositoryActionsTest`)

1. `setRoomMute` / `setGroupMute` send the exact method, path and body above, for `true` and `false`.
2. `transferRoute` to a room sends `SINGLE_OUTPUT` and to a group sends `OUTPUT_GROUP`. A 200
   `RouteResponse` with a new `routeId` comes back as `Route` with that id and target.
3. `transferRoute` with RFC 7807 bodies on 400/404/422 → `Rejected(status, type)`. Engine IO
   failure → `Unreachable`. 200 with a garbage body → `Unexpected`.
4. `transferRoute(…, Target.Unknown(…))` throws and sends no request.
5. Carried over from 001 (contract test 7), now extended to the Now Playing view-model tests: no
   code path requests `PUT /api/v2/groups/{id}/volume`. The fake fails the test if it does.
