# Hanger sensitivity (SG4 evidence, planner, 2026-10-18)

Question (Manu, answer 3A): can a "High" hanger sensitivity be offered without eating garments?

Method (Raspberry Pi 5, capped systemd unit; `tools/segmentation-bench/pipeline_v11.py` with the new env `HTHR`):
1. Scan: best plausible D-FINE "Hanger" score per photo (full image + top-crop pass, same plausibility rule as v11) on all 142 photos.
   | threshold | photos with a hanger box |
   |---|---|
   | 0.25 (v11 Normal) | 99 |
   | 0.15 | 118 |
   | 0.12 (proposed High) | 124 |
   | 0.10 | 126 |
   | 0.05 | 131 |
2. Full v11 ("A median + band") at threshold 0.12 on the 25 photos whose best score lies in [0.12, 0.25): 17 change by more than 0.3 % of garment area.
3. Visual review of the 17 changed results against Normal:
   - **Better:** about 12. The wooden bar and clips are removed on #59, #81, #86, #98, #99, #100, #110, #111, #120, #123 and #141; #110 and #120 are the wooden clip hangers v11 missed.
   - **Worse:** 1. On #45 a hook tip stays visible; Normal removed it.
   - **No garment part lost** in any of the 17. Area drops of 6–15 % are the removed hanger and the background trapped under it.
   - **Unchanged:** #17, #31 and #33 (small edge differences).

Decision (SG4): offer "Hanger detection: Normal (0.25, default) / High (0.12)". The default stays Normal until device feedback, because High was measured only on Manu's 142 photos. The existing safety caps still apply at both settings: kill ≤ 15 % of garment area, and the plausibility box rule. Extra Pi time at High: D-FINE already runs at Normal, so only the SAM + re-selection steps run on more photos (+2–5 s on those photos).

**Resumen para Manu:**
- Con sensibilidad Alta, la percha se quita en unas 12 de las 17 fotos que cambian, incluidas las perchas de madera #110 y #120.
- En 1 foto (#45) queda la punta del gancho.
- No se pierde ninguna parte de prenda.
