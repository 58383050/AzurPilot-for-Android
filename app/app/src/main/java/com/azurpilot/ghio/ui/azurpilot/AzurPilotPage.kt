package com.azurpilot.ghio.ui.azurpilot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.R
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.AzurPilotRunController
import com.azurpilot.ghio.proot.ProotHost
import com.azurpilot.ghio.proot.ProotPhase
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.theme.AppTokens
import com.azurpilot.ghio.ui.azurpilot.sections.ConfigSection
import com.azurpilot.ghio.ui.azurpilot.sections.LogsSection
import com.azurpilot.ghio.ui.azurpilot.sections.OverviewSection
import com.azurpilot.ghio.ui.azurpilot.sections.StatisticsSection
import com.azurpilot.ghio.ui.azurpilot.sections.TaskConfigPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.AnnouncementPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.DeploySettingsPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.InstancesPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.MeowfficerPage
import com.azurpilot.ghio.ui.azurpilot.sections.settings.SettingsSection
import com.azurpilot.ghio.ui.azurpilot.sections.settings.UpdaterPage
import com.azurpilot.ghio.ui.components.ExpressiveLoadingIndicator
import com.azurpilot.ghio.ui.ksu.AppFilterChip as FilterChip
import com.azurpilot.ghio.ui.ksu.AppIcon as Icon
import com.azurpilot.ghio.ui.ksu.AppIconButton as IconButton
import com.azurpilot.ghio.ui.ksu.AppSnackbarHost as SnackbarHost
import com.azurpilot.ghio.ui.ksu.AppSurface as Surface
import com.azurpilot.ghio.ui.ksu.AppText as Text
import com.azurpilot.ghio.ui.navigation.AppRoute
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

/**
 * 渲染控制台的平级分区，详情通过宿主唯一导航栈打开。
 *
 * Renders peer console sections, opening details through the host's single navigation stack.
 *
 * @param active 是否允许补充启动运行环境。 / Whether environment warm-up is allowed.
 * @param onNavigate 向宿主推入类型目的地。 / Pushes typed destinations into the host stack.
 */
@Composable
fun AzurPilotPage(
    active: Boolean,
    onNavigate: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    AzurPilotRuntimeSurface(active = active, modifier = modifier) { repository ->
        AzurPilotContent(repository, onNavigate)
    }
}

/**
 * 保持运行时内容与保存状态，断连时用遮罩提示；运行控制仍由主页与悬浮窗负责。
 *
 * Retains runtime content and saved state under a connection gate. Runtime control stays with the
 * home page and overlay.
 */
@Composable
private fun AzurPilotRuntimeSurface(
    active: Boolean,
    modifier: Modifier = Modifier,
    repository: AzurPilotRepository = koinInject(),
    hostState: HostState = koinInject(),
    prootHost: ProotHost = koinInject(),
    runController: AzurPilotRunController = koinInject(),
    content: @Composable (AzurPilotRepository) -> Unit,
) {
    val connected by repository.connected.collectAsStateWithLifecycle()
    val hostSnapshot by hostState.snapshot.collectAsStateWithLifecycle()
    val prootState by prootHost.state.collectAsStateWithLifecycle()
    val runState by runController.state.collectAsStateWithLifecycle()
    val message by repository.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 和主页一样：这一页可见时才补一次环境拉起，避免在后台白转
    LaunchedEffect(active, hostSnapshot.privilegedConnected) {
        if (active && hostSnapshot.privilegedConnected) hostState.ensureEnvironmentStarted()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            repository.dismissMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 底层页面与导航栈保持常驻，断连或服务重启时不暴力卸载，保护用户输入与回退状态
        content(repository)

        // 运行时未就绪或断连时使用悬浮遮罩平滑覆盖，避免破坏底层视图树
        if (!connected) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                RuntimeGate(
                    reachable = runState.reachable,
                    phase = prootState.phase,
                    detail = prootState.detail,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(AppTokens.Spacing.lg),
        )
    }
}

