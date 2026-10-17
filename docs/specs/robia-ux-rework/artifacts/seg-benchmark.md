# Segmentation and taxonomy evidence (planner, 2026-10-16)

Machine: Raspberry Pi 5, 4 cores, CPU only, onnxruntime 1.31.0, ai-edge-litert. Input mirrors the app: EXIF-rotated photo, long edge 1024.
Data: Manu's 142 garment photos (`~/.local/share/robia-planning/precision-data/photos_raw`, private, never committed). Visual comparison sheets were shared privately; none are in this repository.
Reference code: `tools/segmentation-bench/` (copied from the planner workspace; model files are downloaded by URL, see TS§SG2a).

## Variants tried (17 hard photos first, then all 142 for the winner)
| # | Pipeline | Result | Time (median) |
|---|---|---|---|
| v1 | u2netp (320) | coarse, leaks door on white garments | 1.3 s |
| v1 | ISNet general (1024, 170 MB) | better edges, still leaks white door into white blouses (#51, #52) | 7 s |
| v1 | BiRefNet-lite fp32 | killed: > 4 GB RSS | — |
| v1+post | ISNet + morphological opening for hangers | cut garment straps → rejected | — |
| v2 | SAM2.1-tiny (box from ISNet) | inherits ISNet leak; 134 MB; encoder 6-10 s | 7.5 s |
| v2 | MediaPipe magic_touch, 1 auto point | isolates white-on-white blouses well; picks only part of two-part garments | 0.15 s |
| v3 | ISNet/u2netp + magic_touch 3 points (one per vertical third, overlap ≥ 0.6) + guided-filter edges | **fixes door leaks, keeps all parts**; hangers kept | +0.5 s |
| — | u2net_cloth_seg (human parsing) | fails on flat/hanging garments → rejected | 11 s |
| v4 | + D-FINE-S Objects365 detector, class "Hanger" | hanger detected in 93/142 at ≥ 0.45 (49 lower); removal by magic_touch cut straps on 2 items | +0.9 s |
| v5 | hanger mask by SAM2.1-tiny (pos hook, neg garment core) | removes wire hangers without cutting straps; too slow | +7 s |
| v6 | same with MobileSAM (MIT, 44 MB) | same quality as v5 | +3-4 s |
| v7 | MobileSAM without hook point | no gain | — |
| v8 | detector threshold 0.20 + stricter kill | wrong boxes (door handle, pocket) → no gain | — |
| v9 | plausibility rule (box width ≥ 35 % garment, at top) + second detector pass on the top band + SAM box-only | specks gone; clip bars removed on more items | — |
| **v10** | v9 + "kill whole hanger mask if ≥ 85 % inside the box" | **chosen** | see below |

## v10 on all 142 photos
- Hanger detected (after plausibility): 106; none: 36.
- Median total: 3.3 s without hanger, 6.7 s with hanger (Pi CPU, with a concurrent job; earlier clean runs 2.2 s / 6.1 s). Stages: u2netp 0.8 s, magic_touch ×3 0.6 s, D-FINE 1.2 s (two passes when needed), MobileSAM 3.9 s (only with hanger), guided filter 0.04 s.
- Visual review (planner, contact sheet of 142): door/wall leaks and door-handle specks fixed except 1 small speck; wire-hook hangers on dresses/tops mostly removed; **clip-bar hangers on skirts/shorts still visible on roughly 25-30 items** (detector miss or partial mask). → the Refine tool (SG3) is required, not optional polish.
- Expected on phone: Poco F3 (Snapdragon 870) CPU is in the same class as the Pi 5 for these kernels; G-SEG-1 measures it.

## Manu's idea: segment everything + EmbeddingGemma labels
- MobileSAM automatic grid (48 points × 3 masks, NMS → 12-16 segments) 15-24 s + EmbeddingGemma image embedding per segment (70 vision tokens, 7 text prompts: garment, accessory, hanger, door, wall, floor, furniture) 43-58 s per photo.
- Labels unreliable: top-2 margins 0.001-0.06; white blouses (#51, #52) labelled hanger/door → union of garment segments empty; some hangers labelled garment.
- Gemma as a judge "cutout with hanger vs without": 8/11 correct with margins 0.001-0.03 (noise).
- Conclusion: rejected. A detector trained on objects (D-FINE Objects365 includes "Hanger") is the working form of the idea.

## Taxonomy check (EmbeddingGemma 2, recipe eg2-r1, TEXT mode, stored vectors vt140, 142 labelled photos)
| Added labels | top-1 correct (baseline 120/142) | stolen by new labels |
|---|---|---|
| socks, underwear, swimwear, sleepwear | 96 | sleepwear 32, underwear 1 |
| socks, underwear, swimwear (variant a) | 118 | underwear 2 |
| socks, underwear, swimwear (short texts, b) | 118 | 1 + 1 |
| socks, underwear (c) | 118 | 1 + 1 |
No photo of socks/underwear exists in the set: positive recall is untested (device row V-Z2-14).
