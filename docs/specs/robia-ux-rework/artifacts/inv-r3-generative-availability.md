# Robia R3 — local generative-image availability (desk research)

**Scope.** Garment photo → same garment, hanger removed, white/transparent output; fidelity to prints/logos/shape matters. Reviewed 18 Oct 2026. **V** = verified in the cited model card/project/repository; **I** = engineering inference, not a measured Pi result. No weights were downloaded and no model was run. [unverified]

## Availability / fit matrix

| Model | Weights; licence; size | Image-conditioned capability | ~RAM at 512 px; phone/edge; Pi verdict |
|---|---|---|---|---|
| **Stable Diffusion 1.5** | V: public EMA-only safetensors, 4,265,146,304 B by HTTP HEAD; 0.9B params; CreativeML OpenRAIL-M.[1] | V: img2img; base model is not mask-inpainting-specific. | V/I: sd.cpp reports ~2.1 GB q8, ~2.3 GB f16 (1.6/1.9 GB with flash attention).[14] Android/CPU path exists.[13] **YES**, baseline; use a tight mask/composite, not whole-image redraw. |
| **SD1.5 inpainting (Runway mirror)** | V: public `sd-v1-5-inpainting.ckpt`, 4,265,437,280 B by HEAD; 0.9B-class SD1.x; CreativeML OpenRAIL-M.[2] | V: dedicated mask inpainting; five extra U-Net channels for masked image + mask.[2] | I: ~2.1–2.5 GB q8/q4 sd.cpp class. **YES**, best first Pi test for hanger removal; preserve unmasked garment pixels by masking only hanger/background. |
| **SD-Turbo** | V: public safetensors, 5,214,561,328 B (F32 file); 0.9B params; Stability Community License/commercial registration terms.[3] | V: img2img in 1–4 steps; not dedicated inpainting.[3] | I: ~2–3 GB after sd.cpp quantization; **YES (second choice)** for speed, but likely weaker logo/print fidelity. |
| **LCM / LCM-LoRA SD1.5** | V: public 67.5M-parameter LoRA; OpenRAIL++; add-on, not a base model.[30] | V: 2–8-step generation and img2img; can be combined with inpainting/ControlNet.[31] | I: base SD1.5 footprint + ~135 MB LoRA; **YES as SD1.5 speed variant**, but validate masked fidelity. |
| **Google MobileDiffusion** | V: paper/blog reports 520M params and sub-second 512² mobile T2I.[5][6] **No official public checkpoint found** in the cited Google/paper sources. Licence therefore unknown. | V: T2I only in the public description; no edit/inpaint checkpoint/API documented. | I: small enough for phone, but **NO** for this task: unavailable and not editing-capable. |
| **FLUX.2 [klein] 4B** | V: public BF16, 4B, Apache-2.0; official file 7.75 GB; leejet GGUF Q4_0 2.46 GB/Q8_0 4.3 GB.[11][12] | V: unified T2I + multi-reference image editing; sd.cpp has edit examples.[11][15] | I: Q4 denoiser + Qwen3-4B text encoder + VAE likely >5 GB process; ~13 GB VRAM is the vendor target.[11] **NO Pi**; interesting Snapdragon-class GPU/desktop, not CPU cap. |
| **Sana 0.6B / SANA-Sprint** | V: public 0.6B BF16 model, Apache-2.0 plus Gemma-2-2B terms; weights released.[7][8] | V: SANA-Sprint supports 1–4-step T2I and ControlNet; Diffusers provides img2img, not a proven mask-inpaint path.[7] | I: “0.6B” excludes the ~2B Gemma encoder; total runtime likely >5 GB unquantized. **NO Pi**; possible high-end phone/NPU only after a native port. |
| **SnapGen / SnapGen++** | V: papers report 379M / 0.4B and mobile latency; official project pages expose no downloadable checkpoint or licence. Mark weights **not located**, not “open”.[9][10][26][27] | V: T2I papers; no public editing/inpainting interface. | I: denoisers are phone-sized, but SnapGen’s text encoders include Gemma-2-2B.[25] **NO** for Robia until weights and editing support appear. |
| **DreamLite** | V: 0.39B, 4-step unified T2I + text-guided editing; code Apache-2.0, weights intended CC BY-NC 4.0, but official repo says HF weights not released.[23][24] | V: editing via source-image concatenation, including product/object replacement; no conventional mask API documented.[23][24] | I: mobile demo is ~3 s at 1024² with 4-bit Qwen-VL + fp16 U-Net/VAE. **WATCH, not testable now**; license/weight gate blocks deployment. |
| **LayerDiffuse (SD1.5/SDXL add-on)** | V: public SD1.5 transparent decoder/attention weights; repo Apache-2.0, HF v1 card incomplete; Diffusers port is MIT, while base SD licence still applies.[16][17] | V: RGBA/transparent-layer generation, not hanger detection or inpainting itself. | I: SD1.5 footprint plus ~0.2–0.5 GB adapter/decoder. **Conditional YES** for transparent output after segmentation/inpainting; not a standalone solution and no sd.cpp native path found. |
| **InstructPix2Pix** | V: public 0.9B params; safetensors file is ~7.7 GB; model/code carry SD/OpenRAIL-derived restrictions.[4] | V: direct image + text instruction editing; no mask input.[4] | I: ~2–3 GB quantized at 512. **YES (third run)**, but whole-image edits may alter garment details; composite only the hanger region. |
| **Qwen-Image-Edit-2509** | V: public Apache-2.0, 20B BF16 params.[18] | V: strong single/multi-image consistency and ControlNet conditions.[18] | I: ~40 GB BF16 weights; **NO** phone/Pi, despite excellent edit semantics. |
| **FLUX.1 Kontext [dev]** | V: public 12B BF16; FLUX.1 dev non-commercial licence.[19] | V: instruction editing/reference with successive-edit consistency.[19] | I: >20 GB BF16; **NO** local edge. |
| **Qualcomm AI Hub SD/ControlNet** | V: optimized Snapdragon assets. ControlNet-Canny: 361M ControlNet + 340M text encoder + 865M U-Net + 83M VAE, 1.4 GB model package, Apache-2.0 plus Qualcomm terms.[20][29] | V: text + Canny reference image; applicable to image editing, but not dedicated inpainting.[20] | V: phone/NPU path; listed devices include Snapdragon 888 and newer, not explicitly Poco F3/870.[20] **NO Pi**; possible phone prototype only with QNN export. |
| **MediaPipe Image Generator** | V: on-device converted SD1.5 EMA-only foundation; external model licence remains applicable.[22] | V: prompt + optional condition image/plugins (edge/depth/face), similar to ControlNet; no documented mask-inpaint task.[22] | I: high-end Android target; **NO for this exact fidelity task** unless custom mask/compositing is added. |
| **BlazeEdit (2026)** | V: paper-only evidence found: 195M image-to-image model; no public weights/licence found.[25] | V: mask-conditioned object removal, outpainting, relighting, tone correction and stickers.[25] | I: very promising phone/Pi footprint; paper reports 290 ms on Pixel 10 Edge TPU. **WATCH**, not runnable until weights release. |

