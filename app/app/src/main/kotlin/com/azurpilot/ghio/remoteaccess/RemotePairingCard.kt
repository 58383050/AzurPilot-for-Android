package com.azurpilot.ghio.remoteaccess

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.R
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.ksu.AppButton
import com.azurpilot.ghio.ui.ksu.AppText
import com.azurpilot.ghio.ui.ksu.AppTextButton
import com.azurpilot.ghio.ui.ksu.AppTextField
import kotlinx.coroutines.launch
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.settings.AppSettingsManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

/**
 * 提供扫码、六码配对和已配对 AP 入口；与本机 Runtime 的运行状态无关。
 *
 * Provides scanning, six-digit pairing and saved AP entries independently of local runtime state.
 */
@Composable
fun RemotePairingCard(onConnected: () -> Unit = {}, repository: AzurPilotRepository = koinInject(),
    onLocalSelected: () -> Unit = {}, settings: AppSettingsManager = koinInject()) {
    val context = LocalContext.current
    val store = remember { RemotePairings(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val remoteName by repository.remoteName.collectAsStateWithLifecycle()
    val remoteClientMode by settings.remoteClientMode.collectAsStateWithLifecycle()
    var devices by remember { mutableStateOf<List<RemotePairing>>(emptyList()) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun pair(value: String) {
        if (busy) return
        busy = true; error = false
        scope.launch {
            runCatching {
                val paired = store.pair(value)
                settings.setRemoteClientMode(true)
                devices = store.list(); code = ""
                paired
            }.onSuccess { paired ->
                repository.connectTarget(paired) { ok -> busy = false; error = !ok; if (ok) onConnected() }
            }.onFailure { error = true; busy = false }
        }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringExtra("particle_secret")?.let(::pair)
    }
    LaunchedEffect(Unit) { runCatching { devices = store.list() }.onFailure { error = true } }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppText(stringResource(R.string.remote_pair_title), style = MaterialTheme.typography.titleMedium)
            AppText(stringResource(R.string.remote_pair_hint), style = MaterialTheme.typography.bodySmall)
            if (remoteName != null) {
                AppText(stringResource(R.string.remote_pair_current, remoteName.orEmpty()))
            }
            if (remoteName != null || remoteClientMode) {
                AppTextButton(enabled = !busy, onClick = {
                    scope.launch {
                        settings.setRemoteClientMode(false)
                        repository.connectTarget(null) { }
                        onLocalSelected()
                    }
                }) { AppText(stringResource(R.string.remote_pair_local)) }
            }
            AppButton(enabled = !busy, onClick = {
                scanner.launch(Intent(context, ParticleScanActivity::class.java))
            }) { AppText(stringResource(R.string.remote_pair_scan)) }
            AppTextField(value = code, onValueChange = { value ->
                code = value.filter { it in '0'..'9' }.take(6)
            }, label = { AppText(stringResource(R.string.remote_pair_code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            AppButton(enabled = !busy && code.length == 6, onClick = { pair(code) }) {
                AppText(stringResource(if (busy) R.string.remote_pair_busy else R.string.remote_pair_connect))
            }
            if (error) AppText(stringResource(R.string.remote_pair_failed), color = MaterialTheme.colorScheme.error)
            devices.forEach { device ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            settings.setRemoteClientMode(true)
                            repository.connectTarget(device) { ok -> busy = false; error = !ok; if (ok) onConnected() }
                        }
                    }, modifier = Modifier.weight(1f)) { AppText(device.name.ifBlank { device.deviceId.take(8) }) }
                    AppTextButton(enabled = !busy && repository.sourceId.value != device.deviceId, onClick = {
                        scope.launch { runCatching { store.remove(device.deviceId); devices = store.list() }
                            .onFailure { error = true } }
                    }) { AppText(stringResource(R.string.remote_pair_remove)) }
                }
            }
        }
    }
}
