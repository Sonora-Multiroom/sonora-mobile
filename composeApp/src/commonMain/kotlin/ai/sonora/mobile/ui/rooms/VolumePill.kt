package ai.sonora.mobile.ui.rooms

import ai.sonora.mobile.ui.theme.SonoraIcons
import ai.sonora.mobile.ui.theme.SonoraTheme
import ai.sonora.mobile.ui.theme.minTouchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The volume control: a 44 dp fully rounded bar that fills with `accentContainer`, speaker icon on
 * the left, percentage on the right. Deliberately no thumb and no thin track, so it never looks
 * like a progress bar (FR-012, Constitution II). Drag or tap to set; the finger position is the
 * value. A muted or [enabled]=false pill shows its value but ignores input and offers no
 * accessibility adjustment.
 */
@Composable
fun VolumePill(
    value: Int,
    muted: Boolean,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit = {},
    onDrag: (Int) -> Unit = {},
    onDragEnd: (Int) -> Unit = {},
) {
    val colors = SonoraTheme.colors
    val interactive = enabled && !muted
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val shown = value.coerceIn(0, 100)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(minTouchTarget)
            .clip(SonoraTheme.shapes.pill)
            .background(colors.surfaceRaised)
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val v = valueAt(offset.x, size.width)
                        start(); drag(v); end(v)
                    },
                )
            }
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                var latest = 0
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latest = valueAt(offset.x, size.width)
                        start(); drag(latest)
                    },
                    onDragEnd = { end(latest) },
                    onDragCancel = { end(latest) },
                    onHorizontalDrag = { change, _ ->
                        latest = valueAt(change.position.x, size.width)
                        drag(latest)
                    },
                )
            }
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..100f, steps = 99)
                if (interactive) {
                    setProgress { target ->
                        val v = target.roundToInt().coerceIn(0, 100)
                        start(); drag(v); end(v)
                        true
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = shown / 100f)
                .background(colors.accentContainer),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp).clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = if (muted) SonoraIcons.SpeakerMuted else SonoraIcons.Speaker,
                contentDescription = null,
                tint = if (interactive || muted) colors.accent else colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "$shown%",
                style = SonoraTheme.type.body13Semi.copy(fontFeatureSettings = "tnum"),
                color = colors.text,
            )
        }
    }
}

/** The 0..100 value under x pixels of a bar [width] pixels wide. */
internal fun valueAt(x: Float, width: Int): Int =
    if (width <= 0) 0 else (x / width * 100f).roundToInt().coerceIn(0, 100)