/**
 * 网关还没连上时的接管屏
 *
 * 「未就绪」与「环境正在起」要分开说：准备链要几分钟，只显示一句「未就绪」会让人以为坏了。
 *
 * The takeover screen shown while the gateway is not connected yet.
 *
 * "Not ready" and "environment coming up" must be told apart: the preparation chain takes minutes,
 * and a bare "not ready" would read as something broken.
 *
 * @param reachable 运行时 HTTP 端口是否可达 / whether the runtime's HTTP port is reachable
 * @param phase PRoot 会话阶段 / the PRoot session phase
 * @param detail 失败阶段的详情文案 / the failure-phase detail text
 */
@Composable
private fun RuntimeGate(reachable: Boolean, phase: ProotPhase, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(AppTokens.Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveLoadingIndicator()
        Text(
            text =
                when {
                    phase == ProotPhase.FAILED ->
                        stringResource(R.string.proot_phase_failed, detail)
                    phase == ProotPhase.PREPARING -> stringResource(R.string.proot_phase_preparing)
                    phase == ProotPhase.UPDATING -> stringResource(R.string.proot_phase_updating)
                    phase == ProotPhase.STARTING -> stringResource(R.string.proot_phase_starting)
                    reachable -> stringResource(R.string.ap_connecting)
                    else -> stringResource(R.string.ap_runtime_idle)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppTokens.Spacing.lg),
        )
    }
}

/**
 * AzurPilot 详情页标准容器：拥有独立的 TopAppBar 与返回导航
 *
 * 遵循 MD3 规范：各详情页拥有独立的 TopAppBar，进入详情页时整体推入（不保留顶层 TabRow 与 ChipRow）， 退出时向右滑出返回上层，维持层级与空间连续。
 *
 * Standard container for AzurPilot detail pages: owns an independent TopAppBar and back navigation.
 * Adheres to MD3: detail pages have independent TopAppBars and transition as whole screens without
 * retaining the top-level section tabs or instance chip row.
 *
 * @param title 顶栏标题 / Top app bar title
 * @param onBack 返回上一级回调 / Back callback
 * @param modifier 修饰符 / Modifier
 * @param actions 顶栏右侧动作插槽 / Top app bar actions slot
 * @param content 详情页内容主体 / Detail page content body
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApDetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    com.azurpilot.ghio.ui.ksu.AppPageScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = actions,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), content = content)
    }
}

/**
 * 在主线程组合平级分区，并保存每个分区的滚动位置与输入状态。
 *
 * Composes peer sections on the main thread and saves each section's scroll and input state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AzurPilotContent(
    repository: AzurPilotRepository,
    onNavigate: (AppRoute) -> Unit,
) {
    val instances by repository.instances.collectAsStateWithLifecycle()
    val selected by repository.selectedInstance.collectAsStateWithLifecycle()
    var currentSection by rememberSaveable { mutableStateOf(AzurPilotSection.Overview) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val pagerState =
        androidx.compose.foundation.pager.rememberPagerState(
            initialPage = currentSection.ordinal,
            pageCount = { AzurPilotSection.entries.size },
        )
    val mainPagerState = com.azurpilot.ghio.ui.ksu.rememberMainPagerState(pagerState)
    LaunchedEffect(pagerState.settledPage) {
        currentSection = AzurPilotSection.entries[pagerState.settledPage]
    }
    LaunchedEffect(pagerState.currentPage) { mainPagerState.syncPage() }

    com.azurpilot.ghio.ui.ksu.AppPageScaffold(title = stringResource(currentSection.labelRes)) {
        padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            com.azurpilot.ghio.ui.ksu.AppTabRow(
                selectedTabIndex = mainPagerState.selectedPage,
                tabs = AzurPilotSection.entries.map { stringResource(it.labelRes) },
                onTabClick = mainPagerState::animateToPage,
            )
            InstanceChipRow(
                instances = instances.map { it.name },
                selected = selected,
                onSelect = repository::selectInstance,
                onManage = { onNavigate(AppRoute.Instances) },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier.fillMaxSize()
                            .pagerGestureOverride(
                                pagerState,
                                mode =
                                    top.yukonga.miuix.kmp.utils.PagerInterceptionMode
                                        .CrossAxisInterceptor,
                            ),
                    userScrollEnabled = false,
                    overscrollEffect = null,
                    pageNestedScrollConnection =
                        top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection,
                    flingBehavior =
                        androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                            pagerState,
                            snapAnimationSpec =
                                top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec,
                        ),
                ) { page ->
                    val section = AzurPilotSection.entries[page]
                    saveableStateHolder.SaveableStateProvider(key = section.name) {
                        when (section) {
                            AzurPilotSection.Overview -> OverviewSection(repository)
                            AzurPilotSection.Config ->
                                ConfigSection(
                                    repository = repository,
                                    onOpenTask = { task -> onNavigate(AppRoute.Task(task)) },
                                )
                            AzurPilotSection.Logs -> LogsSection(repository)
                            AzurPilotSection.Statistics -> StatisticsSection(repository)
                            AzurPilotSection.Settings ->
                                SettingsSection(
                                    repository = repository,
                                    onOpenInstances = { onNavigate(AppRoute.Instances) },
                                    onOpenAnnouncement = { onNavigate(AppRoute.Announcement) },
                                    onOpenMeowfficer = { onNavigate(AppRoute.Meowfficer) },
                                    onOpenUpdater = { onNavigate(AppRoute.Updater) },
                                    onOpenDeploy = { onNavigate(AppRoute.Deploy) },
                                )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 渲染宿主栈中的运行时详情；每页拥有独立标题栏和任务搜索动作。
 *
 * Renders runtime details in the host stack, with a dedicated title bar and task search action.
 */
