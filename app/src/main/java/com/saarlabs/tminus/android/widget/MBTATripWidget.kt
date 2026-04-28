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
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
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
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.WidgetTripConfig
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.usecases.WidgetTripUseCase
import com.saarlabs.tminus.ui.theme.readFontScale
import com.saarlabs.tminus.ui.widgetTripStopDisplayLabel
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.SettingsKeys
import com.saarlabs.tminus.TminusApplication
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.android.util.formattedTime
import com.saarlabs.tminus.android.util.colorFromHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

public class MBTATripWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    private val widgetTripUseCase: WidgetTripUseCase
        get() = TminusApplication.widgetTripUseCase

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            provideGlanceInternal(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("MBTATripWidget", "provideGlance failed", e)
            provideContent { WidgetContent.ErrorState(context = context) }
        }
    }

    private suspend fun provideGlanceInternal(context: Context, id: GlanceId) {
        val widgetPreferences = WidgetPreferences(context.applicationContext)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            provideContent { WidgetContent.ErrorState(context = context) }
            return
        }

        var config = withContext(Dispatchers.IO) { widgetPreferences.getConfigOnce(appWidgetId) }
        if (config == null) {
            repeat(36) {
                delay(200)
                config = withContext(Dispatchers.IO) { widgetPreferences.getConfigOnce(appWidgetId) }
                if (config != null) return@repeat
            }
        }
        if (config == null) {
            // #region agent log
            AgentDebugLog.log(
                "MBTATripWidget.kt:provideGlanceInternal",
                "showing trip configure prompt (no saved config)",
                "H4",
                mapOf("appWidgetId" to appWidgetId),
            )
            // #endregion
            withContext(Dispatchers.IO) {
                WidgetPreferences(context.applicationContext).setPendingConfigWidgetId(appWidgetId)
            }
            provideContent {
                WidgetContent.ConfigurePrompt(context = context, appWidgetId = appWidgetId)
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
            when (
                val globalResult = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }
            ) {
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
                                WidgetContent.LoadError(context = context, config = cfg, fontScale = fontScale)
                            }
                            return
                        }
                    }
                }
            }

        val result =
            withContext(Dispatchers.IO) {
                widgetTripUseCase.getNextTrip(
                    globalData = globalData,
                    fromStopId = cfg.fromStopId,
                    toStopId = cfg.toStopId,
                )
            }

        when (result) {
            is ApiResult.Error -> {
                provideContent { WidgetContent.LoadError(context = context, config = cfg, fontScale = fontScale) }
            }
            is ApiResult.Ok -> {
                val tripData = result.data.trip
                provideContent {
                    if (tripData != null) {
                        WidgetContent.TripData(
                            context = context,
                            config = cfg,
                            tripData = tripData,
                            use24Hour = use24Hour,
                            fontScale = fontScale,
                        )
                    } else {
                        WidgetContent.NoTrips(context = context, config = cfg, fontScale = fontScale)
                    }
                }
            }
        }
    }
}

private object WidgetContent {

    private data class WidgetTypography(
        val routeLabel: TextUnit,
        val headsign: TextUnit,
        val stationName: TextUnit,
        /** Large countdown digit(s), inline before the origin station name. */
        val minutes: TextUnit,
        val minutesUnit: TextUnit,
        val bodyTime: TextUnit,
        val caption: TextUnit,
        val padding: Dp,
        val gapSmall: Dp,
        val gapMedium: Dp,
        val pillCorner: Dp,
    )

