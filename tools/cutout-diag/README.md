# Cutout diagnostics (robia-round3 K01–K03, gates G-ADB1/G-ADB2)

- `reference_stages.py IN_DIR OUT_DIR` — runs the reference pipeline (`../segmentation-bench/pipeline_v11.py`, chosen variant BAND=1 FILL=median NOENC=1) and writes per photo `s1_saliency.png`, `s2_selection.png`, `s3_hanger.json`, `s4_kill.png`, `s4b_filled.png`, `s4b_reselect.png`, `s5_alpha.png`, `final.png`, `report.json`. Models: see `../segmentation-bench/README.md`.
- `compare.py APP_DUMP REF_DUMP` — IoU per stage (≥ 0.95, final alpha ≥ 0.97) and `band_edge_ratio` (hanger-band edge width / body edge width, ≤ 1.5). Exit 1 on failure.

App dump (debug build only, K01):
```sh
adb push photos/. /sdcard/Android/data/com.gusanitolabs.robia/files/cutout-debug/in/
adb shell am start -n com.gusanitolabs.robia/.MainActivity --ez com.gusanitolabs.robia.CUTOUT_DEBUG true
adb logcat -s RobiaCutout:I | grep -m1 cutout_debug_all_done
adb pull /sdcard/Android/data/com.gusanitolabs.robia/files/cutout-debug/out app-dump
python -I compare.py app-dump ref-dump
```
Photos and dumps are personal data: keep them outside the repository.
