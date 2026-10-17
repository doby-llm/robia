# Revision 3 — R21 matting models and combinations (planner, 2026-10-18)

Machine: Raspberry Pi 5, CPU, onnxruntime 1.31.0 (arena off, 3 threads). Each run was a separate systemd user unit with `MemoryMax` (3.5–5 GB) and no swap; an earlier uncapped run took the Hermes session down with OOM. Input matches the app: EXIF-rotated photo, long edge 1024. Garments: 23 hard photos (white on a white door, clip-bar and wire hangers, two-part garments). Visual sheets are private: Manu's photos never leave the Pi.

## Models alone (no hanger removal)
| Model | Licence / size | Pi time / RAM | Result |
|---|---|---|---|
| u2netp (today's coarse model in v10/v11) | Apache-2.0 / 4.6 MB | 0.9–1.7 s / 0.56 GB | coarse; on its own keeps the hanger |
| IS-Net general | Apache-2.0 / 179 MB | 6 s / 0.9 GB | good edges; keeps the hanger; white blouses #51/#52 collapse into the door (tiny garment + door edge) |
| BEN2 base | MIT / 223 MB | ~90 s / 3.3 GB | keeps the hanger; on #51/#52 keeps the **whole white door** → unusable on Manu's backgrounds; far too slow for a phone |
| BiRefNet-lite (fp16 ONNX, 1024) | MIT / 115 MB | killed at 3.5 GB and at 5 GB caps | does not fit a phone at the app's resolution (G-SEG-1 limit 1.3 GB) |
Not tried: RMBG-1.4/2.0 (non-commercial licence), InSPyReNet/MVANet (same 1024 Swin-class cost as BiRefNet), u2net_cloth_seg (already rejected: human parsing).

Main finding: every dedicated matting model treats the hanger as part of the foreground object, because a hanger is salient. None of them solves Manu's two problems (hangers; white garment on a white door). The v3 idea, MediaPipe magic_touch selection seeded by the coarse mask, is what fixes the door leaks.

## Combinations inside the v11 pipeline (`tools/segmentation-bench/pipeline_v12.py`)
| Variant | Pi time (median, 23 photos) | RAM | Verdict |
|---|---|---|---|
| v11 = u2netp + magic_touch ×3 + D-FINE hanger + MobileSAM + median fill ("hanger first") | ≈ 6–9 s | ≈ 1.2 GB | baseline proposal |
| v12 u2netp + **LaMa** inpainting of the hanger (big-lama ONNX, 208 MB) | 19.8 s | 1.4 GB | **identical to v11** on 8 hanger photos (#0 #80 #89 #112 #120 #129 #132 #138): the median fill is already enough because only the hanger band is re-selected → rejected |
| v12 **IS-Net** coarse + magic_touch + median fill | 16.4 s | 1.6 GB | mixed: removes a stray object below #101 that v10/v11 keep; opens a hole in the white blouse #51; otherwise equal → not worth +170 MB and ×2 time |
| v12 IS-Net + LaMa | stopped after 4 photos | — | redundant given the two rows above |

## What actually limits quality
The leftover clips and hangers come from the **hanger detector** (D-FINE-S Objects365, class Hanger), not from the matting or the fill:
- #129, #132, #138: small metal clips stay at the corners.
- #120 and #110: a wooden clip hanger is not detected at all, so it stays whole.

The next lever is better hanger detection, for example an open-vocabulary detector prompted with "hanger" / "clip" (OWLv2, Florence-2, Grounding-DINO-tiny). It must stay ≤ ~100 MB and ≤ 2 s on the Pi to be phone-viable. Not tested in this round. Until then, the photo editor (SG3: a tap on the clip with Remove) is the fallback.

## Decision proposed (SEG-DEC-008)
Keep v11 as the precise-cutout pipeline. Do not ship IS-Net, BEN2, BiRefNet-lite or LaMa. Record a follow-up investigation of an open-vocabulary hanger detector, after G-SEG-1 shows the phone budget.

**Resumen para Manu:**
- Los modelos de recorte dedicados (IS-Net, BEN2, BiRefNet-lite) no quitan la percha y, con fondo blanco, se llevan la puerta.
- BiRefNet-lite no cabe ni con 5 GB de RAM.
- BEN2 tarda 90 s por foto.
- Rellenar la percha con LaMa da el mismo resultado que el relleno simple actual, pero cuesta 3 veces más.
- El cuello de botella es detectar la percha (pinzas pequeñas, perchas de madera). Ese es el siguiente paso, no el modelo de recorte.
