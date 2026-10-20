package sonora.multiroom.mobile.ui.rooms

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** How a name is drawn, decided by [marqueeLayout]. */
enum class MarqueeLayout {
    /** The name fits in `maxLines`: plain text, no "…", no fade. */
    Fits,

    /** The name does not fit and may not scroll: `maxLines` lines ending in "…". */
    Truncated,

    /** The name does not fit and may scroll: one line, faded edges, marquee. */
    Scrolling,
}

internal fun marqueeLayout(overflows: Boolean, canScroll: Boolean): MarqueeLayout = when {
    !overflows -> MarqueeLayout.Fits
    canScroll -> MarqueeLayout.Scrolling
    else -> MarqueeLayout.Truncated
}

/**
 * Nothing moves while the screen is stale, the window has lost focus, or the phone's animations
 * are off (`animationScale` 0).
 */
internal fun canScroll(stale: Boolean, windowFocused: Boolean, animationScale: Float): Boolean =
    !stale && windowFocused && animationScale > 0f

/** Cards on one screen start scrolling at different moments instead of in step. */
internal fun marqueeStartDelay(index: Int): Int = 1_200 + (index % 4) * 600

private val EdgeFade = 12.dp

/**
 * A name that scrolls by itself when it does not fit in [maxLines]; one that fits looks exactly as
 * plain text. See [MarqueeLayout] for the three ways it is drawn. The [Text] always carries the
 * full string, so TalkBack reads the whole name once. A refresh that brings the same text leaves a
 * running marquee alone; a different text starts again from its beginning.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    stale: Boolean,
    modifier: Modifier = Modifier,
    startDelayMillis: Int = 1_200,
) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val animationScale by produceState(1f) {
        coroutineContext[MotionDurationScale]?.let { scale ->
            snapshotFlow { scale.scaleFactor }.collect { value = it }
        }
    }
    val scrollAllowed = canScroll(stale, windowFocused, animationScale)
    val measurer = rememberTextMeasurer()

    key(text) {
        BoxWithConstraints(modifier) {
            val overflows = remember(text, style, maxWidth, maxLines) {
                measurer.measure(
                    text,
                    style = style,
                    maxLines = maxLines,
                    constraints = Constraints(maxWidth = constraints.maxWidth),
                ).hasVisualOverflow
            }
            when (marqueeLayout(overflows, scrollAllowed)) {
                MarqueeLayout.Fits -> Text(text, style = style, color = color, maxLines = maxLines)
                MarqueeLayout.Truncated -> Text(
                    text,
                    style = style,
                    color = color,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                MarqueeLayout.Scrolling -> Text(
                    text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fade = EdgeFade.toPx().coerceAtMost(size.width / 2)
                            val fraction = fade / size.width
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    fraction to Color.Black,
                                    1f - fraction to Color.Black,
                                    1f to Color.Transparent,
                                    startX = 0f,
                                    endX = size.width,
                                ),
                                topLeft = Offset.Zero,
                                size = size,
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1_200,
                            initialDelayMillis = startDelayMillis,
                            velocity = 30.dp,
                        ),
                )
            }
        }
    }
}
