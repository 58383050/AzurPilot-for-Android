# GitHub 产物分发到 CNB Releases / Mirroring GitHub artifacts to CNB Releases

## 中文

GitHub Actions 负责构建、签名和校验，CNB Releases 提供国内下载。
两个平台使用相同的 APK、双架构 Runtime 和前端归档字节；CNB 清单只改写下载地址。

- GitHub 仓库：[`wess09/AzurPilot-for-Android`](https://github.com/wess09/AzurPilot-for-Android)。
- CNB 仓库：[`azurpilot/AzurPilot-for-Android`](https://cnb.cool/azurpilot/AzurPilot-for-Android)。
- 国内下载：[CNB Releases](https://cnb.cool/azurpilot/AzurPilot-for-Android/-/releases)。

### 配置访问令牌

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 配置 `CNB_TOKEN`。
仓库已有源码同步用的同名 Secret，可以复用，但令牌必须有目标 CNB 仓库的
`repo-release:rw` 权限。源码同步还需要界面中 `repo-code` 的“读写”权限，更新令牌时保留它。
令牌所属账号也必须有该仓库的 Release 写权限。

参考 CNB 官方[访问令牌说明](https://docs.cnb.cool/en/guide/access-token.html)、
[仓库角色权限](https://docs.cnb.cool/en/guide/role-permissions.html)及
[OpenAPI](https://docs.cnb.cool/en/develops/openapi.html)。
只读令牌或只有源码写权限的令牌不能发布 Release；上传步骤会明确报告权限不足。

正式签名和设备上报凭据继续使用 GitHub 上现有的六个 Secrets。
CNB 不需要私密凭据文件或 Android 签名密钥，因为分发步骤不会重新构建、重新签名。
App 版本沿用 GitHub 的 `1.3.*` 版本规则，见[发布通道](release-channel.md)。

### 自动发布

`.github/workflows/rootfs.yml` 的 `publish` 作业在正式 GitHub Release 发布后，
调用 `tools/cnb_release.py publish` 分发当前作业的产物：

- 更新用轻量 APK，以及 ARM64、x86_64 完整 APK。
- `rootfs-arm64-v8a.tar.xz`、`rootfs-x86_64.tar.xz` 及各自的 `BUILD_MANIFEST`。
- 构建产物中存在的前端归档与对应校验文件。
- 指向 CNB 附件的 `latest.json`，以及重新生成的 `SHA256SUMS`。

脚本核对 GitHub 清单中的大小、SHA-256、当前主机提交及 Runtime 元数据。
大型文件以流式方式上传；每次重试重新取得预签名地址，上传后通过 API 确认附件。
CNB 访问令牌只发送到目标仓库的 CNB API，不发送到附件对象存储。

每次发布使用唯一标签 `github-android-<GitHub Run ID>-<Run Attempt>`，
以主机提交为目标创建草稿。全部附件上传完成，且服务端附件大小和可用哈希校验通过后，
才公开 Release 并设为 Latest。失败时保留草稿，已有 CNB Latest 不变。
原始 APK、Runtime 和前端归档的校验和与 GitHub 完全一致；CNB 下载地址包含唯一标签，
后续发布不会覆盖这些附件。清单保留旧版 App 使用的 ARM64 扁平字段。

`.github/workflows/sync-cnb.yml` 继续同步源码，不清理 CNB Release 标签。
创建 Release 所需的主机提交必须已经同步到 CNB。
自动发布与手动补传共用 `cnb-release-publish` 并发组，避免发布期间相互覆盖下载源。

### 手动补传已有产物

在 GitHub **Actions → Publish existing GitHub release to CNB → Run workflow** 执行。
`github_tag` 默认 `azurpilot-android-latest`；该 GitHub Release 必须包含正式签名的
轻量 APK、两个架构的完整 APK、两个 Runtime 和有效的 `latest.json`。

该工作流只下载清单对应的当前版本资产，校验后上传 CNB，不运行 Gradle 或 Runtime 构建。
旧发布只有一份 `BUILD_MANIFEST`、没有双架构元数据附件时也可补传；已有前端归档仍需校验。
GitHub Release 保留的历史版本 APK 不会一起下载。

若自动上传失败，GitHub 的构建和 Release 已完成，可通过此入口重试分发。
出现 `CNB_TOKEN requires repo-release:rw` 时，给令牌增加权限并更新 GitHub Secret，
然后重跑补传工作流。失败草稿可以在确认补传成功后手动清理。

### 停用 CNB 原生构建

`.cnb.yml` 和 `.cnb/rootfs.yml` 为空配置；`.cnb/web_trigger.yml` 没有构建按钮。
原来的每日 03:17 构建、API 触发和手动构建入口均已停用。
旧 `.cnb/` 构建脚本仅保留为历史实现，不由任何活动流水线调用。

本地验证上传协议和失败行为：

```powershell
uv run --no-project python tools/tests/test_cnb_release.py
```

修改 CI 时还需检查 GitHub 工作流的 YAML 和 Bash 语法，及 CNB 的官方
[流水线 schema](https://docs.cnb.cool/conf-schema-en.json)和
[手动按钮 schema](https://docs.cnb.cool/web-trigger-schema-en.json)。
本地模拟测试不能确认线上令牌的实际权限；首次上传结果以 GitHub Actions 为准。

## English

GitHub Actions builds, signs, and verifies artifacts; CNB Releases provides domestic downloads.
Both platforms serve identical APK, dual-architecture Runtime, and frontend archive bytes.
The CNB index changes only their download URLs.

- GitHub repository: [`wess09/AzurPilot-for-Android`](https://github.com/wess09/AzurPilot-for-Android).
- CNB repository: [`azurpilot/AzurPilot-for-Android`](https://cnb.cool/azurpilot/AzurPilot-for-Android).
- Domestic downloads: [CNB Releases](https://cnb.cool/azurpilot/AzurPilot-for-Android/-/releases).

### Configure the access token

Configure `CNB_TOKEN` under the GitHub repository's
**Settings → Secrets and variables → Actions**.
The existing source-mirroring Secret can be reused, provided its token has `repo-release:rw`
for the target CNB repository. Keep Read/Write access for `repo-code`, needed for Git source
mirroring, when updating the token. Its account must also have Release write access.

See CNB's official [access-token guide](https://docs.cnb.cool/en/guide/access-token.html),
[repository role permissions](https://docs.cnb.cool/en/guide/role-permissions.html), and
[OpenAPI documentation](https://docs.cnb.cool/en/develops/openapi.html).
Read-only tokens or tokens granting only source write access cannot publish Releases;
the upload step reports permission failures explicitly.

The six existing GitHub Secrets continue to supply signing and device-reporting credentials.
CNB needs no private credential file or Android signing key because distribution does not
rebuild or re-sign artifacts. App versions follow GitHub's `1.3.*` rules; see
[release channels](release-channel.md).

### Automatic publication

After publishing the signed GitHub Release, the `publish` job in
`.github/workflows/rootfs.yml` calls `tools/cnb_release.py publish` with this job's artifacts:

- The slim update APK and both ARM64 and x86_64 full APKs.
- `rootfs-arm64-v8a.tar.xz`, `rootfs-x86_64.tar.xz`, and their `BUILD_MANIFEST` files.
- Frontend archives and checksum files present in the build outputs.
- `latest.json` pointing to CNB assets and a regenerated `SHA256SUMS`.

The script checks sizes, SHA-256 hashes, the current host commit, and Runtime metadata
against the GitHub index. Large files are streamed. Each retry obtains a fresh presigned URL,
and uploaded assets are confirmed through the API. The CNB token is sent only to the target
repository's CNB API, never to attachment object storage.

Each publication creates a draft targeting the host commit with the unique tag
`github-android-<GitHub Run ID>-<Run Attempt>`. It becomes public and Latest only after all
uploads complete and server-side asset sizes and available hashes pass verification.
Failures leave a draft and preserve the previous CNB Latest.
Original APK, Runtime, and frontend checksums match GitHub exactly. Download URLs include
the unique tag, so later publications do not overwrite those assets. The index retains
legacy flat ARM64 fields for older App versions.

`.github/workflows/sync-cnb.yml` continues to mirror source without pruning CNB Release tags.
The host commit must already exist on CNB before the Release can be created.
Automatic publishing and manual backfills share the `cnb-release-publish` concurrency group,
preventing conflicting publication and changes to the download source during a backfill.

### Backfill existing artifacts manually

Open GitHub **Actions → Publish existing GitHub release to CNB → Run workflow**.
The `github_tag` input defaults to `azurpilot-android-latest`. That GitHub Release must contain
the signed update APK, both full APKs, both Runtimes, and a valid `latest.json`.

The workflow downloads only the current assets referenced by the index, verifies them,
and uploads to CNB without running Gradle or building Runtimes. Older releases with only one
`BUILD_MANIFEST` and no per-architecture metadata attachments are supported. Any existing
frontend archive must still pass checksum verification. Historical APK versions retained
in the GitHub Release are excluded.

If automatic uploading fails, the GitHub build and Release have already completed;
retry distribution through this workflow. For `CNB_TOKEN requires repo-release:rw`, grant
the permission, update the GitHub Secret, and rerun the backfill workflow.
Failed drafts can be removed manually after confirming a successful backfill.

### Disable CNB native builds

`.cnb.yml` and `.cnb/rootfs.yml` contain empty configurations; `.cnb/web_trigger.yml` has no
build buttons. The former daily 03:17 build, API triggers, and manual build entry points
are disabled. Old `.cnb/` build scripts remain as historical implementations and are not
called by active pipelines.

Validate upload ordering and failure behavior locally:

```powershell
uv run --no-project python tools/tests/test_cnb_release.py
```

CI changes also require GitHub workflow YAML and Bash syntax checks, plus validation against
CNB's official [pipeline schema](https://docs.cnb.cool/conf-schema-en.json) and
[manual-button schema](https://docs.cnb.cool/web-trigger-schema-en.json).
Local simulations cannot confirm the live token's permissions; the first GitHub Actions
upload determines whether they are sufficient.