    @Composable
    private fun typography(fontScale: Float): WidgetTypography {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = minOf(w, h)
        // Anchor to appwidget mins (xml: minWidth 180, minHeight 110) so text tracks resize.
        val refShort = 110f
        val refHeight = 165f
        val edgeScale = (shortEdge / refShort).coerceIn(0.42f, 2.85f)
        val heightScale = (h / refHeight).coerceIn(0.42f, 1.42f)
        val viewportScale = edgeScale * heightScale
        // Same damping as the station-board widget — keep the giant minutes glyph readable but stop
        // it from forcing the headsign / time row to truncate when the user picks 1.4×–1.6×.
        val widgetFontScale = (0.6f + 0.4f * fontScale)
        val scale = viewportScale * widgetFontScale
        val gapScalar = (shortEdge / refShort).coerceIn(0.42f, 2.6f)
        val narrowWidth = w < 340f
        val routeHeaderShrink = if (narrowWidth) 0.88f else 1f
        val padding = (14f * viewportScale).coerceIn(6f, 24f).dp
        val routeLabel = (12f * scale * routeHeaderShrink).coerceIn(6f, 20f).sp
        val headsign = (12f * scale * routeHeaderShrink).coerceIn(6f, 20f).sp
        val stationName = (13f * scale * if (narrowWidth) 0.92f else 1f).coerceIn(8f, 24f).sp
        val minutes = (34f * scale).coerceIn(13f, 72f).sp
        val minutesUnit = (12f * scale).coerceIn(6f, 21f).sp
        val bodyTime = (12f * scale * if (narrowWidth) 0.92f else 1f).coerceIn(7f, 20f).sp
        val caption = (10f * scale * if (narrowWidth) 0.92f else 1f).coerceIn(6f, 17f).sp
        val gapSmall = (4f * gapScalar).coerceIn(2f, 11f).dp
        val gapMedium = (8f * gapScalar).coerceIn(3f, 15f).dp
        val pillCorner = (14f * gapScalar).coerceIn(8f, 22f).dp
        return WidgetTypography(
            routeLabel = routeLabel,
            headsign = headsign,
            stationName = stationName,
            minutes = minutes,
            minutesUnit = minutesUnit,
            bodyTime = bodyTime,
            caption = caption,
            padding = padding,
            gapSmall = gapSmall,
            gapMedium = gapMedium,
            pillCorner = pillCorner,
        )
    }

