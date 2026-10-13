# 内置 Shizuku 启动 / Embedded Shizuku activation

## 中文

### 行为

应用内置了 `shizuku-m` 的 ADB 连接、无线配对及离线重连能力。用户无需另装管理器，
也无需给本应用再点一次 Shizuku 授权。Android 11 及以上可通过通知输入系统配对码；
通知不可用时可用分屏手动填写配对端口和配对码。Android 9–10 需要先通过电脑执行
`adb tcpip 5555`。

首次启动仍需用户开启无线调试、完成系统配对，并在出现 USB 调试确认时选择始终允许。
激活使用 adbd 的 `tcpip:5555` 服务固定端口，并以同一 RSA 身份完成回环认证。
此后本次开机内可在断网或飞行模式下重新连接。手机重启、撤销调试授权、关闭调试，
或清除本应用数据后，需要重新激活。

5555 由系统 adbd 监听，可能对局域网开放；应用客户端仅连接 `127.0.0.1`，
adbd 仍要求系统认可的 RSA 密钥。不要允许陌生调试设备；不再需要时可关闭系统调试。
激活失败会保留引导和重试入口，不把未认证的连接标记成已授权。

### 集成方式

- `app/adb-runtime/` 移植自 [wess09/shizuku-m](https://github.com/wess09/shizuku-m)
  的 `bcb2b62a255575a93462470dc82c35d36a8340d9`，采用 Apache-2.0；来源、修改记录和
  许可证保存在该模块中。配对的 SPAKE2/AES-GCM 原生库支持 ARM64 和 x86_64。
- 内置路径不发布通用 Shizuku 服务，也不为其他应用授权。它用已认证的 shell
  直接启动项目自己的 `RemoteServiceImpl`，复用原有虚拟屏、截图、触控及权限代授。
- `ShellServiceStarter` 通过 Context-only 路径加载服务，避开联发科对
  `LoadedApk.makeApplication` 的注入。Root 原有初始化路径保持独立。
- Binder 通过现有 bootstrap provider 回传，校验 shell/root UID 和一次性 token；
  超时、取消和解绑会撤销 token。App 进程死亡仍会销毁特权子进程。
- RSA 私钥由 Android Keystore 的 AES-GCM 密钥加密保存在 App 私有偏好中，排除云备份
  和设备迁移。配对码只用于当前操作，不持久保存，也不写入日志。
- `SHIZUKU` 后端优先使用已经激活的内置连接；没有内置认证时，外部 Shizuku/Sui
  仍可沿用原授权和用户服务路径。已连接的外部服务会继续运行，重新绑定后使用内置路径。

### 验证

```powershell
cd app
./gradlew.bat compileDebugKotlin -x verifyBundledAzurPilotRuntime
./gradlew.bat :adb-runtime:testDebugUnitTest
./gradlew.bat :adb-runtime:connectedDebugAndroidTest
./gradlew.bat :app:assembleDebug
cd ..
uv run app/scripts/check_i18n_strings.py
```

设备测试验证加密密钥持久化、系统 TLS exporter、SPAKE2 正确配对及错误码拒绝，
不访问或授权真实 adbd。完整启动的人工验收需在设备上完成系统配对、USB 调试确认，
然后检查特权服务、虚拟屏、断网重连、杀进程重连及手机重启后的重新激活。

## English

### Behavior

The app embeds the ADB connection, wireless pairing, and offline reconnection features from
`shizuku-m`. No separate manager installation or additional Shizuku grant for this app is
required. Android 11 and later can accept the system pairing code through a notification;
split-screen port/code entry is available when notifications cannot be used. Android 9–10
first require `adb tcpip 5555` from a computer.

First activation still requires enabling wireless debugging, system pairing, and selecting
always allow if the system USB debugging confirmation appears. Activation uses adbd's
`tcpip:5555` service and authenticates the loopback connection with the same RSA identity.
Reconnection then works offline or in airplane mode for this boot. Rebooting, revoking
debugging authorization, disabling debugging, or clearing app data requires reactivation.

The system adbd listens on port 5555 and may expose it to the local network. The app client
connects only to `127.0.0.1`; adbd still requires a system-approved RSA key. Do not approve
unknown debugging devices, and disable system debugging when it is no longer needed.
Failed activation keeps guidance and retry available without granting an unauthenticated
connection.

### Integration

- `app/adb-runtime/` is ported from [wess09/shizuku-m](https://github.com/wess09/shizuku-m)
  commit `bcb2b62a255575a93462470dc82c35d36a8340d9` under Apache-2.0. The module preserves
  attribution, change notes, and the license. Native SPAKE2/AES-GCM pairing supports ARM64 and
  x86_64.
- The embedded path does not publish a general Shizuku server or grant other apps access.
  Authenticated shell starts this project's `RemoteServiceImpl` directly, reusing virtual
  display, capture, input, and privileged permission grants.
- `ShellServiceStarter` uses Context-only initialization to avoid MediaTek hooks in
  `LoadedApk.makeApplication`. The Root initialization path remains separate.
- The existing bootstrap provider returns the Binder after checking shell/root UID and a
  one-time token. Timeout, cancellation, and unbinding revoke pending tokens. App-process
  death still destroys the privileged child.
- Android Keystore AES-GCM encrypts the RSA private key in app-private preferences, excluded
  from cloud backup and device transfer. Pairing codes are transient and are never logged.
- The `SHIZUKU` backend prefers an activated embedded connection. Without embedded
  authentication, external Shizuku/Sui retain the original grant and user-service path.
  A connected external service keeps running; rebinding selects the embedded path.

### Validation

Use the commands in the Chinese section. Device tests cover encrypted key persistence,
the platform TLS exporter, successful SPAKE2 exchange, and wrong-code rejection without
accessing or authorizing a real adbd. Full manual acceptance requires system pairing and
USB debugging approval, followed by privileged-service and virtual-display checks, offline
reconnection, process-death recovery, and reactivation after a phone reboot.
