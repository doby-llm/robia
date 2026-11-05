import hashlib
import json
import logging
import os
import socket
import struct
from pathlib import Path

import httplib2
import pytest
from googleapiclient.errors import HttpError

import robia_play
from robia_play import LOCALES, main, redact

SUBRESOURCES = {"edits.tracks", "edits.bundles", "edits.listings", "edits.images"}
KEY_MARKER = "KEYMARKER-do-not-print-7f3a"


class _Request:
    def __init__(self, fake, method, kwargs):
        self._fake = fake
        self._method = method
        self._kwargs = kwargs

    def execute(self):
        self._fake.calls.append((self._method, self._kwargs))
        error = self._fake.errors.get(self._method)
        if error is not None:
            raise error
        return self._fake.responses.get(self._method, {})


class _Resource:
    """Mirrors a discovery resource: methods return requests, sub-resources are called."""

    def __init__(self, fake, path):
        self._fake = fake
        self._path = path

    def __getattr__(self, name):
        if name.startswith("_"):
            raise AttributeError(name)
        path = f"{self._path}.{name}"
        if path in self._fake.missing:
            raise AttributeError(name)
        if path in SUBRESOURCES:
            return lambda: _Resource(self._fake, path)
        return lambda **kwargs: _Request(self._fake, path, kwargs)


class FakeService:
    def __init__(self, errors=None, responses=None, missing=()):
        self.calls = []
        self.errors = errors or {}
        self.missing = set(missing)
        self.responses = {
            "edits.insert": {"id": "edit-1"},
            "edits.bundles.upload": {"versionCode": 42},
            "edits.tracks.list": {"tracks": []},
            **(responses or {}),
        }

    def edits(self):
        return _Resource(self, "edits")

    def methods(self):
        return [method for method, _ in self.calls]


@pytest.fixture(autouse=True)
def no_network_and_clean_env(monkeypatch):
    def blocked(*args, **kwargs):
        raise AssertionError("tests must not open network connections")

    monkeypatch.setattr(socket, "getaddrinfo", blocked)
    monkeypatch.setattr(socket, "create_connection", blocked)
    monkeypatch.delenv("ROBIA_PLAY_ALLOW_PRODUCTION", raising=False)
    monkeypatch.delenv("ROBIA_PLAY_KEY", raising=False)


def http_error(status, message, reason=""):
    body = {"error": {"code": status, "message": message, "errors": [{"reason": reason}]}}
    return HttpError(httplib2.Response({"status": status}), json.dumps(body).encode())


def png_bytes(width, height, colour_type=2, bit_depth=8):
    ihdr = struct.pack(">II", width, height) + bytes([bit_depth, colour_type, 0, 0, 0])
    return b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + ihdr + b"\x00\x00\x00\x00"


def jpeg_bytes(width, height):
    sof = struct.pack(">HBHH", 17, 8, height, width) + b"\x03" + bytes(9)
    return b"\xff\xd8" + b"\xff\xc0" + sof + b"\xff\xd9"


def make_aab(tmp_path, payload=b"AAB-DATA", sidecar_digest=None):
    aab = tmp_path / "app-release.aab"
    aab.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest() if sidecar_digest is None else sidecar_digest
    (tmp_path / "app-release.aab.sha256").write_text(
        f"{digest}  app/build/outputs/bundle/release/app-release.aab\n", encoding="utf-8"
    )
    return aab


