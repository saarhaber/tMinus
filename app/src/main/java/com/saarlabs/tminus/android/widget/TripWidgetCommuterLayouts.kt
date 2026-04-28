package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.android.util.formattedTime
import kotlin.math.min

/**
 * Responsive commuter-rail style trip widget: master (large), compact (square), and inline
 * layouts inspired by the MBTA Framingham/Worcester reference widgets.
 */
internal object TripWidgetCommuterLayouts {

    internal enum class TripLayout {
        MASTER,
        COMPACT,
        INLINE,
    }

    internal fun selectLayout(widthDp: Float, heightDp: Float): TripLayout {
        val short = min(widthDp, heightDp)
        val longEdge = maxOf(widthDp, heightDp)
        return when {
            short >= 168f && longEdge >= 200f -> TripLayout.MASTER
            short >= 118f && longEdge < 200f -> TripLayout.COMPACT
            widthDp >= 260f && short < 118f -> TripLayout.INLINE
            short >= 130f -> TripLayout.COMPACT
            else -> TripLayout.INLINE
        }
    }

    internal data class TripTypography(
        val headerRoute: TextUnit,
        val headerDest: TextUnit,
        val minutesHuge: TextUnit,
        val minutesUnit: TextUnit,
        val stationName: TextUnit,
        val stripPrimary: TextUnit,
        val stripSecondary: TextUnit,
        val compactLine: TextUnit,
        val compactSub: TextUnit,
        val compactMinutes: TextUnit,
        val compactNext: TextUnit,
        val inlineRoute: TextUnit,
        val inlineStation: TextUnit,
        val inlineMinutes: TextUnit,
        val inlineNext: TextUnit,
        val padding: Dp,
        val gapXs: Dp,
        val gapSm: Dp,
        val gapMd: Dp,
        val logoSize: Dp,
        val stripPadV: Dp,
    )

    @Composable
    internal fun typography(fontScale: Float): TripTypography {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = min(w, h)
        val layout = selectLayout(w, h)
        val refShort = when (layout) {
            TripLayout.MASTER -> 180f
            TripLayout.COMPACT -> 110f
            TripLayout.INLINE -> 72f
        }
        val refHeight = when (layout) {
            TripLayout.MASTER -> 220f
            TripLayout.COMPACT -> 110f
            TripLayout.INLINE -> 56f
        }
        val edgeScale = (shortEdge / refShort).coerceIn(0.45f, 2.8f)
        val heightScale = (h / refHeight).coerceIn(0.45f, 1.5f)
        val viewportScale = edgeScale * heightScale
        val widgetFontScale = (0.6f + 0.4f * fontScale)
        val scale = viewportScale * widgetFontScale
        val gapScalar = (shortEdge / refShort).coerceIn(0.45f, 2.6f)
        val padding = (12f * viewportScale).coerceIn(5f, 22f).dp
        val gapXs = (3f * gapScalar).coerceIn(2f, 8f).dp
        val gapSm = (5f * gapScalar).coerceIn(2f, 10f).dp
        val gapMd = (8f * gapScalar).coerceIn(4f, 14f).dp
        val logoSize = (22f * scale).coerceIn(16f, 34f).dp
        val stripPadV = (6f * gapScalar).coerceIn(4f, 12f).dp
        return TripTypography(
            headerRoute = (11f * scale).coerceIn(8f, 15f).sp,
            headerDest = (10f * scale).coerceIn(7f, 13f).sp,
            minutesHuge = (40f * scale).coerceIn(22f, 78f).sp,
            minutesUnit = (13f * scale).coerceIn(9f, 20f).sp,
            stationName = (15f * scale).coerceIn(10f, 22f).sp,
            stripPrimary = (11f * scale).coerceIn(8f, 15f).sp,
            stripSecondary = (9f * scale).coerceIn(7f, 12f).sp,
            compactLine = (11f * scale).coerceIn(8f, 14f).sp,
            compactSub = (9f * scale).coerceIn(7f, 11f).sp,
            compactMinutes = (22f * scale).coerceIn(14f, 32f).sp,
            compactNext = (10f * scale).coerceIn(8f, 13f).sp,
            inlineRoute = (9f * scale).coerceIn(7f, 11f).sp,
            inlineStation = (11f * scale).coerceIn(8f, 13f).sp,
            inlineMinutes = (11f * scale).coerceIn(8f, 14f).sp,
            inlineNext = (10f * scale).coerceIn(8f, 12f).sp,
            padding = padding,
            gapXs = gapXs,
            gapSm = gapSm,
            gapMd = gapMd,
            logoSize = logoSize,
            stripPadV = stripPadV,
        )
    }

