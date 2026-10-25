package com.nipunrautela.agnihotra.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.nipunrautela.agnihotra.MainActivity
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.appGraph
import com.nipunrautela.agnihotra.core.withAppLanguage
import com.nipunrautela.agnihotra.core.SolarEventType
import com.nipunrautela.agnihotra.core.use24HourTime
import com.nipunrautela.agnihotra.core.timeFormatter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class AgnihotraWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.appGraph
        val prefs = graph.preferences.preferences.first()
        val textContext = context.withAppLanguage(prefs.language)
        val widgetState = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val selectedId = widgetState[WidgetPreferences.placeId]
        val allPlaces = graph.places.all()
        val place = allPlaces.firstOrNull { it.id == (selectedId ?: prefs.activePlaceId) }
        val times = place?.let { graph.calculator.calculate(LocalDate.now(it.zone), it.coordinates, it.zone) }
        val locale = textContext.resources.configuration.locales[0]
        val formatter = timeFormatter(context.use24HourTime(prefs.timeFormat), locale, prefs.showSeconds)
        val date = place?.let { LocalDate.now(it.zone).format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)) }
        val sunrise = times?.sunrise?.atZone(place!!.zone)?.format(formatter) ?: "—"
        val sunset = times?.sunset?.atZone(place!!.zone)?.format(formatter) ?: "—"
        val next = place?.let { graph.calculator.nextEvent(Instant.now(), it.coordinates, it.zone) }
        val nextText = next?.let {
            textContext.getString(R.string.widget_next_event,
                textContext.getString(if (it.type == SolarEventType.SUNRISE) R.string.sunrise else R.string.sunset),
                it.instant.atZone(place!!.zone).format(formatter))
        }
        val dark = prefs.theme == "dark" || (prefs.theme == "system" &&
            textContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val foreground = if (dark) Color(0xFFF1E5DA) else Color(0xFF30251F)
        val secondary = if (dark) Color(0xFFD0BFB1) else Color(0xFF67574D)
        val accent = if (dark) Color(0xFFF0BD94) else Color(0xFF955032)
        val tile = if (dark) R.drawable.widget_tile_dark else R.drawable.widget_tile_light
        provideContent {
            val roomy = LocalSize.current.height >= 210.dp
            val wide = LocalSize.current.width >= 300.dp
            val dense = context.resources.configuration.fontScale >= 1.3f
            val showHeader = !dense && LocalSize.current.height >= 210.dp
            val showNext = !dense && LocalSize.current.height >= 240.dp
            val showZone = !dense && LocalSize.current.height >= 270.dp
            Column(GlanceModifier.fillMaxSize()
                .background(ImageProvider(if (dark) R.drawable.widget_background_dark else R.drawable.widget_background_light))
                .appWidgetBackground().cornerRadius(24.dp).padding(if (dense) 10.dp else if (roomy) 18.dp else 14.dp)
                .clickable(androidx.glance.appwidget.action.actionStartActivity(android.content.Intent(context, MainActivity::class.java)
                    .putExtra("widget_place_id", place?.id)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))),
                verticalAlignment = Alignment.CenterVertically) {
                if (showHeader) Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_flame), contentDescription = null, modifier = GlanceModifier.size(16.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(textContext.getString(R.string.app_name), style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium))
                    if (date != null && wide) {
                        Spacer(GlanceModifier.defaultWeight())
                        Text(date, style = TextStyle(color = ColorProvider(secondary), fontSize = 11.sp), maxLines = 1)
                    }
                }
                if (showHeader) Spacer(GlanceModifier.height(4.dp))
                // Glance containers support ten direct children; group place metadata so
                // expanded widgets retain the next-event and time-zone footer as well.
                Column {
                    Text(place?.name ?: textContext.getString(R.string.choose_location),
                        style = TextStyle(color = ColorProvider(foreground), fontSize = if (dense) 16.sp else if (roomy) 21.sp else 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    place?.areaName?.let {
                        Spacer(GlanceModifier.height(3.dp))
                        Text(it, style = TextStyle(color = ColorProvider(secondary), fontSize = if (dense) 10.sp else 11.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(if (dense) 4.dp else if (roomy) 12.dp else 8.dp))
                if (place == null) Text(textContext.getString(if (selectedId != null) R.string.widget_place_removed else R.string.widget_setup), style = TextStyle(color = ColorProvider(secondary)))
                else Row(GlanceModifier.fillMaxWidth()) {
                    EventTile(textContext.getString(R.string.sunrise), sunrise, R.drawable.widget_sunrise,
                        foreground, secondary, tile, wide, roomy, dense, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    EventTile(textContext.getString(R.string.sunset), sunset, R.drawable.widget_sunset,
                        foreground, secondary, tile, wide, roomy, dense, GlanceModifier.defaultWeight())
                }
                if (showNext && nextText != null) {
                    Spacer(GlanceModifier.height(12.dp))
                    Text(nextText, style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp), maxLines = 1)
                }
                if (showZone && place != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(place.zoneId, style = TextStyle(color = ColorProvider(secondary), fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }

    @Composable private fun EventTile(label: String, time: String, icon: Int, foreground: Color, secondary: Color,
        background: Int, wide: Boolean, roomy: Boolean, dense: Boolean, modifier: GlanceModifier) {
        val context = LocalContext.current
        val baseSize = if (dense) 16f else if (wide) 23f else 18f
        val outerPadding = if (dense) 10f else if (roomy) 18f else 14f
        val tilePadding = if (dense) 4f else if (roomy) 12f else 8f
        val availableDp = ((LocalSize.current.width.value - 2 * outerPadding - 8) / 2 - 2 * tilePadding).coerceAtLeast(1f)
        val density = context.resources.displayMetrics.density
        val paint = android.graphics.Paint().apply {
            textSize = baseSize * density * context.resources.configuration.fontScale
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val timeSize = minOf(baseSize, baseSize * availableDp * density / paint.measureText(time).coerceAtLeast(1f)).sp
        Column(modifier.background(ImageProvider(background)).cornerRadius(16.dp).padding(if (dense) 4.dp else if (roomy) 12.dp else 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(icon), contentDescription = if (dense) label else null,
                    modifier = GlanceModifier.size(if (dense) 14.dp else 16.dp))
                if (!dense) {
                    Spacer(GlanceModifier.width(5.dp))
                    Text(label, style = TextStyle(color = ColorProvider(secondary), fontSize = 12.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(if (dense) 2.dp else 4.dp))
            Text(time, style = TextStyle(color = ColorProvider(foreground), fontSize = timeSize,
                fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

object WidgetPreferences { val placeId = stringPreferencesKey("widget_place") }

class AgnihotraWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = AgnihotraWidget() }
