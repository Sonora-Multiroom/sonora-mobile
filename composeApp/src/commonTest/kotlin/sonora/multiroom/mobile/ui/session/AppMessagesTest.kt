package sonora.multiroom.mobile.ui.session

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppMessagesTest {
    @Test
    fun aPostedMessageIsDeliveredOnce() = runTest {
        val messages = AppMessages()
        messages.post("hello")
        assertEquals("hello", messages.messages.first())
        messages.post("again")
        assertEquals("again", messages.messages.first())
    }

    @Test
    fun theLatestWinsWhenSeveralArePostedBeforeCollection() = runTest {
        val messages = AppMessages()
        messages.post("first")
        messages.post("second")
        messages.post("third")
        assertEquals("third", messages.messages.first())
    }
}
