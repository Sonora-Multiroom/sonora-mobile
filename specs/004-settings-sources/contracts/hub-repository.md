# Contract: Hub repository (additions in 004)

This extends the 001–003 repository contracts, against `api/openapi.json` **0.1.21** (re-fetched
2026-10-04, unchanged). The existing methods keep their behaviour.

## New methods

```kotlin
interface HubRepository {
    // ... 001–003 methods unchanged ...

    /** Turns a room (output) on or off. The body of the answer is ignored; the next refresh confirms. */
    suspend fun setRoomEnabled(roomId: String, enabled: Boolean): HubResult<Unit>

    suspend fun setGroupEnabled(groupId: String, enabled: Boolean): HubResult<Unit>

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): HubResult<Unit>

    /** Removes a runtime source. 404 stays Rejected(404) here; the caller treats it as removed. */
    suspend fun removeSource(sourceId: String): HubResult<Unit>

    /** The hub's extension inventory (captured at hub start-up; connection states change later). */
    suspend fun extensions(): HubResult<ExtensionInventory>

    /**
     * The connection test: how many rooms the hub at this repository's address lists, on or off.
     * A reply that is not a list of outputs is Unexpected.
     */
    suspend fun countRooms(): HubResult<Int>
}
```

Still **absent**: group volume (never called), `joinMode`, `POST/PUT /inputs` (creating or editing
sources), and v1/TTS paths.

## Endpoint mapping

| Method | Generated call | HTTP | Request body | Success |
|---|---|---|---|---|
| `setRoomEnabled` | `OutputsApi.setOutputEnabled` | `PUT /api/v2/outputs/{id}/enabled` | `{"enabled": true\|false}` | 2xx, body ignored |
| `setGroupEnabled` | `GroupsApi.setGroupEnabled` | `PUT /api/v2/groups/{id}/enabled` | same | 2xx, body ignored |
| `setSourceEnabled` | `InputsApi.setInputEnabled` | `PUT /api/v2/inputs/{id}/enabled` | same | 2xx, body ignored |
| `removeSource` | `InputsApi.deleteInput` | `DELETE /api/v2/inputs/{id}` | none | 204 (any 2xx) |
| `extensions` | `ExtensionsApi.listExtensions` | `GET /api/v2/extensions` | — | 200 `ExtensionInventory` → domain (data-model.md) |
| `countRooms` | `OutputsApi.listOutputs(includeDisabled = true)` | `GET /api/v2/outputs?includeDisabled=true` | — | 200 array → count of entries that map to a `Room` |

- Ids are path-encoded by the generated client. An id with `/` or spaces must arrive encoded
  (contract test 2).
- The `{enabled}` body has only that key.
- If the generator names operations differently, the HTTP column is binding (as in 001).
- All calls use the shared 3 s client (001). `countRooms` runs on a repository created for the
  **draft** address by `HubRepositoryFactory`, not on the session's repository.

## Error mapping (unchanged rules, new uses)

`HubError.Rejected(status, problemType, reason, outputId)` / `Unreachable` / `Unexpected`, as in
001–003. What each caller makes of them (copy in `ui/Messages.kt`, `settingsFailureMessage`):

| Call | 404 | 400 | `Unreachable` | other |
|---|---|---|---|---|
| `set*Enabled` | "<name> is no longer on the hub" + refresh | "Couldn't turn <name> off/on" | "Couldn't reach the hub" | "Couldn't turn <name> off/on" |
| `removeSource` | success (already gone) | "<name> comes from the hub's configuration and can't be removed" | "Couldn't reach the hub" | "Couldn't remove <name>" |
| `countRooms` | "Can't reach the hub at this address" (every `Err`) | same | same | same |
| `extensions` | last inventory kept, no message | same | same | same |

## Contract tests (`MockEngine`)

`KtorHubRepositorySettingsTest` (new):
1. Each `set*Enabled` sends `PUT` to the exact path with body `{"enabled":false}` /
   `{"enabled":true}` and nothing else. A 200 with the documented response body → `Ok(Unit)`, and
   a 200 with an empty body → `Ok(Unit)`.
2. An id needing encoding (`"a b/c"`) reaches the engine path-encoded.
3. `removeSource` sends `DELETE /api/v2/inputs/{id}`. 204 → `Ok(Unit)`. A 400 RFC 7807 body →
   `Rejected(400, type, null, null)`. 404 → `Rejected(404, …)`.
4. `extensions`: the live sample from 2026-10-04 (5 extensions, `loadingEnabled: true`) maps to 5
   `Extension`s. `INERT` → `Inactive`. `"SOMETHING_NEW"` status or connection → `Unknown` without
   failing the call. A missing `name` → the id. `loadingEnabled: false` with `[]` maps to that.
   `rejectionReason` is not exposed.
5. `countRooms` sends `GET /api/v2/outputs?includeDisabled=true`. Three outputs, one of them
   disabled → `Ok(3)`. `[]` → `Ok(0)`. An HTML 200 body → `Unexpected`. A JSON object 200 body →
   `Unexpected`. 500 → `Rejected(500…)`. Answering after 4 s (virtual time) → `Unreachable`.
6. Network failure on each new call → `Unreachable`.
7. Still never `PUT /api/v2/groups/{id}/volume`.

`KtorHubRepositorySnapshotTest` (extended):
8. `autoRemove` true/false/missing → `true`/`false`/`false`. `createdAt` `"2026-10-04T12:30:00Z"`
   → that instant. `null`, missing, `""` or `"yesterday"` → `null`, and the snapshot never fails
   because of it.
