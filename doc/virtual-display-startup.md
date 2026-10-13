# 虚拟屏启动聚焦 / Virtual-display launch focus

## 中文

### 问题与证据

2026-10-13 在 M2104K10AC（Android 11 / API 30）上复现：AP 用
`am start --display` 启动碧蓝航线后，Activity 已经 `RESUMED`，但副屏的
`mCurrentFocus` 和 `mFocusedApp` 都为空。采集器仍收到帧，画面却固定为
RGB `(32, 31, 35)`，因此帧计数前进不能证明游戏已经完成启动。

向副屏发送 `ACTION_DOWN` 后立即发送 `ACTION_CANCEL`，窗口获得焦点，Unity
开始初始化并出现登录画面。无需打开最近任务列表，也不发送会完成点击的 `ACTION_UP`。
此设备上仅调用 `IActivityTaskManager.setFocusedTask` 没有恢复焦点。

[Android 的每屏焦点说明](https://source.android.com/docs/core/display/multi_display/displays#per-display-focus)
说明窗口焦点由设备的每屏焦点策略决定，Activity 恢复状态不能替代窗口焦点。

### 启动契约

- `android_app_launch.py` 保留 AP 的原始启动参数、返回值和 `wait` 行为。
  启动成功后用 `ping.capabilities.appFocus` 协商，再调用 `app_focus`。
- 请求包含 `package` 和最新 `displayId`。桥拒绝主屏、已经失效的虚拟屏和不匹配的
  顶层包；窗口尚未就绪时在 3 秒重试预算内等待。
- 聚焦序列与设备操作、显示生命周期和手势分别串行化。有进行中的触摸手势时不抢占。
  按下失败仍发送取消，两个事件均成功注入才返回成功。
- 旧桥、聚焦失败或桥连接中断只记录警告，保留原始启动结果。
  原始启动抛出的异常仍向上传播。
- JNI 的 `DriverClass.startApp` 同样在落屏校验后请求焦点。
- APK 在 Runtime 启动前同步适配器；上游源码热更不会移除此修复。

## English

### Problem and evidence

On 2026-10-13, M2104K10AC running Android 11 / API 30 reproduced the failure:
after AP launched Azur Lane with `am start --display`, its Activity was `RESUMED`,
but the secondary display's `mCurrentFocus` and `mFocusedApp` were null. Capture
still received frames fixed at RGB `(32, 31, 35)`, so an advancing frame counter
does not establish that the game has completed startup.

Sending `ACTION_DOWN` immediately followed by `ACTION_CANCEL` to the secondary
display established window focus, started Unity initialization, and produced the
login screen. Opening Recents was unnecessary, and no `ACTION_UP` was sent to
complete a click. Calling `IActivityTaskManager.setFocusedTask` alone did not
restore focus on this device.

[Android's per-display focus documentation](https://source.android.com/docs/core/display/multi_display/displays#per-display-focus)
describes the device policy controlling window focus. Activity resume state does
not substitute for window focus.

### Launch contract

- `android_app_launch.py` preserves AP's original launch arguments, return value,
  and `wait` behavior. After successful launch, it negotiates
  `ping.capabilities.appFocus` before calling `app_focus`.
- Requests include `package` and the latest `displayId`. The bridge rejects the
  primary display, stale virtual displays, and mismatched top packages. Windows
  that are not ready are retried within a three-second budget.
- The sequence serializes with device operations, display lifecycle, and touch
  gestures. It does not interrupt an active gesture. Cancel is sent even when
  down fails; both events must be accepted for a successful response.
- Older bridges, focus failure, and disconnects only warn and preserve the
  original launch result. Exceptions from the original launch still propagate.
- JNI's `DriverClass.startApp` also requests focus after verifying placement.
- The APK syncs the adapter before Runtime startup, preserving the fix across
  upstream source updates.
