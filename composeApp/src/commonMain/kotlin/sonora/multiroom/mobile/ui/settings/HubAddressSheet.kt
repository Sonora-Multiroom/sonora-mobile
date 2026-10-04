package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import sonora.multiroom.mobile.ui.theme.minTouchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PLACEHOLDER = "http://192.168.1.10:8080"
private const val HINT = "Your phone and the hub need to be on the same network."

/**
 * The hub address sheet (UI contract "Hub address sheet"). Close, Back and a swipe call
 * [onClose], which discards the draft; Save is the only way to keep it (FR-008).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubAddressSheet(
    sheet: SheetState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(colors.outline, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hub address", style = type.cardTitle.copy(fontSize = 20.sp), color = colors.text)
                Box(
                    modifier = Modifier
                        .size(minTouchTarget)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SonoraIcons.Close, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(22.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("URL", style = type.body13Semi, color = colors.textSoft)
                BasicTextField(
                    value = sheet.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = type.body16.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(SonoraTheme.shapes.tile)
                                .background(colors.background)
                                .border(1.dp, colors.outline, SonoraTheme.shapes.tile)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (sheet.draft.isEmpty()) Text(PLACEHOLDER, style = type.body16, color = colors.textMuted)
                            inner()
                        }
                    },
                )
                Text(
                    text = sheet.error ?: HINT,
                    style = type.body13,
                    color = if (sheet.error != null) colors.warningText else colors.textMuted,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // "Test connection" is drawn but unused until the connection test lands (US3).
                SheetButton("Test connection", colors.text, colors.surfaceRaised, enabled = false, onClick = {}, Modifier.weight(1f))
                SheetButton("Save", colors.onAccent, colors.accent, enabled = true, onClick = onSave, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    textColor: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = SonoraTheme.type.body15.copy(fontWeight = FontWeight.SemiBold), color = textColor)
    }
}
