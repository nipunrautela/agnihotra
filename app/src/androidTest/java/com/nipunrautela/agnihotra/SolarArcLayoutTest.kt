package com.nipunrautela.agnihotra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.nipunrautela.agnihotra.ui.AgnihotraTheme
import com.nipunrautela.agnihotra.ui.SolarArc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SolarArcLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sunAndGlowStayInsideTheirRegionAtBothEndsAndMiddayOnNarrowAndWideLayouts() {
        var progress by mutableFloatStateOf(0f)
        var width by mutableStateOf(240.dp)
        compose.setContent {
            AgnihotraTheme("dark") {
                // A reduced local density lets the physical phone exercise tablet-width drawing too.
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    Column(Modifier.width(width).background(Color.Black).testTag("chart_fixture")) {
                        Text("ऊपर का पाठ", color = Color.White, modifier = Modifier.testTag("above"))
                        SolarArc(progress)
                        Text("Text below", color = Color.White, modifier = Modifier.testTag("below"))
                    }
                }
            }
        }
        for (size in listOf(240.dp, 320.dp, 600.dp)) {
            for (position in listOf(0f, 0.5f, 1f)) {
                compose.runOnIdle { width = size; progress = position }
                val arc = compose.onNodeWithTag("solar_arc").fetchSemanticsNode().boundsInRoot
                val above = compose.onNodeWithTag("above").fetchSemanticsNode().boundsInRoot
                val below = compose.onNodeWithTag("below").fetchSemanticsNode().boundsInRoot
                assertTrue(above.bottom <= arc.top && below.top >= arc.bottom)
                val pixels = compose.onNodeWithTag("solar_arc").captureToImage().toPixelMap()
                // Padding around every edge contains no painted path or halo, even at the endpoints.
                for (x in 0 until pixels.width) {
                    assertEquals(Color.Black, pixels[x, 0])
                    assertEquals(Color.Black, pixels[x, pixels.height - 1])
                }
                for (y in 0 until pixels.height) {
                    assertEquals(Color.Black, pixels[0, y])
                    assertEquals(Color.Black, pixels[pixels.width - 1, y])
                }
            }
        }
    }
    @Test fun sunAndCoronaStayInsideTheChartDuringEntryMotion() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AgnihotraTheme("dark") {
                Column(Modifier.width(320.dp).background(Color.Black)) { SolarArc(1f) }
            }
        }
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val destination = File(context.getExternalFilesDir(null), "sun-motion-review").apply { mkdirs() }
            var previous = 0L
            for (time in listOf(200L, 600L, 1_300L)) {
                compose.mainClock.advanceTimeBy(time - previous)
                previous = time
                val capture = compose.onNodeWithTag("solar_arc").captureToImage()
                val pixels = capture.toPixelMap()
                for (x in 0 until pixels.width) {
                    assertEquals(Color.Black, pixels[x, 0])
                    assertEquals(Color.Black, pixels[x, pixels.height - 1])
                }
                for (y in 0 until pixels.height) {
                    assertEquals(Color.Black, pixels[0, y])
                    assertEquals(Color.Black, pixels[pixels.width - 1, y])
                }
                File(destination, "$time.png").outputStream().use { capture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

}
