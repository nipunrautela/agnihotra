package com.nipunrautela.agnihotra.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Illustrated elapsed daylight, not a physical elevation/azimuth plot. Null draws no sun. */
@Composable
internal fun SolarArc(progress: Float?) {
    val journey = remember { Animatable(0f) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        val duration = if (entered) 800 else 1_100
        entered = true
        if (progress == null) journey.snapTo(0f)
        else journey.animateTo(progress.coerceIn(0f, 1f), tween(duration, easing = FastOutSlowInEasing))
    }
    // The quiet corona breathes only during daylight. Android's Compose frame clock
    // suspends with the window, and platform animation-duration scaling still applies.
    val corona = if (progress != null && progress > 0f && progress < 1f) {
        rememberInfiniteTransition(label = "sun_corona").animateFloat(0f, 1f,
            infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "sun_glow")
    } else rememberUpdatedState(0f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartHeight = (maxWidth * 0.36f).coerceIn(112.dp, 144.dp)
        Box(Modifier.fillMaxWidth().height(chartHeight).clipToBounds().testTag("solar_arc")
            .drawWithCache {
                // Reserve the entire corona at both endpoints and at the top of the arc.
                val inset = 30.dp.toPx().coerceAtMost(size.width / 4)
                val radius = (inset - 2.dp.toPx()).coerceAtLeast(0f)
                val halfWidth = (size.width / 2 - inset).coerceAtLeast(0f)
                val baseline = size.height - inset
                val rise = (size.height - 2 * inset).coerceAtLeast(0f)
                val arcSize = Size(halfWidth * 2, rise * 2)
                val origin = Offset(inset, inset)
                fun point(fraction: Float): Offset {
                    val angle = PI + PI * fraction
                    return Offset(size.width / 2 + halfWidth * cos(angle).toFloat(),
                        baseline + rise * sin(angle).toFloat())
                }
                val daylight = Path().apply {
                    moveTo(inset, baseline)
                    for (step in 0..64) point(step / 64f).let { lineTo(it.x, it.y) }
                    close()
                }
                val wash = Brush.verticalGradient(listOf(Amber.copy(alpha = 0.13f), Amber.copy(alpha = 0.015f)),
                    startY = inset, endY = baseline)
                val trail = Brush.horizontalGradient(listOf(Color(0xFFC58252), Amber, Color(0xFFFFDCA2)),
                    startX = inset, endX = size.width - inset)
                val dotted = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())))
                onDrawBehind {
                    drawPath(daylight, wash)
                    drawLine(Amber.copy(alpha = 0.23f), Offset(inset, baseline), Offset(size.width - inset, baseline), 1.dp.toPx())
                    for (fraction in listOf(0f, 0.5f, 1f)) {
                        val x = inset + 2 * halfWidth * fraction
                        drawLine(Amber.copy(alpha = 0.35f), Offset(x, baseline - 3.dp.toPx()),
                            Offset(x, baseline + 3.dp.toPx()), 1.dp.toPx())
                    }
                    drawArc(Amber.copy(alpha = 0.25f), 180f, 180f, false, origin, arcSize, style = dotted)
                    if (progress != null) {
                        val fraction = journey.value.coerceIn(0f, 1f)
                        drawArc(trail, 180f, 180f * fraction, false, origin, arcSize,
                            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        val sun = point(fraction)
                        val breathing = corona.value
                        drawCircle(Brush.radialGradient(listOf(Amber.copy(alpha = 0.22f + breathing * 0.05f), Color.Transparent),
                            center = sun, radius = radius), radius, sun)
                        val discRadius = radius * 0.36f
                        for (ray in 0 until 8) {
                            val angle = PI * ray / 4
                            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                            drawLine(Amber.copy(alpha = 0.45f + breathing * 0.15f), sun + direction * (discRadius + 4.dp.toPx()),
                                sun + direction * (discRadius + 7.dp.toPx()), 1.dp.toPx(), cap = StrokeCap.Round)
                        }
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9BC), Amber, Color(0xFFE4A45D)),
                            center = sun - Offset(discRadius * 0.35f, discRadius * 0.35f), radius = discRadius * 1.6f), discRadius, sun)
                    }
                }
            })
    }
}
