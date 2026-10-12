"""验证 GitHub 产物镜像的完整性、上传协议和草稿发布顺序。

Checks mirrored artifact integrity, the upload protocol, and draft-publication ordering.
"""

from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import cnb_release as cnb


class FakeApi:
    """记录预签名上传和确认步骤，模拟 CNB 服务端附件。

    Records presigned uploads and confirmations while simulating server-side CNB assets.
    """

    def __init__(self):
        self.requests = []
        self.files = {}
        self.confirmed = set()
        self.tickets = {}
        self.fail_upload = False
        self.corrupt_remote = False
        self.existing = None
        self.confirmation_repo = cnb.CNB_REPOSITORY

    def open(self, request, timeout):
        method, url = request.get_method(), request.full_url
        self.requests.append((method, url, request))
        if method == "PUT":
            if self.fail_upload:
                raise URLError("temporary storage failure")
            assert request.get_header("Authorization") is None
            assert hasattr(request.data, "read")
            filename = url.rsplit("/", 1)[-1]
            data = request.data.read()
            assert len(data) == int(request.get_header("Content-length"))
            self.files[filename] = data
            return io.BytesIO(b"")
        assert request.get_header("Authorization") == "Bearer test-access-token"
        if method == "GET" and "/tags/" in url:
            if self.existing is None:
                raise HTTPError(url, 404, "missing", {}, None)
            result = self.existing
        elif method == "POST" and url == cnb.API_ENDPOINT + "/" + cnb.CNB_REPOSITORY + "/-/releases":
            body = json.loads(request.data)
            assert body["draft"] is True and body["make_latest"] == "false"
            result = {"id": "release-1", "draft": True}
        elif method == "POST" and url.endswith("/asset-upload-url"):
            body = json.loads(request.data)
            assert body["overwrite"] is True and body["ttl"] == 0
            filename = body["asset_name"]
            ticket = f"ticket-{len(self.tickets)}"
            self.tickets[ticket] = filename
            result = {"upload_url": f"https://storage.example/{filename}",
                      "verify_url": (f"/{self.confirmation_repo}/-/releases/release-1/"
                                     f"asset-upload-confirmation/{ticket}/asset")}
        elif method == "POST" and "/asset-upload-confirmation/" in url:
            ticket = url.split("/asset-upload-confirmation/")[1].split("/")[0]
            filename = self.tickets[ticket]
            assert filename in self.files
            self.confirmed.add(filename)
            result = None
        elif method == "GET" and url.endswith("/release-1"):
            result = {"id": "release-1", "draft": True, "assets": self.assets()}
        elif method == "PATCH":
            body = json.loads(request.data)
            assert body == {"draft": False, "make_latest": "true"}
            assert self.confirmed == set(self.files)
            result = None
        else:
            raise AssertionError((method, url))
        return io.BytesIO(json.dumps(result).encode() if result is not None else b"")

    def assets(self):
        return [{"name": name, "size": len(data), "hash_algo": "SHA-256",
                 "hash_value": "0" * 64 if self.corrupt_remote else hashlib.sha256(data).hexdigest()}
                for name, data in self.files.items() if name in self.confirmed]


