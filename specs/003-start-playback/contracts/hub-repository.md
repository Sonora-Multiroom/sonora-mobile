# Contract: Hub repository (additions in 003)

Extends the [001](../../001-rooms-screen-foundation/contracts/hub-repository.md) and
[002](../../002-now-playing-move-to-room/contracts/hub-repository.md) repository contracts, against
`api/openapi.json` **0.1.21**. The existing methods keep their behaviour (reconciliation in
[research R1](../research.md#r1-reconciling-the-contract-0120--0121)).

## New methods

```kotlin
interface HubRepository {
    // ... 001 and 002 methods unchanged ...

    /**
     * Starts a configured source on a room or group. No join mode is sent, so the hub applies the
     * source's default, else replace. Returns the hub's route, which may be an existing one when
     * the source already plays on exactly that target.
     * [target] is Target.Room or Target.Group (Target.Unknown → IllegalArgumentException, never sent).
     */
    suspend fun startSource(inputId: String, target: Target): HubResult<Route>

    /**
     * Plays a link (an http/https URI, already normalised) on a room or group. The hub adds it as a
     * runtime source. No name, volume or join mode is sent. Uses the 30 s client (FR-014).
     */
    suspend fun playLink(uri: String, target: Target): HubResult<Route>
}
```

Still **absent**: group volume (never called), the `joinMode`/`volume`/`displayName` request
fields, enabled toggles, input delete, `GET /outputs/{id}/routes`.

## Endpoint mapping

| Method | Generated call | HTTP | Request body | Success |
|---|---|---|---|---|
| `startSource` | `RoutesApi.createRoute` | `POST /api/v2/routes` | `{"inputId": "…", "targetId": "…", "targetType": "SINGLE_OUTPUT" \| "OUTPUT_GROUP"}` | 2xx `RouteResponse` → `Route` via `toRoute()` |
| `playLink` | `PlaybackApi.playback` | `POST /api/v2/play` | `{"uri": "…", "targetId": "…", "targetType": "…"}` | 200 `PlaybackResponse`; `.route` → `Route` via `toRoute()` |

- A success body that does not decode, or a missing or unreadable `route`, maps to `Unexpected`.
- No other key may appear in the request bodies. Absent nullable fields are omitted, not sent as
  `null` (`explicitNulls = false`).
- If the generator names operations differently, the HTTP column is binding (as in 001).

## Error mapping (extends 001)

`HubError.Rejected(status, problemType, reason, outputId)`. `reason` and `outputId` are read from
the RFC 7807 body when present and are `null` otherwise or when the body is not JSON. They apply
to every call, so existing 001/002 tests stay valid with the two new fields `null`.

Documented statuses: `POST /routes` 400/404/422, `POST /play` 400/404/422/502/503. Admission
refusals carry `reason` (`ROUTE_LIMIT_REACHED`, `INPUT_ALREADY_ON_OUTPUT`) and `outputId`. Their
status code is not documented, so mapping keys on `reason` ([research R6](../research.md#r6-hub-errors-for-a-start)).

## Clients and timeouts

| Client | Used by | Connect | Request / socket |
|---|---|---|---|
| shared (001) | everything else, including `startSource` | 3 s | 3 s |
| link | `playLink` only, `client.config { HttpTimeout }` on the same engine | 3 s | 30 s |

A timeout on either maps to `Unreachable`, as in 001. Recovery is the view model's job (FR-016a).

## Domain mapping additions (`ApiMapping.kt`)

- `RouteResponse.joinMode` → `Route.joinMode`: `REPLACE`→Replace, `MIX`→Mix,
  `DUCK_OTHERS`→Announcement, missing/unknown→Replace.
- `InputResponse.defaultJoinMode` → `Source.defaultJoinMode`: same values, missing/unknown→`null`.

## Contract tests (`MockEngine`)

`KtorHubRepositoryActionsTest` (extended):
1. `startSource` to a room or group sends the exact method, path and body above, with no
   `joinMode` key. A 201 `RouteResponse` comes back as `Route`, and so does a 200 (existing route).
2. `playLink` sends `uri`, `targetId` and `targetType` only, with no `displayName`, `volume` or
   `joinMode`. A 200 `PlaybackResponse` comes back as its nested `Route`. A 200 without `route` →
   `Unexpected`.
3. RFC 7807 bodies on every documented status → `Rejected(status, type, reason, outputId)`, with
   `reason`/`outputId` parsed when present (including on 409 and 422 bodies). A non-JSON error body
   → `Rejected(status, null, null, null)`.
4. `playLink` waits longer than 3 s: a `MockEngine` answering after 10 s (virtual time) succeeds.
   `startSource` answering after 4 s → `Unreachable`.
5. `Target.Unknown` throws and sends nothing (both methods).
6. Still never `PUT /api/v2/groups/{id}/volume` (the fake fails the test if it is requested).

`KtorHubRepositorySnapshotTest` (extended):
7. `joinMode` `REPLACE`/`MIX`/`DUCK_OTHERS`/missing/`"SOMETHING_NEW"` → Replace/Mix/Announcement/
   Replace/Replace; `defaultJoinMode` the same with `null` for missing/unknown. The snapshot never fails because of an
   unknown value.
