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
import androidx.glance.layout.fillMaxHeight
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
import kotlin.math.max
import kotlin.math.pow

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
            // Config may land milliseconds after Glance starts composing (save vs update race).
            repeat(36) {
                delay(200)
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
                widgetPreferences.setPendingStationBoardConfigWidgetId(appWidgetId)
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
                    destinationFilter = cfg.destinationHeadsign,
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
                val subtitleSecond =
                    cfg.destinationHeadsign?.takeIf { it.isNotBlank() }?.let { dest ->
                        context.getString(R.string.widget_station_board_scheduled_filtered, dest)
                    }
                        ?: context.getString(R.string.widget_station_board_scheduled_subtitle)
                provideContent {
                    StationBoardContent.Board(
                        context = context,
                        stationTitle = stationTitle,
                        scheduledSubtitle = subtitleSecond,
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
        val routeBannerMaxLines: Int,
        /** Station name in board header — slightly below main [title] tier but readable. */
        val stationHeaderTitle: TextUnit,
        /** "Scheduled departures" — match general [subtitle] scaling so it stays legible. */
        val stationHeaderSubtitle: TextUnit,
        /** Space below destination before the schedule line (tighter when height is limited). */
        val scheduleGapBelowDestination: Dp,
        /** LazyColumn bottom inset — shrinks when vertical space is tight so two rows fit. */
        val listPaddingBottom: Dp,
        /** Blue header band vertical padding (can differ from row gaps). */
        val headerBandPaddingVertical: Dp,
    )

    @Composable
    private fun typography(fontScale: Float): BoardTypography {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = minOf(w, h)
        // Anchor to appwidget min dimensions so resizing moves sizes smoothly (xml: minWidth 180, minHeight 155).
        val refShort = 155f
        val refHeight = 220f
        val verticalTight = h < 275f
        val tightFactor = if (verticalTight) 0.88f else 1f
        // When the widget is short, tighten departure rows so two trips usually fit without clipping.
        val verticalCompact =
            when {
                h < 255f -> 0.74f
                h < 295f -> 0.82f
                h < 345f -> 0.90f
                else -> 1f
            }
        // Short edge + height both shrink typography when the viewport gets cramped (wide-but-short, etc.).
        val edgeScale = (shortEdge / refShort).coerceIn(0.42f, 2.75f)
        val heightScale = (h / refHeight).coerceIn(0.48f, 1.38f)
        val viewportScale = edgeScale * heightScale
        // Widgets have far less horizontal room than the in-app screens, so blunt the user's
        // fontScale a bit (1.6× in app → ~1.24× here) to keep the route chip + minutes column from
        // clipping the time on long route names like "Framingham/Worcester".
        val widgetFontScale = (0.6f + 0.4f * fontScale)
        val scale = viewportScale * widgetFontScale * tightFactor
        // Smaller text overall so more trips fit; header gets extra compaction.
        val dense = scale * 0.72f
        val gapScalar = (shortEdge / refShort).coerceIn(0.45f, 2.5f)
        val padding = (11f * viewportScale).coerceIn(5f, 18f).dp
        // Wide dynamic range — avoid fixed floors so tiny placements keep shrinking with LocalSize.
        val title = (15f * dense).coerceIn(7f, 26f).sp
        val subtitle = (10f * dense).coerceIn(6f, 17f).sp
        val stationHeaderTitle = (14f * dense).coerceIn(12f, 22f).sp
        val stationHeaderSubtitle = (10f * dense).coerceIn(9f, 16f).sp
        // Slightly smaller route banner text when the widget is narrow so wrapped lines fit.
        val routeShrink =
            when {
                shortEdge < 158f -> 0.82f
                shortEdge < 178f -> 0.88f
                verticalTight -> 0.92f
                else -> 1f
            }
        val route = (10f * dense * routeShrink * verticalCompact.pow(0.88f)).coerceIn(5f, 14f).sp
        val headsign = (11f * dense * verticalCompact.pow(0.92f)).coerceIn(7f, 16f).sp
        val time = (10.5f * dense * verticalCompact).coerceIn(7f, 15f).sp
        val caption = (10f * dense).coerceIn(6f, 15f).sp
        val gapSmall = (4f * gapScalar * verticalCompact).coerceIn(2f, 10f).dp
        val gapMedium = (8f * gapScalar * verticalCompact).coerceIn(3f, 14f).dp
        val routeBoxH = (8f * gapScalar * verticalCompact).coerceIn(4f, 13f).dp
        val routeBoxV = (3.5f * gapScalar * verticalCompact).coerceIn(2f, 7f).dp
        val routeCorner = (10f * gapScalar * verticalCompact).coerceIn(6f, 16f).dp
        val rowPaddingV = (5f * gapScalar * verticalCompact).coerceIn(3f, 10f).dp
        val rowCorner = (14f * gapScalar * verticalCompact).coerceIn(8f, 21f).dp
        // Always allow several wrapped lines in the colored route banner — single-line mode caused
        // "Framingham/Worcester …" ellipsis on narrow widgets.
        val routeBannerMaxLines = if (verticalTight) 3 else 4
        val scheduleGapBelowDestination =
            when {
                h < 265f -> 1.dp
                h < 315f -> 2.dp
                else -> 3.dp
            }
        val listPaddingBottom = max(padding.value * verticalCompact, 4f).dp
        val headerBandPaddingVertical =
            (6f * gapScalar * verticalCompact).coerceIn(4f, 11f).dp
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
            routeBannerMaxLines = routeBannerMaxLines,
            stationHeaderTitle = stationHeaderTitle,
            stationHeaderSubtitle = stationHeaderSubtitle,
            scheduleGapBelowDestination = scheduleGapBelowDestination,
            listPaddingBottom = listPaddingBottom,
            headerBandPaddingVertical = headerBandPaddingVertical,
        )
    }

    /**
     * Normalizes commuter-rail-style labels and splits long "A/B" names onto two lines so the
     * banner wraps instead of ellipsizing on narrow widgets.
     */
    private fun routeLabelForWidget(label: String): String {
        var s = label.trim().replace(" / ", "/")
        if (s.contains("/") && s.endsWith(" Line", ignoreCase = true)) {
            s = s.removeSuffix(" Line").trimEnd()
        }
        val slash = s.indexOf('/')
        if (slash in 1 until s.lastIndex && s.length > 12) {
            val left = s.substring(0, slash).trim()
            val right = s.substring(slash + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                s = "$left\n$right"
            }
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
                        .cornerRadius(22.dp),
            ) {
                // Match board chrome: thin header strip so setup empty states feel cohesive.
                Column(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .background(headerColor(context))
                            .padding(horizontal = t.padding, vertical = t.gapMedium),
                ) {
                    Text(
                        text = context.getString(R.string.widget_station_board_label),
                        style =
                            TextStyle(
                                color = ColorProvider(onHeaderColor(context)),
                                fontSize = t.subtitle,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 1,
                    )
                }
                Column(
                    modifier =
                        GlanceModifier.fillMaxSize()
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
                        text = context.getString(R.string.widget_station_board_configure_hint),
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(secondaryTextColor(context)),
                                fontSize = t.caption,
                                textAlign = TextAlign.Center,
                            ),
                        maxLines = 5,
                    )
                    Spacer(modifier = GlanceModifier.height(t.gapMedium))
                    Box(
                        modifier =
                            GlanceModifier
                                .background(accentColor(context))
                                .cornerRadius(999.dp)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
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
                        .cornerRadius(22.dp)
                        .padding(t.padding)
                        .clickable(actionStartActivity<MainActivity>()),
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
                        .cornerRadius(22.dp)
                        .padding(t.padding)
                        .clickable(actionStartActivity<MainActivity>()),
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
            }
        }
    }

    @Composable
    fun Board(
        context: Context,
        stationTitle: String,
        scheduledSubtitle: String,
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
                        .cornerRadius(22.dp),
            ) {
                // Colorful header band
                Column(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .background(headerColor(context))
                            .padding(horizontal = t.padding, vertical = t.headerBandPaddingVertical)
                            .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        text = stopLabel,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(onHeaderColor(context)),
                                fontSize = t.stationHeaderTitle,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 3,
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = scheduledSubtitle,
                        style =
                            TextStyle(
                                color =
                                    ColorProvider(
                                        onHeaderColor(context).copy(alpha = 0.82f),
                                    ),
                                fontSize = t.stationHeaderSubtitle,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 2,
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
                                .padding(
                                    start = t.padding,
                                    end = t.padding,
                                    top = t.gapSmall,
                                    bottom = t.listPaddingBottom,
                                ),
                    ) {
                        items(departures.size) { index ->
                            val d = departures[index]
                            Column {
                                if (index > 0) {
                                    Spacer(modifier = GlanceModifier.height(t.gapSmall))
                                }
                                DepartureRow(
                                    context = context,
                                    departure = d,
                                    use24Hour = use24Hour,
                                    typography = t,
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
        val clockLine =
            departure.platform?.let { plat ->
                "${clockText} · ${context.getString(R.string.widget_track_short, plat)}"
            } ?: clockText

        Row(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(rowBg)
                    .cornerRadius(t.rowCorner)
                    .padding(horizontal = t.gapMedium, vertical = t.rowPaddingV),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    GlanceModifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(routeColor)
                        .cornerRadius(4.dp),
            ) {}
            Spacer(modifier = GlanceModifier.width(t.gapMedium))
            Column(modifier = GlanceModifier.defaultWeight()) {
                // Full-width line banner so long names (e.g. commuter rail) never squeeze the destination.
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxWidth()
                            .background(routeColor)
                            .cornerRadius(t.routeCorner)
                            .padding(horizontal = t.routeBoxPaddingH, vertical = t.routeBoxPaddingV),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = routeLabelForWidget(departure.route.label),
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(routeTextColor),
                                fontSize = t.route,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = t.routeBannerMaxLines,
                    )
                }
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
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
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.scheduleGapBelowDestination))
                Text(
                    text = "$minutesText · $clockLine",
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(routeColor),
                            fontSize = t.time,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                        ),
                    maxLines = 3,
                )
            }
        }
    }
}