class CnbReleaseTest(unittest.TestCase):
    """保证附件字节不变，失败发布不会取代 Latest。

    Preserves artifact bytes and prevents failed publications from replacing Latest.
    """

    def setUp(self):
        self.enterContext(redirect_stdout(io.StringIO()))
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name) / "source"
        self.output = Path(self.tmp.name) / "release"
        self.source.mkdir()
        self.repo = "wess09/AzurPilot-for-Android"
        self.tag = "github-android-123-1"
        self.base = f"https://github.com/{self.repo}/releases/download/azurpilot-android-latest/"
        self.manifest = {"versionCode": 1900000000, "versionName": "1.3.0",
                         "appCommit": "a" * 40, "androidHostCommit": "b" * 40,
                         "azurpilotCommit": "c" * 40, "runtimes": {}, "fullApks": {}}

        def asset(filename):
            data = f"verified GitHub artifact: {filename}".encode()
            (self.source / filename).write_bytes(data)
            return {"url": self.base + filename, "sha256": hashlib.sha256(data).hexdigest(),
                    "size": len(data)}

        apk = asset("AzurPilot-Android-1.3.0-update.apk")
        self.manifest.update(apkUrl=apk["url"], apkSha256=apk["sha256"], apkSize=apk["size"])
        for abi in cnb.ABIS:
            self.manifest["runtimes"][abi] = {"version": "runtime-123", **asset(f"rootfs-{abi}.tar.xz")}
            self.manifest["fullApks"][abi] = asset(f"AzurPilot-Android-1.3.0-{abi}-full.apk")
            (self.source / f"BUILD_MANIFEST-{abi}.json").write_text(json.dumps({
                "rootfs_arch": abi, "rootfs_version": "runtime-123"}), encoding="utf-8")
        filename = f"frontend-{'c' * 40}.tar.xz"
        asset(filename)
        (self.source / f"{filename}.sha256").write_text(
            f"{cnb.digest(self.source / filename)}  {filename}\n", encoding="utf-8")
        self.write_manifest()

    def write_manifest(self):
        (self.source / "latest.json").write_text(json.dumps(self.manifest), encoding="utf-8")

    def prepare(self):
        return cnb.prepare(self.source, self.output, self.repo, "azurpilot-android-latest",
                           cnb.CNB_REPOSITORY, self.tag, "b" * 40)

    def test_same_bytes_and_cnb_urls_with_legacy_compatibility(self):
        original = (self.source / "latest.json").read_bytes()
        mirror = self.prepare()
        self.assertEqual((self.source / "latest.json").read_bytes(), original)
        for entry, filename in cnb.asset_entries(mirror):
            self.assertTrue(entry["url"].startswith(f"https://cnb.cool/{cnb.CNB_REPOSITORY}/"))
            self.assertEqual((self.output / filename).read_bytes(), (self.source / filename).read_bytes())
        self.assertEqual(mirror["rootfsUrl"], mirror["runtimes"]["arm64-v8a"]["url"])
        self.assertEqual(mirror["rootfsSha256"], mirror["runtimes"]["arm64-v8a"]["sha256"])
        for line in (self.output / "SHA256SUMS").read_text().splitlines():
            checksum, filename = line.split("  ", 1)
            self.assertEqual(checksum, cnb.digest(self.output / filename))

    def test_corrupt_or_missing_artifact_fails_before_preparing_release(self):
        path = self.source / "AzurPilot-Android-1.3.0-update.apk"
        original = path.read_bytes()
        path.write_bytes(b"x" * len(original))
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            self.prepare()
        self.assertFalse(self.output.exists())
        path.unlink()
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.prepare()

    def test_wrong_source_url_or_build_commit_is_rejected(self):
        self.manifest["apkUrl"] = "https://example.com/credentials.txt"
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "Unexpected GitHub"):
            self.prepare()
        self.manifest["apkUrl"] = self.base + "AzurPilot-Android-1.3.0-update.apk"
        self.manifest["androidHostCommit"] = "d" * 40
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, "this GitHub build"):
            self.prepare()

    def test_optional_frontend_checksum_and_runtime_manifest_are_verified(self):
        checksum = next(self.source.glob("frontend-*.sha256"))
        original = checksum.read_text()
        checksum.write_text("0" * 64)
        with self.assertRaisesRegex(ValueError, "Frontend"):
            self.prepare()
        checksum.write_text(original)
        path = self.source / "BUILD_MANIFEST-x86_64.json"
        path.write_text('{"rootfs_arch":"arm64-v8a","rootfs_version":"runtime-123"}')
        with self.assertRaisesRegex(ValueError, "manifest mismatch"):
            self.prepare()

    def test_existing_github_release_without_new_optional_metadata_can_be_backfilled(self):
        for path in list(self.source.glob("BUILD_MANIFEST-*.json")) + list(self.source.glob("frontend-*")):
            path.unlink()
        self.prepare()
        self.assertEqual(len(list(self.output.iterdir())), 7)

    def test_backfill_downloads_only_current_artifacts_and_available_metadata(self):
        target = Path(self.tmp.name) / "download"
        names = {path.name for path in self.source.iterdir()}
        names.update({"AzurPilot-Android-1.2.168-update.apk", "unexpected.txt"})
        fetched = []

        def fetch(args, check):
            self.assertTrue(check)
            filename = args[args.index("--pattern") + 1]
            fetched.append(filename)
            (target / filename).write_bytes((self.source / filename).read_bytes())

        listing = json.dumps({"assets": [{"name": name} for name in names]})
        with patch.object(cnb.subprocess, "check_output", return_value=listing), \
                patch.object(cnb.subprocess, "run", side_effect=fetch):
            cnb.download(target, self.repo, "azurpilot-android-latest")
        self.assertEqual(fetched[0], "latest.json")
        self.assertEqual(set(fetched), {path.name for path in self.source.iterdir()})
        with self.assertRaisesRegex(ValueError, "must be empty"):
            cnb.download(target, self.repo, "azurpilot-android-latest")

    def test_uploads_are_streamed_confirmed_and_checked_before_latest(self):
        mirror = self.prepare()
        api = FakeApi()
        client = cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, api)
        client.publish(self.output, mirror, self.tag)
        methods = [method for method, _, _ in api.requests]
        self.assertEqual(methods[-1], "PATCH")
        self.assertEqual(api.confirmed, {path.name for path in self.output.iterdir()})
        self.assertEqual(api.files["latest.json"], (self.output / "latest.json").read_bytes())

    def test_failed_upload_or_remote_checksum_leaves_a_draft(self):
        mirror = self.prepare()
        for failure in ("upload", "checksum"):
            with self.subTest(failure=failure):
                api = FakeApi()
                api.fail_upload = failure == "upload"
                api.corrupt_remote = failure == "checksum"
                client = cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, api)
                with patch.object(cnb.time, "sleep"), self.assertRaises((RuntimeError, ValueError)):
                    client.publish(self.output, mirror, self.tag)
                self.assertFalse(any(method == "PATCH" for method, _, _ in api.requests))

    def test_cnb_canonical_repository_casing_is_accepted_for_confirmations(self):
        mirror = self.prepare()
        api = FakeApi()
        api.confirmation_repo = "AzurPilot/AzurPilot-for-Android"
        cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, api).publish(
            self.output, mirror, self.tag)
        self.assertEqual(api.confirmed, {path.name for path in self.output.iterdir()})
        self.assertEqual(api.requests[-1][0], "PATCH")

    def test_confirmed_release_rerun_verifies_without_replacing_assets(self):
        mirror = self.prepare()
        api = FakeApi()
        api.files = {path.name: path.read_bytes() for path in self.output.iterdir()}
        api.confirmed = set(api.files)
        api.existing = {"id": "release-1", "draft": False, "assets": api.assets()}
        cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, api).publish(self.output, mirror, self.tag)
        self.assertEqual([method for method, _, _ in api.requests], ["GET"])

    def test_token_is_never_sent_to_foreign_api_urls(self):
        client = cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, FakeApi())
        for url in ("https://storage.example/confirm", client.base + "-other/confirm",
                    client.base.replace("azurpilot/", "other/"),
                    client.base.replace("api.cnb.cool", "api.cnb.cool.evil.example")):
            with self.subTest(url=url), self.assertRaisesRegex(ValueError, "Unexpected CNB"):
                client.request("POST", url)
        self.assertIsNone(cnb._NoRedirect().redirect_request(None, None, 302, "redirect", {},
                                                            "https://storage.example"))

    def test_missing_token_and_permission_denial_are_explicit(self):
        with self.assertRaisesRegex(ValueError, "repo-release:rw"):
            cnb.CnbClient("", cnb.CNB_REPOSITORY)
        client = cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, FakeApi())
        with patch.object(client.opener, "open", side_effect=HTTPError("url", 403, "denied", {}, None)):
            with self.assertRaisesRegex(RuntimeError, "repo-release:rw"):
                client.request("POST", client.base, {})

    def test_api_error_body_does_not_count_as_success(self):
        client = cnb.CnbClient("test-access-token", cnb.CNB_REPOSITORY, FakeApi())
        response = io.BytesIO(b'{"errcode":403,"errmsg":"permission denied"}')
        with patch.object(client.opener, "open", return_value=response):
            with self.assertRaisesRegex(RuntimeError, "check token permissions"):
                client.request("POST", client.base, {})


if __name__ == "__main__":
    unittest.main()
