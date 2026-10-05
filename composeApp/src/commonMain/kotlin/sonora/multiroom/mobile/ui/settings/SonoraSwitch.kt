package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The design's switch: a 48×28 track with a 22 dp thumb, moving in 150 ms. It is purely visual:
 * the row that holds it owns the toggle semantics, so the whole row is the switch.
 */
@Composable
fun SonoraSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.switchTrackOff, tween(150))
    val thumb by animateColorAsState(if (checked) colors.onAccent else colors.switchThumbOff, tween(150))
    val left by animateDpAsState(if (checked) 23.dp else 3.dp, tween(150))
    Box(
        modifier = modifier.size(width = 48.dp, height = 28.dp).background(track, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = left).size(22.dp).background(thumb, CircleShape))
    }
}
