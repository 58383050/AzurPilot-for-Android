package com.azurpilot.ghio.privileged

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.azurpilot.ghio.R
import com.azurpilot.ghio.theme.AzurPilotTheme
import kotlinx.coroutines.launch

/**
 * 承载内置无线调试配对与启动；只有主线程 UI 可发起系统设置和配对通知。
 *
 * Hosts embedded wireless debugging pairing and activation. Main-thread UI starts system
 * settings and the pairing notification. Network operations belong to [EmbeddedAdbManager].
 */
class AdbActivationActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startPairingNotification() else Toast.makeText(
            this, R.string.adb_notification_denied, Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * 显示可滚动的 Material 3 启动页；屏幕重建不会保存配对码。
     *
     * Displays a scrollable Material 3 activation page without persisting the pairing code.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AzurPilotTheme {
                val status by EmbeddedAdbManager.status.collectAsStateWithLifecycle()
                val busy = status == EmbeddedAdbManager.Status.Working
                var port by rememberSaveable { mutableStateOf("") }
                var pairingPort by rememberSaveable { mutableStateOf("") }
                var code by androidx.compose.runtime.remember { mutableStateOf("") }
                Scaffold { insets ->
                    Column(
                        Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(R.string.adb_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                        Text(stringResource(if (Build.VERSION.SDK_INT >= 30) R.string.adb_guide else R.string.adb_legacy_guide))
                        Text(stringResource(R.string.adb_network_note))
                        Text(stringResource(when (status) {
                            EmbeddedAdbManager.Status.Idle -> R.string.adb_idle
                            EmbeddedAdbManager.Status.Working -> R.string.adb_working
                            EmbeddedAdbManager.Status.Ready -> R.string.adb_ready
                            EmbeddedAdbManager.Status.Failed -> R.string.adb_failed
                        }))
                        Button(
                            onClick = { lifecycleScope.launch { EmbeddedAdbManager.activate() } },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.adb_start)) }
                        if (Build.VERSION.SDK_INT >= 30) {
                            Button(onClick = { requestPairingNotification() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.adb_pair_notification))
                            }
                            TextButton(onClick = { openDeveloperSettings() }) {
                                Text(stringResource(R.string.adb_open_settings))
                            }
                            Text(stringResource(R.string.adb_manual_pair_guide))
                            OutlinedTextField(
                                value = pairingPort, onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                                label = { Text(stringResource(R.string.adb_pair_port)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, enabled = !busy,
                            )
                            OutlinedTextField(
                                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                label = { Text(stringResource(R.string.adb_pair_code)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true, enabled = !busy,
                            )
                            TextButton(
                                onClick = {
                                    val pairingCode = code
                                    code = ""
                                    lifecycleScope.launch { EmbeddedAdbManager.pair(pairingPort.toInt(), pairingCode) }
                                },
                                enabled = !busy && pairingPort.toIntOrNull() in 1..65535 && code.matches(Regex("[0-9]{6}")),
                            ) { Text(stringResource(R.string.adb_pair_start)) }
                        }
                        Text(stringResource(R.string.adb_manual_connect_guide))
                        OutlinedTextField(
                            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.adb_connect_port)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, enabled = !busy,
                        )
                        TextButton(
                            onClick = { lifecycleScope.launch { EmbeddedAdbManager.activate(manualPort = port.toInt()) } },
                            enabled = !busy && port.toIntOrNull() in 1..65535,
                        ) { Text(stringResource(R.string.adb_start)) }
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.common_back)) }
                    }
                }
            }
        }
    }

    private fun requestPairingNotification() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startPairingNotification()
    }

    private fun startPairingNotification() {
        ContextCompat.startForegroundService(this, Intent(this, AdbPairingService::class.java))
        openDeveloperSettings()
    }

    private fun openDeveloperSettings() {
        runCatching { startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .onFailure { Toast.makeText(this, R.string.adb_settings_failed, Toast.LENGTH_LONG).show() }
    }
}
