package ai.sonora.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SonoraShapes(
    val card: RoundedCornerShape = RoundedCornerShape(22.dp),
    val tile: RoundedCornerShape = RoundedCornerShape(14.dp),
    val smallTile: RoundedCornerShape = RoundedCornerShape(12.dp),
    val badge: RoundedCornerShape = RoundedCornerShape(6.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(8.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

/** Every touch target is at least this big. */
val minTouchTarget: Dp = 44.dp
