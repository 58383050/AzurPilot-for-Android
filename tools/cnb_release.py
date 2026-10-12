"""将 GitHub 已校验的发布产物分发到 CNB，不重新编译或签名。

Mirrors verified GitHub release artifacts to CNB without rebuilding or re-signing.
"""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, unquote, urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


ABIS = ("arm64-v8a", "x86_64")
CNB_REPOSITORY = "azurpilot/AzurPilot-for-Android"
API_ENDPOINT = "https://api.cnb.cool"
WEB_ENDPOINT = "https://cnb.cool"
SHA = re.compile(r"[0-9a-f]{40}")
SHA256 = re.compile(r"[0-9a-f]{64}")
FRONTEND = re.compile(r"frontend-[0-9a-f]{40}\.tar\.xz")


def digest(path: Path, algorithm: str = "sha256") -> str:
    """流式校验 GB 级产物。

    Checks large artifacts without loading them into memory.
    """
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def asset_entries(manifest: dict) -> list[tuple[dict, str]]:
    """要求三种正式 APK 与双架构 Runtime 齐全。

    Requires all three signed APK variants and both runtime architectures.
    """
    version = manifest["versionName"]
    if not isinstance(version, str) or not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Invalid release version")
    if type(manifest.get("versionCode")) is not int or manifest["versionCode"] <= 0:
        raise ValueError("Invalid release version code")
    for key in ("appCommit", "androidHostCommit", "azurpilotCommit"):
        if not isinstance(manifest.get(key), str) or not SHA.fullmatch(manifest[key]):
            raise ValueError(f"Invalid release provenance: {key}")
    entries = [({"url": manifest["apkUrl"], "sha256": manifest["apkSha256"],
                 "size": manifest["apkSize"]}, f"AzurPilot-Android-{version}-update.apk")]
    for abi in ABIS:
        entries.append((manifest["runtimes"][abi], f"rootfs-{abi}.tar.xz"))
        entries.append((manifest["fullApks"][abi], f"AzurPilot-Android-{version}-{abi}-full.apk"))
    return entries


def validate_source_urls(manifest: dict, github_repo: str, github_tag: str) -> None:
    """只接受当前 GitHub Release 的约定资产，阻止路径被清单改写。

    Accepts only expected assets from the specified GitHub release, preventing path substitution.
    """
    base = f"https://github.com/{github_repo}/releases/download/{quote(github_tag, safe='')}/"
    for entry, filename in asset_entries(manifest):
        if unquote(entry["url"]) != unquote(base + quote(filename, safe="")):
            raise ValueError(f"Unexpected GitHub asset URL: {filename}")
        if not isinstance(entry.get("sha256"), str) or not SHA256.fullmatch(entry["sha256"]):
            raise ValueError(f"Invalid asset checksum: {filename}")
        if type(entry.get("size")) is not int or entry["size"] <= 0:
            raise ValueError(f"Invalid asset size: {filename}")


def download(source: Path, github_repo: str, github_tag: str) -> None:
    """用 gh 下载已有正式发布，供手动补传；不触发构建。

    Downloads an existing signed release through gh for manual backfills without triggering builds.
    """
    source.mkdir(parents=True, exist_ok=True)
    if any(source.iterdir()):
        raise ValueError("Source download directory must be empty")
    listing = json.loads(subprocess.check_output(
        ["gh", "release", "view", github_tag, "--repo", github_repo, "--json", "assets"], text=True))
    available = {asset["name"] for asset in listing["assets"]}

    def fetch(filename: str) -> None:
        subprocess.run(["gh", "release", "download", github_tag, "--repo", github_repo,
                        "--dir", str(source), "--pattern", filename], check=True)

    fetch("latest.json")
    manifest = json.loads((source / "latest.json").read_text(encoding="utf-8"))
    validate_source_urls(manifest, github_repo, github_tag)
    required = {filename for _, filename in asset_entries(manifest)}
    optional = {"BUILD_MANIFEST", *(f"BUILD_MANIFEST-{abi}.json" for abi in ABIS)}
    optional.update(name for name in available if FRONTEND.fullmatch(name)
                    or (name.endswith(".sha256") and FRONTEND.fullmatch(name[:-7])))
    for filename in sorted(required | (optional & available)):
        fetch(filename)


