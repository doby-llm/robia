# Revision 3 — R21 generative models (planner, 2026-10-18)

Desk research: `artifacts/inv-r3-generative-availability.md` (sub-agent, sources cited). Measured run: the planner on a Raspberry Pi 5 CPU, in a systemd unit with MemoryMax 6 GB.

## Availability
- **Weights exist and fit a phone-class budget:** only SD 1.5 family models (SD1.5-inpainting, SD-Turbo, InstructPix2Pix, LCM-LoRA). All are ~0.9 B parameters and need about 2–5 GB of RAM.
- **Too big for a phone or the Pi:** FLUX.2 [klein] 4B (with its 4 B text encoder, the process is > 5 GB), Sana 0.6B (plus a 2 B Gemma encoder), Qwen-Image-Edit (20 B) and FLUX.1 Kontext (12 B).
- **No released weights:** MobileDiffusion, SnapGen / SnapGen++, DreamLite (weights announced as non-commercial but not published), BlazeEdit (paper only).

## Measured: SD1.5-inpainting on the hanger mask only
- Setup: diffusers 0.35, torch 2.6 CPU, fp32, 512 px. The mask covered only the dilated hanger from D-FINE + MobileSAM. The garment pixels were to be composited back untouched.
- Memory: 4.6–5.7 GB RSS.
- Speed:
  - 20 steps, 8 photos: no image after 28 min.
  - 8 steps, 2 photos: no image after 24 min of CPU time.
  - 8 steps, 1 photo, 4 threads: no image within a 25 min timeout.
- So: **> 25 min per photo on the Pi.** Even a 50× faster phone NPU path would cost tens of seconds per garment, plus a ~2 GB model download.
- InstructPix2Pix was not run: it has the same SD 1.5 cost, and a whole-image edit cannot guarantee that prints and logos stay the same.

## Verdict
**Rejected for Robia.**
- Too slow on CPU.
- RAM is far above the 1.3 GB budget.
- Whole-image regeneration changes the garment, and a wardrobe catalogue needs the real garment.
- Inpainting only the hanger region does not need diffusion: the median fill already gives the same result as LaMa (see `inv-r3-matting.md`).

Worth watching: BlazeEdit (195 M parameters, mask-conditioned object removal on a phone TPU) if its weights are released.

**Resumen para Manu:**
- De los modelos que nombraste, solo la familia SD 1.5 tiene pesos y un tamaño razonable. FLUX.2 klein y Sana no caben, y MobileDiffusion, SnapGen y DreamLite no tienen pesos públicos.
- SD 1.5 inpainting en la Pi no produjo ni una imagen en 25 minutos y usa unos 5 GB de RAM.
- Regenerar la prenda entera cambia estampados y logos, y en un armario eso no sirve.
- Para borrar solo la percha basta el relleno simple actual.
- Queda descartado. Solo vigilaría BlazeEdit si publican los pesos.
