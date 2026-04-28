package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.usecases.WidgetStationBoardUseCase
import com.saarlabs.tminus.ui.theme.readFontScale
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.SettingsKeys
import com.saarlabs.tminus.TminusApplication
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.android.util.colorFromHex
import com.saarlabs.tminus.android.util.formattedTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

public class MBTAStationBoardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    private val useCase: WidgetStationBoardUseCase
        get() = TminusApplication.widgetStationBoardUseCase

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            provideGlanceInternal(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("MBTAStationBoardWidget", "provideGlance failed", e)
            provideContent { StationBoardContent.ErrorState(context = context) }
        }
    }

    private suspend fun provideGlanceInternal(context: Context, id: GlanceId) {
        val widgetPreferences = WidgetPreferences(context.applicationContext)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            provideContent { StationBoardContent.ErrorState(context = context) }
            return
        }

        var config = withContext(Dispatchers.IO) { widgetPreferences.getStationBoardConfigOnce(appWidgetId) }
        if (config == null) {
            repeat(8) {
                delay(250)
                config = withContext(Dispatchers.IO) { widgetPreferences.getStationBoardConfigOnce(appWidgetId) }
                if (config != null) return@repeat
            }
        }
        if (config == null) {
            // #region agent log
            AgentDebugLog.log(
                "MBTAStationBoardWidget.kt:provideGlanceInternal",
                "showing station configure prompt (no saved config)",
                "H4",
                mapOf("appWidgetId" to appWidgetId),
            )
            // #endregion
            withContext(Dispatchers.IO) {
                WidgetPreferences(context.applicationContext).setPendingStationBoardConfigWidgetId(appWidgetId)
            }
            provideContent {
                StationBoardContent.ConfigurePrompt(context = context, appWidgetId = appWidgetId)
            }
            return
        }

        val cfg = checkNotNull(config)
        GlobalDataStore.awaitClientReady()
        val use24Hour =
            withContext(Dispatchers.IO) {
                context.applicationContext
                    .getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)
            }
        val fontScale = withContext(Dispatchers.IO) { readFontScale(context.applicationContext) }
        val globalData =
            when (val globalResult = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> {
                    when (
                        val retry =
                            withContext(Dispatchers.IO) {
                                GlobalDataStore.getOrLoad(forceRefresh = true)
                            }
                    ) {
                        is ApiResult.Ok -> retry.data
                        is ApiResult.Error -> {
                            provideContent {
                                StationBoardContent.LoadError(context = context, config = cfg, fontScale = fontScale)
                            }
                            return
                        }
                    }
                }
            }

        val result =
            withContext(Dispatchers.IO) {
                useCase.getDepartures(
                    globalData = globalData,
                    stopId = cfg.stopId,
                    routeFilter = cfg.routeId,
                    now = EasternTimeInstant.now(),
                    limit = 12,
                )
            }

        when (result) {
            is ApiResult.Error -> {
                provideContent { StationBoardContent.LoadError(context = context, config = cfg, fontScale = fontScale) }
            }
            is ApiResult.Ok -> {
                val departures = result.data.departures
                val stationTitle =
                    cfg.stopLabel.ifEmpty {
                        globalData.getStop(cfg.stopId)?.resolveParent(globalData.stops)?.name.orEmpty()
                    }
                provideContent {
                    StationBoardContent.Board(
                        context = context,
                        stationTitle = stationTitle,
                        departures = departures,
                        use24Hour = use24Hour,
                        fontScale = fontScale,
                    )
                }
            }
        }
    }
}

private object StationBoardContent {

    private data class BoardTypography(
        val title: TextUnit,
        val subtitle: TextUnit,
        val route: TextUnit,
        val headsign: TextUnit,
        val time: TextUnit,
        val caption: TextUnit,
        val padding: Dp,
        val routeBoxPaddingH: Dp,
        val routeBoxPaddingV: Dp,
        val routeCorner: Dp,
        val gapSmall: Dp,
        val gapMedium: Dp,
        val rowPaddingV: Dp,
        val rowCorner: Dp,
        val routeChipMinWidth: Dp,
        val stackTime: Boolean,
    )

