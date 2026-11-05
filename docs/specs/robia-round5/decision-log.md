# Decision log — robia-round5

Types: M = Manu decided · D = planner default (Manu may overturn) · I = interpretation of an answer.

| ID | Type | Date | Decision |
|---|---|---|---|
| R5-DEC-M01 | M | 2026-11-04 | GusanitoLabs Play developer account is **personal** → per-app closed test, ≥ 12 testers opted in 14 consecutive days, then apply for production. |
| R5-DEC-M02 | M | 2026-11-04 | Palette picker offers Tobacco & Bordeaux · Light/Airy (**default**) and Earth & Steel (current). |
| R5-DEC-M03 | M | 2026-11-04 | No closed test with the old colours: code is closed (G-CODE) before the promo video and the Play preparation start. |
| R5-DEC-M04 | M | 2026-11-04 | Video and screenshots use a demo wardrobe, not Manu's real one. |
| R5-DEC-M05 | M | 2026-11-04 | Videos in EN, ES and DE. Vertical and horizontal are each designed natively for their format; never one cropped from the other. |
| R5-DEC-M06 | M | 2026-11-04 | Uploads and releases via the Google Play Developer API with a service account (not manual uploads), except what the API cannot do. |
| R5-DEC-M07 | M | 2026-11-04 | Models: coordinator Sonnet 5.5 high; workers Haiku 5.5 high; creative lane Opus 5.5 high with brag/Hyperframes. |
| R5-DEC-D01 | D | 2026-11-04 | Lightweight packet (SPEC + tasks + briefs for worker tasks + prompts); no independent review for TH (no user-data writes). |
| R5-DEC-D02 | D | 2026-11-04 | Demo wardrobe lives in a separate `capture` build (`com.gusanitolabs.robia.capture`) so Manu's real app and data are never touched. |
| R5-DEC-D03 | D | 2026-11-04 | Palette fix: light `onTertiaryContainer` #8E4E58 → #FFD9DE (1.61:1 → 7.74:1); light `sparkOnPrimary` follows. |
| R5-DEC-D04 | D | 2026-11-04 | Existing installs switch to Tobacco & Bordeaux on update (absent setting = new default). |
| R5-DEC-D05 | D | 2026-11-04 | Setting name "Colour palette" / "Paleta de colores" / "Farbpalette" (the row "Theme" already means Light/Dark). |
| R5-DEC-D06 | D | 2026-11-04 | First public version name 1.0.0. |
| R5-DEC-D07 | D | 2026-11-04 | The first AAB goes to Internal testing by hand in Play Console (the API requires it; Packly's automated browser upload failed with 55318F15). Everything after uses the API. |
| R5-DEC-D08 | D | 2026-11-04 | Play Console forms are filled by a Hermes browser subagent (Haiku 5.5) launched from Hermes chat, not by the Sonnet coordinator (`claude -p` has no browser). |
| R5-DEC-D09 | D | 2026-11-04 | Music: brag's bundled track (CC BY 4.0, credit in the YouTube description) or a CC0 track; no generated voice-over. |
| R5-DEC-D10 | D | 2026-11-04 | Play listing assets: 6 framed 1080×1920 screenshots per locale, feature graphic per locale, one icon. YouTube promo URL in the listing is optional (Manu's channel). |