def write_metadata(tmp_path, aab, version_code=42, sha256=None):
    digest = hashlib.sha256(aab.read_bytes()).hexdigest() if sha256 is None else sha256
    path = tmp_path / "release-metadata.txt"
    path.write_text(
        "\n".join(
            [
                "ref=refs/heads/main",
                "sha=abc123",
                "versionName=1.0.0",
                f"versionCode={version_code}",
                "aab=app-release.aab",
                f"sha256={digest}",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return path


def make_notes(tmp_path):
    notes = tmp_path / "notes"
    notes.mkdir()
    for loc in LOCALES:
        (notes / f"{loc}.txt").write_text(f"Notas {loc}\n", encoding="utf-8")
    return notes


def make_listing(root: Path):
    """A valid play-assets tree; tests break one rule at a time."""
    root.mkdir(parents=True, exist_ok=True)
    (root / "icon.png").write_bytes(png_bytes(512, 512, colour_type=6))
    for loc in LOCALES:
        base = root / loc
        (base / "phone").mkdir(parents=True)
        (base / "title.txt").write_text("Robia", encoding="utf-8")
        (base / "short.txt").write_text("Tu armario, organizado", encoding="utf-8")
        (base / "full.txt").write_text("Texto completo " * 20, encoding="utf-8")
        (base / "featureGraphic.png").write_bytes(png_bytes(1024, 500))
        (base / "phone" / "01.png").write_bytes(png_bytes(1080, 1920))
        (base / "phone" / "02.png").write_bytes(jpeg_bytes(1080, 1920))
    return root


def upload_args(aab, notes, *extra, track="alpha"):
    return ["upload-bundle", "--aab", str(aab), "--track", track, "--notes-dir", str(notes), *extra]


# --- sha256, metadata and production guard ---------------------------------


def test_sha_mismatch_exits_2_with_zero_calls(tmp_path):
    aab = make_aab(tmp_path, sidecar_digest="0" * 64)
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path)), service=fake) == 2
    assert fake.calls == []


def test_metadata_sha_mismatch_exits_2_with_zero_calls(tmp_path):
    aab = make_aab(tmp_path)
    meta = write_metadata(tmp_path, aab, sha256="f" * 64)
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path), "--metadata", str(meta)), service=fake) == 2
    assert fake.calls == []


@pytest.mark.parametrize("track", ["production", "wear:production"])
@pytest.mark.parametrize("flag,env", [(False, None), (True, None), (False, "1")])
def test_production_needs_both_guards_and_makes_zero_calls(tmp_path, monkeypatch, track, flag, env):
    aab = make_aab(tmp_path)
    if env:
        monkeypatch.setenv("ROBIA_PLAY_ALLOW_PRODUCTION", env)
    extra = ["--production"] if flag else []
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path), *extra, track=track), service=fake) == 3
    assert fake.calls == []


def test_production_with_both_guards_is_dry_run_only(tmp_path, monkeypatch, capsys):
    aab = make_aab(tmp_path)
    monkeypatch.setenv("ROBIA_PLAY_ALLOW_PRODUCTION", "1")
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path), "--production", track="production"), service=fake) == 0
    assert "edits.commit" not in fake.methods()
    assert "DRY RUN: nothing committed" in capsys.readouterr().out


# --- dry run and commit ----------------------------------------------------


def test_dry_run_never_commits_and_deletes_the_edit_once(tmp_path, capsys):
    aab = make_aab(tmp_path)
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path)), service=fake) == 0
    assert fake.methods() == [
        "edits.insert",
        "edits.bundles.upload",
        "edits.tracks.update",
        "edits.validate",
        "edits.delete",
    ]
    assert "DRY RUN: nothing committed" in capsys.readouterr().out


def test_commit_is_the_only_path_to_edits_commit(tmp_path):
    aab = make_aab(tmp_path)
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path), "--commit"), service=fake) == 0
    assert fake.methods().count("edits.commit") == 1
    assert "edits.delete" not in fake.methods()


@pytest.mark.parametrize("track,api_id", [("internal", "qa"), ("alpha", "alpha"), ("beta", "beta")])
def test_track_ids_are_mapped(tmp_path, track, api_id):
    aab = make_aab(tmp_path)
    fake = FakeService()
    main(upload_args(aab, make_notes(tmp_path), "--version-code", "42", track=track), service=fake)
    update = [kwargs for method, kwargs in fake.calls if method == "edits.tracks.update"][0]
    assert update["track"] == api_id
    assert update["body"]["track"] == api_id


def test_unknown_track_is_a_usage_error(tmp_path):
    aab = make_aab(tmp_path)
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path), track="Internal Testing"), service=fake) == 1
    assert fake.calls == []