    @Composable
    private fun typography(fontScale: Float): BoardTypography {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = minOf(w, h)
        val baseScale = (shortEdge / 112f).coerceIn(0.55f, 2.2f)
        // Widgets have far less horizontal room than the in-app screens, so blunt the user's
        // fontScale a bit (1.6× in app → ~1.24× here) to keep the route chip + minutes column from
        // clipping the time on long route names like "Framingham/Worcester".
        val widgetFontScale = (0.6f + 0.4f * fontScale)
        val scale = baseScale * widgetFontScale
        // Stack minutes under the headsign on typical phone widget widths so the destination
        // line is not squeezed beside a fixed-width time column (was clipping long headsigns).
        val stackTime = w < 300f
        val padding = (12f * baseScale).coerceIn(8f, 20f).dp
        val title = (16f * scale).coerceIn(12f, 28f).sp
        val subtitle = (11f * scale).coerceIn(9f, 18f).sp
        val route = (11f * scale).coerceIn(9f, 18f).sp
        val headsign = (13f * scale).coerceIn(10f, 22f).sp
        val time = (14f * scale).coerceIn(11f, 24f).sp
        val caption = (11f * scale).coerceIn(9f, 18f).sp
        val gapSmall = (4f * baseScale).coerceIn(3f, 10f).dp
        val gapMedium = (8f * baseScale).coerceIn(4f, 14f).dp
        val routeBoxH = (8f * baseScale).coerceIn(6f, 14f).dp
        val routeBoxV = (5f * baseScale).coerceIn(4f, 10f).dp
        val routeCorner = (10f * baseScale).coerceIn(8f, 16f).dp
        val rowPaddingV = (8f * baseScale).coerceIn(6f, 14f).dp
        val rowCorner = (14f * baseScale).coerceIn(10f, 20f).dp
        val routeChipMinWidth = (68f * baseScale).coerceIn(52f, 110f).dp
        return BoardTypography(
            title = title,
            subtitle = subtitle,
            route = route,
            headsign = headsign,
            time = time,
            caption = caption,
            padding = padding,
            routeBoxPaddingH = routeBoxH,
            routeBoxPaddingV = routeBoxV,
            routeCorner = routeCorner,
            gapSmall = gapSmall,
            gapMedium = gapMedium,
            rowPaddingV = rowPaddingV,
            rowCorner = rowCorner,
            routeChipMinWidth = routeChipMinWidth,
            stackTime = stackTime,
        )
    }

    /**
     * Keeps route pills readable inside a width-capped chip: shortens common commuter-rail patterns
     * (e.g. "Framingham / Worcester Line" → "Framingham/Worcester") while leaving names like "Red Line" unchanged.
     */
    private fun routeLabelForWidget(label: String): String {
        var s = label.trim().replace(" / ", "/")
        if (s.contains("/") && s.endsWith(" Line", ignoreCase = true)) {
            s = s.removeSuffix(" Line").trimEnd()
        }
        return s
    }

