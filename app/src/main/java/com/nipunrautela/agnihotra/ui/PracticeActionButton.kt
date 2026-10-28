package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nipunrautela.agnihotra.R

/** The same flame travels from navigation to the session's primary action. */
@Composable
internal fun PracticeActionButton(onClick: () -> Unit, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, flameModifier: Modifier = Modifier) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        shadowElevation = 0.dp,
        modifier = modifier.semantics { contentDescription = description; role = Role.Button }) {
        Box(contentAlignment = Alignment.Center) {
            FlameMark(Modifier.fillMaxHeight(0.72f).aspectRatio(0.75f).testTag("flame_mark").then(flameModifier))
        }
    }
}

@Composable
internal fun FlameMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_flame), null, modifier)
}
