package sonora.multiroom.mobile.ui.startplayback

import sonora.multiroom.mobile.domain.RadioRows
import sonora.multiroom.mobile.domain.Station
import sonora.multiroom.mobile.ui.radio.StationRow
import sonora.multiroom.mobile.ui.theme.SonoraIcons
import sonora.multiroom.mobile.ui.theme.SonoraTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "Or a radio station": the "Find a radio station" row (or, once a station that is not among the
 * recent ones is chosen, that station with "Change"), then up to three recent stations
 * (contracts/radio-ui.md "Start Playback").
 */
@Composable
internal fun RadioSection(
    rows: RadioRows,
    stale: Boolean,
    enabled: Boolean,
    onOpenRadio: () -> Unit,
    onSelectStation: (Station) -> Unit,
) {
    val colors = SonoraTheme.colors
    SectionLegend(RADIO_SECTION_LEGEND, Modifier.padding(top = 6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val found = rows.found
        if (found != null) {
            StationRow(
                station = found,
                secondLine = stationSecondLineInStartPlayback(found),
                selected = true,
                scrollName = true,
                stale = stale,
                enabled = enabled,
                spoken = selectedStationSpoken(found),
                onClick = onOpenRadio,
                trailing = { Text(CHANGE_STATION, style = SonoraTheme.type.body13Semi, color = colors.accent) },
            )
        } else {
            FindStationRow(enabled, onOpenRadio)
        }
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (station in rows.recent) {
                val selected = station.uuid == rows.selectedUuid
                StationRow(
                    station = station,
                    secondLine = stationSecondLineInStartPlayback(station),
                    selected = selected,
                    scrollName = selected,
                    stale = stale,
                    enabled = enabled,
                    role = Role.RadioButton,
                    // Tapping the row that is already selected changes nothing.
                    onClick = { if (!selected) onSelectStation(station) },
                    trailing = { RadioDot(selected, 20.dp) },
                )
            }
        }
    }
}

@Composable
private fun FindStationRow(enabled: Boolean, onClick: () -> Unit) {
    val colors = SonoraTheme.colors
    val type = SonoraTheme.type
    val shape = SonoraTheme.shapes.tile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(colors.surface, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(colors.surfaceRaised, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SonoraIcons.Search, contentDescription = null, tint = colors.textSoft, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(FIND_STATION_TITLE, style = type.body15.copy(fontWeight = FontWeight.SemiBold), color = colors.text)
            Text(FIND_STATION_SUBTITLE, style = type.label12, color = colors.textMuted)
        }
        Icon(SonoraIcons.Chevron, contentDescription = null, tint = colors.chevron, modifier = Modifier.size(20.dp))
    }
}
