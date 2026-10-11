package com.azurpilot.ghio.ui.hangar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.constant.DefaultDisplayConfig
import com.azurpilot.ghio.domain.RemoteBackend
import com.azurpilot.ghio.theme.ksu.LocalUiMode
import com.azurpilot.ghio.theme.ksu.UiMode
import com.azurpilot.ghio.privileged.PermissionManager
import com.azurpilot.ghio.privileged.RemoteAccessState
import com.azurpilot.ghio.privileged.ShizukuReadinessStage
import com.azurpilot.ghio.proot.AzurPilotOverview
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.AzurPilotRunController
import com.azurpilot.ghio.proot.AzurPilotRunState
import com.azurpilot.ghio.proot.AzurPilotTaskState
import com.azurpilot.ghio.proot.ProotHost
import com.azurpilot.ghio.proot.ProotPhase
import com.azurpilot.ghio.proot.ProotSnapshot
import com.azurpilot.ghio.service.HostSnapshot
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.theme.AzurPilotTheme
import com.azurpilot.ghio.ui.components.AppCard
import com.azurpilot.ghio.ui.components.ScreenSaverButton
import com.azurpilot.ghio.ui.ksu.AppButton
import com.azurpilot.ghio.ui.ksu.AppIcon
import com.azurpilot.ghio.ui.ksu.AppLinearProgressIndicator
import com.azurpilot.ghio.ui.ksu.AppPageScaffold
import com.azurpilot.ghio.ui.ksu.AppText
import com.azurpilot.ghio.ui.ksu.AppTextButton
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.cos
import kotlin.math.sin

/**
 * 设备主页只展示可执行的操作与真实任务状态，不暴露实例或底层服务。
 *
 * The device home shows actionable controls and verified task state, without exposing
 * instance selection, process details, or internal service health.
 */
@Composable
fun HangarScreen(
    active: Boolean,
    modifier: Modifier = Modifier,
    bottomInnerPadding: Dp = 0.dp,
    onOpenSettings: () -> Unit = {},
    onSettingsAnchorChanged: (Offset) -> Unit = {},
    onOpenTasks: () -> Unit = {},
    hostState: HostState = koinInject(),
    runController: AzurPilotRunController = koinInject(),
    prootHost: ProotHost = koinInject(),
    repository: AzurPilotRepository = koinInject(),
    permissionManager: PermissionManager = koinInject(),
) {
    val host by hostState.snapshot.collectAsStateWithLifecycle()
    val run by runController.state.collectAsStateWithLifecycle()
    val proot by prootHost.state.collectAsStateWithLifecycle()
    val connected by repository.connected.collectAsStateWithLifecycle()
    val overview by repository.overview.collectAsStateWithLifecycle()
    val access by permissionManager.state.collectAsStateWithLifecycle()
    val shizuku by permissionManager.readiness.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 仅在虚拟屏确实不存在时补拉环境。返回主页不能再次主动探测桥接，
    // 否则探测的短暂失败会把已就绪的页面闪成「正在准备设备」。
    LaunchedEffect(active, host.privilegedConnected) {
        if (active && host.privilegedConnected &&
            hostState.snapshot.value.vdDisplayId == DefaultDisplayConfig.DISPLAY_NONE
        ) {
            hostState.ensureEnvironmentStarted()
        }
    }

    val mode = homeMode(run, proot, host, access, shizuku.stage)
    // 网关可能残留上次浏览的实例，不能将其他实例的任务误显示在主页。
    val currentConfig = if (run.runnerAlive) run.runningConfig ?: run.selectedConfig
        else run.selectedConfig
    val activeOverview = overview?.takeIf {
        connected && it.instance == currentConfig
    }

    val gearAnchor = Modifier.onGloballyPositioned { coordinates ->
        onSettingsAnchorChanged(coordinates.boundsInRoot().center)
    }

    AppPageScaffold(
        title = stringResource(R.string.nav_hangar),
        modifier = modifier,
        actions = {
            if (com.azurpilot.ghio.theme.ksu.LocalUiMode.current ==
                com.azurpilot.ghio.theme.ksu.UiMode.Miuix
            ) {
                top.yukonga.miuix.kmp.basic.IconButton(
                    onClick = onOpenSettings,
                    modifier = gearAnchor,
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onBackground,
                    )
                }
            } else {
                IconButton(onClick = onOpenSettings, modifier = gearAnchor) {
                    AppIcon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                    )
                }
            }
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            HomeRunCard(
                mode = mode,
                onStart = runController::startRunner,
                onStop = runController::stopRunner,
                onRetry = prootHost::ensureStarted,
                onResolve = {
                    when (mode) {
                        HomeMode.NEEDS_SHIZUKU -> permissionManager.openShizuku(context)
                        HomeMode.NEEDS_INSTALL_SHIZUKU -> permissionManager.openShizukuDownload(context)
                        HomeMode.NEEDS_AUTH -> scope.launch {
                            permissionManager.requestRemoteAccess()
                            permissionManager.bindService()
                        }
                        HomeMode.NEEDS_CONNECTION -> scope.launch { permissionManager.bindService() }
                        HomeMode.NEEDS_ACTION -> onOpenSettings()
                        else -> Unit
                    }
                },
            )

            // 息屏运行是独立的手动操作，不能依赖调度器已经启动。
            // 是否支持由 ScreenSaverButton 按真实运行模式判断。
            ScreenSaverButton(modifier = Modifier.fillMaxWidth())

            HomeTasks(
                overview = activeOverview,
                loading = !connected || activeOverview == null,
                running = run.runnerAlive && run.reachable && run.statusKnown,
                onOpenTasks = onOpenTasks,
            )
            Spacer(Modifier.height(bottomInnerPadding + 16.dp))
        }
    }
}

