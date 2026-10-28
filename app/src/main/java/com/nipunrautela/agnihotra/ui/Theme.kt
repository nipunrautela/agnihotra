package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val Copper = Color(0xFF955032)
val Amber = Color(0xFFF0BD72)
private val LightColors = lightColorScheme(
    primary = Copper, onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DFC9), onPrimaryContainer = Color(0xFF402318),
    secondary = Color(0xFF756044), secondaryContainer = Color(0xFFF1E7D8),
    onSecondaryContainer = Color(0xFF402F20),
    background = Color(0xFFFAF6EF), onBackground = Color(0xFF30251F),
    surface = Color(0xFFFAF6EF), onSurface = Color(0xFF30251F),
    surfaceVariant = Color(0xFFF0E9DF), onSurfaceVariant = Color(0xFF706057),
    outline = Color(0xFF8A776B), outlineVariant = Color(0xFFE0D5C9),
    surfaceContainerLowest = Color(0xFFFFFCF7), surfaceContainerLow = Color(0xFFF5EFE6),
    surfaceContainer = Color(0xFFF0E8DC), surfaceContainerHigh = Color(0xFFECE2D5),
    surfaceContainerHighest = Color(0xFFE5D9CA), surfaceTint = Copper,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0BD94), onPrimary = Color(0xFF4A281A),
    primaryContainer = Color(0xFF593927), onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFD8C4A6), secondaryContainer = Color(0xFF3A3028),
    onSecondaryContainer = Color(0xFFF4E4D2),
    background = Color(0xFF1C1917), onBackground = Color(0xFFF1E5DA),
    surface = Color(0xFF1C1917), onSurface = Color(0xFFF1E5DA),
    surfaceVariant = Color(0xFF302923), onSurfaceVariant = Color(0xFFD0BFB1),
    outline = Color(0xFFA18E80), outlineVariant = Color(0xFF4D4137),
    surfaceContainerLowest = Color(0xFF181512), surfaceContainerLow = Color(0xFF25201C),
    surfaceContainer = Color(0xFF2C2520), surfaceContainerHigh = Color(0xFF342C25),
    surfaceContainerHighest = Color(0xFF3E342C), surfaceTint = Color(0xFFF0BD94),
)

@Composable
fun AgnihotraTheme(theme: String = "system", content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val activity = LocalContext.current.activityOrNull()
    val view = LocalView.current
    SideEffect {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        typography = Typography(
            displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light),
            displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp),
            headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 30.sp),
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        ), content = content,
    )
}
