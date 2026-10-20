package sonora.multiroom.mobile.ui.rooms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarqueeTextTest {
    @Test
    fun aNameThatFitsIsNeverScrolledOrTruncated() {
        assertEquals(MarqueeLayout.Fits, marqueeLayout(overflows = false, canScroll = true))
        assertEquals(MarqueeLayout.Fits, marqueeLayout(overflows = false, canScroll = false))
    }

    @Test
    fun aLongNameIsTruncatedWhenItMayNotScroll() {
        assertEquals(MarqueeLayout.Truncated, marqueeLayout(overflows = true, canScroll = false))
    }

    @Test
    fun aLongNameScrollsWhenItMay() {
        assertEquals(MarqueeLayout.Scrolling, marqueeLayout(overflows = true, canScroll = true))
    }

    @Test
    fun scrollsOnlyWhenFreshFocusedAndAnimated() {
        assertTrue(canScroll(stale = false, windowFocused = true, animationScale = 1f))
        assertTrue(canScroll(stale = false, windowFocused = true, animationScale = 0.5f))
    }

    @Test
    fun staleStopsScrolling() {
        assertFalse(canScroll(stale = true, windowFocused = true, animationScale = 1f))
    }

    @Test
    fun lostWindowFocusStopsScrolling() {
        assertFalse(canScroll(stale = false, windowFocused = false, animationScale = 1f))
    }

    @Test
    fun removedAnimationsStopScrolling() {
        assertFalse(canScroll(stale = false, windowFocused = true, animationScale = 0f))
    }

    @Test
    fun startDelaysAreStaggeredAndWrapEveryFourCards() {
        assertEquals(
            listOf(1_200, 1_800, 2_400, 3_000, 1_200),
            (0..4).map { marqueeStartDelay(it) },
        )
    }
}
