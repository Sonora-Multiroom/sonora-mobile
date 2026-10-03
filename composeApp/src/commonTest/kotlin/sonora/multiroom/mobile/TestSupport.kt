package sonora.multiroom.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** `runTest` with `Dispatchers.Main` on the test scheduler, so `viewModelScope` uses virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
        // Let short-lived jobs (a throttle wait, say) finish before Main goes away.
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
    } finally {
        Dispatchers.resetMain()
    }
}
