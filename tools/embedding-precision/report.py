"""Build the self-contained HTML precision report (Spanish UI) from cached embeddings.

Scoring happens in the browser from int16-quantized unit vectors, so the reviewer can switch
image variant, label texts, method (text only / examples / combined) and margins live.

Usage: report.py BASE OUT_HTML
"""
import argparse
import base64
import io
import json
import statistics
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from labels import CATEGORIES, OCCASIONS  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument("base", type=Path)
ap.add_argument("out", type=Path)
a = ap.parse_args()
B = a.base


def q16(v: np.ndarray) -> dict:
    m = float(np.max(np.abs(v))) or 1.0
    q = np.round(v / m * 32767).astype("<i2")
    return {"b": base64.b64encode(q.tobytes()).decode(), "s": m / 32767}


def thumb(path: Path, edge: int = 260) -> str:
    img = Image.open(path).convert("RGB")
    img.thumbnail((edge, edge))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=76)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


VARIANTS = {"raw": "_raw_vt140"}  # original photo, EXIF-oriented, resized to max edge 1024 (no cutout)
stems = sorted({p.name.rsplit("_", 1)[0] for p in (B / "processed").glob("*_raw.jpg")})
available = [k for k, suf in VARIANTS.items() if all((B / "emb" / f"{s}{suf}.npy").exists() for s in stems)]
if not available:
    sys.exit("no complete embedding variant yet")

provisional = json.loads((B / "provisional_labels.json").read_text())
sheet_index = {v: int(k) for k, v in json.loads((B / "sheets" / "index.json").read_text()).items()}
rows = []
for s in stems:
    rows.append({
        "stem": s,
        "n": sheet_index[s],
        "tr": thumb(B / "processed" / f"{s}_raw.jpg"),
        "v": {k: q16(np.load(B / "emb" / f"{s}{VARIANTS[k]}.npy")) for k in available},
        "prov": provisional[s],
    })
rows.sort(key=lambda r: r["n"])

lab = np.load(B / "emb" / "labels.npz")
label_vecs = {k: [q16(v) for v in lab[k]] for k in lab.files}

timings = [json.loads(line) for line in (B / "timings.jsonl").read_text().splitlines() if line.strip()]
timings = [t for t in timings if t.get("variant") == "raw" and t.get("vision_tokens") == 140]
perf = []
for vt in sorted({t.get("vision_tokens") for t in timings}):
    ts = sorted(t["seconds"] for t in timings if t.get("vision_tokens") == vt)
    perf.append({"vt": vt, "n": len(ts), "median": round(statistics.median(ts), 1),
                 "p90": round(ts[int(len(ts) * 0.9) - 1], 1) if len(ts) > 10 else None})
max_rss = 0
for log in B.glob("embed_*.log"):
    for line in log.read_text().splitlines():
        if line.startswith("{"):
            max_rss = max(max_rss, json.loads(line).get("max_rss_mb", 0))

data = {
    "rows": rows,
    "labels": label_vecs,
    "categories": [{"id": k, "name": v[0], "desc": v[1]} for k, v in CATEGORIES.items()],
    "occasions": [{"id": k, "name": v[0], "desc": v[1]} for k, v in OCCASIONS.items()],
    "variants": available,
    "perf": perf,
    "maxRss": max_rss or 890,
}
template = (Path(__file__).parent / "report_template.html").read_text()
a.out.write_text(template.replace("/*__DATA__*/null", json.dumps(data, separators=(",", ":"))))
print(json.dumps({"photos": len(rows), "variants": available, "bytes": a.out.stat().st_size, "perf": perf}))
