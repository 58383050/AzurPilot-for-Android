# Quick Tunnel 远程访问与配对 / Quick Tunnel remote access and pairing

## 中文

### 用户入口

首次启动欢迎页、主页和本机部署页均提供「连接远程 AP」。首次使用只需确认协议，随后可扫描
AP 配对页的动态图形，或输入服务器生成的六位码；不经过本机权限、下载或 Runtime 启动流程。
配对成功后直接切换 AFA 内置原生控制台；以后点击保存的 AP 名称即可重新连接。
控制台断连页也保留配对与保存入口，不会退回本机 Runtime 的等待页。

`AppSettings.remoteClientMode` 保存客户端模式。该模式屏蔽本机自动部署、启动、定时 Runtime 更新、
Shizuku 引导与机型支持提示，应用锁和协议确认仍生效。「返回本机 AP」恢复原来的本机流程；
切换来源不会停止已经运行的本机任务。本机 Runtime 的「试用 Quick Tunnel」仅用于让本机 AP 提供远程访问，
与作为客户端连接其他 AP 是独立操作。

AFA 每次打开都请求固定入口，读取禁止缓存的 301 跳转，校验目标为
`https://<name>.trycloudflare.com/`。因此 AP 重启、Tunnel URL 改变后无需重新配对。
设备离线时，浏览器入口以 302 跳到 `/error`；原生客户端提示稍后重试，重新打开会重新解析入口。

「试用 Quick Tunnel」将本机 AP 的远程模式保存为 `cloudflare` 并提示重启。
首次启用会写入 Android ID 与随机安装因子的 SHA-256 摘要作为设备 ID，设备注册密钥由 AP 保存在
`config/remote-access.json`。原始 Android ID 不上传，已有非空设备 ID 和注册身份继续复用。
旧 SSH / WebRTC 设置保留；已有模式只在用户选择试用时切换。

### 构建前提

本机提供远程入口需要新版 AP Runtime：必须包含 AP 仓库的 `remote_cloudflare.py`、配对 API、
前端及 `pyflared==0.1.0` 锁定依赖。新 APK 不会把旧 Runtime 的 Python 源码替换成开发机副本。
构建 Runtime 时，将 `AZURPILOT_REPO` / `AZURPILOT_REF` 指向包含对应 AP 实现的源码与提交，
然后执行 `rootfs/build/build-azurpilot.sh`；架构须与设备一致。构建步骤见
[runtime-provisioning.md](runtime-provisioning.md) 和 [multi-arch.md](multi-arch.md)。

只连接远程 AP 时不需要更新本机 Runtime。远端 AP 必须已启用 Quick Tunnel，并在其远程访问页
生成配对码。图形采用 ParticlePair 公布的 Particle Code v1 帧格式：128 位密钥、CRC-16、
Hamming(12,8) 与 18×18 绿色差分载波。协议、渲染和原生识别均独立实现，未打包上游源码。

### 存储与安全边界

- `remoteaccess/RemotePairings.kt` 使用 OkHttp 和 kotlinx.serialization 调用注册服务。
  服务器地址固定为 `https://remote.azurpilot.com`，领取接口为 `/api/v1/pairings/claim`，不接受扫码内容指定任意服务器。
  旧试验地址的已保存配对与本机注册配置会迁移到新域名，继续使用原设备 ID 和独立授权。
- 设备列表和独立凭据通过 Android Keystore AES-GCM 加密，保存到 `noBackupFilesDir`。
  长期凭据不进入 DataStore 明文偏好、Android 备份、日志或 Intent。扫描 Activity 仅返回本次配对的一次性密钥。
- `ParticleScanActivity` 用原生 CameraX 采集绿色通道，搜索中心裁剪、缩放、旋转与镜像。
  预览和分析通过 `UseCaseGroup` 共用正方形视口，采样使用 `ImageProxy.cropRect`，取景框与解码区域保持一致。
  差分同步、纠错和 CRC 成功后还需重复识别相同密钥；相机帧只在内存中使用。
- `AzurPilotGateway` 每次连接重新解析固定入口，以 WSS 完成鉴权后才发布就绪并恢复订阅。
  内置原生总览、配置、日志和统计复用同一个 `AzurPilotRepository`，远程 AP 支持实例选择。
- 切换来源前等待编辑保存，关闭旧连接并清空缓存。原生总览显示来源，设置可返回本机 AP。
  本机 PRoot、悬浮窗、设备控制和启动器 HTTP 地址不变；远程配置不会同步为本机偏好。
