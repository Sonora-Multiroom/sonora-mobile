package sonora.multiroom.mobile.ui.radio

import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "The station catalogue isn't answering" with Retry: shown when the hub answers but the radio
 * catalogue does not, above the rows already listed (inside the sheet that asked). Announced as an alert.
 */
@Composable
fun CatalogueCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SonoraTheme.shapes.card)
            .background(colors.surface)
            .padding(start = 16.dp, end = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SonoraIcons.Info, contentDescription = null, tint = colors.warningText, modifier = Modifier.size(20.dp))
        Text(
            RadioText.CATALOGUE_DOWN,
            style = type.body14,
            color = colors.warningText,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Text(
            RadioText.RETRY,
            style = type.body15.copy(fontWeight = FontWeight.SemiBold),
            color = colors.accent,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(SonoraTheme.shapes.pill)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )
    }
}
