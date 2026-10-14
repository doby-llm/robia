"""Read-only download of the photo folder listed in BASE/drive_listing.json into BASE/photos_raw/ (resumable, concurrent).

Usage: download_drive.py BASE --token PATH
PATH is a local OAuth token JSON (refresh_token, client_id, client_secret, scopes). Never commit it.
"""
import argparse
import io
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload

ap = argparse.ArgumentParser()
ap.add_argument("base", type=Path)
ap.add_argument("--token", type=Path, required=True)
a = ap.parse_args()

BASE = a.base
listing = json.loads((BASE / "drive_listing.json").read_text())
out = BASE / "photos_raw"
out.mkdir(exist_ok=True)


def creds():
    d = json.loads(a.token.read_text())
    c = Credentials(token=None, refresh_token=d["refresh_token"], token_uri=d.get("token_uri", "https://oauth2.googleapis.com/token"),
                    client_id=d["client_id"], client_secret=d["client_secret"], scopes=d["scopes"])
    c.refresh(Request())
    return c


C = creds()


def fetch(item):
    dest = out / item["name"]
    if dest.exists() and dest.stat().st_size == int(item["size"]):
        return "skip"
    svc = build("drive", "v3", credentials=C, cache_discovery=False)
    buf = io.BytesIO()
    dl = MediaIoBaseDownload(buf, svc.files().get_media(fileId=item["id"]), chunksize=8 * 1024 * 1024)
    done = False
    while not done:
        _, done = dl.next_chunk()
    tmp = dest.with_suffix(".part")
    tmp.write_bytes(buf.getvalue())
    tmp.rename(dest)
    return "ok" if dest.stat().st_size == int(item["size"]) else "size_mismatch"


with ThreadPoolExecutor(4) as ex:
    results = list(ex.map(fetch, listing))
from collections import Counter
print(Counter(results))
