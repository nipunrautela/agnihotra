package com.nipunrautela.agnihotra

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.nipunrautela.agnihotra.data.SavedPlace
import com.nipunrautela.agnihotra.widget.AgnihotraWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Use a disposable test installation; see tools/verify_device.ps1. */
class WidgetRenderingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val graph get() = context.appGraph

    @Before fun setUpFixture() = runBlocking(Dispatchers.IO) {
        graph.database.clearAllTables()
        graph.preferences.setBoolean("analytics", false)
        graph.preferences.setString("time_format", "12")
        graph.places.save(SavedPlace("widget-fixture", "Current location", 28.6139, 77.209,
            areaName = "Connaught Place, New Delhi", fromCurrentLocation = true))
        graph.preferences.repairSelection(graph.places.all())
        graph.preferences.selectPlace("widget-fixture")
        Unit
    }

    @Test fun compactLightWidgetShowsNameAreaAndBothTimesWithoutClipping() = runBlocking {
        graph.preferences.setString("theme", "light")
        graph.preferences.setString("language", "en")
        val labels = renderAndCheckBounds(240, 140)
        assertTrue(labels.containsAll(listOf("Current location", "Connaught Place, New Delhi", "Sunrise", "Sunset")))
        assertEquals(2, labels.count { it.contains(Regex("\\d:\\d{2}")) })
    }

    @Test fun expandedDarkHindiWidgetKeepsAllTextInsideItsBounds() = runBlocking {
        graph.preferences.setString("theme", "dark")
        graph.preferences.setString("language", "hi")
        val labels = renderAndCheckBounds(340, 230)
        assertTrue(labels.containsAll(listOf("Current location", "Connaught Place, New Delhi", "सूर्योदय", "सूर्यास्त")))
        assertTrue(labels.contains("Asia/Kolkata"))
    }

    @Test fun compactWidgetAtLargeFontKeepsAreaTimesAndAccessibleEventLabels() = runBlocking {
        graph.preferences.setString("theme", "light")
        graph.preferences.setString("language", "en")
        val labels = renderAndCheckBounds(240, 140, fontScale = 1.5f)
        assertTrue(labels.containsAll(listOf("Current location", "Connaught Place, New Delhi", "Sunrise", "Sunset")))
        assertEquals(2, labels.count { it.contains(Regex("\\d:\\d{2}")) })
    }

    private suspend fun renderAndCheckBounds(widthDp: Int, heightDp: Int, fontScale: Float = 1f): List<String> {
        val renderingContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply { this.fontScale = fontScale })
        val remote = AgnihotraWidget().compose(renderingContext, size = DpSize(widthDp.dp, heightDp.dp))
        return withContext(Dispatchers.Main) {
            val root = remote.apply(renderingContext, FrameLayout(renderingContext)) as ViewGroup
            val width = (widthDp * context.resources.displayMetrics.density).toInt()
            val height = (heightDp * context.resources.displayMetrics.density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val labels = mutableListOf<String>()
            fun inspect(view: View) {
                view.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { labels += it }
                if (view is TextView && view.visibility == View.VISIBLE) {
                    val bounds = Rect(0, 0, view.width, view.height)
                    root.offsetDescendantRectToMyCoords(view, bounds)
                    assertTrue("Text above widget", bounds.top >= 0)
                    assertTrue("Text below widget: ${view.text}", bounds.bottom <= height + 1)
                    assertTrue("Text outside widget width", bounds.left >= 0 && bounds.right <= width + 1)
                    assertTrue("Clipped text line: ${view.text}", view.height >= view.lineHeight - 2)
                    labels += view.text.toString()
                } else if (view is ViewGroup) (0 until view.childCount).forEach { inspect(view.getChildAt(it)) }
            }
            inspect(root)
            labels
        }
    }
}