## Pi recommendation (at most three runs)

1. **SD1.5-inpainting q8_0 via stable-diffusion.cpp:** convert the official checkpoint at `https://huggingface.co/sd-legacy/stable-diffusion-inpainting/resolve/main/sd-v1-5-inpainting.ckpt` (4,265,437,280 B) to q8_0; 512×512, 15–20 steps, mask only hanger and immediate background, then composite the untouched garment. Expected process class: ~2–3 GB; the only candidate with a native inpainting objective. [unverified]
2. **SD-Turbo q8_0 img2img:** source `https://huggingface.co/stabilityai/sd-turbo/resolve/main/sd_turbo.safetensors` (5,214,561,328 B), convert; 1–4 steps, low strength, and composite only the removed-hanger region. Fastest stress test, but reject if logos/prints drift. [unverified]
3. **InstructPix2Pix q8_0:** source `https://huggingface.co/timbrooks/instruct-pix2pix`; 8–12 steps, instruction “remove hanger, preserve garment exactly,” and composite a narrow edited region. It is a useful editing control, not a fidelity guarantee. [unverified]

**Bottom line:** start with conventional segmentation plus masked SD1.5 inpainting; generation cannot reliably preserve logos when asked to redraw the whole garment. Quantized RAM figures and all Pi timing above are estimates unless explicitly marked V; do not treat phone/H100/desktop timings as Pi evidence. [unverified]

## Sources

[1] https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5
[2] https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-inpainting
[3] https://huggingface.co/stabilityai/sd-turbo
[4] https://huggingface.co/timbrooks/instruct-pix2pix
[5] https://research.google/blog/mobilediffusion-rapid-text-to-image-generation-on-device
[6] https://arxiv.org/abs/2311.16567
[7] https://huggingface.co/Efficient-Large-Model/Sana_Sprint_0.6B_1024px_diffusers
[8] https://github.com/NVlabs/Sana
[9] https://snap-research.github.io/snapgen
[10] https://snap-research.github.io/snapgenplusplus
[11] https://huggingface.co/black-forest-labs/FLUX.2-klein-4B
[12] https://huggingface.co/leejet/FLUX.2-klein-4B-GGUF
[13] https://github.com/leejet/stable-diffusion.cpp
[14] https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/quantization_and_gguf.md
[15] https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/flux2.md
[16] https://github.com/lllyasviel/LayerDiffuse
[17] https://huggingface.co/LayerDiffusion/layerdiffusion-v1
[18] https://huggingface.co/Qwen/Qwen-Image-Edit-2509
[19] https://huggingface.co/black-forest-labs/FLUX.1-Kontext-dev
[20] https://aihub.qualcomm.com/models/controlnet_canny
[22] https://developers.google.com/edge/mediapipe/solutions/vision/image_generator
[23] https://arxiv.org/html/2603.28713v1
[24] https://github.com/ingeniousfrog/DreamLite-20260505
[25] https://arxiv.org/html/2605.28067
[26] https://arxiv.org/abs/2601.08303
[27] https://arxiv.org/abs/2412.09619
[29] https://github.com/qualcomm/ai-hub-models/blob/main/src/qai_hub_models/models/controlnet_canny/README.md
[30] https://huggingface.co/latent-consistency/lcm-lora-sdv1-5
[31] https://huggingface.co/docs/diffusers/main/en/using-diffusers/inference_with_lcm_lora
