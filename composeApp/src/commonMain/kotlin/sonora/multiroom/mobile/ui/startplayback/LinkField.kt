package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal const val LINK_MESSAGE = "Enter a web address (https://…)"

/** "Paste a link": label, field and the inline message (contracts/start-playback-ui.md). */
@Composable
internal fun LinkField(
    text: String,
    messageShown: Boolean,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PASTE A LINK", style = type.sectionLabel, color = colors.textMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.surface, shape)
                .then(if (focused) Modifier.border(2.dp, colors.accent, shape) else Modifier)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SonoraIcons.Link, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text("SoundCloud, YouTube or stream URL", style = type.body15, color = colors.textMuted, maxLines = 1)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = type.body15.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onDone()
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .onFocusChanged { state ->
                            if (focused && !state.isFocused) onFocusLost()
                            focused = state.isFocused
                        }
                        .semantics {
                            contentDescription = "Paste a link"
                            if (messageShown) error(LINK_MESSAGE)
                        },
                )
            }
        }
        if (messageShown) {
            Text(LINK_MESSAGE, style = type.body13, color = colors.warningText)
        }
    }
}
