# 导航与动效 / Navigation and motion

## 中文

### 参考与实现

参考 KernelSU 提交 `6e3860443f8fea7c5fc226277e53888033796488` 的 Material 模式：
组件使用 Google Material 3 Expressive，页面使用 Miuix `miuix-nav 0.9.4`。
此 App 保留 Material 3 控件和现有动态色，使用 `MotionScheme.expressive()` 与原生
`LoadingIndicator`。页面推进和返回使用 Miuix 默认转场、圆角和手势处理。

Miuix 导航以固定源码在 `app/navigation-runtime/` 构建，因为其发布包的 JVM 21
内联 API 与本仓库 JVM 17 基线不兼容。版本、原始文件哈希和许可证都保存在模块内。
导入步骤及两处 Android 平台声明适配见模块 [README](../app/navigation-runtime/README.md)。

### 状态与布局

- `AppNavigator` 维护一个可序列化的 `AppRoute` 栈。设置、日志、任务与管理详情
  都在根栈中，控制台不再创建嵌套导航。重复打开已有目的地会返回该条目。
- 路由参数以数据保存；任务名、日志路径和组件 ID 不再经过 URL 拼接与解码。
- 主 Tab 和控制台分区用固定尺寸容器的短淡化切换，用 `SaveableStateHolder`
  保存滚动与输入状态。主 Tab 不会经过中间页；进出虚拟屏时直接切换，避免动画跨屏幕旋转。
- `AppNavigationScaffold` 在页面转场外保留底栏、横屏导轨和 Snackbar。
  底栏边距由 Scaffold 测量和消费，不再依赖首帧测高回调。导航宿主限制绘制边界，
  退场图层不会覆盖固定导航栏。
- 每个详情拥有独立的标题栏。Miuix 为条目提供生命周期、保存状态和 ViewModel 作用域；
  全局设置 ViewModel 仍在根导航外获取，运行时仓库仍是 Koin 单例。
- 运行时断连时遮罩覆盖内容，已输入的配置不会因连接状态分支而卸载。
  截图前台订阅在 AppRoot 统一切换，避免主页和详情同时争抢订阅。
- 删除全页转场之上的卡片递延入场和无匹配目标的共享边界修饰符。
  列表变动、展开收起等有反馈用途的组件动效仍保留。

### 返回与动画缩放

Miuix 处理页面返回进度、滑动关闭、取消回弹和最终提交；应用不另建一套手势动画。
RTL 的滑动关闭方向镜像。根栈只有一个条目时返回由系统处理，Manifest 显式启用
`enableOnBackInvokedCallback`，以保留支持设备上的系统返回桌面动画。
Material 对话框和底部弹层使用其组件实现。

Compose 动画遵循系统动画缩放。预测式返回需要支持该协议的 Android 版本；
Android 11 真机只能验证常规系统返回与框架内的滑动关闭，不能代替 Android 13+
预测式手势的测试。

### 验证

```powershell
app/gradlew.bat -p app compileDebugKotlin -x verifyBundledAzurPilotRuntime
app/gradlew.bat -p app :navigation-runtime:testDebugUnitTest
app/gradlew.bat -p app :app:assembleDebug :app:assembleDebugAndroidTest -x verifyBundledAzurPilotRuntime -PnavigationTestsOnly=true
uv run python app/scripts/check_i18n_strings.py
uv run python app/scripts/check_licenses.py
```

导航模块保留上游的纯逻辑测试，覆盖保存、栈变动、生命周期、手势仲裁和运动收尾。
`NavigationStateInstrumentedTest` 在真实 Compose 导航宿主内验证特殊字符参数与父页面
输入的恢复，以及滑动取消后再次提交。运行该类时不需要清除 App 数据。
2026-10-11 的验证结果：上游导航模块 116 项测试通过，设备导航回归 2 项通过。
设备为 Redmi M2104K10AC，Android 11 / API 30。ADB 验证了设置详情、日志列表与详情、
实例管理、任务配置、开源协议、虚拟屏切换及横竖屏下的返回与滚动恢复。
动画缩放为 0 时页面仍可进入和返回；测试后还原动画缩放与旋转设置。

Debug 包在横屏下重复进入显示设置并系统返回 5 轮，`gfxinfo` 记录 596 帧：
P95 为 11 ms，P99 为 44 ms，系统标记 118 帧为 jank。该结果包含 Debug 开销，
没有同场景旧版本基线，不能据此宣称性能提升或发布版已达到稳定高刷新率。