    internal fun glassBodyColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_cr_glass_body).toLong() and 0xFFFFFFFFL)

    internal fun glassStripColor(context: Context): Color =
        Color(ContextCompat.getColor(context, R.color.widget_cr_glass_strip).toLong() and 0xFFFFFFFFL)

    internal fun directionBoundLabel(context: Context, tripData: WidgetTripData, toLabel: String): String {
        val head = tripData.headsign?.trim().orEmpty()
        if (head.endsWith("Bound", ignoreCase = true)) return head
        if (tripData.route.type == RouteType.COMMUTER_RAIL && toLabel.isNotBlank()) {
            return context.getString(R.string.widget_trip_bound, toLabel)
        }
        return head.ifEmpty { toLabel }
    }

    @Composable
    internal fun CommuterRailTrip(
        context: Context,
        tripData: WidgetTripData,
        fromLabel: String,
        toLabel: String,
        routeColor: Color,
        routeTextColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        use24Hour: Boolean,
        fontScale: Float,
        surfaceModifier: GlanceModifier,
    ) {
        val size = LocalSize.current
        val layout = selectLayout(size.width.value, size.height.value)
        val t = typography(fontScale)
        val glassBody = glassBodyColor(context)
        val glassStrip = glassStripColor(context)
        val trainLabel =
            tripData.headsign?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_train, tripData.tripId)
        val boundLabel = directionBoundLabel(context, tripData, toLabel)
        val depStr = tripData.departureTime.formattedTime(use24Hour)
        val arrStr = tripData.arrivalTime.formattedTime(use24Hour)
        val minutesStr = tripData.minutesUntil.coerceAtLeast(0).toString()

        when (layout) {
            TripLayout.MASTER ->
                MasterLayout(
                    context = context,
                    tripData = tripData,
                    fromLabel = fromLabel,
                    toLabel = toLabel,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    glassBody = glassBody,
                    glassStrip = glassStrip,
                    trainLabel = trainLabel,
                    boundLabel = boundLabel,
                    depStr = depStr,
                    arrStr = arrStr,
                    minutesStr = minutesStr,
                    t = t,
                    surfaceModifier = surfaceModifier,
                )
            TripLayout.COMPACT ->
                CompactLayout(
                    context = context,
                    tripData = tripData,
                    toLabel = toLabel,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    glassBody = glassBody,
                    trainLabel = trainLabel,
                    depStr = depStr,
                    minutesStr = minutesStr,
                    t = t,
                    surfaceModifier = surfaceModifier,
                )
            TripLayout.INLINE ->
                InlineLayout(
                    context = context,
                    tripData = tripData,
                    routeColor = routeColor,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    glassBody = glassBody,
                    boundLabel = boundLabel,
                    depStr = depStr,
                    minutesStr = minutesStr,
                    t = t,
                    surfaceModifier = surfaceModifier,
                )
        }
    }

    @Composable
    private fun RouteHeaderRow(
        context: Context,
        tripData: WidgetTripData,
        routeColor: Color,
        routeTextColor: Color,
        trainLabel: String,
        t: TripTypography,
        centerTitle: Boolean,
    ) {
        val routeAlign = if (centerTitle) TextAlign.Center else TextAlign.Start
        Column(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(routeColor)
                    .padding(horizontal = t.padding, vertical = t.gapSm),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_mbta_t_circle),
                    contentDescription =
                        context.getString(R.string.widget_mbta_mark_content_description),
                    modifier = GlanceModifier.width(t.logoSize).height(t.logoSize),
                )
                Spacer(modifier = GlanceModifier.width(t.gapSm))
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment =
                        if (centerTitle) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        text = tripData.route.label,
                        style =
                            TextStyle(
                                color = ColorProvider(routeTextColor),
                                fontSize = t.headerRoute,
                                fontWeight = FontWeight.Bold,
                                textAlign = routeAlign,
                            ),
                        maxLines = 2,
                    )
                    Text(
                        text = context.getString(R.string.widget_trip_route_type_commuter),
                        style =
                            TextStyle(
                                color = ColorProvider(routeTextColor.copy(alpha = 0.88f)),
                                fontSize = t.headerDest,
                                fontWeight = FontWeight.Normal,
                                textAlign = routeAlign,
                            ),
                        maxLines = 1,
                    )
                }
            }
            if (trainLabel.isNotBlank()) {
                Spacer(modifier = GlanceModifier.height(t.gapXs))
                Text(
                    text = trainLabel,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(routeTextColor.copy(alpha = 0.9f)),
                            fontSize = t.headerDest,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 3,
                )
            }
        }
    }

    @Composable
    private fun MasterLayout(
        context: Context,
        tripData: WidgetTripData,
        fromLabel: String,
        toLabel: String,
        routeColor: Color,
        routeTextColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        glassBody: Color,
        glassStrip: Color,
        trainLabel: String,
        boundLabel: String,
        depStr: String,
        arrStr: String,
        minutesStr: String,
        t: TripTypography,
        surfaceModifier: GlanceModifier,
    ) {
        Column(modifier = surfaceModifier) {
            RouteHeaderRow(
                context = context,
                tripData = tripData,
                routeColor = routeColor,
                routeTextColor = routeTextColor,
                trainLabel = trainLabel,
                t = t,
                centerTitle = true,
            )
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .defaultWeight()
                        .background(glassBody)
                        .padding(horizontal = t.padding, vertical = t.gapMd),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = minutesStr,
                                style =
                                    TextStyle(
                                        color = ColorProvider(routeColor),
                                        fontSize = t.minutesHuge,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                maxLines = 1,
                            )
                            Spacer(modifier = GlanceModifier.width(t.gapXs))
                            Text(
                                text = context.getString(R.string.widget_min_unit),
                                modifier = GlanceModifier.padding(bottom = 3.dp),
                                style =
                                    TextStyle(
                                        color = ColorProvider(onSurfaceVariant),
                                        fontSize = t.minutesUnit,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(t.gapXs))
                        Text(
                            text = fromLabel,
                            style =
                                TextStyle(
                                    color = ColorProvider(onSurface),
                                    fontSize = t.stationName,
                                    fontWeight = FontWeight.Bold,
                                ),
                            maxLines = 2,
                        )
                    }
                }
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Spacer(modifier = GlanceModifier.fillMaxSize())
                }
                Column(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .background(glassStrip)
                            .padding(horizontal = t.padding, vertical = t.stripPadV),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_train_small),
                            contentDescription = null,
                            modifier = GlanceModifier.width(18.dp).height(12.dp),
                        )
                        Spacer(modifier = GlanceModifier.width(t.gapXs))
                        Text(
                            text = boundLabel,
                            modifier = GlanceModifier.defaultWeight(),
                            style =
                                TextStyle(
                                    color = ColorProvider(onSurface),
                                    fontSize = t.stripPrimary,
                                    fontWeight = FontWeight.Medium,
                                ),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(t.gapXs))
                    Text(
                        text =
                            context.getString(
                                R.string.widget_trip_next_train_arrives,
                                depStr,
                                arrStr,
                            ),
                        style =
                            TextStyle(
                                color = ColorProvider(onSurface),
                                fontSize = t.stripPrimary,
                                fontWeight = FontWeight.Bold,
                            ),
                        maxLines = 2,
                    )
                }
            }
        }
    }

    @Composable
    private fun CompactLayout(
        context: Context,
        tripData: WidgetTripData,
        toLabel: String,
        routeColor: Color,
        routeTextColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        glassBody: Color,
        trainLabel: String,
        depStr: String,
        minutesStr: String,
        t: TripTypography,
        surfaceModifier: GlanceModifier,
    ) {
        val shortRoute =
            tripData.route.shortName.ifBlank { tripData.route.label }.take(12)
        Column(
            modifier = surfaceModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RouteHeaderRow(
                context = context,
                tripData = tripData,
                routeColor = routeColor,
                routeTextColor = routeTextColor,
                trainLabel = trainLabel,
                t = t,
                centerTitle = false,
            )
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .defaultWeight()
                        .background(glassBody)
                        .padding(t.padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shortRoute,
                    style =
                        TextStyle(
                            color = ColorProvider(onSurface),
                            fontSize = t.compactLine,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Text(
                    text = context.getString(R.string.widget_trip_route_type_commuter),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant),
                            fontSize = t.compactSub,
                        ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSm))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_mbta_t_circle),
                    contentDescription = null,
                    modifier = GlanceModifier.width(t.logoSize + 8.dp).height(t.logoSize + 8.dp),
                )
                Spacer(modifier = GlanceModifier.height(t.gapSm))
                Text(
                    text = context.getString(R.string.widget_min_short, minutesStr.toIntOrNull() ?: 0),
                    style =
                        TextStyle(
                            color = ColorProvider(routeColor),
                            fontSize = t.compactMinutes,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Text(
                    text =
                        context.getString(
                            R.string.widget_trip_next_to_destination,
                            depStr,
                            toLabel,
                        ),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurface),
                            fontSize = t.compactNext,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 2,
                )
            }
        }
    }

    @Composable
    private fun InlineLayout(
        context: Context,
        tripData: WidgetTripData,
        routeColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        glassBody: Color,
        boundLabel: String,
        depStr: String,
        minutesStr: String,
        t: TripTypography,
        surfaceModifier: GlanceModifier,
    ) {
        Row(
            modifier =
                surfaceModifier
                    .background(glassBody)
                    .padding(horizontal = t.padding, vertical = t.gapXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_mbta_t_circle),
                    contentDescription = null,
                    modifier = GlanceModifier.width(t.logoSize - 2.dp).height(t.logoSize - 2.dp),
                )
                Spacer(modifier = GlanceModifier.width(t.gapSm))
                Column {
                    Text(
                        text = tripData.route.label,
                        style =
                            TextStyle(
                                color = ColorProvider(onSurfaceVariant),
                                fontSize = t.inlineRoute,
                                fontWeight = FontWeight.Normal,
                            ),
                        maxLines = 1,
                    )
                    Text(
                        text = boundLabel,
                        style =
                            TextStyle(
                                color = ColorProvider(onSurface),
                                fontSize = t.inlineStation,
                                fontWeight = FontWeight.Bold,
                            ),
                        maxLines = 1,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        context.getString(
                            R.string.widget_min_short,
                            minutesStr.toIntOrNull() ?: 0,
                        ),
                    style =
                        TextStyle(
                            color = ColorProvider(routeColor),
                            fontSize = t.inlineMinutes,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_train_small),
                    contentDescription = null,
                    modifier = GlanceModifier.width(16.dp).height(11.dp),
                )
            }
            Text(
                text = context.getString(R.string.widget_trip_next_time_only, depStr),
                modifier = GlanceModifier.defaultWeight(),
                style =
                    TextStyle(
                        color = ColorProvider(onSurface),
                        fontSize = t.inlineNext,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                    ),
                maxLines = 1,
            )
        }
    }
}
