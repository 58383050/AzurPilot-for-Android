package com.azurpilot.ghio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azurpilot.ghio.BuildConfig
import com.azurpilot.ghio.R
import com.azurpilot.ghio.auth.AppLockManager
import com.azurpilot.ghio.constant.ProjectLinks
import com.azurpilot.ghio.domain.RemoteBackend
import com.azurpilot.ghio.domain.ThemeMode
import com.azurpilot.ghio.log.LogExportKind
import com.azurpilot.ghio.privileged.PermissionManager
import com.azurpilot.ghio.proot.AzurPilotRepository
import com.azurpilot.ghio.proot.ProotHost
import com.azurpilot.ghio.proot.ProotPhase
import com.azurpilot.ghio.provision.ProvisionState
import com.azurpilot.ghio.provision.RootfsProvisioner
import com.azurpilot.ghio.service.HostState
import com.azurpilot.ghio.settings.AppSettingsManager
import com.azurpilot.ghio.settings.SettingsIntent
import com.azurpilot.ghio.settings.SettingsViewModel
import com.azurpilot.ghio.theme.AzurPilotTheme
import com.azurpilot.ghio.ui.azurpilot.AzurPilotDetailPage
import com.azurpilot.ghio.ui.azurpilot.AzurPilotPage
import com.azurpilot.ghio.ui.components.AppLockGate
import com.azurpilot.ghio.ui.components.CommunityWarningDialog
import com.azurpilot.ghio.ui.components.ShizukuReadinessDialog
import com.azurpilot.ghio.ui.hangar.HangarScreen
import com.azurpilot.ghio.ui.ksu.AppCheckbox as Checkbox
import com.azurpilot.ghio.ui.ksu.AppDialog as AlertDialog
import com.azurpilot.ghio.ui.ksu.AppText as Text
import com.azurpilot.ghio.ui.ksu.AppTextButton as TextButton
import com.azurpilot.ghio.ui.logs.AppLogDetailScreen
import com.azurpilot.ghio.ui.logs.AppLogScreen
import com.azurpilot.ghio.ui.logs.AzurPilotErrorDetailScreen
import com.azurpilot.ghio.ui.logs.AzurPilotLogDetailScreen
import com.azurpilot.ghio.ui.logs.AzurPilotLogScreen
import com.azurpilot.ghio.ui.logs.LogExportController
import com.azurpilot.ghio.ui.motion.AppNavigation
import com.azurpilot.ghio.ui.navigation.AppRoute
import com.azurpilot.ghio.ui.navigation.rememberAppNavigator
import com.azurpilot.ghio.ui.screen.ScreenPage
import com.azurpilot.ghio.ui.settings.AboutSettingsPage
import com.azurpilot.ghio.ui.settings.AdvancedSettingsPage
import com.azurpilot.ghio.ui.settings.DeviceReportSettingsPage
import com.azurpilot.ghio.ui.settings.DisplaySettingsPage
import com.azurpilot.ghio.ui.settings.KeepAliveSettingsPage
import com.azurpilot.ghio.ui.settings.LicenseDetailPage
import com.azurpilot.ghio.ui.settings.LogsSettingsPage
import com.azurpilot.ghio.ui.settings.OcrSettingsPage
import com.azurpilot.ghio.ui.settings.OpenSourceLicensesPage
import com.azurpilot.ghio.ui.settings.RuntimeSettingsPage
import com.azurpilot.ghio.ui.settings.SettingsScreen
import com.azurpilot.ghio.ui.settings.VirtualDisplaySettingsPage
import com.azurpilot.ghio.ui.settings.WidgetSettingsPage
import com.azurpilot.ghio.ui.setup.ProvisionScreen
import com.azurpilot.ghio.ui.shortcut.ShortcutRequests
import com.azurpilot.ghio.update.AppUpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 在主线程装配主题、应用锁、部署引导、统一导航及更新对话框。
 *
 * 主页面保存各 Tab 状态，设置、日志和控制台详情共享一个可恢复的 Miuix 页面栈。 ViewModel 在导航树外获取，页面切换不会改变全局设置作用域。
 *
 * Assembles the theme, app lock, provisioning gate, unified navigation, and update dialogs on the
 * main thread. Main tabs preserve their state, while settings, logs, and console details share a
 * restorable Miuix stack. View models are obtained outside navigation so page changes do not alter
 * the global settings scope.
 *
 * @param onDarkThemeChanged 通知宿主同步窗口外观。 / Updates the host window appearance.
 */
