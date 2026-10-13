# Runtime 部署与更新 / Runtime provisioning and updates

> 本文说明 RootfsProvisioner 的状态机、下载链路、校验与解压流水线，以及首启行为。
> This document describes the RootfsProvisioner state machine, the download chain,
> verification and extraction, and first-boot behavior.

## 中文

### 状态机

`ProvisionState`（`provision/RootfsProvisioner.kt`）：

| 状态 | 含义 | 去向 |
|---|---|---|
| `Checking` | 比对内置版本与已装版本 | `Downloading` / `Extracting` / `Ready` / `Failed` |
| `NotBundled` | 未内置且 Release 无本架构 Runtime | 用户重试或换完整版 APK |
| `LowDisk` | 剩余空间不足 2 GB | 清理后重试 |
| `Downloading` | 从 Release 下载（done/total 为压缩字节数） | `Extracting` |
| `Extracting` | 解压中（进度同样按压缩字节计） | `Ready` |
| `Ready` | 在位（已装或本次部署完成） | — |
| `Failed` | 任何异常，`reason` 为面向用户的文案 | 重试 |

### 部署决策

1. 恢复中断：存在 `rootfs.previous` 时先改名回 `rootfs`。
2. `isInstalled()`：marker `.provisioned` 的版本与 rootfs 内
   `opt/azurpilot/BUILD_MANIFEST` 一致，且 `rootfs_arch` 与设备 ABI 匹配
   （旧包无 `rootfs_arch` 字段时按 arm64 处理），且 Python 入口存在。
3. 在位 → 只做 `checkForUpdates()` 提示，不自动替换。
4. 未在位 → 内置资产（架构匹配时）或 Release 下载。

### 下载链路

新安装默认从 CNB 国内源获取固定发布清单，已保存的源选择保留。App、Runtime 和前端
共用下载源，可在部署页或设置页选择 GitHub 直连及代理镜像。两侧清单和附件对应同一版本，
换源只切换 URL，大小与 SHA-256 校验不变。

- **单连接** `HttpURLConnection`（`update/ReleaseDownloader.kt`）：
  connect 20 s、read 120 s、跟随重定向；每个读块前检查 `shouldAbort`。
- **换源语义**：用户在下载中切换下载源 → `shouldAbort` 命中 →
  `DownloadAborted` → 删除半成品 → 按新源从头重下（`SourceChanged` 循环）。
  连接阶段挂死时最长 20 s、读阶段最长 120 s 脱身。
- **校验**：下载完成后先比大小再比 SHA-256（清单 `sha256` 字段），
  任一不符抛错进 `Failed`。
- **进度口径**：`Downloading` 与 `Extracting` 都按压缩字节计，total 取清单 `size`。

### 解压流水线

1. 解到 `rootfs.tmp`，全部成功后原子改名——半途失败不留半成品。
2. 纯 Java 流式解（commons-compress tar + tukaani xz）。busybox tar 解
   ubuntu-base 的硬链接有前向引用坑，不使用。
3. 符号链接用 `Os.symlink`；硬链接物化为副本，前向引用登记后补拷。
4. 路径防护：拒绝越界路径与穿过已解出符号链接的条目。
5. 可执行位按 tar mode 还原（proot/python 依赖它）。
6. 重部署时保留用户配置目录（含 SQLite、实例子目录、`reloadalas`）、`password.txt`
   与 `log/`。模板和部署种子使用新镜像版本，旧热更状态与事务日志不迁移。
   原子换名前再次同步，并删除首轮暂存的旧 WAL，防止数据库恢复错位。
7. 写 marker `.provisioned` = 清单版本，随后 `rootfs.tmp` → `rootfs` 改名。

### 首启行为

- 后台自动更新只处理已经在位的 Runtime；首次部署由前台流水线负责，下载与解压进度
  显示在部署页。Runtime 未在位时，不记录当天的自动检查日期。