/** 将来源状态收敛成用户可理解的结果，失联时不沿用旧的运行判定。 / Map verified status to user-facing states; never treat stale status as verified. */
private enum class HomeMode {
    READY, RUNNING, RUNNING_INTERRUPTED, STARTING, STOPPING, PREPARING, PREPARING_DEVICE,
    UPDATING, FAILED, CONNECTING, NEEDS_SHIZUKU, NEEDS_INSTALL_SHIZUKU,
    NEEDS_AUTH, NEEDS_CONNECTION, NEEDS_ACTION, TOOL_ACTIVE
}

/** 同步状态模型，不启动额外轮询。 / Derive the home state without adding a new polling loop. */
private fun homeMode(
    run: AzurPilotRunState,
    proot: ProotSnapshot,
    host: HostSnapshot,
    access: RemoteAccessState,
    shizukuStage: ShizukuReadinessStage,
): HomeMode = when {
    run.busy -> when {
        run.runnerAlive -> HomeMode.STOPPING
        host.vdDisplayId == DefaultDisplayConfig.DISPLAY_NONE -> HomeMode.PREPARING_DEVICE
        // 设备已创建但桥暂时不可达是连接问题，不应误报为重新准备设备。
        !host.bridgeReachable -> HomeMode.CONNECTING
        else -> HomeMode.STARTING
    }

    // 运行状态与设备控制状态是不同的事实；控制丢失时不能宣称运行正常。
    run.reachable && run.statusKnown && run.runnerAlive ->
        if (host.privilegedConnected && host.environmentUp &&
            access.isAvailable(access.configuredBackend) &&
            access.isGranted(access.configuredBackend))
            HomeMode.RUNNING else HomeMode.RUNNING_INTERRUPTED

    proot.phase == ProotPhase.UPDATING -> HomeMode.UPDATING
    proot.phase == ProotPhase.PREPARING || proot.phase == ProotPhase.STARTING ->
        HomeMode.PREPARING
    proot.phase == ProotPhase.FAILED -> HomeMode.FAILED

    // 原来的 ready 只验证了本机 HTTP 接口，Shizuku 没启动也会误显示就绪。
    access.configuredBackend == RemoteBackend.SHIZUKU && !access.shizukuAvailable ->
        when (shizukuStage) {
            ShizukuReadinessStage.NotInstalled -> HomeMode.NEEDS_INSTALL_SHIZUKU
            ShizukuReadinessStage.OfficialConflict -> HomeMode.NEEDS_ACTION
            else -> HomeMode.NEEDS_SHIZUKU
        }
    !access.isAvailable(access.configuredBackend) -> HomeMode.NEEDS_ACTION
    !access.isGranted(access.configuredBackend) -> HomeMode.NEEDS_AUTH
    !host.privilegedConnected -> HomeMode.NEEDS_CONNECTION
    host.environmentIssue != null -> HomeMode.NEEDS_ACTION
    host.vdDisplayId == DefaultDisplayConfig.DISPLAY_NONE -> HomeMode.PREPARING_DEVICE
    !host.bridgeReachable -> HomeMode.CONNECTING

    run.reachable && run.statusKnown && run.toolAlive -> HomeMode.TOOL_ACTIVE
    run.reachable && run.statusKnown -> HomeMode.READY
    else -> HomeMode.CONNECTING
}

