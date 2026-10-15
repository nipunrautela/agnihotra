package com.nipunrautela.agnihotra.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.integrations.ReleaseConfiguration

@Composable
fun PrivacyPage() {
    val links = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf(
            R.string.privacy_local_title to R.string.privacy_local_detail,
            R.string.privacy_location_title to R.string.privacy_location_detail,
            R.string.privacy_analytics_title to R.string.privacy_analytics_detail,
            R.string.privacy_control_title to R.string.privacy_control_detail,
        ).forEach { (heading, body) ->
            Text(stringResource(heading), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        }
        ReleaseConfiguration.privacyPolicyUrl?.let { url ->
            TextButton(onClick = { links.openUri(url) }) { Text(stringResource(R.string.privacy_full_policy)) }
        }
        ReleaseConfiguration.supportEmail?.let { email ->
            TextButton(onClick = { links.openUri("mailto:$email") }) { Text(stringResource(R.string.contact_support)) }
        }
    }
}
