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

private enum class Item(val label: String, val icon: ImageVector) {
    Rooms("Rooms", SonoraIcons.Rooms),
    Sources("Sources", SonoraIcons.Sources),
    Settings("Settings", SonoraIcons.Settings),
}

/**
 * Rooms / Sources / Settings, per design/screens/Main.dc.html. The selected tab is accent. Sources
 * is a tab of Settings now (FR-003): it opens Settings on that tab, and only Settings is ever
 * shown as selected.
 */
@Composable
fun BottomBar(
    selected: Destination,
    onSelectRooms: () -> Unit,
    onOpenSources: () -> Unit,
    onSelectSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SonoraTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.navDivider))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            for (tab in Item.entries) {
                val isSelected = when (tab) {
                    Item.Rooms -> selected == Destination.Rooms
                    Item.Sources -> false
                    Item.Settings -> selected == Destination.Settings
                }
                val tint = if (isSelected) colors.accent else colors.textMuted
                Column(
                    modifier = Modifier
                        .widthIn(min = 72.dp)
                        .height(52.dp)
                        .clickable(role = Role.Tab) {
                            when (tab) {
                                Item.Rooms -> onSelectRooms()
                                Item.Sources -> onOpenSources()
                                Item.Settings -> onSelectSettings()
                            }
                        }
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
