# Flaky tests: requests recorded from MockEngine threads without a lock

- **Status:** Proposed, not started. Written 2026-11-16 after the second CI failure.
- **Origin:** Two failed CI runs of `:composeApp:testAndroidHostTest`, each passing on a rerun with
  no code change:
  - 2026-11-08, push to `main`
    ([run 42502674325](https://github.com/Sonora-Multiroom/sonora-mobile/actions/runs/42502674325)):
    `KtorHubRepositorySnapshotTest.requestsExactlyTheFiveV2GetsWithDisabledItemsIncluded`,
    `AssertionError`.
  - 2026-11-15, pull request #22, a documentation and contract change
    ([run 43773366031](https://github.com/Sonora-Multiroom/sonora-mobile/actions/runs/43773366031)):
    `RoomsViewModelControlsTest.dragAGroupThroughTheRealRepositoryNeverTouchesTheGroupVolumeEndpoint`,
    `ConcurrentModificationException`.
- **Depends on:** Nothing.
- **Effort:** Small. A helper in `TestSupport.kt` and two test files; no app code. Fits the tinyspec
  path (no screen, hub call or dependency).

## Problem

Both tests collect the requests a Ktor `MockEngine` receives in a plain `mutableListOf`
(`KtorHubRepositorySnapshotTest.kt` line 43, `RoomsViewModelControlsTest.kt` line 358). The engine
runs its handler on real threads, not on the test scheduler, so the list is written from several
threads and read from the test thread with no synchronisation:

- The snapshot sends its five GETs in parallel. Two handlers adding at the same moment can lose one
  entry, and the test then counts four requests instead of five (the 2026-11-08 failure).
- The drag test polls the list (`requests.any { … }`, `requests.count { … }`) while the engine is
  still answering. An add during that read throws `ConcurrentModificationException` (the
  2026-11-15 failure).

Neither failure says anything about the app. The cost is in the merge gate: a green CI run is
required (constitution, "Development Workflow & Quality Gates"), a red run on an unrelated change
has to be diagnosed before anyone trusts a rerun, and the failed run on `main` stayed red. Two
failures in eight days, across two tests, on a suite of 705.

## Proposal

- One recorder for requests seen by a `MockEngine`, in `commonTest`'s `TestSupport.kt`: adding takes
  a `Mutex` (the handler is a suspend function), and readers get an immutable copy, so a read never
  meets a write. It must work in `commonTest` (Constitution III): no `java.util.concurrent` and no
  `synchronized`.
- Use it in both tests in place of the bare lists.
- Proof that it worked: each of the two tests run a few hundred times in a loop locally, with no
  failure. A single green run proves nothing for a race.

## Open questions

1. **Are other tests exposed?** Any test that gives a real `MockEngine` to the repository and
   inspects what it saw from the test thread has the same shape. `FakeHub.calls` (a plain list in
   `ui/rooms/FakeHub.kt`) is a fake repository and looks like it runs on the test scheduler only;
   confirm that rather than assume it.
2. **Is the drag test's real-time waiting sound otherwise?** It polls up to 500 times with a 10 ms
   real delay because the engine answers on a real thread. That is slow-CI-sensitive in its own
   right; a `MockEngine` bound to the test dispatcher would remove both the polling and the race,
   if Ktor allows it for this client setup.
