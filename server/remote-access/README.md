# AP 远程注册服务 / AP remote registry

## 中文

Go + SQLite 服务为设备 ID 保存当前 Quick Tunnel URL，提供固定入口与单次配对。
不保存 WebUI 主密码，不代理 AP 业务流量。

```powershell
go test ./...
$env:GOOS='linux'
$env:GOARCH='amd64'
$env:CGO_ENABLED='0'
go build -trimpath -ldflags '-s -w' -o ../../.tmp/remote-access-linux-amd64 .
```

默认本地运行：`go run .`，绑定 `127.0.0.1:18882`。
生产部署必须使用 TLS。`TLS_CERT` / `TLS_KEY` 可指定本服务自己的证书；
`ACME_HOST` 启用自动申请与续期，证书缓存只写 `ACME_CACHE`。
不要使用或替换现有业务的证书文件。

### 独立部署

示例服务入口：`https://ap-remote.108-186-180-52.sslip.io:8443`。
sslip.io 根据名称中的 IP 解析到服务器，不需要更改已有域名 DNS。

新增文件：

| 路径 | 用途 |
| --- | --- |
| `/opt/azurpilot-remote/remote-access` | 静态 Linux 服务二进制 |
| `/opt/azurpilot-remote/service.env` | 本服务环境配置，参考 `deploy/service.env.example` |
| `/etc/systemd/system/azurpilot-remote.service` | 独立进程单元，参考 `deploy/azurpilot-remote.service` |
| `/etc/nginx/conf.d/azurpilot-remote-acme.conf` | 只匹配新域名的 HTTP-01 验证入口 |
| `/var/lib/azurpilot-remote/` | systemd 创建的私有 SQLite 与证书目录 |

首次安装前确认这些路径未被其他业务占用。只新增上述文件，不改写已有 Nginx 站点、全局设置、
证书、业务目录或已有 systemd 单元。保存所有原 Nginx 文件的 SHA-256，执行 `nginx -t` 后
仅 reload 加载新增虚拟主机；部署后逐文件比较原有摘要。`daemon-reload` 后启动新增单元。

公网开放本服务的 TCP 8443；80 端口通过新增虚拟主机将 ACME 验证转给本机 18883。
初次 HTTPS 握手可能等待证书签发；用正常 TLS 验证请求 `/healthz` 确认成功。
证书自动续期依赖此验证入口，删除前应先停用本服务。服务运行于 DynamicUser，数据库及
ACME 账户私钥不得放入仓库。SSH 密码不属于部署配置，也不写入任何示例文件。

### 协议

| 接口 | 行为 |
| --- | --- |
| `POST /v1/devices/register` | Bearer 设备密钥；提交 `device_id` / `name` / `tunnel_url`，首次占有并后续更新 |
| `DELETE /v1/devices/{id}` | Bearer 设备密钥；标记离线，不删除身份 |
| `GET /d/{id}/{path...}` | 180 秒租约内返回禁止缓存的 301，并保留路径与查询；离线 503 |
| `POST /v1/pairings` | Bearer 设备密钥；提交 `device_id` / `access_token`，返回六位 `code`、`id`、128 位 `particle_secret`、`expires_at` |
| `POST /v1/pairings/claim` | 提交六位 `code` 或 32 位十六进制 `scan_secret`；五分钟内共享一次领取状态 |
| `GET /v1/pairings/{id}` | Bearer 设备密钥；返回 pending / claimed / expired |
| `GET /healthz` | 健康检查 |

设备密钥只存 SHA-256；六码和扫描 secret 也只存摘要。配对的独立访问凭据在领取前暂存于私有
SQLite，领取后清空。只能跳转到根路径的 HTTPS `*.trycloudflare.com`，拒绝凭据、端口和任意域名。
每 IP 每分钟最多 5 次领取，服务整体每分钟最多 120 次；不信任客户端传入的转发 IP 头。

测试包括设备身份防冒用、换址 301、失效租约、扫描 secret、并发单次领取和限流。数据库重启后保留
映射和密钥，AP 每次启动仍重新注册最新地址。Quick Tunnel 为试验功能，没有生产可用性承诺。

## English

This Go/SQLite service maps installation IDs to current Quick Tunnel URLs and supplies stable
redirects plus one-time pairing. It stores no primary WebUI password and proxies no AP traffic.
Run `go test ./...`; cross-build Linux/amd64 with CGO disabled. Local `go run .` binds to
`127.0.0.1:18882`. Production requires TLS, through service-owned `TLS_CERT` / `TLS_KEY` or
automatic certificates configured by `ACME_HOST` and `ACME_CACHE`.

The example URL is `https://ap-remote.108-186-180-52.sslip.io:8443`. Add only the binary and
environment under `/opt/azurpilot-remote/`, the new `azurpilot-remote.service` unit, and the new
`azurpilot-remote-acme.conf` virtual host from `deploy/`. DynamicUser owns the private state under
`/var/lib/azurpilot-remote/`. Preserve all existing Nginx files, sites, certificates, systemd units
and business directories. Compare their SHA-256 values before and after deployment. Test Nginx
configuration, reload only to load the new host, and start the independent service.

Public TCP 8443 serves the registry. The new port-80 host forwards HTTP-01 challenges to loopback
18883. Initial TLS handshakes may wait for issuance; verify `/healthz` with normal certificate
validation. Renewal requires retaining the challenge route. Keep database, ACME private keys and
SSH credentials out of source control; SSH passwords are not service configuration.

`POST /v1/devices/register` takes `device_id`, `name`, `tunnel_url` with a Bearer device secret.
The same secret owns subsequent updates; `DELETE /v1/devices/{id}` marks it offline without
deleting ownership. `GET /d/{id}/{path...}` returns a non-cacheable 301 preserving path/query
while the 180-second lease is valid, otherwise 503. Targets must be root HTTPS subdomains of
trycloudflare.com, with no credentials, custom ports or arbitrary hosts.

Authenticated `POST /v1/pairings` accepts `device_id` and an independent `access_token`, returning
an ID, six-digit code, 128-bit `particle_secret` and five-minute expiry. `POST /v1/pairings/claim` accepts
either a six-digit code or a 32-hex-digit `scan_secret`; both share one successful claim. Authenticated
`GET /v1/pairings/{id}` returns pending, claimed or expired. Device keys, codes and scan secrets
are hashed; an unclaimed access token is temporarily retained in private SQLite and cleared on claim.
Claims are limited to 5 per IP and 120 globally per minute. Forwarded IP headers are not trusted.

Tests cover ownership, updated redirects, leases, scan secrets, concurrent claims and rate limits.
Database restarts preserve ownership and mappings; AP still re-registers its current URL at each start.
Quick Tunnel is experimental and has no production availability guarantee.