def prepare(source: Path, output: Path, github_repo: str, github_tag: str,
            cnb_repo: str, tag: str, expected_host: str = "") -> dict:
    """校验原始字节，只重写 CNB 下载清单；使用硬链接避免再复制多 GB 文件。

    Verifies original bytes and rewrites only the CNB download index, using hard links to avoid
    duplicating multi-GB files.
    """
    if not re.fullmatch(r"[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+", cnb_repo):
        raise ValueError("Invalid CNB repository")
    if not re.fullmatch(r"github-android-\d+-\d+", tag):
        raise ValueError("Invalid GitHub build release tag")
    manifest = json.loads((source / "latest.json").read_text(encoding="utf-8"))
    validate_source_urls(manifest, github_repo, github_tag)
    if expected_host and manifest["androidHostCommit"] != expected_host:
        raise ValueError("Release does not belong to this GitHub build")
    files = {}
    for entry, filename in asset_entries(manifest):
        path = source / filename
        if path.is_symlink() or not path.is_file() or path.stat().st_size != entry["size"]:
            raise ValueError(f"Missing or incorrect artifact size: {filename}")
        if digest(path) != entry["sha256"]:
            raise ValueError(f"Artifact checksum mismatch: {filename}")
        files[filename] = path
    for abi in ABIS:
        filename = f"BUILD_MANIFEST-{abi}.json"
        path = source / filename
        if path.is_file():
            runtime = json.loads(path.read_text(encoding="utf-8"))
            if (runtime.get("rootfs_arch") != abi or
                    runtime.get("rootfs_version") != manifest["runtimes"][abi]["version"]):
                raise ValueError(f"Runtime manifest mismatch: {abi}")
            files[filename] = path
    # 旧 GitHub 发布只有一份 BUILD_MANIFEST，手动补传时保留它。
    if (source / "BUILD_MANIFEST").is_file():
        files["BUILD_MANIFEST"] = source / "BUILD_MANIFEST"
    for path in source.glob("frontend-*.tar.xz"):
        if not FRONTEND.fullmatch(path.name):
            continue
        checksum = source / f"{path.name}.sha256"
        words = checksum.read_text(encoding="utf-8").split()
        if not words or not SHA256.fullmatch(words[0]) or digest(path) != words[0]:
            raise ValueError(f"Frontend checksum mismatch: {path.name}")
        files[path.name], files[checksum.name] = path, checksum
    if output.exists() and any(output.iterdir()):
        raise ValueError("CNB release directory must be empty")
    output.mkdir(parents=True, exist_ok=True)
    for filename, path in files.items():
        target = output / filename
        try:
            os.link(path, target)
        except OSError:
            shutil.copyfile(path, target)
    cnb = copy.deepcopy(manifest)
    base = f"{WEB_ENDPOINT}/{cnb_repo}/-/releases/download/{tag}/"
    cnb["apkUrl"] = base + f"AzurPilot-Android-{cnb['versionName']}-update.apk"
    for abi in ABIS:
        cnb["runtimes"][abi]["url"] = base + f"rootfs-{abi}.tar.xz"
        cnb["fullApks"][abi]["url"] = base + f"AzurPilot-Android-{cnb['versionName']}-{abi}-full.apk"
    arm64 = cnb["runtimes"]["arm64-v8a"]
    cnb.update(rootfsVersion=arm64["version"], rootfsUrl=arm64["url"],
               rootfsSha256=arm64["sha256"], rootfsSize=arm64["size"])
    (output / "latest.json").write_text(json.dumps(cnb, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    (output / "SHA256SUMS").write_text(
        "".join(f"{digest(path)}  {path.name}\n" for path in sorted(output.iterdir())), encoding="utf-8")
    return cnb


class _NoRedirect(HTTPRedirectHandler):
    """不允许带访问令牌的 API 请求重定向。

    Prevents redirects for API requests carrying an access token.
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class CnbClient:
    """按官方 Release API 流式上传，在全部校验后发布草稿。

    Streams uploads through the official release API and publishes drafts only after verification.
    """

    def __init__(self, token: str, repo: str, opener=None):
        if not token.strip():
            raise ValueError("Missing CNB_TOKEN; configure a token with repo-release:rw")
        self.token = token.strip()
        self.base = f"{API_ENDPOINT}/{repo}/-/releases"
        self.opener = opener or build_opener(_NoRedirect())

    def _api_path(self, url: str) -> str:
        """接受 CNB 返回的仓库大小写规范化地址，仍限制主机、仓库和路径边界。

        Accepts CNB's canonical repository casing while enforcing host, repo, and path boundaries.
        """
        parsed, base = urlsplit(url), urlsplit(self.base)
        suffix = parsed.path[len(base.path):]
        if (parsed.scheme != base.scheme or parsed.netloc.lower() != base.netloc.lower() or
                parsed.path[:len(base.path)].lower() != base.path.lower() or
                (suffix and not suffix.startswith("/"))):
            raise ValueError("Unexpected CNB API URL")
        return suffix

    def request(self, method: str, url: str, data=None, missing_ok: bool = False):
        """API 令牌只发给同一仓库的 CNB API，错误日志不包含令牌或签名 URL。

        Sends the token only to this repository's CNB API; errors omit tokens and signed URLs.
        """
        self._api_path(url)
        payload = json.dumps(data).encode() if data is not None else None
        request = Request(url, data=payload, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/json",
            "Content-Type": "application/json",
        })
        try:
            with self.opener.open(request, timeout=90) as response:
                body = response.read()
                result = json.loads(body) if body else None
                if isinstance(result, dict) and result.get("errcode", 0):
                    raise RuntimeError("CNB API rejected the request; check token permissions")
                return result
        except HTTPError as error:
            error.close()
            if missing_ok and error.code == 404:
                return None
            if error.code in (401, 403):
                raise RuntimeError("CNB access denied; CNB_TOKEN requires repo-release:rw") from None
            raise RuntimeError(f"CNB API request failed (HTTP {error.code})") from None
        except URLError:
            raise RuntimeError("CNB API connection failed") from None

    def upload(self, release_id: str, path: Path) -> None:
        """每次重试重新取得签名 URL，上传文件流后确认附件永久保留。

        Obtains a fresh signed URL per retry, streams the file, and confirms permanent retention.
        """
        for attempt in range(3):
            ticket = self.request("POST", f"{self.base}/{quote(release_id, safe='')}/asset-upload-url",
                                  {"asset_name": path.name, "size": path.stat().st_size,
                                   "overwrite": True, "ttl": 0})
            upload_url, verify_url = ticket["upload_url"], ticket["verify_url"]
            if urlsplit(upload_url).scheme != "https":
                raise ValueError("CNB upload URL must use HTTPS")
            verify_url = urljoin(API_ENDPOINT + "/", verify_url)
            if not self._api_path(verify_url).startswith(
                    f"/{quote(release_id, safe='')}/asset-upload-confirmation/"):
                raise ValueError("Unexpected CNB upload confirmation URL")
            try:
                with path.open("rb") as stream:
                    # 签名地址自带授权，不能把 CNB_TOKEN 转发到对象存储。
                    request = Request(upload_url, data=stream, method="PUT", headers={
                        "Content-Type": "application/octet-stream",
                        "Content-Length": str(path.stat().st_size),
                    })
                    with self.opener.open(request, timeout=900) as response:
                        response.read(1024)
                self.request("POST", verify_url)
                return
            except (HTTPError, URLError, TimeoutError, ConnectionError, RuntimeError) as error:
                if isinstance(error, HTTPError):
                    error.close()
                if attempt == 2:
                    raise RuntimeError(f"CNB upload failed after 3 attempts: {path.name}") from None
                time.sleep(2 ** (attempt + 1))

    def verify(self, release: dict, output: Path) -> None:
        """核对服务端附件大小及可用的哈希，不接受缺失或不完整的发布。

        Checks server-side asset sizes and available hashes, rejecting missing or incomplete releases.
        """
        assets = {asset["name"]: asset for asset in release.get("assets", [])}
        for path in output.iterdir():
            asset = assets.get(path.name)
            if not asset or asset.get("size") != path.stat().st_size:
                raise ValueError(f"CNB asset missing or incomplete: {path.name}")
            algorithm = asset.get("hash_algo", "").strip().lower().replace("-", "")
            if algorithm and asset.get("hash_value"):
                if algorithm not in {"sha256", "sha512", "sha1", "md5"}:
                    raise ValueError(f"Unsupported CNB checksum algorithm: {algorithm}")
                if digest(path, algorithm) != asset["hash_value"].lower():
                    raise ValueError(f"CNB asset checksum mismatch: {path.name}")

    def publish(self, output: Path, manifest: dict, tag: str) -> None:
        """所有文件确认成功后才设为 Latest；失败保留草稿，不影响已有版本。

        Sets Latest only after all files are confirmed; failures leave a draft and preserve prior releases.
        """
        release = self.request("GET", f"{self.base}/tags/{quote(tag, safe='')}", missing_ok=True)
        if release is None:
            release = self.request("POST", self.base, {
                "tag_name": tag, "target_commitish": manifest["androidHostCommit"],
                "name": f"AzurPilot Android {manifest['versionName']}", "draft": True,
                "prerelease": False, "make_latest": "false",
                "body": "GitHub Actions 构建并校验，同一批产物同步到 CNB。\n\n"
                        "Built and verified by GitHub Actions; the same artifacts are mirrored to CNB.\n\n"
                        f"- Android: `{manifest['androidHostCommit']}`\n"
                        f"- AzurPilot: `{manifest['azurpilotCommit']}`\n"
                        "- SHA-256: `SHA256SUMS`\n",
            })
        if not release.get("draft", False):
            self.verify(release, output)
            return
        release_id = release["id"]
        for path in sorted(output.iterdir(), key=lambda item: item.name == "latest.json"):
            print(f"Upload {path.name} ({path.stat().st_size} bytes)", flush=True)
            self.upload(release_id, path)
        complete = self.request("GET", f"{self.base}/{quote(release_id, safe='')}")
        self.verify(complete, output)
        self.request("PATCH", f"{self.base}/{quote(release_id, safe='')}",
                     {"draft": False, "make_latest": "true"})


def main() -> None:
    """执行自动发布或已有 GitHub Release 的补传。

    Runs automatic publication or backfills an existing GitHub release.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("download", "publish"))
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path(".tmp/cnb-release"))
    parser.add_argument("--github-repo", default=os.environ.get("GITHUB_REPOSITORY"))
    parser.add_argument("--github-tag", default="azurpilot-android-latest")
    parser.add_argument("--repo", default=CNB_REPOSITORY)
    parser.add_argument("--tag")
    parser.add_argument("--expected-host-commit", default="")
    args = parser.parse_args()
    if not args.github_repo:
        parser.error("--github-repo is required")
    if args.action == "download":
        download(args.source_dir, args.github_repo, args.github_tag)
        return
    if not args.tag:
        parser.error("--tag is required for publication")
    client = CnbClient(os.environ.get("CNB_TOKEN", ""), args.repo)
    manifest = prepare(args.source_dir, args.output_dir, args.github_repo, args.github_tag,
                       args.repo, args.tag, args.expected_host_commit)
    client.publish(args.output_dir, manifest, args.tag)
    print(f"Published {WEB_ENDPOINT}/{args.repo}/-/releases/tag/{args.tag}")


if __name__ == "__main__":
    main()
