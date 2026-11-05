"""Google Play Developer API CLI for com.gusanitolabs.robia.

Dry run by default: a command that changes Play data only commits with --commit.
See README.md for setup, the production guard and exit codes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
import re
import stat
import struct
import sys
from dataclasses import dataclass
from pathlib import Path

import httplib2
from google.auth.exceptions import GoogleAuthError
from google.oauth2 import service_account
from google_auth_httplib2 import AuthorizedHttp
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.gusanitolabs.robia"
LOCALES = ("en-US", "es-ES", "de-DE")
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
KEY_ENV = "ROBIA_PLAY_KEY"
DEFAULT_KEY = "~/.config/robia-play/service-account.json"
PRODUCTION_ENV = "ROBIA_PLAY_ALLOW_PRODUCTION"
TRACK_IDS = {"internal": "qa", "alpha": "alpha", "beta": "beta", "production": "production"}
# Only form-factor prefixes are documented for custom track ids, so this is a format check (interpretation).
TRACK_ID_RE = re.compile(r"[a-z0-9][a-z0-9_:-]{0,63}")
DEFAULT_VERSION_NAME = "1.0.0"
HASH_CHUNK = 1024 * 1024
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
SOF_MARKERS = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
STANDALONE_JPEG_MARKERS = {0x01, 0xD8, *range(0xD0, 0xD8)}
PHONE_SUFFIXES = {".png", ".jpg", ".jpeg"}
ICON_BYTES_MAX = 1024 * 1024
FEATURE_SIZE = (1024, 500)
ICON_SIZE = (512, 512)
PHONE_MIN, PHONE_MAX = 2, 8
SIDE_MIN, SIDE_MAX = 320, 3840
TITLE_MAX, SHORT_MAX, FULL_MAX = 30, 80, 4000

EXIT_OK = 0
EXIT_USAGE = 1
EXIT_LOCAL = 2
EXIT_PRODUCTION = 3
EXIT_API = 4
EXIT_VALIDATE = 5

API_ERRORS = (HttpError, OSError, httplib2.HttpLib2Error, GoogleAuthError)
EDIT_METHODS = ("edits.insert", "edits.delete", "edits.commit", "edits.validate")
STATUS_METHODS = ("edits.insert", "edits.delete", "edits.tracks.list")
TRACK_METHODS = ("edits.tracks.update",)
BUNDLE_METHODS = ("edits.bundles.upload",)
LISTING_METHODS = ("edits.listings.update", "edits.images.deleteall", "edits.images.upload")

_SECRET_RE = re.compile(r'-----BEGIN[^\n]*|"private_key"[^\n]*', re.IGNORECASE)
log = logging.getLogger("robia_play")


class CliError(Exception):
    """A failure with its exit code; the message must already be safe to print."""

    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code


class _Parser(argparse.ArgumentParser):
    def error(self, message: str):
        raise CliError(EXIT_USAGE, f"usage: {message}")


def redact(text: str) -> str:
    return _SECRET_RE.sub("[redacted]", text)


def safe_text(text: str, limit: int = 300) -> str:
    return redact(text)[:limit]


# --- credentials and service -------------------------------------------------


def check_key_file(path: Path) -> None:
    # Checked before the file is opened, and the path is never printed.
    try:
        mode = os.stat(path).st_mode
    except OSError:
        raise CliError(EXIT_USAGE, "service-account key is missing or unreadable") from None
    if not stat.S_ISREG(mode):
        raise CliError(EXIT_USAGE, "service-account key is not a regular file")
    if mode & 0o077:
        raise CliError(EXIT_USAGE, "service-account key must be mode 600 (no group or other bits)")


def build_service():
    path = Path(os.environ.get(KEY_ENV) or DEFAULT_KEY).expanduser()
    check_key_file(path)
    try:
        creds = service_account.Credentials.from_service_account_file(str(path), scopes=[SCOPE])
    except Exception:
        # The exception text can echo key material, so only a fixed message is kept.
        raise CliError(EXIT_USAGE, "service-account key could not be loaded") from None
    http = AuthorizedHttp(creds, http=httplib2.Http(timeout=120))
    return build("androidpublisher", "v3", http=http, cache_discovery=False)


def require_methods(service, names: tuple[str, ...]) -> None:
    for name in names:
        obj = service
        parts = name.split(".")
        try:
            for part in parts[:-1]:
                obj = getattr(obj, part)()
            getattr(obj, parts[-1])
        except AttributeError:
            raise CliError(EXIT_API, f"Play API discovery document has no method {name}") from None


# --- local checks ------------------------------------------------------------


def resolve_track(track: str) -> str:
    api_id = TRACK_IDS.get(track, track)
    if not TRACK_ID_RE.fullmatch(api_id):
        raise CliError(EXIT_USAGE, f"unknown track {track!r}")
    return api_id


def check_production(track_id: str, production: bool) -> None:
    # A form-factor production track such as wear:production is production too (interpretation).
    if track_id.split(":")[-1] != "production":
        return
    if not production or os.environ.get(PRODUCTION_ENV) != "1":
        raise CliError(EXIT_PRODUCTION, f"{track_id} refused: needs --production and {PRODUCTION_ENV}=1")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(HASH_CHUNK), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_metadata(path: Path) -> dict[str, str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, ValueError):
        raise CliError(EXIT_LOCAL, "metadata file missing or unreadable") from None
    meta = {}
    for line in lines:
        key, sep, value = line.partition("=")
        if sep:
            meta[key.strip()] = value.strip()
    return meta


def metadata_version_code(meta: dict[str, str]) -> int | None:
    if "versionCode" not in meta:
        return None
    try:
        return int(meta["versionCode"])
    except ValueError:
        raise CliError(EXIT_LOCAL, "metadata versionCode is not an integer") from None


def check_aab(aab: Path, meta: dict[str, str]) -> str:
    """Compare the AAB hash with the .sha256 sidecar and metadata before any network call."""
    if not aab.is_file():
        raise CliError(EXIT_LOCAL, "AAB file not found")
    sidecar = aab.with_name(aab.name + ".sha256")
    try:
        parts = sidecar.read_text(encoding="utf-8").split(maxsplit=1)
    except (OSError, ValueError):
        raise CliError(EXIT_LOCAL, "sha256 sidecar file missing or unreadable") from None
    if len(parts) != 2:
        raise CliError(EXIT_LOCAL, "sha256 sidecar file has an unexpected format")
    expected, named = parts[0].lower(), Path(parts[1].lstrip("*").strip()).name
    if named != aab.name:
        raise CliError(EXIT_LOCAL, "sha256 sidecar names a different AAB")
    digest = sha256_file(aab)
    if digest != expected:
        raise CliError(EXIT_LOCAL, "AAB sha256 differs from the .sha256 sidecar")
    if "sha256" in meta and meta["sha256"].lower() != digest:
        raise CliError(EXIT_LOCAL, "AAB sha256 differs from the metadata")
    return digest


def _read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8").strip()
    except (OSError, ValueError):
        return ""


def read_notes(directory: Path) -> dict[str, str]:
    notes = {}
    for loc in LOCALES:
        text = _read_text(directory / f"{loc}.txt")
        if not text:
            raise CliError(EXIT_LOCAL, f"release notes for {loc} are missing or empty")
        notes[loc] = text
    return notes


@dataclass(frozen=True)
class Image:
    kind: str  # "png" or "jpeg"
    width: int
    height: int
    colour_type: int | None  # PNG only: 2 = RGB, 6 = RGBA
    bit_depth: int | None  # PNG only


def _jpeg_size(data: bytes) -> tuple[int, int]:
    pos = 2
    while pos + 4 <= len(data) and data[pos] == 0xFF:
        marker = data[pos + 1]
        if marker == 0xFF:  # fill byte
            pos += 1
            continue
        if marker == 0xD9:  # end of image before any frame header
            break
        if marker in SOF_MARKERS:
            height, width = struct.unpack(">HH", data[pos + 5 : pos + 9])
            return width, height
        if marker in STANDALONE_JPEG_MARKERS:
            pos += 2
            continue
        (segment_length,) = struct.unpack(">H", data[pos + 2 : pos + 4])
        pos += 2 + segment_length
    raise ValueError("no JPEG frame header")


def read_image(path: Path) -> Image:
    data = path.read_bytes()
    if data.startswith(PNG_SIGNATURE):
        if data[12:16] != b"IHDR":
            raise ValueError("PNG without IHDR")
        width, height = struct.unpack(">II", data[16:24])
        bit_depth, colour_type = struct.unpack(">BB", data[24:26])
        return Image("png", width, height, colour_type, bit_depth)
    if data.startswith(b"\xff\xd8"):
        width, height = _jpeg_size(data)
        return Image("jpeg", width, height, None, None)
    raise ValueError("not a PNG or JPEG file")


def mime_for(path: Path) -> str:
    with path.open("rb") as fh:
        return "image/png" if fh.read(8) == PNG_SIGNATURE else "image/jpeg"


def _image_problem(path: Path, label: str, *, icon: bool = False, exact: tuple[int, int] | None = None) -> str | None:
    if not path.is_file():
        return f"{label}: missing"
    try:
        img = read_image(path)
    except (OSError, ValueError, struct.error):
        return f"{label}: not a readable PNG or JPEG"
    if icon:
        if img.kind != "png" or img.colour_type != 6 or img.bit_depth != 8:
            return f"{label}: must be PNG 32-bit RGBA (colour type 6)"
        if path.stat().st_size > ICON_BYTES_MAX:
            return f"{label}: larger than 1024 KiB"
    elif img.kind == "png" and (img.colour_type != 2 or img.bit_depth != 8):
        return f"{label}: PNG must be 24-bit without alpha (colour type 2)"
    if exact is not None:
        if (img.width, img.height) != exact:
            return f"{label}: must be {exact[0]}x{exact[1]}, is {img.width}x{img.height}"
        return None
    short, long = sorted((img.width, img.height))
    if short < SIDE_MIN or long > SIDE_MAX:
        return f"{label}: each side must be 320..3840 px, is {img.width}x{img.height}"
    if long > 2 * short:
        return f"{label}: longer side must be at most 2x the shorter, is {img.width}x{img.height}"
    return None


def _text_problem(path: Path, label: str, limit: int) -> tuple[str, str | None]:
    text = _read_text(path)
    if not text:
        return "", f"{label}: missing or empty"
    if len(text) > limit:
        return text, f"{label}: {len(text)} characters, limit {limit}"
    return text, None


@dataclass(frozen=True)
class LocaleAssets:
    title: str
    short: str
    full: str
    feature: Path
    phones: tuple[Path, ...]


@dataclass(frozen=True)
class ListingAssets:
    icon: Path
    locales: dict[str, LocaleAssets]


def load_listing(root: Path) -> ListingAssets:
    """Validate the listing layout from the brief; any violation raises exit 2 before the network."""
    problems: list[str] = []
    icon = root / "icon.png"
    problems.append(_image_problem(icon, "icon.png", icon=True, exact=ICON_SIZE))
    locales = {}
    for loc in LOCALES:
        base = root / loc
        texts = []
        for name, limit in (("title", TITLE_MAX), ("short", SHORT_MAX), ("full", FULL_MAX)):
            text, problem = _text_problem(base / f"{name}.txt", f"{loc}/{name}.txt", limit)
            texts.append(text)
            problems.append(problem)
        feature = base / "featureGraphic.png"
        problems.append(_image_problem(feature, f"{loc}/featureGraphic.png", exact=FEATURE_SIZE))
        phone_dir = base / "phone"
        phones = []
        if phone_dir.is_dir():
            phones = sorted(p for p in phone_dir.iterdir() if p.suffix.lower() in PHONE_SUFFIXES)
        if not PHONE_MIN <= len(phones) <= PHONE_MAX:
            problems.append(f"{loc}/phone: need 2 to 8 screenshots, found {len(phones)}")
        for phone in phones:
            problems.append(_image_problem(phone, f"{loc}/phone/{phone.name}"))
        locales[loc] = LocaleAssets(texts[0], texts[1], texts[2], feature, tuple(phones))
    problems = [p for p in problems if p]
    if problems:
        raise CliError(EXIT_LOCAL, "listing check failed:\n" + "\n".join(f"  {p}" for p in problems))
    return ListingAssets(icon=icon, locales=locales)


# --- API helpers -------------------------------------------------------------


def describe_http_error(exc: HttpError) -> tuple[int, str, str]:
    status = int(exc.resp.status)
    reason, message = "", ""
    try:
        error = json.loads(exc.content.decode("utf-8"))["error"]
        message = str(error.get("message", ""))
        errors = error.get("errors") or []
        reason = str(errors[0].get("reason", "")) if errors else ""
    except (ValueError, KeyError, TypeError, AttributeError, IndexError):
        message = str(getattr(exc, "reason", "") or "")
    return status, reason, safe_text(message)


def _insert_edit(service) -> str:
    edit = service.edits().insert(packageName=PACKAGE, body={}).execute()["id"]
    log.info("edit %s opened", edit)
    return edit


def _validate(service, edit: str) -> None:
    try:
        service.edits().validate(packageName=PACKAGE, editId=edit).execute()
    except HttpError as exc:
        if exc.resp.status != 400:
            raise
        _, reason, message = describe_http_error(exc)
        raise CliError(EXIT_VALIDATE, f"edits.validate reported errors ({reason or 'HTTP 400'}): {message}") from None
    log.info("validated edit %s", edit)


def _finish(service, edit: str, commit: bool, summary: str) -> bool:
    if not commit:
        print("DRY RUN: nothing committed")
        return False
    service.edits().commit(packageName=PACKAGE, editId=edit).execute()
    print(f"COMMITTED edit {edit}: {summary}")
    log.info("committed edit %s", edit)
    return True


def _discard(service, edit: str) -> None:
    try:
        service.edits().delete(packageName=PACKAGE, editId=edit).execute()
    except API_ERRORS as exc:
        log.warning("edit %s was not deleted (%s); check it in the Play Console", edit, type(exc).__name__)
    else:
        log.info("edit %s deleted", edit)


# --- commands ----------------------------------------------------------------


def cmd_status(args, get_service) -> int:
    service = get_service()
    require_methods(service, STATUS_METHODS)
    edit = _insert_edit(service)
    try:
        tracks = service.edits().tracks().list(packageName=PACKAGE, editId=edit).execute().get("tracks", [])
        for track in tracks:
            print(f"track {track.get('track', '?')}")
            for release in track.get("releases", []):
                codes = ",".join(str(code) for code in release.get("versionCodes", []))
                print(
                    f"  name={release.get('name', '')} status={release.get('status', '')} "
                    f"versionCodes={codes} userFraction={release.get('userFraction', '-')}"
                )
        log.info("status: %d tracks", len(tracks))
    finally:
        _discard(service, edit)
    return EXIT_OK


def _upload_bundle(service, edit: str, aab: Path) -> int:
    media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
    try:
        response = service.edits().bundles().upload(packageName=PACKAGE, editId=edit, media_body=media).execute()
    except HttpError as exc:
        _, _, message = describe_http_error(exc)
        if "version code" in message.lower() and "already" in message.lower():
            raise CliError(
                EXIT_API, "versionCode already used on Play: rerun with --version-code N (N = versionCode of this AAB)"
            ) from None
        raise
    version_code = int(response["versionCode"])
    log.info("bundle uploaded to edit %s, versionCode %d", edit, version_code)
    return version_code


def cmd_upload_bundle(args, get_service) -> int:
    track = resolve_track(args.track)
    aab = Path(args.aab)
    meta = read_metadata(Path(args.metadata)) if args.metadata else {}
    check_aab(aab, meta)
    check_production(track, args.production)
    notes = read_notes(Path(args.notes_dir))
    meta_code = metadata_version_code(meta)
    if args.version_code is not None and meta_code is not None and args.version_code != meta_code:
        raise CliError(EXIT_LOCAL, f"--version-code {args.version_code} differs from metadata versionCode {meta_code}")
    version_code = args.version_code
    version_name = meta.get("versionName") or DEFAULT_VERSION_NAME
    service = get_service()
    require_methods(service, EDIT_METHODS + TRACK_METHODS + (() if version_code is not None else BUNDLE_METHODS))
    edit = _insert_edit(service)
    committed = False
    try:
        if version_code is None:
            version_code = _upload_bundle(service, edit, aab)
            if meta_code is not None and version_code != meta_code:
                raise CliError(EXIT_LOCAL, f"uploaded versionCode {version_code} differs from metadata {meta_code}")
        body = {
            "track": track,
            "releases": [
                {
                    "name": version_name,
                    "versionCodes": [version_code],
                    "status": args.status,
                    "releaseNotes": [{"language": loc, "text": notes[loc]} for loc in LOCALES],
                }
            ],
        }
        service.edits().tracks().update(packageName=PACKAGE, editId=edit, track=track, body=body).execute()
        log.info("track %s set in edit %s: versionCode %d, status %s", track, edit, version_code, args.status)
        _validate(service, edit)
        committed = _finish(service, edit, args.commit, f"track {track}, versionCode {version_code}")
    finally:
        if not committed:
            _discard(service, edit)
    return EXIT_OK


def _replace_images(service, edit: str, loc: str, image_type: str, paths: list[Path]) -> None:
    service.edits().images().deleteall(packageName=PACKAGE, editId=edit, language=loc, imageType=image_type).execute()
    for path in paths:
        media = MediaFileUpload(str(path), mimetype=mime_for(path))
        service.edits().images().upload(
            packageName=PACKAGE, editId=edit, language=loc, imageType=image_type, media_body=media
        ).execute()
    log.info("images %s %s: %d uploaded", loc, image_type, len(paths))


def cmd_listing(args, get_service) -> int:
    assets = load_listing(Path(args.dir))
    service = get_service()
    require_methods(service, EDIT_METHODS + LISTING_METHODS)
    edit = _insert_edit(service)
    committed = False
    try:
        for loc in LOCALES:
            info = assets.locales[loc]
            body = {"title": info.title, "shortDescription": info.short, "fullDescription": info.full}
            service.edits().listings().update(packageName=PACKAGE, editId=edit, language=loc, body=body).execute()
            _replace_images(service, edit, loc, "phoneScreenshots", list(info.phones))
            _replace_images(service, edit, loc, "featureGraphic", [info.feature])
            _replace_images(service, edit, loc, "icon", [assets.icon])
        _validate(service, edit)
        committed = _finish(service, edit, args.commit, f"listing for {len(LOCALES)} locales")
    finally:
        if not committed:
            _discard(service, edit)
    return EXIT_OK


def cmd_release_notes(args, get_service=None) -> int:
    notes = read_notes(Path(args.dir))
    for loc in LOCALES:
        print(f"{loc}: {len(notes[loc])} characters")
    return EXIT_OK


# --- entry point -------------------------------------------------------------


def _commit_flag(parser: argparse.ArgumentParser, help_text: str) -> None:
    parser.add_argument("--commit", action="store_true", help=help_text)


def build_parser() -> argparse.ArgumentParser:
    parser = _Parser(description=f"Google Play Developer API for {PACKAGE}. Dry run unless --commit.")
    sub = parser.add_subparsers(dest="command", required=True, parser_class=_Parser)

    status = sub.add_parser("status", help="print track releases; never commits")
    _commit_flag(status, "accepted for uniformity; has no effect")
    status.set_defaults(func=cmd_status)

    upload = sub.add_parser("upload-bundle", help="upload an AAB and set a track release")
    upload.add_argument("--aab", required=True)
    upload.add_argument("--metadata", help="release-metadata.txt from the release workflow")
    upload.add_argument("--track", required=True, help="internal, alpha, beta, production or a closed-track id")
    upload.add_argument("--notes-dir", required=True, help="directory with en-US.txt, es-ES.txt, de-DE.txt")
    upload.add_argument("--status", choices=("draft", "completed"), default="draft")
    upload.add_argument("--version-code", type=int, help="reuse a versionCode already on Play; skips the upload")
    upload.add_argument("--production", action="store_true", help="required with ROBIA_PLAY_ALLOW_PRODUCTION=1")
    _commit_flag(upload, "commit the edit (default: dry run)")
    upload.set_defaults(func=cmd_upload_bundle)

    listing = sub.add_parser("listing", help="push store listing text and images for every locale")
    listing.add_argument("--dir", required=True, help="play-assets directory (see TASK-PL3 layout)")
    _commit_flag(listing, "commit the edit (default: dry run)")
    listing.set_defaults(func=cmd_listing)

    notes = sub.add_parser("release-notes", help="offline: count release-notes characters per locale")
    notes.add_argument("--dir", required=True)
    _commit_flag(notes, "accepted for uniformity; has no effect")
    notes.set_defaults(func=cmd_release_notes)
    return parser


def main(argv: list[str] | None = None, *, service=None) -> int:
    try:
        args = build_parser().parse_args(argv)
        log.info("command %s", args.command)
        # The client is built only after a command's local checks, so no key is needed to see those failures.
        get_service = (lambda: service) if service is not None else build_service
        return args.func(args, get_service)
    except CliError as exc:
        log.error("%s", redact(str(exc)))
        return exc.code
    except HttpError as exc:
        status, reason, message = describe_http_error(exc)
        log.error("Google API error: HTTP %d %s: %s", status, reason or "-", message)
        return EXIT_API
    except API_ERRORS as exc:
        log.error("network or auth error: %s", type(exc).__name__)
        return EXIT_API


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s", stream=sys.stderr)
    sys.exit(main())
