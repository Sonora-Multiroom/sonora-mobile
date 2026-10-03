package sonora.multiroom.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons rebuilt from the inline SVG of the design/screens .dc.html files: 24x24 viewport, 1.8 stroke, round
 * caps and joins. Colours are applied by the caller (tint), so the vectors are drawn in black.
 */
object SonoraIcons {
    private fun icon(
        name: String,
        filled: Boolean = false,
        vararg paths: String,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (d in paths) {
            if (filled) {
                addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
            } else {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
    }.build()

    /** `<circle>` as an arc path. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    /** `<rect rx>` as a path. */
    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float): String =
        "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx $rx v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} $rx " +
            "h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

    private const val SPEAKER = "M4 9h4l5-4v14l-5-4H4z"

    val Speaker = icon("speaker", paths = arrayOf(SPEAKER, "M17 9a4 4 0 0 1 0 6"))
    val SpeakerMuted = icon("speakerMuted", paths = arrayOf(SPEAKER, "M17 9l5 6M22 9l-5 6"))
    val Pause = icon("pause", filled = true, paths = arrayOf(rect(6f, 5f, 4f, 14f, 1f), rect(14f, 5f, 4f, 14f, 1f)))
    val Play = icon("play", filled = true, paths = arrayOf("M8 5l11 7-11 7z"))
    val Stop = icon("stop", filled = true, paths = arrayOf(rect(6f, 6f, 12f, 12f, 2f)))
    val Plus = icon("plus", paths = arrayOf("M12 5v14M5 12h14"))
    val Back = icon("back", paths = arrayOf("M15 6l-6 6 6 6"))
    val Close = icon("close", paths = arrayOf("M6 6l12 12M18 6L6 18"))
    val Rooms = icon("rooms", paths = arrayOf("M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"))
    val Sources = icon("sources", paths = arrayOf("M4 4v16M9 4v16M14 4l6 16"))
    val Settings = icon(
        "settings",
        paths = arrayOf("M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f)),
    )

    /** Kind icons: stream (radio waves), line-in (plug), file (note), link (chain). */
    val Stream = icon(
        "stream",
        paths = arrayOf(
            circle(12f, 12f, 2f),
            "M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8",
        ),
    )
    val LineIn = icon("lineIn", paths = arrayOf("M12 3v6M9 9h6v5a3 3 0 0 1-6 0zM12 17v4"))
    val File = icon(
        "file",
        paths = arrayOf("M9 18V5l11-2v13", circle(6f, 18f, 3f), circle(17f, 16f, 3f)),
    )
    val Link = icon(
        "link",
        paths = arrayOf(
            "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1",
            "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
        ),
    )

    /** A room (speaker cabinet), and the same with a slash for "not connected". */
    val Room = icon(
        "room",
        paths = arrayOf(rect(6f, 3f, 12f, 18f, 2f), circle(12f, 14f, 3f), circle(12f, 7f, 1f)),
    )
    val RoomNotConnected = icon(
        "roomNotConnected",
        paths = arrayOf(rect(6f, 3f, 12f, 18f, 2f), circle(12f, 14f, 3f), "M3 3l18 18"),
    )
    val Info = icon("info", paths = arrayOf(circle(12f, 12f, 9f), "M12 8v5M12 16h.01"))
}
