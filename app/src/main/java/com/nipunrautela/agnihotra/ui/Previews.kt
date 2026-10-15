package com.nipunrautela.agnihotra.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nipunrautela.agnihotra.core.SolarCalculator
import com.nipunrautela.agnihotra.data.AppPreferences
import com.nipunrautela.agnihotra.data.SavedPlace

@Preview(name = "Welcome · light", showBackground = true, device = "spec:width=393dp,height=851dp,dpi=420")
@Preview(name = "Welcome · Hindi", locale = "hi", showBackground = true, fontScale = 1.2f)
@Composable private fun WelcomePreview() {
    AgnihotraTheme("light") { Surface { WelcomeScreen {} } }
}

@Preview(name = "Today · light", showBackground = true, device = "spec:width=393dp,height=851dp,dpi=420")
@Composable private fun TodayPreview() { TodaySample("light") }

@Preview(name = "Today · dusk", showBackground = true, device = "spec:width=393dp,height=851dp,dpi=420")
@Composable private fun TodayDarkPreview() { TodaySample("dark") }

@Preview(name = "Today · Hindi large text", locale = "hi", fontScale = 1.5f, showBackground = true)
@Composable private fun TodayHindiPreview() { TodaySample("light") }

@Preview(name = "Settings · Hindi 200%", locale = "hi", fontScale = 2f,
    showBackground = true, device = "spec:width=320dp,height=640dp,dpi=320")
@Composable private fun SettingsHindiPreview() {
    AgnihotraTheme("dark") {
        Surface {
            SettingsPage(AppPreferences(language = "hi"), false, false, false,
                { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
        }
    }
}

@Composable private fun TodaySample(theme: String) {
    val place = SavedPlace("preview", "New Delhi", 28.6139, 77.209)
    AgnihotraTheme(theme) {
        Surface {
            TodayPage(AppState(false, listOf(place), AppPreferences(activePlaceId = place.id, homePlaceId = place.id)),
                SolarCalculator(), {}, {})
        }
    }
}