    private fun surface(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_surface).toLong() and 0xFFFFFFFFL)

    private fun onSurface(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_on_surface).toLong() and 0xFFFFFFFFL)

    private fun onSurfaceVariant(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_on_surface_variant).toLong() and 0xFFFFFFFFL)

    private fun rowBg(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_row_bg).toLong() and 0xFFFFFFFFL)

    private fun accent(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_accent).toLong() and 0xFFFFFFFFL)

    private fun onAccent(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_on_header).toLong() and 0xFFFFFFFFL)

    @Composable
    fun ConfigurePrompt(context: Context, appWidgetId: Int) {
        val fontScale = readFontScale(context.applicationContext)
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surface(context))
                        .cornerRadius(20.dp)
                        .padding(t.padding)
                        .clickable(
                            androidx.glance.appwidget.action.actionStartActivity(
                                Intent(context, WidgetConfigActivity::class.java).apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                },
                                actionParametersOf(),
                            ),
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_trip_label),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurface(context)),
                            fontSize = t.stationName,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_set_from_to),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant(context)),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Box(
                    modifier =
                        GlanceModifier
                            .background(accent(context))
                            .cornerRadius(20.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = context.getString(R.string.widget_configure),
                        style =
                            TextStyle(
                                color = ColorProvider(onAccent(context)),
                                fontSize = t.routeLabel,
                                fontWeight = FontWeight.Medium,
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
                        .background(surface(context))
                        .cornerRadius(20.dp)
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
                            color = ColorProvider(onSurface(context)),
                            fontSize = t.stationName,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
            }
        }
    }

    /** Shows the configured route when trip times or network data could not be loaded. */
    @Composable
    fun LoadError(context: Context, config: WidgetTripConfig, fontScale: Float) {
        val fromLabel = config.fromLabel.ifEmpty { context.getString(R.string.widget_from) }
        val toLabel = config.toLabel.ifEmpty { context.getString(R.string.widget_to) }
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surface(context))
                        .cornerRadius(20.dp)
                        .padding(t.padding)
                        .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$fromLabel → $toLabel",
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurface(context)),
                            fontSize = t.stationName,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 3,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_trip_times_error),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant(context)),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 6,
                )
            }
        }
    }

    @Composable
    fun NoTrips(context: Context, config: WidgetTripConfig, fontScale: Float) {
        val fromLabel = config.fromLabel.ifEmpty { context.getString(R.string.widget_from) }
        val toLabel = config.toLabel.ifEmpty { context.getString(R.string.widget_to) }
        GlanceTheme {
            val t = typography(fontScale)
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(surface(context))
                        .cornerRadius(20.dp)
                        .padding(t.padding)
                        .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$fromLabel → $toLabel",
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurface(context)),
                            fontSize = t.stationName,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_no_trips),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant(context)),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 6,
                )
            }
        }
    }

    @Composable
    fun TripData(
        context: Context,
        config: WidgetTripConfig,
        tripData: WidgetTripData,
        use24Hour: Boolean,
        fontScale: Float,
    ) {
        val fromLabel =
            widgetTripStopDisplayLabel(config.fromLabel, tripData.fromStop, context.resources)
        val toLabel =
            widgetTripStopDisplayLabel(config.toLabel, tripData.toStop, context.resources)
        val fallback = onSurface(context)
        val routeColor =
            runCatching { colorFromHex(tripData.route.color) }.getOrElse { fallback }
        val routeTextColor =
            runCatching { colorFromHex(tripData.route.textColor) }.getOrElse { Color.White }
        val trainLabel =
            tripData.headsign?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_train, tripData.tripId)

        GlanceTheme {
            val t = typography(fontScale)
            val baseSurface =
                GlanceModifier.fillMaxSize()
                    .cornerRadius(20.dp)
                    .clickable(actionStartActivity<MainActivity>())
            if (tripData.route.type == RouteType.COMMUTER_RAIL) {
                TripWidgetCommuterLayouts.CommuterRailTrip(
                    context = context,
                    tripData = tripData,
                    fromLabel = fromLabel,
                    toLabel = toLabel,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    onSurface = onSurface(context),
                    onSurfaceVariant = onSurfaceVariant(context),
                    use24Hour = use24Hour,
                    fontScale = fontScale,
                    surfaceModifier =
                        baseSurface
                            .appWidgetBackground()
                            .background(Color(0x00000000)),
                )
            } else {
                Column(
                    modifier = baseSurface.background(surface(context)),
                ) {
                    Row(
                        modifier =
                            GlanceModifier.fillMaxWidth()
                                .background(routeColor)
                                .padding(horizontal = t.padding, vertical = t.gapMedium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tripData.route.label,
                            modifier = GlanceModifier.defaultWeight(),
                            style =
                                TextStyle(
                                    color = ColorProvider(routeTextColor),
                                    fontSize = t.routeLabel,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 2,
                        )
                        Spacer(modifier = GlanceModifier.width(t.gapMedium))
                        Text(
                            text = trainLabel,
                            style =
                                TextStyle(
                                    color = ColorProvider(routeTextColor.copy(alpha = 0.85f)),
                                    fontSize = t.headsign,
                                    textAlign = TextAlign.End,
                                ),
                            maxLines = 2,
                        )
                    }

                    Row(
                        modifier =
                            GlanceModifier.fillMaxWidth()
                                .fillMaxHeight()
                                .padding(horizontal = t.padding, vertical = t.gapMedium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            val minutesStr = tripData.minutesUntil.coerceAtLeast(0).toString()
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = minutesStr,
                                    style =
                                        TextStyle(
                                            color = ColorProvider(routeColor),
                                            fontSize = t.minutes,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Start,
                                        ),
                                    maxLines = 1,
                                )
                                Spacer(modifier = GlanceModifier.width(t.gapSmall))
                                Text(
                                    text = fromLabel,
                                    modifier = GlanceModifier.defaultWeight(),
                                    style =
                                        TextStyle(
                                            color = ColorProvider(onSurface(context)),
                                            fontSize = t.stationName,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Start,
                                        ),
                                    maxLines = 2,
                                )
                            }
                            Spacer(modifier = GlanceModifier.height(t.gapSmall))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = context.getString(R.string.widget_min_unit),
                                    style =
                                        TextStyle(
                                            color = ColorProvider(onSurfaceVariant(context)),
                                            fontSize = t.minutesUnit,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = TextAlign.Start,
                                        ),
                                    maxLines = 1,
                                )
                                Spacer(modifier = GlanceModifier.width(t.gapSmall))
                                Text(
                                    text = "→ $toLabel",
                                    modifier = GlanceModifier.defaultWeight(),
                                    style =
                                        TextStyle(
                                            color = ColorProvider(onSurface(context)),
                                            fontSize = t.stationName,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Start,
                                        ),
                                    maxLines = 2,
                                )
                            }
                            Spacer(modifier = GlanceModifier.height(t.gapSmall))
                            Text(
                                text =
                                    "${tripData.departureTime.formattedTime(use24Hour)} → ${tripData.arrivalTime.formattedTime(use24Hour)}",
                                modifier = GlanceModifier.fillMaxWidth(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(onSurfaceVariant(context)),
                                        fontSize = t.bodyTime,
                                    ),
                                maxLines = 2,
                            )
                            if (tripData.fromPlatform != null || tripData.toPlatform != null) {
                                val platformText = buildString {
                                    tripData.fromPlatform?.let {
                                        append(context.getString(R.string.widget_track_short, it))
                                    }
                                    if (tripData.fromPlatform != null && tripData.toPlatform != null)
                                        append(" • ")
                                    tripData.toPlatform?.let {
                                        append(context.getString(R.string.widget_track_short, it))
                                    }
                                }
                                if (platformText.isNotEmpty()) {
                                    Text(
                                        text = platformText,
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        style =
                                            TextStyle(
                                                color = ColorProvider(onSurfaceVariant(context)),
                                                fontSize = t.caption,
                                            ),
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
