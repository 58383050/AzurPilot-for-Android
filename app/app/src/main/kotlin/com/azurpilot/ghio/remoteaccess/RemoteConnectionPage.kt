package com.azurpilot.ghio.remoteaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.R
import com.azurpilot.ghio.ui.ksu.AppPageScaffold
import com.azurpilot.ghio.ui.ksu.AppText

/**
 * 独立的远程入口，不读取本机运行时或提权服务的就绪状态。
 *
 * Standalone remote entry that does not depend on local Runtime or privileged-service readiness.
 */
@Composable
fun RemoteConnectionPage(onBack: () -> Unit, onConnected: () -> Unit, onLocalSelected: () -> Unit) {
    AppPageScaffold(title = stringResource(R.string.remote_pair_title), onBack = onBack,
        modifier = Modifier.testTag("remote-connection-page")) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppText(stringResource(R.string.remote_client_hint), style = MaterialTheme.typography.bodyMedium)
            RemotePairingCard(onConnected = onConnected, onLocalSelected = onLocalSelected)
        }
    }
}
