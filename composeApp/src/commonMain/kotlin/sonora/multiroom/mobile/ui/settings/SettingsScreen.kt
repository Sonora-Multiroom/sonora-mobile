package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.domain.appVersionLabel
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import sonora.multiroom.mobile.ui.theme.minTouchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        onTextChange = viewModel::onTextChange,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    versionLabel: String = appVersionLabel(),
) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = type.screenTitle, color = colors.text)

        TextField(
            value = state.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hub address", style = type.body14) },
            placeholder = { Text("multiroom.lan:8080", style = type.body16) },
            textStyle = type.body16,
            singleLine = true,
            isError = state.error != null,
            supportingText = {
                Text(
                    text = state.error ?: "The hub's name or IP on your home network",
                    style = type.body13,
                    color = if (state.error != null) colors.warningText else colors.textMuted,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            shape = SonoraTheme.shapes.smallTile,
            colors = TextFieldDefaults.colors(
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                errorContainerColor = colors.surface,
                focusedIndicatorColor = colors.accent,
                unfocusedIndicatorColor = colors.outline,
                errorIndicatorColor = colors.warningText,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textMuted,
                errorLabelColor = colors.warningText,
                cursorColor = colors.accent,
                errorCursorColor = colors.accent,
                focusedPlaceholderColor = colors.textMuted,
                unfocusedPlaceholderColor = colors.textMuted,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.accent, SonoraTheme.shapes.pill)
                .clickable(role = Role.Button, onClick = onSave),
            contentAlignment = Alignment.Center,
        ) {
            Text("Save", style = type.button16, color = colors.onAccent)
        }

        if (state.saved) {
            Text("Saved", style = type.body14, color = colors.textMuted, modifier = Modifier.height(minTouchTarget))
        }

        // Version footer (FR-021a): tells testers which build they run, including the alpha stage.
        Spacer(Modifier.weight(1f))
        Text(
            text = versionLabel,
            style = type.label12,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
