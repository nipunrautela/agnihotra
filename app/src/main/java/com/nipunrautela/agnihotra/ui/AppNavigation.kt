package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nipunrautela.agnihotra.R

/** Five stable slots: animating the flame never changes a neighboring item's bounds. */
@Composable
internal fun NavigationDock(page: Page, onPage: (Page) -> Unit, centerAction: @Composable () -> Unit) {
    // Reserve two label lines at the actual font scale. Shared-element remeasurement
    // cannot make the dock taller while the flame is travelling through its overlay.
    val slotHeight = (44 + 32 * LocalDensity.current.fontScale).dp
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 8.dp).testTag("navigation_dock")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Page.TODAY, Page.TIMETABLE, Page.PRACTICE, Page.MANTRAS, Page.PLACES).forEach { destination ->
                Box(Modifier.weight(1f).height(slotHeight), contentAlignment = Alignment.Center) {
                    if (destination != Page.PRACTICE || page == Page.PRACTICE) {
                        val icon = when (destination) {
                            Page.TODAY -> Icons.Outlined.WbSunny
                            Page.TIMETABLE -> Icons.Outlined.CalendarMonth
                            Page.PRACTICE -> Icons.Outlined.Timer
                            Page.MANTRAS -> Icons.AutoMirrored.Outlined.MenuBook
                            else -> Icons.Outlined.Place
                        }
                        DockItem(destination, icon, page == destination, onClick = { onPage(destination) })
                    }
                    if (destination == Page.PRACTICE) centerAction()
                }
            }
        }
    }
}

@Composable
private fun DockItem(page: Page, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val opacity = animateFloatAsState(if (pressed) 0.65f else 1f, tween(100), label = "tab_press")
    val indicator by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        tween(180), label = "tab_selection")
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .graphicsLayer { alpha = opacity.value }
        .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
        .padding(vertical = 4.dp).testTag("navigation_${page.name}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(44.dp, 32.dp).background(indicator, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(if (page == Page.PRACTICE) R.string.session_tab else page.label),
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
