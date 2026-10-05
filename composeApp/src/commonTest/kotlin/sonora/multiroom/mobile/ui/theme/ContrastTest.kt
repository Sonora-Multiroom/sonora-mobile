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

    // ---- Now Playing and the Move sheet (002, FR-027). Dimmed text of disabled controls and
    // unselectable rows is exempt (Constitution VI 1.2.1) and not listed here. -----------------

    /** [fg] at [alpha] composited over opaque [bg]. */
    private fun over(fg: Color, alpha: Float, bg: Color) = Color(
        red = fg.red * alpha + bg.red * (1 - alpha),
        green = fg.green * alpha + bg.green * (1 - alpha),
        blue = fg.blue * alpha + bg.blue * (1 - alpha),
    )

    @Test
    fun nowPlayingSecondaryText() {
        for ((bgName, bg) in listOf("background" to c.background, "surface" to c.surface)) {
            assertText("textSoft on $bgName", c.textSoft, bg)
            assertText("textBright on $bgName", c.textBright, bg)
        }
        // Member pill labels and percentages sit on both halves of the pill.
        assertText("member label on surfaceRaised", c.textBright, c.surfaceRaised)
        assertText("member label on accentContainer", c.textBright, c.accentContainer)
        // textMuted would be 4.33:1 on accentContainer, so a muted member's number is textSoft.
        assertText("muted member % on surfaceRaised", c.textSoft, c.surfaceRaised)
        assertText("muted member % on accentContainer", c.textSoft, c.accentContainer)
    }

    @Test
    fun liveBadgeAndStatusChips() {
        // The badge is black 40 % over the stream panel.
        assertText("live badge", c.warningText, over(Color.Black, 0.4f, c.streamPanel))
        assertText("Playing chip", c.accent, c.streamPanel)
        assertText("Paused chip", c.textSoft, c.surfaceRaised)
        assertText("other chips", c.textMuted, c.surfaceRaised)
        assertText("Failed chip", c.warningText, c.accentContainer)
    }

    @Test
    fun moveSheetRows() {
        for ((bgName, bg) in listOf("surface" to c.surface, "selected row" to c.selectedBg)) {
            assertText("label on $bgName", c.text, bg)
            assertText("note on $bgName", c.textMuted, bg)
            assertText("warning note on $bgName", c.warningText, bg)
        }
        assertText("legend on surface", c.textMuted, c.surface)
        assertText("Cancel on surface", c.textSoft, c.surface)
        assertText("button label", c.onAccent, c.accent)
    }

    @Test
    fun nowPlayingIcons() {
        assertGraphic("tile icon on surfaceRaised", c.textSoft, c.surfaceRaised)
        assertGraphic("radio on surface", c.textMuted, c.surface)
        assertGraphic("radio selected on selected row", c.accent, c.selectedBg)
        assertGraphic("keeps-playing check on surface", c.kindLineIn.icon, c.surface)
        assertGraphic("action icon on surface", c.text, c.surface)
        assertGraphic("mute icon on surface", c.textSoft, c.surface)
        assertGraphic("stop icon on accent", c.onAccent, c.accent)
        // The panel disc: kind icon colour behind the background-coloured glyph.
        for (k in listOf(c.kindLineIn, c.kindFile, c.kindLink)) assertGraphic("disc glyph", c.background, k.icon)
        assertGraphic("stream disc glyph", c.onAccent, c.accent)
        // The selected row's #6B4C1F outline (2.24:1) is a redundant cue drawn as in the design:
        // the selection is also carried by the accent radio dot above and by the row's semantics.
    }

    @Test
    fun startPlaybackPairs() {
        // Source rows are transparent over the background when not selected; tiles are surface.
        for ((bgName, bg) in listOf("background" to c.background, "surface" to c.surface, "selected" to c.selectedBg)) {
            assertText("name on $bgName", c.text, bg)
            assertText("kind or status on $bgName", c.textMuted, bg)
            assertGraphic("radio on $bgName", c.textMuted, bg)
        }
        assertGraphic("selected radio on selected", c.accent, c.selectedBg)
        assertText("link text on its field", c.text, c.surface)
        assertText("link placeholder on its field", c.textMuted, c.surface)
        assertGraphic("link icon on its field", c.textMuted, c.surface)
        assertText("link message on background", c.warningText, c.background)
        assertGraphic("Close icon on background", c.textSoft, c.background)
        assertText("warning line on background", c.warningText, c.background)
        assertText("muted lines on background", c.textMuted, c.background)
        assertText("Play label on accent", c.onAccent, c.accent)
        // Dimmed unselectable tiles are exempt (Constitution VI).
    }

    // ---- Settings (004, research R12) --------------------------------------------------------

    @Test
    fun settingsPairs() {
        assertText("danger on dangerContainer", c.danger, c.dangerContainer)
        assertText("onDanger on danger", c.onDanger, c.danger)
        assertText("positive on positiveContainer", c.positive, c.positiveContainer)
        assertText("positive on surface", c.positive, c.surface)
        assertText("danger on surface", c.danger, c.surface)
        assertText("textSoft on surfaceRaised", c.textSoft, c.surfaceRaised)
        assertText("accent on surface (Playing line)", c.accent, c.surface)
        assertText("text on outline (selected tab)", c.text, c.outline)
    }
}
