package sonora.multiroom.mobile.ui.settings

import sonora.multiroom.mobile.ui.session.SettingsTab
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun tabLabel(tab: SettingsTab) = when (tab) {
    SettingsTab.Rooms -> "Rooms"
    SettingsTab.Groups -> "Groups"
    SettingsTab.Sources -> "Sources"
    SettingsTab.Extensions -> "Extensions"
}

/**
 * The four-way segmented bar. Each pill is drawn at 40 dp, but its touch area spans the bar's whole
 * 48 dp height, so every target meets the 44 dp minimum.
 */
@Composable
fun SettingsTabs(selected: SettingsTab, onSelect: (SettingsTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .height(48.dp)
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 2.dp)
            .selectableGroup(),
    ) {
        for (tab in SettingsTab.entries) {
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(if (isSelected) colors.outline else androidx.compose.ui.graphics.Color.Transparent, SonoraTheme.shapes.smallTile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tabLabel(tab),
                        style = SonoraTheme.type.body13.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) colors.text else colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
