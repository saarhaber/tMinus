package com.saarlabs.tminus.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop

/** Secondary line under a stop name so duplicate MBTA names (e.g. two North Stations) stay distinct. */
public fun stopSelectionSubtitleResources(stop: Stop, res: Resources): String {
    val modePart =
        when (stop.vehicleType) {
            RouteType.LIGHT_RAIL -> res.getString(R.string.stop_subtitle_light_rail)
            RouteType.HEAVY_RAIL -> res.getString(R.string.stop_subtitle_subway)
            RouteType.COMMUTER_RAIL -> res.getString(R.string.stop_subtitle_commuter_rail)
            RouteType.BUS -> res.getString(R.string.stop_subtitle_bus)
            RouteType.FERRY -> res.getString(R.string.stop_subtitle_ferry)
            null ->
                when (stop.locationType) {
                    LocationType.STATION -> res.getString(R.string.stop_subtitle_transit_hub)
                    LocationType.STOP -> res.getString(R.string.stop_subtitle_stop_standalone)
                    else -> ""
                }
        }
    val suffix =
        stop.platformCode?.takeIf { it.isNotBlank() }?.let { code ->
            res.getString(R.string.stop_subtitle_platform_suffix, code)
        }
            ?: ""
    return if (suffix.isEmpty()) modePart else "$modePart$suffix"
}

/** Single-line label for summaries (lists, buttons) when a subtitle row is not used. */
public fun stopOneLineDisplay(stop: Stop, res: Resources): String {
    val sub = stopSelectionSubtitleResources(stop, res)
    return if (sub.isEmpty()) stop.name else "${stop.name} ($sub)"
}

/**
 * Label shown on trip widgets: plain stop name. Treats legacy saved values that used
 * [stopOneLineDisplay] as equivalent to [stop.name] so "(Transit hub)" subtitles do not appear.
 */
public fun widgetTripStopDisplayLabel(configured: String, stop: Stop, res: Resources): String {
    val trimmed = configured.trim()
    if (trimmed.isEmpty() || trimmed == stop.name) return stop.name
    if (trimmed == stopOneLineDisplay(stop, res)) return stop.name
    return trimmed
}

@Composable
public fun stopSelectionSubtitle(stop: Stop): String {
    val res = LocalContext.current.resources
    return stopSelectionSubtitleResources(stop, res)
}

@Composable
public fun StopSelectionTitleWithSubtitle(
    stop: Stop,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val subtitle = stopSelectionSubtitle(stop)
    Column(modifier = modifier) {
        Text(text = stop.name, style = titleStyle)
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