@Composable
fun AzurPilotDetailPage(route: AppRoute.Console, onBack: () -> Unit) {
    AzurPilotRuntimeSurface(active = true) { repository ->
        val topBarAction = remember { ApTopBarAction() }
        val titleRes =
            when (route) {
                is AppRoute.Task -> R.string.ap_title_task_config
                AppRoute.Instances -> R.string.ap_title_instances
                AppRoute.Announcement -> R.string.ap_title_announcement
                AppRoute.Meowfficer -> R.string.ap_title_meowfficer
                AppRoute.Updater -> R.string.ap_title_updater
                AppRoute.Deploy -> R.string.ap_title_deploy
            }
        ApDetailPage(
            title = stringResource(titleRes),
            onBack = onBack,
            actions = {
                if (route is AppRoute.Task) {
                    AnimatedVisibility(visible = topBarAction.visible) {
                        IconButton(onClick = { topBarAction.onClick?.invoke() }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.ap_bar_search),
                            )
                        }
                    }
                }
            },
        ) {
            when (route) {
                is AppRoute.Task ->
                    TaskConfigPage(repository, route.taskName, topBarAction = topBarAction)
                AppRoute.Instances -> InstancesPage(repository)
                AppRoute.Announcement -> AnnouncementPage(repository)
                AppRoute.Meowfficer -> MeowfficerPage(repository)
                AppRoute.Updater -> UpdaterPage(repository)
                AppRoute.Deploy -> DeploySettingsPage(repository)
            }
        }
    }
}

/**
 * 实例选择条
 *
 * 常显而不是藏进顶栏菜单：实例是这一页的**上下文**，所有分区的内容都随它变。 藏起来会出现「看了半天才发现看的是另一个实例」。
 *
 * The instance selection chip row.
 *
 * Always visible rather than tucked into a top-bar menu: the instance is the page's **context** —
 * every section's content changes with it. Hiding it leads to "reading a whole screen before
 * noticing it was another instance".
 *
 * @param instances 实例名列表 / the instance names
 * @param selected 当前选中的实例名；无实例时为 null / the selected instance name; null when no instance exists
 * @param onSelect 选中某实例 / invoked when an instance is selected
 * @param onManage 打开实例管理页 / invoked to open the instance-management page
 */
@Composable
private fun InstanceChipRow(
    instances: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppTokens.Spacing.lg, vertical = AppTokens.Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppTokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (instances.isEmpty()) {
            Text(
                text = stringResource(R.string.ap_instance_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        instances.forEach { name ->
            FilterChip(
                selected = name == selected,
                onClick = { onSelect(name) },
                label = { Text(name) },
            )
        }
        FilterChip(
            selected = false,
            onClick = onManage,
            label = { Text(stringResource(R.string.ap_instance_manage)) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AppTokens.IconSize.sm),
                )
            },
        )
    }
}
