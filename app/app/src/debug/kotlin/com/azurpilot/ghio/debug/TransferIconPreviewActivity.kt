package com.azurpilot.ghio.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azurpilot.ghio.MainActivity
import com.azurpilot.ghio.R
import com.azurpilot.ghio.theme.AzurPilotTheme
import com.azurpilot.ghio.ui.components.AnimatedTransferIcon
import com.azurpilot.ghio.ui.components.TransferStage
import com.azurpilot.ghio.ui.ksu.AppText

/**
 * 在调试包中以弹窗和行内尺寸预览实际图标，不触发下载或运行时部署。
 *
 * 使用应用当前主题；仅调试清单注册此入口，便于 ADB 在真机核对动画和系统设置。
 *
 * Previews actual icons at dialog and inline sizes in debug builds without starting transfers.
 *
 * Uses the app's current theme. Only the debug manifest registers this entry point so ADB can
 * inspect animation playback and system settings on a physical device.
 */
class TransferIconPreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AzurPilotTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        AppText("动态图标预览", style = MaterialTheme.typography.headlineSmall)
                        AppText("useAnimations · 48 / 24 / 20 dp")
                        TransferStage.entries.forEach { stage ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimatedTransferIcon(stage)
                                AnimatedTransferIcon(stage, Modifier.size(24.dp))
                                AnimatedTransferIcon(stage, Modifier.size(20.dp))
                                AppText(
                                    when (stage) {
                                        TransferStage.DOWNLOAD -> "下载"
                                        TransferStage.EXTRACT -> "解压"
                                        TransferStage.VERIFY -> "校验"
                                        TransferStage.APPLY -> "更新"
                                    },
                                )
                            }
                        }
                        TextButton(onClick = {
                            startActivity(Intent(this@TransferIconPreviewActivity, MainActivity::class.java))
                            finish()
                        }) {
                            AppText(stringResource(R.string.common_back))
                        }
                    }
                }
            }
        }
    }
}
