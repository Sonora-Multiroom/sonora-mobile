package sonora.multiroom.mobile.ui.rooms

import sonora.multiroom.mobile.domain.SourceKind
import sonora.multiroom.mobile.ui.theme.KindColors
import sonora.multiroom.mobile.ui.theme.SonoraColors
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import androidx.compose.ui.graphics.vector.ImageVector

fun SonoraColors.forKind(kind: SourceKind): KindColors = when (kind) {
    SourceKind.Stream -> kindStream
    SourceKind.LineIn -> kindLineIn
    SourceKind.File -> kindFile
    SourceKind.Link -> kindLink
}

fun iconForKind(kind: SourceKind): ImageVector = when (kind) {
    SourceKind.Stream -> SonoraIcons.Stream
    SourceKind.LineIn -> SonoraIcons.LineIn
    SourceKind.File -> SonoraIcons.File
    SourceKind.Link -> SonoraIcons.Link
}