/** 主视觉区只包含状态与唯一主要操作。 / The primary surface contains only the result and one primary action. */
@Composable
private fun HomeRunCard(
    mode: HomeMode,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onResolve: () -> Unit,
) {
    val title = when (mode) {
        HomeMode.READY -> R.string.home_ready_title
        HomeMode.RUNNING -> R.string.home_running_title
        HomeMode.RUNNING_INTERRUPTED -> R.string.home_running_interrupted_title
        HomeMode.STARTING -> R.string.home_starting_title
        HomeMode.STOPPING -> R.string.home_stopping_title
        HomeMode.PREPARING -> R.string.home_preparing_title
        HomeMode.PREPARING_DEVICE -> R.string.home_preparing_device_title
        HomeMode.UPDATING -> R.string.home_updating_title
        HomeMode.FAILED -> R.string.home_failed_title
        HomeMode.CONNECTING -> R.string.home_connecting_title
        HomeMode.NEEDS_SHIZUKU -> R.string.home_needs_shizuku_title
        HomeMode.NEEDS_INSTALL_SHIZUKU -> R.string.home_needs_install_title
        HomeMode.NEEDS_AUTH -> R.string.home_needs_auth_title
        HomeMode.NEEDS_CONNECTION -> R.string.home_needs_connection_title
        HomeMode.NEEDS_ACTION -> R.string.home_attention_title
        HomeMode.TOOL_ACTIVE -> R.string.home_tool_active_title
    }
    val description = when (mode) {
        HomeMode.READY -> R.string.home_ready_description
        HomeMode.RUNNING -> R.string.home_running_description
        HomeMode.RUNNING_INTERRUPTED -> R.string.home_running_interrupted_description
        HomeMode.STARTING, HomeMode.STOPPING -> R.string.home_busy_description
        HomeMode.PREPARING -> R.string.home_preparing_description
        HomeMode.PREPARING_DEVICE -> R.string.home_preparing_device_description
        HomeMode.UPDATING -> R.string.home_updating_description
        HomeMode.FAILED -> R.string.home_failed_description
        HomeMode.CONNECTING -> R.string.home_connecting_description
        HomeMode.NEEDS_SHIZUKU -> R.string.home_needs_shizuku_description
        HomeMode.NEEDS_INSTALL_SHIZUKU -> R.string.home_needs_install_description
        HomeMode.NEEDS_AUTH -> R.string.home_needs_auth_description
        HomeMode.NEEDS_CONNECTION -> R.string.home_needs_connection_description
        HomeMode.NEEDS_ACTION -> R.string.home_attention_description
        HomeMode.TOOL_ACTIVE -> R.string.home_tool_active_description
    }
    val isActive = mode == HomeMode.RUNNING || mode == HomeMode.RUNNING_INTERRUPTED
    val canResolve = mode == HomeMode.NEEDS_SHIZUKU ||
        mode == HomeMode.NEEDS_INSTALL_SHIZUKU || mode == HomeMode.NEEDS_AUTH ||
        mode == HomeMode.NEEDS_CONNECTION || mode == HomeMode.NEEDS_ACTION
    val canStart = mode == HomeMode.READY
    val canRetry = mode == HomeMode.FAILED
    val primaryColor: Color = when (mode) {
        HomeMode.RUNNING -> AzurPilotTheme.palette.success
        HomeMode.RUNNING_INTERRUPTED, HomeMode.NEEDS_SHIZUKU,
        HomeMode.NEEDS_INSTALL_SHIZUKU, HomeMode.NEEDS_AUTH,
        HomeMode.NEEDS_CONNECTION -> MaterialTheme.colorScheme.error
        HomeMode.READY -> MaterialTheme.colorScheme.primary
        HomeMode.FAILED, HomeMode.NEEDS_ACTION -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isWorking = when (mode) {
        HomeMode.STARTING, HomeMode.STOPPING, HomeMode.PREPARING,
        HomeMode.PREPARING_DEVICE, HomeMode.UPDATING, HomeMode.CONNECTING -> true
        else -> false
    }
    val animatedPrimaryColor by animateColorAsState(
        targetValue = primaryColor,
        animationSpec = tween(durationMillis = 240),
        label = "homeStatusColor",
    )

    AppCard(contentPadding = PaddingValues(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(9.dp).background(animatedPrimaryColor, CircleShape))
                AppText(
                    stringResource(R.string.home_status_label),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(18.dp))
            // 状态变化时一起过渡标题与说明，避免两段文字各自跳变。
            AnimatedContent(
                targetState = title to description,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 5 })
                        .togetherWith(fadeOut(tween(130)))
                },
                label = "homeStatusTransition",
            ) { (titleRes, descriptionRes) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText(
                        stringResource(titleRes),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    AppText(
                        stringResource(descriptionRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // 只有真正处于准备/启动/停止阶段才播放进度，不虚构百分比。
            AnimatedVisibility(
                visible = isWorking,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
            ) {
                Column {
                    Spacer(Modifier.height(19.dp))
                    AppLinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(24.dp))

            AppButton(
                onClick = when {
                    isActive -> onStop
                    canRetry -> onRetry
                    canResolve -> onResolve
                    else -> onStart
                },
                enabled = isActive || canStart || canRetry || canResolve,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                if (isWorking) {
                    HomeLoadingClock(
                        modifier = Modifier.size(20.dp),
                        color = if (LocalUiMode.current == UiMode.Miuix) {
                            top.yukonga.miuix.kmp.theme.LocalContentColor.current
                        } else {
                            androidx.compose.material3.LocalContentColor.current
                        },
                    )
                } else {
                    AppIcon(
                        imageVector = when {
                            isActive -> Icons.Filled.Stop
                            canRetry -> Icons.Filled.Refresh
                            canResolve -> Icons.Filled.Settings
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                AppText(
                    stringResource(
                        when {
                            isActive -> R.string.hangar_stop
                            canRetry -> R.string.home_retry
                            mode == HomeMode.STARTING -> R.string.home_starting_title
                            mode == HomeMode.STOPPING -> R.string.home_stopping_title
                            mode == HomeMode.PREPARING -> R.string.home_preparing_title
                            mode == HomeMode.PREPARING_DEVICE -> R.string.home_preparing_device_title
                            mode == HomeMode.UPDATING -> R.string.home_updating_title
                            mode == HomeMode.CONNECTING -> R.string.home_connecting_title
                            mode == HomeMode.NEEDS_SHIZUKU -> R.string.home_open_shizuku
                            mode == HomeMode.NEEDS_INSTALL_SHIZUKU -> R.string.home_install_shizuku
                            mode == HomeMode.NEEDS_AUTH -> R.string.home_grant_access
                            mode == HomeMode.NEEDS_CONNECTION -> R.string.home_reconnect
                            mode == HomeMode.NEEDS_ACTION -> R.string.home_attention_settings
                            canStart -> R.string.hangar_start
                            else -> R.string.home_wait_button
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * 轻量时钟加载动画：表盘保持静止，仅指针旋转。
 * 在 Canvas 绘制阶段读取动画角度，避免每帧触发布局重组。
 */
@Composable
private fun HomeLoadingClock(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val transition = rememberInfiniteTransition(label = "homeLoadingClock")
    val minuteHand = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeLoadingClockHand",
    )
    val hourHand = transition.animateFloat(
        initialValue = 300f,
        targetValue = 660f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeLoadingClockHour",
    )

    Canvas(modifier = modifier) {
        val clockCenter = center
        val radius = size.minDimension * 0.43f
        drawCircle(
            color = color,
            radius = radius,
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 两根指针均匀速顺时针转动，时针速度为分针的三分之一。
        val hourRadians = Math.toRadians(hourHand.value.toDouble())
        drawLine(
            color = color,
            start = clockCenter,
            end = Offset(
                clockCenter.x + radius * 0.48f * sin(hourRadians).toFloat(),
                clockCenter.y - radius * 0.48f * cos(hourRadians).toFloat(),
            ),
            strokeWidth = 1.9.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val minuteRadians = Math.toRadians(minuteHand.value.toDouble())
        drawLine(
            color = color,
            start = clockCenter,
            end = Offset(
                clockCenter.x + radius * 0.72f * sin(minuteRadians).toFloat(),
                clockCenter.y - radius * 0.72f * cos(minuteRadians).toFloat(),
            ),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = 1.1.dp.toPx())
    }
}

/** 必须用户处理时才出现提醒，而不是常驻内部服务诊断。 / Show only user-actionable issues, not a permanent diagnostics dashboard. */
@Composable
private fun HomeAttention(onOpenSettings: () -> Unit) {
    AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(
                imageVector = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppText(
                    stringResource(R.string.home_attention_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                AppText(
                    stringResource(R.string.home_attention_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextButton(onClick = onOpenSettings) {
                    AppText(stringResource(R.string.home_attention_settings))
                }
            }
        }
    }
}

/** 只呈现可确认的当前和下一项任务。 / Show only the confirmed active and upcoming tasks. */
@Composable
private fun HomeTasks(
    overview: AzurPilotOverview?,
    loading: Boolean,
    running: Boolean,
    onOpenTasks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                stringResource(R.string.home_tasks_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            AppTextButton(onClick = onOpenTasks) {
                AppText(stringResource(R.string.home_tasks_view_all))
            }
        }

        val tasks = overview?.tasks.orEmpty()
        val current = tasks.firstOrNull { it.state == AzurPilotTaskState.Running }
        val next = tasks.firstOrNull { it.state == AzurPilotTaskState.Pending }
            ?: tasks.firstOrNull { it.state == AzurPilotTaskState.Waiting }

        AppCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)) {
            when {
                loading -> AppText(
                    stringResource(R.string.home_tasks_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                tasks.isEmpty() -> AppText(
                    stringResource(R.string.home_tasks_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (running && current != null) {
                        HomeTaskLine(stringResource(R.string.home_tasks_current), current.name)
                    }
                    if (next != null) {
                        HomeTaskLine(stringResource(R.string.home_tasks_next), next.name)
                    }
                    HomeTaskLine(
                        stringResource(R.string.home_tasks_count),
                        stringResource(R.string.home_tasks_count_value, tasks.size),
                    )
                }
            }
        }
    }
}

/** 简单的标签和值，减少多层嵌套与截断。 / Compact key/value layout without nested cards. */
@Composable
private fun HomeTaskLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppText(
            label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        AppText(
            value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
