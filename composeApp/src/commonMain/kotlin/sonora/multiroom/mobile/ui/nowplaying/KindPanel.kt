package sonora.multiroom.mobile.ui.nowplaying

import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.ui.rooms.forKind
import sonora.multiroom.mobile.ui.rooms.iconForKind
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The decorative panel: three concentric rings round a disc with the kind's icon. It depends on the
 * kind only, never on playback state, so it cannot be mistaken for progress (FR-008). A stream is
 * drawn exactly as in design/screens/NowPlaying.dc.html; the other kinds reuse their tile colours.
 * Purely decorative, so it carries no semantics: the badge text is announced through the subtitle.
 */
@Composable
fun KindPanel(kind: SourceKind, live: Boolean, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val kindColors = colors.forKind(kind)
    val stream = kind == SourceKind.Stream
    val panel = if (stream) colors.streamPanel else kindColors.tile
    val ring = if (stream) colors.accent else kindColors.icon
    val disc = if (stream) colors.accent else kindColors.icon
    val onDisc = if (stream) colors.onAccent else colors.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(248.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(panel)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        for ((size, alpha) in listOf(380.dp to 0.14f, 276.dp to 0.24f, 176.dp to 0.38f)) {
            Box(Modifier.requiredSize(size).border(1.dp, ring.copy(alpha = alpha), CircleShape))
        }
        Box(Modifier.size(92.dp).background(disc, CircleShape), contentAlignment = Alignment.Center) {
            Icon(iconForKind(kind), contentDescription = null, tint = onDisc, modifier = Modifier.size(42.dp))
        }
        if (live) {
            Text(
                "LIVE STREAM",
                style = SonoraTheme.type.label12.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.88.sp),
                color = colors.warningText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
                    .background(colors.badgeScrim, SonoraTheme.shapes.chip)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}