def test_version_code_skips_upload_and_sends_it(tmp_path):
    aab = make_aab(tmp_path)
    fake = FakeService()
    assert main(upload_args(aab, make_notes(tmp_path), "--version-code", "42"), service=fake) == 0
    assert "edits.bundles.upload" not in fake.methods()
    update = [kwargs for method, kwargs in fake.calls if method == "edits.tracks.update"][0]
    assert update["body"]["releases"][0]["versionCodes"] == [42]


def test_uploaded_version_code_must_match_metadata(tmp_path):
    aab = make_aab(tmp_path)
    meta = write_metadata(tmp_path, aab, version_code=42)
    fake = FakeService(responses={"edits.bundles.upload": {"versionCode": 7}})
    assert main(upload_args(aab, make_notes(tmp_path), "--metadata", str(meta)), service=fake) == 2
    assert "edits.tracks.update" not in fake.methods()
    assert fake.methods()[-1] == "edits.delete"


def test_version_code_already_used_gives_hint_and_exit_4(tmp_path, caplog):
    caplog.set_level(logging.INFO, logger="robia_play")
    aab = make_aab(tmp_path)
    error = http_error(400, "APK specifies a version code that has already been used.")
    fake = FakeService(errors={"edits.bundles.upload": error})
    assert main(upload_args(aab, make_notes(tmp_path)), service=fake) == 4
    assert "rerun with --version-code N" in caplog.text


# --- API failures ------------------------------------------------------------


def test_validate_error_exits_5_without_commit_and_deletes_edit(tmp_path):
    aab = make_aab(tmp_path)
    fake = FakeService(errors={"edits.validate": http_error(400, "Track release is invalid.")})
    assert main(upload_args(aab, make_notes(tmp_path), "--version-code", "42", "--commit"), service=fake) == 5
    assert "edits.commit" not in fake.methods()
    assert fake.methods().count("edits.delete") == 1


def test_tracks_update_http_error_exits_4_and_deletes_edit(tmp_path):
    aab = make_aab(tmp_path)
    fake = FakeService(errors={"edits.tracks.update": http_error(500, "backend error", "backendError")})
    assert main(upload_args(aab, make_notes(tmp_path)), service=fake) == 4
    assert fake.methods()[-1] == "edits.delete"
    assert "edits.commit" not in fake.methods()


def test_missing_discovery_method_exits_4_before_any_call(tmp_path, caplog):
    caplog.set_level(logging.INFO, logger="robia_play")
    aab = make_aab(tmp_path)
    fake = FakeService(missing={"edits.tracks.update"})
    assert main(upload_args(aab, make_notes(tmp_path)), service=fake) == 4
    assert fake.calls == []
    assert "edits.tracks.update" in caplog.text


def test_status_prints_tracks_and_never_commits(capsys):
    responses = {
        "edits.tracks.list": {
            "tracks": [
                {"track": "qa", "releases": [{"name": "1.0.0", "status": "completed", "versionCodes": ["42"]}]},
                {"track": "alpha", "releases": [{"name": "1.0.0", "status": "draft", "versionCodes": ["7"], "userFraction": 0.2}]},
            ]
        }
    }
    fake = FakeService(responses=responses)
    assert main(["status"], service=fake) == 0
    out = capsys.readouterr().out
    assert "track qa" in out and "track alpha" in out
    assert "versionCodes=7 userFraction=0.2" in out
    assert fake.methods() == ["edits.insert", "edits.tracks.list", "edits.delete"]


# --- listing -----------------------------------------------------------------


def listing_methods_count(fake):
    return fake.methods().count("edits.images.upload")


def test_valid_listing_is_accepted_dry_run(tmp_path, capsys):
    root = make_listing(tmp_path / "play-assets")
    fake = FakeService()
    assert main(["listing", "--dir", str(root)], service=fake) == 0
    assert fake.methods().count("edits.listings.update") == 3
    assert listing_methods_count(fake) == 3 * (2 + 1 + 1)
    assert fake.methods().count("edits.images.deleteall") == 3 * 3
    assert "edits.commit" not in fake.methods()
    assert fake.methods()[-1] == "edits.delete"
    assert "DRY RUN: nothing committed" in capsys.readouterr().out


