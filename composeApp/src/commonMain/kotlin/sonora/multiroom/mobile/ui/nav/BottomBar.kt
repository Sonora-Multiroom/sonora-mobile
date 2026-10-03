package sonora.multiroom.mobile.ui.nav

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private data class Tab(val destination: Destination, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Destination.Rooms, "Rooms", SonoraIcons.Rooms),
    Tab(Destination.Sources, "Sources", SonoraIcons.Sources),
    Tab(Destination.Settings, "Settings", SonoraIcons.Settings),
)

/** Rooms / Sources / Settings, per design/screens/Main.dc.html. The selected tab is accent. */
@Composable
fun BottomBar(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.navDivider))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            for (tab in tabs) {
                val isSelected = tab.destination == selected
                val tint = if (isSelected) colors.accent else colors.textMuted
                Column(
                    modifier = Modifier
                        .widthIn(min = 72.dp)
                        .height(52.dp)
                        .clickable(role = Role.Tab) { if (!isSelected) onSelect(tab.destination) }
                        .semantics { this.selected = isSelected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        tab.label,
                        style = if (isSelected) SonoraTheme.type.label12.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else SonoraTheme.type.label12,
                        color = tint,
                    )
                }
            }
        }
    }
}