@Composable
fun AppRoot(
    onDarkThemeChanged: (Boolean) -> Unit,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    permissionManager: PermissionManager = koinInject(),
    provisioner: RootfsProvisioner = koinInject(),
    appUpdateManager: AppUpdateManager = koinInject(),
    appSettings: AppSettingsManager = koinInject(),
    appLockManager: AppLockManager = koinInject(),
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val readiness by permissionManager.readiness.collectAsStateWithLifecycle()
    val isGranting by permissionManager.isGranting.collectAsStateWithLifecycle()

    // 首启 rootfs 部署的门：未 Ready 时整屏接管，tab/二级页都在门内
    val provisionState by provisioner.state.collectAsStateWithLifecycle()
    val runtimeCheck by provisioner.updateCheck.collectAsStateWithLifecycle()
    val githubMirror by appSettings.githubMirror.collectAsStateWithLifecycle()
    var provisionSkipped by remember { mutableStateOf(false) }
    var runtimePromptDismissed by rememberSaveable { mutableStateOf(false) }
    var applyingRuntimeUpdate by remember { mutableStateOf(false) }
    val prootHost: ProotHost = koinInject()
    var prootStarted by remember { mutableStateOf(prootHost.state.value.phase != ProotPhase.IDLE) }
    LaunchedEffect(Unit) {
        if (provisioner.state.value !is ProvisionState.Ready) provisioner.start()
    }
    val appUpdateState by appUpdateManager.state.collectAsStateWithLifecycle()
    var appUpdateChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!appUpdateChecked) {
            appUpdateChecked = true
            appUpdateManager.check()
        }
    }
    val showProvision = provisionState !is ProvisionState.Ready && !provisionSkipped

    // 启动时只查版本，先等用户决定是否更新，再启动 proot。
    val installedRuntime = provisioner.installedVersion()
    val runtimeAvailable =
        runtimeCheck.checked &&
            runtimeCheck.error == null &&
            runtimeCheck.latestVersion != null &&
            runtimeCheck.latestVersion != installedRuntime
    val hotUpdateOn by appSettings.hotUpdateEnabled.collectAsStateWithLifecycle()
    // 整包重部署只在「基础镜像有差异」时弹出：rootfs_version 仅上游提交前缀不同
    // (commitOnly) 且热更开启时，源码差异交给热更通道，不弹整包。用户主动打开 App
    // 时全量更新必须前台提示（每日定时自动更新只是兜底，不抢前台的知情权）。
    val fullUpdatePending = runtimeAvailable && !(runtimeCheck.commitOnly && hotUpdateOn)
    LaunchedEffect(
        provisionState,
        runtimeCheck,
        runtimePromptDismissed,
        applyingRuntimeUpdate,
        hotUpdateOn,
    ) {
        if (
            !prootStarted &&
                provisionState is ProvisionState.Ready &&
                runtimeCheck.checked &&
                !runtimeCheck.checking &&
                !applyingRuntimeUpdate &&
                (!fullUpdatePending || runtimePromptDismissed)
        ) {
            prootHost.ensureStarted()
            prootStarted = true
        }
    }

    val appearance by appSettings.appearance.collectAsStateWithLifecycle()
    val appearanceMode = appearance.colorMode.toIntOrNull()
    val darkTheme =
        if (appearanceMode in listOf(1, 4)) false
        else if (appearanceMode in listOf(2, 5, 6)) true
        else if (appearanceMode in listOf(0, 3)) isSystemInDarkTheme()
        else
            when (settingsState.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
    LaunchedEffect(darkTheme) { onDarkThemeChanged(darkTheme) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    // 应用进入后台/熄屏时锁定应用
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                appLockManager.lock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val isUnlocked by appLockManager.isUnlocked.collectAsStateWithLifecycle()
    val appLockEnabled by appSettings.appLockEnabled.collectAsStateWithLifecycle()
    val settingsLoaded by appSettings.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(settingsLoaded) {
        if (settingsLoaded)
            com.azurpilot.ghio.ui.ksu.setEnableOnBackInvokedCallback(
                context.applicationInfo,
                appearance.enablePredictiveBack.toBoolean(),
            )
    }
    // Fail closed until the persisted setting is read; the new-install default
    // is false, but existing users may have explicitly enabled app lock.
    val isAppLocked =
        !settingsLoaded ||
            (appLockEnabled && appLockManager.canAuthenticate(context) && !isUnlocked)
    var appLockError by remember { mutableStateOf<String?>(null) }

    // 首启「机型支持列表」弹窗：盘上标志未置位且本会话未处理过才弹；
    // 必须等设置读盘完成，否则老用户会先看到默认 false 闪一下
    val communityNoticeAcknowledged by
        appSettings.communityNoticeAcknowledged.collectAsStateWithLifecycle()
    val compatNoticeShown by appSettings.compatNoticeShown.collectAsStateWithLifecycle()
    var compatNoticeDismissed by rememberSaveable { mutableStateOf(false) }
    var showCompatNoticeDialog by rememberSaveable { mutableStateOf(false) }

    AzurPilotTheme(
        darkTheme = darkTheme,
        legacyColorMode =
            when (settingsState.themeMode) {
                ThemeMode.System -> 0
                ThemeMode.Light -> 1
                ThemeMode.Dark -> 2
            },
    ) {
        AppLockGate(
            isUnlocked = !isAppLocked,
            errorMessage = appLockError,
            autoRequestUnlock = settingsLoaded,
            onUnlockRequest = {
                if (!settingsLoaded) return@AppLockGate
                appLockError = null
                activity?.let { act ->
                    appLockManager.authenticate(
                        activity = act,
                        title = context.getString(R.string.auth_prompt_title_app),
                        subtitle = context.getString(R.string.auth_prompt_subtitle),
                        onSuccess = {
                            appLockManager.markUnlocked()
                        },
                        onError = { code, message ->
                            appLockError =
                                context.getString(R.string.app_lock_auth_failed, code, message)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isAppLocked && settingsLoaded && !communityNoticeAcknowledged) {
                CommunityWarningDialog(
                    onAcknowledge = {
                        scope.launch { appSettings.setCommunityNoticeAcknowledged(true) }
                    }
                )
            }

            if (
                !isAppLocked &&
                    communityNoticeAcknowledged &&
                    provisionState is ProvisionState.Ready &&
                    fullUpdatePending &&
                    !runtimePromptDismissed &&
                    !applyingRuntimeUpdate &&
                    !prootStarted
            ) {
                AlertDialog(
                    onDismissRequest = { runtimePromptDismissed = true },
                    title = { Text(stringResource(R.string.runtime_update_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.runtime_update_message,
                                installedRuntime.orEmpty(),
                                runtimeCheck.latestVersion.orEmpty(),
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                applyingRuntimeUpdate = true
                                provisioner.applyUpdate()
                            }
                        ) {
                            Text(stringResource(R.string.runtime_update_now))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { runtimePromptDismissed = true }) {
                            Text(stringResource(R.string.app_update_later))
                        }
                    },
                )
            }
            LaunchedEffect(provisionState, applyingRuntimeUpdate) {
                if (
                    applyingRuntimeUpdate &&
                        provisionState is ProvisionState.Ready &&
                        runtimeCheck.latestVersion == provisioner.installedVersion()
                )
                    applyingRuntimeUpdate = false
                if (
                    applyingRuntimeUpdate &&
                        provisionState is ProvisionState.Ready &&
                        runtimeCheck.error != null
                ) {
                    applyingRuntimeUpdate = false
                }
            }
            if (!isAppLocked && communityNoticeAcknowledged) {
                appUpdateState.available
                    ?.takeUnless {
                        provisionState is ProvisionState.Ready &&
                            fullUpdatePending &&
                            !runtimePromptDismissed &&
                            !applyingRuntimeUpdate
                    }
                    ?.let { update ->
                        AlertDialog(
                            onDismissRequest = appUpdateManager::dismiss,
                            title = { Text(stringResource(R.string.app_update_title)) },
                            text = {
                                Text(
                                    if (appUpdateState.downloading)
                                        stringResource(R.string.app_update_downloading)
                                    else if (appUpdateState.error != null)
                                        stringResource(
                                            R.string.app_update_error,
                                            appUpdateState.error!!,
                                        )
                                    else
                                        stringResource(
                                            R.string.app_update_message,
                                            update.versionName,
                                        )
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = !appUpdateState.downloading,
                                    onClick = appUpdateManager::downloadAndInstall,
                                ) {
                                    Text(stringResource(R.string.app_update_install))
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !appUpdateState.downloading,
                                    onClick = appUpdateManager::dismiss,
                                ) {
                                    Text(stringResource(R.string.app_update_later))
                                }
                            },
                        )
                    }
            }
            val navigator = rememberAppNavigator()
            val onSubPage = navigator.current != AppRoute.Main
            // AppCompat 切换语言会重建 Activity；显式保存目标页，避免恢复到中途页。
            var selectedPage by rememberSaveable { mutableStateOf(TopDestination.Hangar.ordinal) }
            val tabStateHolder = rememberSaveableStateHolder()
            val pagerState =
                androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = selectedPage,
                    pageCount = { TopDestination.entries.size },
                )
            val mainPagerState =
                com.azurpilot.ghio.ui.ksu.rememberMainPagerState(
                    pagerState,
                    animatePageChanges =
                        !com.azurpilot.ghio.ui.ksu.useNavigationRail(
                            appearance.enableFloatingBottomBar.toBoolean()
                        ),
                )
            LaunchedEffect(pagerState.settledPage) { selectedPage = pagerState.settledPage }
            // 方向归主导航管理，画面页被 pager 回收后也必须恢复系统策略。
            LaunchedEffect(activity, selectedPage, onSubPage) {
                activity?.requestedOrientation =
                    if (!onSubPage && selectedPage == TopDestination.Screen.ordinal)
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            val repository: AzurPilotRepository = koinInject()
            val runtimeVisible =
                navigator.current is AppRoute.Console ||
                    (!onSubPage && selectedPage == TopDestination.AzurPilot.ordinal)
            LaunchedEffect(runtimeVisible) { repository.setForeground(runtimeVisible) }
            DisposableEffect(repository) {
                onDispose { repository.setForeground(false) }
            }
            val snackbarHostState = remember { SnackbarHostState() }
            var exportKind by remember { mutableStateOf<LogExportKind?>(null) }

            val hostState: HostState = koinInject()
            // 主页是否可见：HangarScreen 靠它决定要不要自动补一次环境拉起
            val hangarActive = selectedPage == TopDestination.Hangar.ordinal

            // 首启「机型支持列表」弹窗：进入主页后触发；弹窗状态一旦激活即保持展示，避免后台异步状态变动导致弹窗自动消失。
            // 排序最末：必须等 App 更新与 Runtime 更新两项检查都出结果（否则更新弹窗会在它
            // 触发后才落下，两个 AlertDialog 叠在一起）；Runtime 更新弹窗退场（稍后）后才轮到它。
            // 勾选「不再弹出」才持久化抑制；点击「去提交」前往 GitHub 登记并标记已处理。
            LaunchedEffect(
                hangarActive,
                onSubPage,
                showProvision,
                isAppLocked,
                settingsLoaded,
                communityNoticeAcknowledged,
                compatNoticeShown,
                compatNoticeDismissed,
                readiness.needsGuidance,
                appUpdateState,
                runtimeCheck,
                fullUpdatePending,
                runtimePromptDismissed,
            ) {
                if (
                    hangarActive &&
                        !onSubPage &&
                        !showProvision &&
                        !isAppLocked &&
                        settingsLoaded &&
                        communityNoticeAcknowledged &&
                        !compatNoticeShown &&
                        !compatNoticeDismissed &&
                        !showCompatNoticeDialog &&
                        !readiness.needsGuidance &&
                        !appUpdateState.checking &&
                        appUpdateState.available == null &&
                        runtimeCheck.checked &&
                        !runtimeCheck.checking &&
                        (!fullUpdatePending || runtimePromptDismissed)
                ) {
                    delay(300)
                    if (hangarActive && !onSubPage && !showProvision && !isAppLocked) {
                        showCompatNoticeDialog = true
                    }
                }
            }

            if (showCompatNoticeDialog) {
                val uriHandler = LocalUriHandler.current
                var dontAskAgain by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = {
                        showCompatNoticeDialog = false
                        compatNoticeDismissed = true
                        if (dontAskAgain) {
                            scope.launch { appSettings.setCompatNoticeShown(true) }
                        }
                    },
                    title = { Text(stringResource(R.string.compat_notice_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.compat_notice_message))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable { dontAskAgain = !dontAskAgain }
                                        .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = dontAskAgain,
                                    onCheckedChange = { dontAskAgain = it },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.compat_notice_dont_ask_again),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCompatNoticeDialog = false
                                compatNoticeDismissed = true
                                scope.launch { appSettings.setCompatNoticeShown(true) }
                                uriHandler.openUri(ProjectLinks.ISSUE_DEVICE_SUPPORT)
                            }
                        ) {
                            Text(stringResource(R.string.compat_notice_go))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCompatNoticeDialog = false
                                compatNoticeDismissed = true
                                if (dontAskAgain) {
                                    scope.launch { appSettings.setCompatNoticeShown(true) }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.compat_notice_later))
                        }
                    },
                )
            }

            /**
             * 选择平级页面并关闭根详情栈；只组合目标与退场页面，不经过中间 Tab。
             *
             * Selects a peer page and closes root details without visiting intermediate tabs.
             */
            fun goToPage(index: Int) {
                if (index == selectedPage && !onSubPage) return
                navigator.returnToMain()
                mainPagerState.animateToPage(index)
            }

            // 桌面快捷方式的跳转请求：MainActivity 收到后登记到总线，这里消费。
            // 部署或应用锁挡住时保留请求，就绪后再跳转，避免在遮罩背后切换页面。
            val shortcutRequests: ShortcutRequests = koinInject()
            LaunchedEffect(showProvision, isAppLocked) {
                if (showProvision || isAppLocked) return@LaunchedEffect
                shortcutRequests.requests.collect { id ->
                    when (id) {
                        ShortcutRequests.OPEN_SCREEN -> goToPage(TopDestination.Screen.ordinal)
                        ShortcutRequests.OPEN_RUNNER_LOG -> navigator.push(AppRoute.AzurPilotLog)
                    }
                    shortcutRequests.consume()
                }
            }

            // 未装/未启动/未授权时的引导；needsGuidance 为 false 时自身不渲染
            ShizukuReadinessDialog(
                readiness = readiness,
                // NotInstalled 档：不内置安装包，确认键 = 跳浏览器到分发页；装完切回来 onResume 自动重测
                onDownload = { permissionManager.openShizukuDownload(context) },
                onOpenApp = { permissionManager.openShizuku(context) },
                onRequestAuth = { scope.launch { permissionManager.requestRemoteAccess() } },
                onUninstall = { permissionManager.uninstallShizuku(context) },
                onDismiss = { scope.launch { permissionManager.skipShizukuCheck() } },
                onSwitchToRoot = {
                    settingsViewModel.onIntent(SettingsIntent.SetBackend(RemoteBackend.ROOT))
                },
                isRequesting = isGranting,
            )

            if (showProvision) {
                ProvisionScreen(
                    state = provisionState,
                    onRetry = { provisioner.retry() },
                    // 跳过只留给开发包：跳过只能调外壳，AzurPilot 起不来
                    onSkip =
                        if (
                            BuildConfig.DEBUG &&
                                (provisionState is ProvisionState.NotBundled ||
                                    provisionState is ProvisionState.Failed)
                        ) {
                            { provisionSkipped = true }
                        } else {
                            null
                        },
                    // 首启就得选源：直连不通的用户不该先撞一次超时
                    mirrorId = githubMirror,
                    onMirrorChange = { mirror ->
                        scope.launch { appSettings.setGithubMirror(mirror) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                com.azurpilot.ghio.ui.ksu.AppRootScaffold {
                    AppNavigation(navigator, modifier = Modifier.fillMaxSize()) { swipeDismiss ->
                        entry<AppRoute.Main>(swipeDismiss = swipeDismiss) {
                            AppNavigationScaffold(
                                mainPagerState = mainPagerState,
                                rootBackEnabled = navigator.backStack.size == 1,
                                snackbarHostState = snackbarHostState,
                            ) { page, bottomInnerPadding ->
                                tabStateHolder.SaveableStateProvider(page) {
                                    when (TopDestination.entries[page]) {
                                        TopDestination.Hangar ->
                                            HangarScreen(
                                                active =
                                                    page == pagerState.settledPage && !onSubPage,
                                                bottomInnerPadding = bottomInnerPadding,
                                            )
                                        TopDestination.Screen ->
                                            ScreenPage(
                                                active =
                                                    page == pagerState.settledPage && !onSubPage,
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .padding(bottom = bottomInnerPadding),
                                            )
                                        TopDestination.AzurPilot ->
                                            AzurPilotPage(
                                                active =
                                                    page == pagerState.settledPage && !onSubPage,
                                                onNavigate = navigator::push,
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .padding(bottom = bottomInnerPadding),
                                            )
                                        TopDestination.Settings ->
                                            SettingsScreen(
                                                state = settingsState,
                                                onOpenSection = { navigator.push(it.route) },
                                                bottomInnerPadding = bottomInnerPadding,
                                            )
                                    }
                                }
                            }
                        }
                        // 二级页面共享根导航的转场与返回手势处理。
                        entry<AppRoute.AppLog>(swipeDismiss = swipeDismiss) {
                            AppLogScreen(
                                onBack = { navigator.pop() },
                                onOpen = { navigator.push(AppRoute.AppLogDetail(it)) },
                            )
                        }
                        entry<AppRoute.AppLogDetail>(swipeDismiss = swipeDismiss) { route ->
                            AppLogDetailScreen(
                                fileName = route.fileName,
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.AzurPilotLog>(swipeDismiss = swipeDismiss) {
                            AzurPilotLogScreen(
                                onBack = { navigator.pop() },
                                onOpenDaily = { navigator.push(AppRoute.AzurPilotLogDetail(it)) },
                                onOpenError = { navigator.push(AppRoute.AzurPilotErrorDetail(it)) },
                            )
                        }
                        entry<AppRoute.AzurPilotLogDetail>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotLogDetailScreen(
                                fileName = route.fileName,
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.AzurPilotErrorDetail>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotErrorDetailScreen(
                                dirName = route.dirName,
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.Appearance>(swipeDismiss = swipeDismiss) {
                            com.azurpilot.ghio.ui.ksu.screen.colorpalette.AppearanceScreen(
                                onBack = { navigator.pop() }
                            )
                        }
                        entry<AppRoute.Display>(swipeDismiss = swipeDismiss) {
                            DisplaySettingsPage(
                                onOpenAppearance = { navigator.push(AppRoute.Appearance) },
                                state = settingsState,
                                onIntent = settingsViewModel::onIntent,
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.VirtualDisplay>(swipeDismiss = swipeDismiss) {
                            VirtualDisplaySettingsPage(onBack = { navigator.pop() })
                        }
                        entry<AppRoute.Logs>(swipeDismiss = swipeDismiss) {
                            LogsSettingsPage(
                                state = settingsState,
                                onIntent = settingsViewModel::onIntent,
                                onOpenAppLog = { navigator.push(AppRoute.AppLog) },
                                onOpenRunnerLog = { navigator.push(AppRoute.AzurPilotLog) },
                                onExportRunnerLogs = { exportKind = LogExportKind.AZURPILOT },
                                onExportLauncherLogs = { exportKind = LogExportKind.LAUNCHER },
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.KeepAlive>(swipeDismiss = swipeDismiss) {
                            KeepAliveSettingsPage(
                                state = settingsState,
                                onIntent = settingsViewModel::onIntent,
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.Advanced>(swipeDismiss = swipeDismiss) {
                            AdvancedSettingsPage(
                                state = settingsState,
                                onIntent = settingsViewModel::onIntent,
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.Widget>(swipeDismiss = swipeDismiss) {
                            WidgetSettingsPage(onBack = { navigator.pop() })
                        }
                        entry<AppRoute.Runtime>(swipeDismiss = swipeDismiss) {
                            RuntimeSettingsPage(onBack = { navigator.pop() })
                        }
                        entry<AppRoute.Ocr>(swipeDismiss = swipeDismiss) {
                            OcrSettingsPage(onBack = { navigator.pop() })
                        }
                        entry<AppRoute.About>(swipeDismiss = swipeDismiss) {
                            AboutSettingsPage(
                                onOpenLicenses = { navigator.push(AppRoute.Licenses) },
                                onOpenLicenseDetail = { id ->
                                    navigator.push(AppRoute.LicenseDetail(id))
                                },
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.DeviceReport>(swipeDismiss = swipeDismiss) {
                            DeviceReportSettingsPage(onBack = { navigator.pop() })
                        }
                        entry<AppRoute.Licenses>(swipeDismiss = swipeDismiss) {
                            OpenSourceLicensesPage(
                                onOpenDetail = { id -> navigator.push(AppRoute.LicenseDetail(id)) },
                                onBack = { navigator.pop() },
                            )
                        }
                        entry<AppRoute.LicenseDetail>(swipeDismiss = swipeDismiss) { route ->
                            val componentId = route.componentId

                            LicenseDetailPage(
                                componentId = componentId,
                                onBack = { navigator.pop() },
                                snackbarHostState = snackbarHostState,
                            )
                        }
                        entry<AppRoute.Task>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotDetailPage(route = route, onBack = navigator::pop)
                        }
                        entry<AppRoute.Instances>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotDetailPage(route = route, onBack = navigator::pop)
                        }
                        entry<AppRoute.Announcement>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotDetailPage(route = route, onBack = navigator::pop)
                        }
                        entry<AppRoute.Meowfficer>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotDetailPage(route = route, onBack = navigator::pop)
                        }
                        entry<AppRoute.Updater>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotDetailPage(route = route, onBack = navigator::pop)
                        }
                        entry<AppRoute.Deploy>(swipeDismiss = swipeDismiss) { route ->
                            AzurPilotDetailPage(route = route, onBack = navigator::pop)
                        }
                    }
                }

                // 无条件挂在这一层：它注册的 SAF launcher 要活得比 sheet 的显隐久
                LogExportController(
                    kind = exportKind,
                    onDismiss = { exportKind = null },
                    onMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}
