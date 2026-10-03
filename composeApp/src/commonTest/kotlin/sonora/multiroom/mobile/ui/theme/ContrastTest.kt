package sonora.multiroom.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG 2.x contrast of the colour pairs the Rooms screen actually draws (FR-023). */
class ContrastTest {
    private val c = SonoraColors()

    private fun channel(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> if (la >= lb) la to lb else lb to la } }
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Two decimals without String.format, which is JVM-only. */
    private fun rounded(r: Double): Double = (r * 100).roundToInt() / 100.0

    private fun assertText(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 4.5, "$name text contrast is ${rounded(r)}, needs 4.5")
    }

    private fun assertGraphic(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 3.0, "$name graphic contrast is ${rounded(r)}, needs 3.0")
    }

    @Test
    fun bodyTextOnEverySurface() {
        for ((bgName, bg) in listOf("background" to c.background, "surface" to c.surface, "surfaceRaised" to c.surfaceRaised)) {
            assertText("text on $bgName", c.text, bg)
            assertText("textMuted on $bgName", c.textMuted, bg)
            assertText("warningText on $bgName", c.warningText, bg)
        }
    }

    @Test
    fun accentPairs() {
        assertText("onAccent on accent", c.onAccent, c.accent)
        assertText("accent on background", c.accent, c.background)
        assertText("accent on surface", c.accent, c.surface)
        assertText("warningText on accentContainer", c.warningText, c.accentContainer)
    }

    @Test
    fun volumePillPercentageOnBothHalves() {
        assertText("pill % on accentContainer", c.text, c.accentContainer)
        assertText("pill % on surfaceRaised", c.text, c.surfaceRaised)
    }

    @Test
    fun badgeAndIdleText() {
        assertText("badge text", c.badgeText, c.badge)
        assertText("idle tile text", c.idleTileText, c.surfaceRaised)
    }

    @Test
    fun kindIconsOnTheirTiles() {
        assertGraphic("stream", c.kindStream.icon, c.kindStream.tile)
        assertGraphic("line-in", c.kindLineIn.icon, c.kindLineIn.tile)
        assertGraphic("file", c.kindFile.icon, c.kindFile.tile)
        assertGraphic("link", c.kindLink.icon, c.kindLink.tile)
    }

    @Test
    fun otherIcons() {
        assertGraphic("pill speaker on accentContainer", c.accent, c.accentContainer)
        assertGraphic("pill speaker on surfaceRaised", c.accent, c.surfaceRaised)
        assertGraphic("pill speaker (disabled) on accentContainer", c.textMuted, c.accentContainer)
        assertGraphic("nav icon unselected", c.textMuted, c.background)
        assertGraphic("nav icon selected", c.accent, c.background)
        assertGraphic("stop icon on its button", c.text, c.outline)
        assertGraphic("pause icon on its button", c.onAccent, c.accent)
        assertGraphic("disabled action icon", c.textMuted, c.surfaceRaised)
        assertGraphic("idle play icon", c.accent, c.badge)
        assertGraphic("off-room icon", c.offTileIcon, c.offTile)
    }
}