@pytest.mark.parametrize(
    "rel,payload,expect",
    [
        ("en-US/title.txt", "x" * 31, "30"),
        ("en-US/full.txt", "x" * 4001, "4000"),
        ("es-ES/phone/02.png", None, "found 1"),
        ("de-DE/phone/02.png", png_bytes(1080, 2400), "longer side"),
    ],
)
def test_listing_violations_refused_with_no_calls(tmp_path, rel, payload, expect, caplog):
    caplog.set_level(logging.INFO, logger="robia_play")
    root = make_listing(tmp_path / "play-assets")
    target = root / rel
    if payload is None:
        target.unlink()
    elif isinstance(payload, bytes):
        target.write_bytes(payload)
    else:
        target.write_text(payload, encoding="utf-8")
    fake = FakeService()
    assert main(["listing", "--dir", str(root), "--commit"], service=fake) == 2
    assert fake.calls == []
    assert expect in caplog.text


def test_listing_commit_path_commits_once(tmp_path):
    root = make_listing(tmp_path / "play-assets")
    fake = FakeService()
    assert main(["listing", "--dir", str(root), "--commit"], service=fake) == 0
    assert fake.methods().count("edits.commit") == 1
    assert "edits.delete" not in fake.methods()


# --- release notes, keys and redaction --------------------------------------


def test_release_notes_counts_and_missing_locale_exits_2(tmp_path, capsys):
    notes = make_notes(tmp_path)
    assert main(["release-notes", "--dir", str(notes)]) == 0
    assert "en-US: " in capsys.readouterr().out
    (notes / "de-DE.txt").write_text("   \n", encoding="utf-8")
    assert main(["release-notes", "--dir", str(notes)]) == 2


def test_key_with_group_or_other_bits_exits_1_before_reading(tmp_path, monkeypatch, capsys, caplog):
    key = tmp_path / "service-account.json"
    key.write_text(json.dumps({"private_key": KEY_MARKER}), encoding="utf-8")
    os.chmod(key, 0o644)
    monkeypatch.setenv("ROBIA_PLAY_KEY", str(key))

    def must_not_read(*args, **kwargs):
        raise AssertionError("key was read")

    monkeypatch.setattr(robia_play.service_account.Credentials, "from_service_account_file", must_not_read)
    caplog.set_level(logging.INFO, logger="robia_play")
    assert main(["status"]) == 1
    captured = capsys.readouterr()
    assert KEY_MARKER not in captured.out + captured.err + caplog.text


def test_unreadable_key_content_is_not_echoed(tmp_path, monkeypatch, capsys, caplog):
    key = tmp_path / "service-account.json"
    key.write_text('{"private_key": "' + KEY_MARKER + '"', encoding="utf-8")
    os.chmod(key, 0o600)
    monkeypatch.setenv("ROBIA_PLAY_KEY", str(key))
    caplog.set_level(logging.INFO, logger="robia_play")
    assert main(["status"]) == 1
    captured = capsys.readouterr()
    assert KEY_MARKER not in captured.out + captured.err + caplog.text


def test_redact_removes_key_blocks_and_fields():
    assert "BEGIN" not in redact("-----BEGIN PRIVATE KEY-----\nMIIE")
    assert KEY_MARKER not in redact(f'{{"private_key": "{KEY_MARKER}"}}')


def test_usage_error_exits_1():
    assert main(["upload-bundle"], service=FakeService()) == 1


def test_local_checks_fail_before_any_key_is_needed(tmp_path, monkeypatch):
    monkeypatch.setenv("ROBIA_PLAY_KEY", str(tmp_path / "no-such-key.json"))
    root = make_listing(tmp_path / "play-assets")
    (root / "en-US" / "title.txt").write_text("x" * 31, encoding="utf-8")
    assert main(["listing", "--dir", str(root)]) == 2
    aab = make_aab(tmp_path, sidecar_digest="0" * 64)
    assert main(upload_args(aab, make_notes(tmp_path))) == 2
