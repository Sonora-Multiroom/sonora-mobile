package sonora.multiroom.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/** A kind tile: background and icon colour. */
data class KindColors(val tile: Color, val icon: Color)

/** Every colour of the design (AGENTS.md "Design" tokens, plus the few extras in Main.dc.html). */
data class SonoraColors(
    val background: Color = Color(0xFF0E0F12),
    val surface: Color = Color(0xFF17191E),
    val surfaceRaised: Color = Color(0xFF22252B),
    val outline: Color = Color(0xFF2C3038),
    val text: Color = Color(0xFFF2F1EE),
    val textMuted: Color = Color(0xFF9A9DA6),
    val accent: Color = Color(0xFFF2A541),
    val onAccent: Color = Color(0xFF1A1206),
    val accentContainer: Color = Color(0xFF4A3412),
    val selectedBg: Color = Color(0xFF1F1A12),
    val selectedOutline: Color = Color(0xFF6B4C1F),
    val warningText: Color = Color(0xFFF2D3A4),
    val kindStream: KindColors = KindColors(Color(0xFF3A2A12), Color(0xFFF2A541)),
    val kindLineIn: KindColors = KindColors(Color(0xFF12302E), Color(0xFF5FD3C4)),
    val kindFile: KindColors = KindColors(Color(0xFF261F3A), Color(0xFFB49CFF)),
    val kindLink: KindColors = KindColors(Color(0xFF3A1E14), Color(0xFFFF8A5B)),
    // Extras used by the Rooms design: badge, idle tiles, nav divider.
    val badge: Color = Color(0xFF262A31),
    val badgeText: Color = Color(0xFFC9CBD1),
    val idleTileText: Color = Color(0xFFC9CBD1),
    val offTile: Color = Color(0xFF1B1D22),
    val offTileIcon: Color = Color(0xFF6E727B),
    val navDivider: Color = Color(0xFF1F2228),
    // Extras used by Now Playing and the Move sheet (design/screens/NowPlaying + Transfer).
    /** Secondary controls and icons (back control, Cancel, tile icons). */
    val textSoft: Color = Color(0xFFC9CBD1),
    /** Member pill labels. */
    val textBright: Color = Color(0xFFE4E3DF),
    /** The Now Playing panel behind a stream, and the Playing chip. */
    val streamPanel: Color = Color(0xFF2A1F10),
    val sheetHandle: Color = Color(0xFF3A3E46),
    val scrim: Color = Color(0xB8050608),
    /** Black at 40 %: behind the "Live stream" badge. */
    val badgeScrim: Color = Color(0x66000000),
)