`-PnavigationTestsOnly=true` 从 `src/navigationTest/java` 独立构建设备导航测试，
避开旧 `PiPackageInstrumentedTest` 对已删除运行时 API 的引用。MIUI 可能要求允许
USB 安装和测试宿主启动；本次设备测试通过 ADB 补充启动宿主后完成。
系统预测式返回尚未在 Android 13+ 设备上实测。

不要运行 `:app:testDebugUnitTest`；主模块的旧单元测试源集存在无关的孤立桩。

## English

### Reference and implementation

The reference is KernelSU's Material mode at revision
`6e3860443f8fea7c5fc226277e53888033796488`: Google Material 3 Expressive for
components, and Miuix `miuix-nav 0.9.4` for navigation. This app retains Material 3
controls and dynamic color, using `MotionScheme.expressive()` and the native
`LoadingIndicator`. Page navigation uses Miuix's default transition, corner clipping,
and gesture handling.

The navigation sources are pinned in `app/navigation-runtime/` because the published
artifact's JVM 21 inline APIs are incompatible with this repository's JVM 17 baseline.
The module records its version, original source hashes, and license. Its
[README](../app/navigation-runtime/README.md) describes import steps and the two
Android platform declaration adaptations.

### State and layout

- `AppNavigator` owns one serializable `AppRoute` stack for settings, logs, tasks,
  and management details. The console has no nested navigator. Opening an existing
  destination returns to its entry.
- Route arguments are data, without URL construction or decoding.
- Main tabs and console sections fade within fixed-size containers and preserve
  scroll and input state through `SaveableStateHolder`. Main tabs do not visit
  intermediate pages. Entering or leaving the virtual display switches immediately
  so page motion cannot span a screen rotation.
- `AppNavigationScaffold` keeps the bottom bar, landscape rail, and Snackbar outside
  transitions. Scaffold measures and consumes bottom bar insets without first-frame
  measurement callbacks. The navigation host clips drawing to its bounds so exiting
  layers cannot cover fixed navigation controls.
- Details own their title bars. Miuix supplies entry lifecycle, saved state, and
  ViewModel scopes. Global settings view models remain outside navigation, while
  the runtime repository remains a Koin singleton.
- A connection gate covers runtime content without unloading user input.
  AppRoot manages the foreground screenshot subscription centrally.
- Card entrance staggers and unmatched shared-bounds modifiers are removed.
  Useful feedback animations for list changes and expandable components remain.

### Back and animation scaling

Miuix owns back progress, swipe dismissal, cancellation, and completion; the app
adds no separate gesture animation. Swipe dismissal mirrors in RTL. A root-only
stack falls through to system back. The manifest explicitly enables
`enableOnBackInvokedCallback` for system back-to-home animation on supported devices.
Material dialogs and sheets use their component implementations.

Compose animations follow system animation scaling. Android 11 hardware can validate
ordinary system back and the framework's swipe dismissal, but cannot substitute for
Android 13+ predictive-back gesture testing.

### Validation

Use the commands above. The navigation module retains upstream tests for state saving,
stack changes, lifecycle, gesture arbitration, and motion settling.
`NavigationStateInstrumentedTest` validates special-character arguments, parent input
restoration, and swipe cancellation followed by completion in the real Compose host.
Running this class does not require clearing app data. Validation on 2026-10-11 passed all 116 navigation module tests and both device
navigation tests on a Redmi M2104K10AC running Android 11 / API 30. ADB checks covered
settings, log lists and details, instance management, task configuration, licenses,
virtual-display switching, orientation changes, back navigation, and scroll restoration.
Navigation also completed with animation scaling set to zero. Animation and rotation
settings were restored afterward.

Five repeated landscape display-settings pushes and system backs in the debug build
recorded 596 frames through `gfxinfo`: P95 was 11 ms, P99 was 44 ms, and the system
marked 118 frames as janky. This includes debug overhead and has no comparable old-build
baseline; it does not establish a performance improvement or stable release refresh rate.

`-PnavigationTestsOnly=true` builds navigation tests independently from
`src/navigationTest/java`, avoiding the old `PiPackageInstrumentedTest` references
to deleted runtime APIs. MIUI may require USB-install and test-host launch prompts;
this run used supplemental ADB host launches. System predictive back has not yet
been tested on Android 13+ hardware.

Do not run `:app:testDebugUnitTest`; the main module has unrelated orphaned test stubs.
