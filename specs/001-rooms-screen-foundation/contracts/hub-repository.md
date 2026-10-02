# Contract: Hub repository

The only boundary between the app and the hub (Constitution I). Screens and view models depend on
this interface. Only its Ktor implementation touches generated types.

## Interface (commonMain, `ai.sonora.mobile.data`)

```kotlin
interface HubRepository {
    /** The five GETs, run concurrently; Ok only if all succeed. */
    suspend fun snapshot(): HubResult<HubSnapshot>

    suspend fun setRoomVolume(roomId: String, volume: Int): HubResult<Unit>   // volume clamped 0..100
    suspend fun stopRoute(routeId: String): HubResult<Unit>
    suspend fun setRoutePaused(routeId: String, paused: Boolean): HubResult<Unit>
    suspend fun setMasterMute(muted: Boolean): HubResult<Unit>
}

sealed interface HubResult<out T> {
    data class Ok<T>(val value: T) : HubResult<T>
    data class Err(val error: HubError) : HubResult<Nothing>
}

sealed interface HubError {
    data object Unreachable : HubError                  // connect failure, timeout (3 s), IO
    data class Rejected(val status: Int, val problemType: String?) : HubError  // 4xx/5xx
    data object Unexpected : HubError                   // undecodable body, anything else
}

fun interface HubRepositoryFactory { fun create(address: HubAddress): HubRepository }
```

Deliberately **absent**: group volume (`PUT /groups/{id}/volume` must never be called, FR-013c),
group/room mute setters (out of scope), transfer, play, create route, enabled toggles, input delete
(later features add them here).

## Endpoint mapping (all relative to the stored base URL)

| Method | Generated call | HTTP | Request body | Success |
|---|---|---|---|---|
| `snapshot` | `OutputsApi.listOutputs` | `GET /api/v2/outputs` | | 200 `OutputResponse[]` |
| | `GroupsApi.listGroups` | `GET /api/v2/groups` | | 200 `GroupResponse[]` |
| | `RoutesApi.listRoutes` | `GET /api/v2/routes` | | 200 `RouteResponse[]` |
| | `InputsApi.listInputs` | `GET /api/v2/inputs` | | 200 `InputResponse[]` |
| | `MasterMuteApi.getMasterMute` | `GET /api/v2/master-mute` | | 200 `MasterMuteResponse` |
| `setRoomVolume` | `OutputsApi.setOutputVolume` | `PUT /api/v2/outputs/{id}/volume` | `{"volume": n}` | 200 |
| `stopRoute` | `RoutesApi.deleteRoute` | `DELETE /api/v2/routes/{id}` | | 204 |
| `setRoutePaused` | `RoutesApi.setPauseState` | `PUT /api/v2/routes/{id}/pause` | `{"paused": b}` | 200 |
| `setMasterMute` | `MasterMuteApi.setMasterMute` | `PUT /api/v2/master-mute` | `{"muted": b}` | 200 |

`listRoutes` takes no filter parameters here. STOPPED routes are filtered in `RoomsBuilder`, not by
query, so the rule is tested in one place. Response bodies of the four actions are ignored: the next
refresh is the confirmation (FR-018).

Generated class/operation names come from `operationId`/tags in `api/openapi.json` (version
0.1.20). If the generator names them differently, the mapping above (HTTP column) is binding.

## HTTP client configuration

- Engine: `expect fun httpEngine()`. Android OkHttp, iOS Darwin.
- `HttpTimeout`: request/connect/socket 3000 ms.
- `ContentNegotiation` with
  `Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }`.
- No auth, no default headers beyond JSON content type. No retries: the poll loop is the retry.
- `expectSuccess = false`: status codes are mapped explicitly.

## Error mapping

| Situation | `HubError` |
|---|---|
| `HttpRequestTimeoutException`, `ConnectTimeoutException`, `SocketTimeoutException`, `IOException`-family (unresolved host, refused, reset) | `Unreachable` |
| HTTP status ≥ 400 | `Rejected(status, problemType = ErrorResponse.type if the body decodes, else null)` |
| 2xx with undecodable body / `SerializationException` | `Unexpected` |
| `CancellationException` | rethrown, never mapped |

The problem `title`/`detail` is never part of `HubError`, so it cannot reach the UI verbatim
(Constitution V).

## User-facing messages (`ui/Messages.kt`, single function)

| Action | `Unreachable` | `Rejected` 404 | other `Rejected` / `Unexpected` |
|---|---|---|---|
| Volume on X | "Couldn't change the volume in X. Can't reach the hub." | "X is no longer on the hub." | "Couldn't change the volume in X." |
| Stop X | "Couldn't stop X. Can't reach the hub." | "That playback has already ended." | "Couldn't stop X." |
| Pause/Resume X | "Couldn't pause X. Can't reach the hub." / "…resume…" | "That playback has already ended." | "Couldn't pause X." / "Couldn't resume X." |
| Master mute | "Couldn't mute all rooms. Can't reach the hub." / "…unmute…" | | "Couldn't mute all rooms." / "Couldn't unmute all rooms." |

For a group volume change, the first failing member determines the message and X is the group name.

## Contract tests (`commonTest/.../data/KtorHubRepositoryTest.kt`, `MockEngine`)

1. Snapshot decodes example payloads shaped by `openapi.json` into the expected domain values.
2. Unknown JSON field ignored. Unknown `status` → `RouteStatus.Unknown`. Unknown or null
   `targetType` → route kept with `Target.Unknown(targetId)`. Unknown `source` →
   `SourceOrigin.Unknown`.
3. Null/missing optional fields take the defaults in [data-model.md](../data-model.md).
4. Each action sends the exact method, path and JSON body in the table above.
5. RFC 7807 body on 404/422 → `Rejected(status, type)`. Non-JSON error body → `Rejected(status, null)`.
6. Engine throws an IO exception / times out → `Unreachable`. One failing GET fails the whole
   snapshot.
7. No test or production code path issues `PUT /api/v2/groups/{id}/volume`. A MockEngine
   handler fails the test if that path is requested during the group-volume ViewModel tests.
