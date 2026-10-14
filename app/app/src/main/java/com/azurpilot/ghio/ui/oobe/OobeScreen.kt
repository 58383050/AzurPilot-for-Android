@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.azurpilot.ghio.ui.oobe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.privileged.PermissionManager
import com.azurpilot.ghio.theme.ksu.LocalUiMode
import com.azurpilot.ghio.theme.ksu.UiMode
import com.azurpilot.ghio.ui.ksu.AppButton as Button
import com.azurpilot.ghio.ui.ksu.AppIcon as Icon
import com.azurpilot.ghio.ui.ksu.AppIconButton as IconButton
import com.azurpilot.ghio.ui.ksu.AppSurface as Surface
import com.azurpilot.ghio.ui.ksu.AppText as Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 保存七步激活流程的状态，并让导航、标题与内容作为完整页面共同移动。
 * 预览允许关闭，首次激活只通过完成回调持久化。
 *
 * Retains seven activation steps and moves navigation, headings, and content as one page.
 * Preview can dismiss; initial activation persists only through the completion callback.
 */
@Composable
fun OobeScreen(alreadyAcknowledged: Boolean, onComplete: () -> Unit,
    modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val steps = OobeStep.entries
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var acknowledged by rememberSaveable { mutableStateOf(alreadyAcknowledged) }
    var navigating by remember { mutableStateOf(false) }
    val savedSteps = rememberSaveableStateHolder()
    val permissions: PermissionManager = koinInject()
    val access by permissions.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val miuix = LocalUiMode.current == UiMode.Miuix
    val reduced = rememberOobeReducedMotion()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val progress = stringResource(R.string.oobe_progress_format, currentIndex + 1, steps.size)
    val previous: () -> Unit = {
        if (!navigating) {
            if (currentIndex == 0) onDismiss?.invoke() else currentIndex--
        }
    }
    BackHandler(enabled = currentIndex > 0 || onDismiss != null, onBack = previous)
    MaterialTheme(typography = if (miuix) MaterialTheme.typography.oobeMiuix() else MaterialTheme.typography) {
    // 预览覆盖主页时让整页参与命中，空白区域不能点到后方操作。
    Surface(modifier.fillMaxSize().pointerInput(Unit) { }, color = MaterialTheme.colorScheme.background) {
        AnimatedContent(currentIndex, Modifier.fillMaxSize().clipToBounds().semantics {
            stateDescription = progress
        }, transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            if (reduced) fadeIn(tween(100)).togetherWith(fadeOut(tween(100)))
            else {
                val spec = if (miuix) tween<IntOffset>(875, easing = HyperOsPageEasing) else spatial
                slideInHorizontally(spec) { it * direction }
                    .togetherWith(slideOutHorizontally(spec) { -it * direction })
            }.using(SizeTransform(clip = false))
        }, label = "OobePage") { index ->
            savedSteps.SaveableStateProvider(index) {
                val step = steps[index]
                val canProceed = !navigating && (step != OobeStep.AGREEMENT || acknowledged)
                val primary = stringResource(when {
                    step == OobeStep.READY -> R.string.oobe_action_start
                    step == OobeStep.PRIVILEGED && !access.isGranted(access.configuredBackend) -> R.string.oobe_action_skip
                    else -> R.string.oobe_action_next
                })
                val secondary = stringResource(if (index == 0) R.string.dialog_cancel else R.string.oobe_action_prev)
                val next: () -> Unit = {
                    // 离场页与同一帧重复点击不能改变新页面的步骤。
                    if (canProceed && index == currentIndex) {
                        if (step == OobeStep.READY) onComplete()
                        else if (step == OobeStep.PRIVILEGED && !access.isGranted(access.configuredBackend)) {
                            navigating = true
                            scope.launch {
                                try { permissions.skipShizukuCheck(); currentIndex++ }
                                finally { navigating = false }
                            }
                        } else currentIndex++
                    }
                }
                val back: () -> Unit = { if (index == currentIndex) previous() }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (index == 0) {
                        OobeStepWelcome(Modifier.weight(1f).fillMaxWidth(), next, primary,
                            canProceed, if (onDismiss != null) back else null, secondary)
                    } else {
                        Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().height(56.dp)
                            .padding(horizontal = if (miuix) 12.dp else 20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(back, Modifier.size(48.dp).semantics { contentDescription = secondary },
                                enabled = !navigating) {
                                if (miuix) Icon(painterResource(R.drawable.oobe_os3_back), null,
                                    Modifier.size(22.dp, 16.dp), tint = MaterialTheme.colorScheme.onSurface)
                                else Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                            }
                        }
                        Column(Modifier.weight(1f).widthIn(max = 600.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = if (miuix) 16.dp else 28.dp)) {
                            when (step) {
                                OobeStep.LANGUAGE -> OobeStepLanguage()
                                OobeStep.AGREEMENT -> OobeStepAgreement(alreadyAcknowledged,
                                    onAcknowledgeStateChanged = { acknowledged = it })
                                OobeStep.PREFERENCES -> OobeStepPreferences()
                                OobeStep.PRIVILEGED -> OobeStepPrivileged()
                                OobeStep.TUTORIAL -> OobeStepTutorial()
                                OobeStep.READY -> OobeStepReady()
                                OobeStep.WELCOME -> Unit
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                        Row(Modifier.widthIn(max = 600.dp).fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = if (miuix) 28.dp else 20.dp),
                            horizontalArrangement = if (miuix) Arrangement.Center else Arrangement.End) {
                            OobeNextButton(primary, canProceed, next)
                        }
                    }
                }
            }
        }
    }
    }
}

/** 激活向导的主题导航按钮。 / The themed activation navigation button. */
@Composable
internal fun OobeNextButton(label: String, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    if (LocalUiMode.current == UiMode.Miuix) IconButton(onClick,
        modifier.size(70.dp).background(
            if (MaterialTheme.colorScheme.background.luminance() > .5f) Color.White.copy(alpha = .9f)
            else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .semantics { contentDescription = label; role = Role.Button; if (!enabled) disabled() },
        enabled = enabled) {
        val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .8f else .38f)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Icon(painterResource(R.drawable.oobe_os3_next_arrow), null,
            Modifier.size(29.dp, 20.dp).graphicsLayer { if (rtl) scaleX = -1f }, tint = tint)

    } else Button(onClick, modifier.heightIn(min = 56.dp).widthIn(min = 112.dp, max = 240.dp),
        enabled = enabled) { Text(label) }
}