- AFA「移除」只清除本机保存的入口；在 AP「已授权的配对」撤销后，已连接会话在下一次请求
  或心跳时失去授权。主 WebUI 密码无需改变。

### 服务与验证

Go + SQLite 服务见 [../server/remote-access/README.md](../server/remote-access/README.md)。
固定入口只重定向，不转发业务流量。仅增加服务自己的目录、systemd 单元及独立 ACME 虚拟主机，
不更改现有站点、证书或业务文件。

开发验证使用临时设备身份、隔离 WebUI 和真实 Quick Tunnel，不启动真实游戏任务。
Kotlin 使用 `compileDebugKotlin`；`app/gradlew -p tools/remote-gateway-check test` 直接验证
生产网关和光学协议，不运行本仓库已知孤立测试桩的 `testDebugUnitTest`。
Android 15 x86_64 独立模拟器已验证实际 Keystore 加密保存、六码配对、原生控制台读取配置、
Tunnel 换址后重连与活动凭据撤销。相机验收使用生产 TypeScript 编码器生成视频，送入模拟器
`videofile` 摄像头，由实际 CameraX 采集并调用生产解码器，最后通过扫描密钥向真实服务领取配对。
物理手机的相机成像与厂商后台限制仍需单独评估。

独立设备测试构建命令：

```powershell
.\app\gradlew.bat -p app :app:assembleDebugAndroidTest -PremoteAccessTestsOnly=true '-Pazurpilot.slimApk=true'
```

`RemoteEntryInstrumentedTest` 从真实 `MainActivity` 的全新安装路径验收欢迎页、协议确认、配对页、
原生控制台及 Activity 重建；以独立 `oobeTestApplicationId` 构建不含 rootfs 的 APK，传入
`freshRemoteEntry=true` 和一次性 `pairingCode`，断言全过程不安装或启动本机 Runtime。
这与只渲染配对卡的组件用例不同，覆盖应用最外层的部署门。
只验收入口时省略 `pairingCode`；传入后才执行公网配对与原生控制台断言。
完成入口验收后杀死测试应用进程，再以 `resumeRemoteEntry=true` 单独运行
`coldStartRetainsRemoteModeWithoutLocalRuntime`，验证冷启动保留远程模式与配对入口。

其他测试类为 `com.azurpilot.ghio.remoteaccess.RemoteAccessInstrumentedTest`。先确认测试设备
`sys.boot_completed=1` 且外部存储已挂载；通过 instrumentation 参数 `pairingCode` 执行原生
配对用例，`scanSecret` 执行相机用例，`runtimeHosting=true` 执行内置 Runtime 提供远程入口的用例。
`reconnect=true` / `revoke=true` 启用宿主协调的断连和撤销验收；阶段写入测试实例的私有
`files/remote-acceptance-phase`。测试数据只能使用独立安装、临时 AP 配置及单次凭据。
默认不写相机帧；只有显式传入 `cameraDiagnostics=true` 才保存合成相机验收帧供定位。
Quick Tunnel 处于试验范围，不能据此承诺生产环境可用性。

## English

### User entry

Connect to remote AP is available on the first-launch welcome screen, home page and local provisioning page.
First use only requires legal acknowledgement before scanning or entering a server-generated six-digit code;
local permissions, downloads and Runtime startup are unnecessary. Pairing opens the native console, and saved
entries reconnect it. Disconnected console pages retain pairing and saved-device controls.

`AppSettings.remoteClientMode` persists client mode and suppresses local automatic provisioning, startup,
scheduled Runtime updates, Shizuku guidance and device-support prompts. App lock and legal acknowledgement
remain effective. Return to local AP restores the existing local flow without stopping running local tasks.
Hosting a local Quick Tunnel is separate from controlling another AP.

The animation uses the published Particle Code v1 format: a 128-bit secret,
CRC-16, Hamming(12,8), and an 18×18 differential green carrier. Protocol, rendering and native decoding
are independently implemented; upstream source code is not bundled.

Every open resolves the stable entry again, checks the non-cacheable 301 response, and validates its
`https://<name>.trycloudflare.com/` destination. AP restarts therefore do not require pairing again.
Offline browser entries receive a 302 to `/error`. Native clients show a retry message; reopening resolves the entry again.

Try Quick Tunnel saves `cloudflare` as the local AP remote mode and requests a runtime restart.
First use stores a SHA-256 digest of Android ID plus an installation nonce as the ID; AP persists a registration
key in `config/remote-access.json`. The raw Android ID is not uploaded. Existing nonempty device IDs
and registration identities are retained. Existing SSH/WebRTC modes change only on explicit trial selection.