- 解压剥离了 `__pycache__`，新 rootfs 首次启动要重建全部字节码，
  WebUI 可能超过 90 秒才就绪——ProotHost 会打
  `AzurPilot WebUI not ready within 90000ms` 警告，属预期，服务随后自然就绪。
- 第二次启动起恢复正常速度。

## English

### State machine

`ProvisionState` (`provision/RootfsProvisioner.kt`):

| State | Meaning | Next |
|---|---|---|
| `Checking` | Compare bundled version with the installed one | `Downloading` / `Extracting` / `Ready` / `Failed` |
| `NotBundled` | Nothing bundled and the release has no Runtime for this ABI | User retries or installs the full APK |
| `LowDisk` | Fewer than 2 GB free | Retry after cleanup |
| `Downloading` | Fetching from the release (done/total count compressed bytes) | `Extracting` |
| `Extracting` | Extraction in progress (same compressed-byte unit) | `Ready` |
| `Ready` | In place (already installed or deployed now) | — |
| `Failed` | Any error; `reason` carries a user-facing message | Retry |

### Deployment decision

1. Restore an interrupted run: rename `rootfs.previous` back to `rootfs`.
2. `isInstalled()`: the `.provisioned` marker matches the in-rootfs
   `opt/azurpilot/BUILD_MANIFEST`, `rootfs_arch` matches the device ABI
   (manifests without the field count as arm64), and the Python entry point exists.
3. When installed → only `checkForUpdates()` runs; nothing is replaced
   automatically.
4. When missing → extract the bundled asset (if the ABI matches) or
   download from the release.

### Download chain

New installations use CNB's fixed release index by default; saved source choices are retained.
App, Runtime, and frontend downloads share the source selected in provisioning or Settings.
GitHub direct access and proxies remain available. Switching sources changes the URL while
preserving version, size, and SHA-256 verification.

- **Single connection** over `HttpURLConnection`
  (`update/ReleaseDownloader.kt`): 20 s connect, 120 s read, redirects
  followed; `shouldAbort` is checked before every read chunk.
- **Source-switch semantics**: switching the download source mid-download
  trips `shouldAbort` → `DownloadAborted` → the partial file is deleted and
  the download restarts from the new source (`SourceChanged` loop). A stalled
  connect gives up within 20 s; a stalled read within 120 s.
- **Verification**: after download, compare size and then SHA-256 against the
  manifest's `sha256`. Any mismatch raises into `Failed`.
- **Progress unit**: both `Downloading` and `Extracting` count compressed
  bytes; the total comes from the manifest's `size`.

### Extraction pipeline

1. Extract into `rootfs.tmp`, then atomically rename — a failed run leaves
   no partial tree.
2. Pure-Java streaming (commons-compress tar + tukaani xz). busybox tar is
   not used: it breaks on ubuntu-base hard links with forward references.
3. Symlinks go through `Os.symlink`; hard links are materialized as copies,
   with forward references deferred to a second pass.
4. Path hardening: entries outside the root or traversing an extracted
   symlink are rejected.
5. The executable bit is restored from the tar mode (proot and python need it).
6. Preserve user configuration, including SQLite, instance directories, and `reloadalas`,
   plus `password.txt` and `log/`. Keep new templates and deployment seeds and exclude old
   hot-update state and journals. Resync before swapping and remove stale staged WAL files.
7. The `.provisioned` marker is written with the manifest version, then
   `rootfs.tmp` renames to `rootfs`.

### First boot

- Background automatic updates only handle an installed Runtime. The foreground pipeline owns
  first-time deployment and shows download and extraction progress. An absent Runtime does not
  consume the day's scheduled check.
- Extraction strips `__pycache__`, so the first boot of a fresh rootfs
  rebuilds all bytecode. The WebUI can take longer than 90 seconds; ProotHost
  logs `AzurPilot WebUI not ready within 90000ms` as an expected warning, and
  the service comes up afterward.
- Subsequent boots run at normal speed.
