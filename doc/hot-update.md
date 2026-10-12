# 运行时热更新 / Runtime hot updates

## 中文

新镜像内置 Git、Node.js 24.21.0 和 npm，并保留 AzurPilot 浅克隆。源码和前端
更新不再等待宿主 CI 发布 `frontend-<commit>.tar.xz`。Python、系统库、Android
overlay 和依赖环境仍通过整包 Runtime 更新，见
[runtime-provisioning.md](runtime-provisioning.md)。

### 更新链路

1. App 经 `/android/update/status` 检查构建清单记录的仓库和分支，默认 `dev`。
   自动热更只在调度器和工具空闲时应用，手动更新会恢复原有实例。
2. 更新服务在 `/opt/azurpilot-update/source` 中浅拉**确切目标提交**。Git 下载
   使用 App 选择的镜像前缀，不依赖 Release 是否已发布相应前端。
3. 比较候选与当前 `uv.lock`。锁文件不同则保持当前源码和任务，返回
   `requiresRuntimeUpdate=true`，交给每日整包更新或设置页的整包更新入口。
   安装依赖仍关闭，避免未经构建及 OCR 验证的环境进入正式版本。
4. 在候选目录执行 `npm ci` 和 `npm run build`，复用上游源码指纹及 npm 源重试
   策略，构建超时 600 秒。准备失败不停止当前任务、不更改正式源码或 dist。
5. 构建成功后记录 `reloadalas` 和依赖同步标记，安全停止实例。随后写入
   `config/android-update-transaction.json`，保存旧提交和完整构建清单。
6. 备份旧 dist，切换源码，安装候选 dist，原子更新 `BUILD_MANIFEST`，再通过
   上游父监督器重载和恢复实例。热更保持 `rootfs_version` 不变。
7. 切换失败恢复旧源码、dist 和清单。进程中断时 `android_host.py` 在下一次
   启动 WebUI 前根据日志恢复；回滚失败保留日志及备份并阻止启动。准备阶段
   中断也会清除遗留 busy 状态，允许重试。

热更与整包更新共用 App 侧流水线锁。忙时轮询只读取本地进度，避免每三秒
联网检查。状态和应用请求允许 45 秒读取时间，覆盖服务端 30 秒上游探测。

### 工具链与兼容

- `build-azurpilot.sh` 按 ABI 下载固定版本官方 Node 分发包，使用仓内固定的
  SHA-256 校验。镜像构建和手机热更使用同一套 Node/npm，宿主无需另装 Node。
- 构建清单新增 `node_version`、`azurpilot_branch`、`hot_update_mode=local`。
  工具链变化进入 rootfs 构建输入哈希，已有用户需要先整包升级一次。
- `android_update.py` 通过 `sitecustomize.py` 延迟适配上游模块，不改写上游
  跟踪文件。APK 也同步此适配器，避免 Git reset 丢失 Android 更新逻辑。
- 未标记 `local` 或缺少工具的旧镜像保持预构建前端通道。GitHub/CNB 继续
  发布兼容 dist；`AZURPILOT_ANDROID_DIST_BASE` 仅供旧通道使用。
- `AZURPILOT_ANDROID_GIT_MIRROR` 传入 GitHub 镜像前缀，更改下载源后下次
  会话生效。npm 缓存放在 `/opt/azurpilot-npm-cache`，跨源码热更复用。
- Node/npm、Git 对象和候选目录增加安装与更新空间。手机前端构建消耗 CPU、
  内存和网络；空间不足或构建失败保持旧版。服务启动兼容性仍需真机验证。

### 整包更新判定

`rootfs_version` 为 `<上游提交前12位>-<构建输入哈希前10位>`。哈希相同而提交
变化时设置 `commitOnly`，优先热更；哈希不同则整包重部署。整包自动更新器
只有在热更接口返回 `enabled=true` 且未要求整包时才跳过源码差异，HTTP 200
本身不代表可热更。关闭热更开关会恢复整包更新提示。

## English

New images bundle Git, Node.js 24.21.0, and npm, and retain a shallow AzurPilot clone.
Source and frontend updates no longer wait for a matching CI `frontend-<commit>.tar.xz`.
Python, system libraries, Android overlays, and dependency environments still use full
Runtime updates. See [runtime-provisioning.md](runtime-provisioning.md).

### Update flow

1. The app checks the manifest repository and branch through `/android/update/status`,
   defaulting to `dev`. Automatic updates apply while schedulers and tools are idle;
   manual updates restore previously running instances.
2. The service shallow-fetches the **exact target commit** into
   `/opt/azurpilot-update/source`, using the app-selected Git mirror without depending
   on matching frontend Release assets.
3. It compares candidate and current `uv.lock`. Differences leave source and tasks
   unchanged and return `requiresRuntimeUpdate=true` for the daily full update or the
   full-update control in Settings. Dependency installation remains disabled so
   unverified environments cannot bypass build and OCR validation.
4. The candidate runs `npm ci` and `npm run build`, reusing upstream fingerprints and
   registry retry behavior with a 600-second build timeout. Preparation failures leave
   running tasks, live source, and dist untouched.
5. After building, the service records `reloadalas` and the dependency-sync marker,
   safely stops instances, and writes `config/android-update-transaction.json` with
   the previous commit and complete manifest.
6. It backs up dist, switches source, installs candidate dist, atomically updates
   `BUILD_MANIFEST`, and requests supervisor reload and recovery. Hot updates leave
   `rootfs_version` unchanged.
7. Failed switches restore source, dist, and manifest. After interruption,
   `android_host.py` recovers before WebUI startup. Failed recovery retains the journal
   and backup and prevents startup. Interrupted preparation clears stale busy state.

Hot and full updates share the app pipeline lock. Busy polls read local progress
without repeated network checks. Status and apply requests allow 45 seconds for the
server's 30-second upstream probe.

### Toolchain and compatibility

- `build-azurpilot.sh` downloads pinned official Node distributions per ABI and verifies
  repository-pinned SHA-256 values. Builds and devices use the same Node/npm; a separate
  host Node installation is unnecessary.
- The manifest adds `node_version`, `azurpilot_branch`, and `hot_update_mode=local`.
  Toolchain changes alter the rootfs input hash; existing users need one full upgrade.
- `android_update.py` lazily adapts upstream modules through `sitecustomize.py` without
  rewriting tracked sources. APK updates synchronize the adapter so Git resets cannot
  remove Android update behavior.
- Legacy images without the marker or tools retain prebuilt frontend behavior.
  GitHub/CNB keep publishing compatible dist. `AZURPILOT_ANDROID_DIST_BASE` serves only
  this legacy channel.
- `AZURPILOT_ANDROID_GIT_MIRROR` supplies a GitHub mirror prefix, effective on the next
  session. `/opt/azurpilot-npm-cache` persists across source updates.
- Node/npm, Git objects, and candidates increase storage use. Frontend builds consume
  device CPU, memory, and network resources; space or build failures retain the old
  version. Service startup compatibility still requires device verification.

### Full-update decision

`rootfs_version` is `<upstream-commit-12>-<input-hash-10>`. Matching hashes with different
commits set `commitOnly` and prefer hot updates; different hashes require a full
redeploy. The automatic full updater skips source differences only when the hot API
reports `enabled=true` without requiring a full update. HTTP 200 alone does not prove
capability. Disabling hot updates restores full-update prompts.
