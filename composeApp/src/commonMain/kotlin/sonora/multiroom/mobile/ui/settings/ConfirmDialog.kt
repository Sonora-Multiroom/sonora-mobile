package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The turn-off / remove dialog (UI contract "Confirmation dialog"). Back and a tap outside call
 * [onDismiss], which is the same as "Keep playing". The window's dim is the platform's.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShapeCard)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)
                .semantics(mergeDescendants = false) { paneTitle = title },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(colors.dangerContainer, SonoraTheme.shapes.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SonoraIcons.StopOutline, contentDescription = null, tint = colors.danger, modifier = Modifier.size(22.dp))
            }
            Text(
                title,
                style = type.cardTitle.copy(fontSize = 20.sp),
                color = colors.text,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(body, style = type.body15.copy(lineHeight = 22.5.sp), color = colors.textSoft)
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DialogButton("Keep playing", colors.text, colors.surfaceRaised, onDismiss, Modifier.weight(1f))
                DialogButton(confirmLabel, colors.onDanger, colors.danger, onConfirm, Modifier.weight(1f))
            }
        }
    }
}

private val RoundedCornerShapeCard = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

@Composable
private fun DialogButton(label: String, textColor: Color, background: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = SonoraTheme.type.body15.copy(fontWeight = FontWeight.SemiBold), color = textColor)
    }
}
