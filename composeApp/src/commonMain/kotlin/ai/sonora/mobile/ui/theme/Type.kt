package ai.sonora.mobile.ui.theme

import ai.sonora.mobile.resources.Res
import ai.sonora.mobile.resources.dm_sans
import ai.sonora.mobile.resources.sora
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

/** Sora for titles, DM Sans for everything else (CLAUDE.md "Design"). */
data class SonoraType(
    val screenTitle: TextStyle,
    val cardTitle: TextStyle,
    val body16: TextStyle,
    val body16Semi: TextStyle,
    val body15: TextStyle,
    val body14: TextStyle,
    val body13: TextStyle,
    val body13Semi: TextStyle,
    val label12: TextStyle,
    val sectionLabel: TextStyle,
    val button16: TextStyle,
)

private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

@Composable
fun rememberSonoraType(): SonoraType {
    // The two bundled files are variable fonts, so each weight selects an axis value.
    val sora = FontFamily(weights.map { w -> Font(Res.font.sora, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) })
    val dmSans = FontFamily(weights.map { w -> Font(Res.font.dm_sans, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) })
    return remember(sora, dmSans) {
        SonoraType(
            screenTitle = TextStyle(fontFamily = sora, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
            cardTitle = TextStyle(fontFamily = sora, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            body16 = TextStyle(fontFamily = dmSans, fontSize = 16.sp, fontWeight = FontWeight.Normal),
            body16Semi = TextStyle(fontFamily = dmSans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            body15 = TextStyle(fontFamily = dmSans, fontSize = 15.sp, fontWeight = FontWeight.Normal),
            body14 = TextStyle(fontFamily = dmSans, fontSize = 14.sp, fontWeight = FontWeight.Normal),
            body13 = TextStyle(fontFamily = dmSans, fontSize = 13.sp, fontWeight = FontWeight.Normal),
            body13Semi = TextStyle(fontFamily = dmSans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            label12 = TextStyle(fontFamily = dmSans, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            sectionLabel = TextStyle(fontFamily = dmSans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.96.sp),
            button16 = TextStyle(fontFamily = dmSans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