    private fun surfaceColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_surface).toLong() and 0xFFFFFFFFL)

    private fun headerColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_header).toLong() and 0xFFFFFFFFL)

    private fun onHeaderColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_on_header).toLong() and 0xFFFFFFFFL)

    private fun primaryTextColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_on_surface).toLong() and 0xFFFFFFFFL)

    private fun secondaryTextColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_on_surface_variant).toLong() and 0xFFFFFFFFL)

    private fun rowBackground(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_row_bg).toLong() and 0xFFFFFFFFL)

    private fun accentColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_accent).toLong() and 0xFFFFFFFFL)

    @Composable
    fun ConfigurePrompt(context: Context, appWidgetId: Int) {
        val fontScale = readFontScale(context.applicationContext)
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surfaceColor(context))
                        .cornerRadius(20.dp)
                        .padding(t.padding)
                        .clickable(
                            androidx.glance.appwidget.action.actionStartActivity(
                                Intent(context, StationBoardWidgetConfigActivity::class.java).apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                },
                                actionParametersOf(),
                            ),
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_station_board_label),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryTextColor(context)),
                            fontSize = t.title,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_station_board_configure_hint),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(secondaryTextColor(context)),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Box(
                    modifier =
                        GlanceModifier
                            .background(accentColor(context))
                            .cornerRadius(20.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = context.getString(R.string.widget_configure),
                        style =
                            TextStyle(
                                color = ColorProvider(onHeaderColor(context)),
                                fontSize = t.subtitle,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    fun ErrorState(context: Context) {
        val fontScale = readFontScale(context.applicationContext)
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surfaceColor(context))
                        .cornerRadius(20.dp)
                        .padding(t.padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_unable_to_load),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryTextColor(context)),
                            fontSize = t.title,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                RefreshPill(context = context, t = t)
            }
        }
    }

    @Composable
    fun LoadError(context: Context, config: WidgetStationBoardConfig, fontScale: Float) {
        val stopLabel = config.stopLabel.ifEmpty { context.getString(R.string.widget_station_board_selecting_stop) }
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surfaceColor(context))
                        .cornerRadius(20.dp)
                        .padding(t.padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stopLabel,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryTextColor(context)),
                            fontSize = t.title,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 3,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_station_board_times_error),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(secondaryTextColor(context)),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 6,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                RefreshPill(context = context, t = t)
            }
        }
    }

    @Composable
    private fun RefreshPill(context: Context, t: BoardTypography) {
        Box(
            modifier =
                GlanceModifier
                    .background(accentColor(context))
                    .cornerRadius(20.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(actionRunCallback<WidgetRefreshActionCallback>(actionParametersOf())),
        ) {
            Text(
                text = context.getString(R.string.widget_tap_to_refresh),
                style =
                    TextStyle(
                        color = ColorProvider(onHeaderColor(context)),
                        fontSize = t.subtitle,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
            )
        }
    }

    @Composable
    fun Board(
        context: Context,
        stationTitle: String,
        departures: List<WidgetStationBoardDeparture>,
        use24Hour: Boolean,
        fontScale: Float,
    ) {
        val stopLabel =
            stationTitle.ifEmpty { context.getString(R.string.widget_station_board_selecting_stop) }
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surfaceColor(context))
                        .cornerRadius(20.dp),
            ) {
                // Colorful header band
                Column(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .background(headerColor(context))
                            .padding(horizontal = t.padding, vertical = t.padding)
                            .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stopLabel,
                            modifier = GlanceModifier.defaultWeight(),
                            style =
                                TextStyle(
                                    color = ColorProvider(onHeaderColor(context)),
                                    fontSize = t.title,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 2,
                        )
                        Spacer(modifier = GlanceModifier.width(t.gapSmall))
                        Box(
                            modifier =
                                GlanceModifier
                                    .background(Color(0x33FFFFFF))
                                    .cornerRadius(14.dp)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .clickable(
                                        actionRunCallback<WidgetRefreshActionCallback>(
                                            actionParametersOf(),
                                        ),
                                    ),
                        ) {
                            Text(
                                text = context.getString(R.string.widget_refresh_short),
                                style =
                                    TextStyle(
                                        color = ColorProvider(onHeaderColor(context)),
                                        fontSize = t.caption,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.height(t.gapSmall))
                    Text(
                        text = context.getString(R.string.widget_station_board_scheduled_subtitle),
                        style =
                            TextStyle(
                                color =
                                    ColorProvider(
                                        onHeaderColor(context).copy(alpha = 0.85f),
                                    ),
                                fontSize = t.subtitle,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 1,
                    )
                }

                if (departures.isEmpty()) {
                    Column(
                        modifier =
                            GlanceModifier.fillMaxSize()
                                .padding(t.padding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = context.getString(R.string.widget_station_board_no_departures),
                            modifier = GlanceModifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = ColorProvider(secondaryTextColor(context)),
                                    fontSize = t.caption,
                                    textAlign = TextAlign.Center,
                                ),
                            maxLines = 4,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier =
                            GlanceModifier.fillMaxSize()
                                .padding(horizontal = t.padding, vertical = t.gapSmall),
                    ) {
                        items(departures) { d ->
                            Column {
                                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                                DepartureRow(
                                    context = context,
                                    departure = d,
                                    use24Hour = use24Hour,
                                    typography = t,
                                    stackTime = t.stackTime,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DepartureRow(
        context: Context,
        departure: WidgetStationBoardDeparture,
        use24Hour: Boolean,
        typography: BoardTypography,
        stackTime: Boolean,
    ) {
        val fallback = primaryTextColor(context)
        val routeColor =
            runCatching { colorFromHex(departure.route.color) }.getOrElse { fallback }
        val routeTextColor =
            runCatching { colorFromHex(departure.route.textColor) }.getOrElse { Color.White }
        val primary = primaryTextColor(context)
        val secondary = secondaryTextColor(context)
        val rowBg = rowBackground(context)
        val t = typography
        val minutesText =
            if (departure.minutesUntil <= 0) {
                context.getString(R.string.widget_now)
            } else {
                context.getString(R.string.widget_min_short, departure.minutesUntil)
            }
        val clockText = departure.departureTime.formattedTime(use24Hour)

        Row(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(rowBg)
                    .cornerRadius(t.rowCorner)
                    .padding(horizontal = t.gapMedium, vertical = t.rowPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left accent bar using route color
            Box(
                modifier =
                    GlanceModifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(routeColor)
                        .cornerRadius(4.dp),
            ) {}
            Spacer(modifier = GlanceModifier.width(t.gapMedium))
            Box(
                modifier =
                    GlanceModifier
                        .background(routeColor)
                        .cornerRadius(t.routeCorner)
                        .padding(horizontal = t.routeBoxPaddingH, vertical = t.routeBoxPaddingV),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = routeLabelForWidget(departure.route.label),
                    style =
                        TextStyle(
                            color = ColorProvider(routeTextColor),
                            fontSize = t.route,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 2,
                )
            }
            Spacer(modifier = GlanceModifier.width(t.gapMedium))
            if (stackTime) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = departure.headsign,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(primary),
                                fontSize = t.headsign,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 3,
                    )
                    departure.platform?.let { plat ->
                        Text(
                            text = context.getString(R.string.widget_track_short, plat),
                            style =
                                TextStyle(
                                    color = ColorProvider(secondary),
                                    fontSize = t.caption,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(t.gapSmall))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = minutesText,
                            style =
                                TextStyle(
                                    color = ColorProvider(routeColor),
                                    fontSize = t.time,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 1,
                        )
                        Spacer(modifier = GlanceModifier.width(t.gapSmall))
                        Text(
                            text = clockText,
                            style =
                                TextStyle(
                                    color = ColorProvider(secondary),
                                    fontSize = t.caption,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = departure.headsign,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(primary),
                                fontSize = t.headsign,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 3,
                    )
                    departure.platform?.let { plat ->
                        Text(
                            text = context.getString(R.string.widget_track_short, plat),
                            style =
                                TextStyle(
                                    color = ColorProvider(secondary),
                                    fontSize = t.caption,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.width(t.gapSmall))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = minutesText,
                        style =
                            TextStyle(
                                color = ColorProvider(routeColor),
                                fontSize = t.time,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                            ),
                        maxLines = 1,
                    )
                    Text(
                        text = clockText,
                        style =
                            TextStyle(
                                color = ColorProvider(secondary),
                                fontSize = t.caption,
                                textAlign = TextAlign.End,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