### Build prerequisite

Hosting a tunnel requires an AP runtime containing `remote_cloudflare.py`, the pairing API, frontend,
and locked `pyflared==0.1.0` dependency. Installing a new APK does not replace the Python source of an
old runtime with a development checkout. Point `AZURPILOT_REPO` and `AZURPILOT_REF` at the matching
AP implementation and commit, then run `rootfs/build/build-azurpilot.sh` for the device ABI. See
[runtime-provisioning.md](runtime-provisioning.md) and [multi-arch.md](multi-arch.md).

Controlling a remote AP does not require upgrading the local runtime. The remote AP must have
Quick Tunnel enabled and generate a code on its remote access page.

### Storage and trust boundaries

- `remoteaccess/RemotePairings.kt` uses OkHttp and kotlinx.serialization. The registry is fixed to
  `https://remote.azurpilot.com`, with claims at `/api/v1/pairings/claim`; scanned data cannot select arbitrary servers.
  Saved pairings and local registration settings from the old trial host migrate while retaining device IDs and credentials.
- Android Keystore AES-GCM encrypts entries and independent credentials in `noBackupFilesDir`.
  Long-term credentials are excluded from plaintext DataStore, backups, logs, and Intents. The scanner activity returns only a one-time pairing secret.
- Native `ParticleScanActivity` samples green-channel CameraX frames, searching centered crops, scales,
  rotations and mirrors. Repeated matching sync, error correction and CRC are required. Frames stay in memory.
  Preview and analysis share a square `UseCaseGroup` viewport; sampling follows `ImageProxy.cropRect`.
- `AzurPilotGateway` resolves the stable entry on each connection, authenticates over WSS before publishing
  readiness, and restores subscriptions. Native overview, configuration, logs and statistics use the existing
  repository; remote APs support instance selection.
- Switching settles edits, closes old sockets and clears caches. Settings can return to local AP.
  Local PRoot, overlays, device control and host HTTP endpoints remain local. Remote settings do not overwrite
  local app preferences.
- Remove in AFA deletes the local saved entry. Revoking a pairing in AP rejects its next request or
  heartbeat, including already connected sessions, without changing the primary WebUI password.

### Service and verification

The Go/SQLite service is documented in [../server/remote-access/README.md](../server/remote-access/README.md).
Stable entries redirect rather than proxy traffic. Deployment adds only its own directory, systemd
unit and ACME virtual host, preserving existing site, certificate, and business files.

Development verification uses temporary identities, an isolated WebUI and a real Quick Tunnel,
without starting game tasks. Kotlin is checked with `compileDebugKotlin`; the known orphaned unit
test source set is not run. `app/gradlew -p tools/remote-gateway-check test` verifies the production gateway
and optical decoder on JVM. An isolated Android 15 x86_64 emulator also verifies Keystore encryption,
six-digit pairing, native configuration reads, reconnection after tunnel changes, and active credential revocation.
The camera test feeds a video from the production TypeScript encoder through the emulator's `videofile` camera,
actual CameraX analysis, and production decoder, then claims the pairing from the real registry.
Physical camera characteristics and vendor background restrictions still need separate assessment.

`RemoteEntryInstrumentedTest` starts the actual `MainActivity` in a fresh isolated installation built
without rootfs, using `oobeTestApplicationId`, `freshRemoteEntry=true` and a one-time `pairingCode`.
It covers welcome, legal acknowledgement, pairing, the native console and activity recreation,
asserting that no local Runtime is installed or started throughout.
Omit `pairingCode` for entry-only acceptance; public pairing and native-console assertions require it.
After entry acceptance, force-stop the isolated app and run `coldStartRetainsRemoteModeWithoutLocalRuntime`
with `resumeRemoteEntry=true` to verify persisted client mode after a cold start.

Build instrumented tests with `app/gradlew -p app :app:assembleDebugAndroidTest -PremoteAccessTestsOnly=true
-Pazurpilot.slimApk=true`. Run `RemoteAccessInstrumentedTest` only after Android boot and storage mount complete.
Arguments `pairingCode`, `scanSecret`, and `runtimeHosting=true` select the native pairing, camera, and bundled
runtime hosting cases. `reconnect=true` and `revoke=true` coordinate host-controlled recovery checks through the
private `files/remote-acceptance-phase` file. Use isolated installs, temporary AP configurations, and one-time tokens.
Camera frames are saved only when `cameraDiagnostics=true` explicitly enables synthetic-frame diagnostics.
Quick Tunnel is experimental.
