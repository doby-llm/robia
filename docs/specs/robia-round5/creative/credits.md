# Credits and licences · CR1

Checked 2026-11-05 against primary sources (pages fetched, or files read via the GitHub API). "Verified" = read on
the source; anything not read there is marked **unverified**.

## Music
| | |
|---|---|
| Track | brag bundled `skills/brag/assets/music/happy-beats-business-moves-vol-1-by-ende-dot-app.mp3` (2:44, cue preset 120.19 BPM), used from 3.02 s for 24–25 s |
| Author / source | Sascha Ende, ende.app, series "Happy Beats / Business Moves" (Vol. 1) |
| Licence | **CC BY 4.0**, author's "attribution optional" addition |
| Credit line we use (YouTube description, `share-copy-<locale>.txt`) | `Music: "Happy Beats / Business Moves Vol. 1" by Sascha Ende – ende.app (CC BY 4.0)` |
| Rules that matter | Commercial and promotional use allowed; **never register the track in YouTube Content ID** (or similar); if a false claim appears, dispute it and cite the licence. |

Evidence:
- brag `skills/brag/assets/music/README.md` (repo `latent-spaces/brag`, read via GitHub API): "licensed under CC BY 4.0
  … They are not covered by the repo's MIT license. Credit: "Music: Sascha Ende, ende.app (CC BY 4.0)"." brag
  `README.md` §Credits lists the same.
- https://ende.app/en/standard-license — "Standard license: Attribution 4.0 International (CC BY 4.0)"; "You do NOT have
  to credit me."; "If you have room: credit me ("Music by Sascha Ende at ende.app")"; "Content ID: Never register my
  music with YouTube Content ID or similar systems."
- https://ende.app/en/faq — "All tracks here are licensed under the Creative Commons CC BY 4.0 License"; credit format
  example `Music: "<Title>" by Sascha Ende` / `Website: ende.app`.
- **Unverified:** the exact track title as written on its ende.app page (the track page was not opened; the title above
  is brag's wording) and whether the track is labelled "By Sascha" or "By Sascha and Marvin". CR2: open the track page
  once and copy the title exactly into the credit line.
- CC0 alternative: not needed (attribution is already optional and we credit anyway).

## Sound effects
| | |
|---|---|
| Files | brag `skills/brag/assets/sfx/{ui,interface,impact}/…` (see the storyboards for the exact files) |
| Author / source | Kenney (kenney.nl) — brag `sfx-analysis.md`: "Source: Kenney" |
| Packs | Interface Sounds, UI Audio, Impact Sounds (Casino Audio is also bundled but unused) |
| Licence | **CC0** (public domain); attribution not required |
| Credit line (optional, we add it) | `Sound effects: Kenney (kenney.nl), CC0` |

Evidence: https://kenney.nl/assets/interface-sounds, https://kenney.nl/assets/ui-audio,
https://kenney.nl/assets/impact-sounds, https://kenney.nl/assets/casino-audio — each shows "License: Creative Commons
CC0"; https://kenney.nl/support — "all game assets on the asset pages are public domain licensed (CC0)", "Attribution is
not required, but if you choose to give credit you can do so by mentioning "Kenney"", "Do not use our logo".
**Unverified:** the folder → pack mapping (brag only says "Source: Kenney"; mapping inferred from file names) and the
licence file inside each pack download (not downloaded). brag's `keyboard/` folder has no identified pack, so the
storyboards do not use it.

## Font
| | |
|---|---|
| Font | Inter (Regular, SemiBold, Bold), v4.1 — https://github.com/rsms/inter |
| Licence | **SIL Open Font License 1.1** — "Copyright (c) 2016 The Inter Project Authors" |
| Credit | none required for rendered video/images; keep the OFL text with any copy of the font files we ship |

Evidence: `rsms/inter` `LICENSE.txt` and repo licence `OFL-1.1` (read via GitHub API); latest release `v4.1`. The app
already bundles `res/font/inter_{regular,semibold,bold}.ttf`. "No credit for rendered output" follows from OFL 1.1
covering the font software, not documents made with it (OFL text; the OFL FAQ page was not fetched).

## "Get it on Google Play" badge
| | |
|---|---|
| Artwork | Official badge from Google's Partner Marketing Hub, folder "Get it on Google Play Badges › Digital › png" — localized files exist, e.g. `GetItOnGooglePlay_Badge_Web_color_English.png`, `…_Spanish.png`, `…_German.png` |
| Rules (verified) | min height 28 px (digital); clear space = ¼ badge height; solid/simple contrasting background; don't change colour, elements or scale of parts; use the localized badge matching the campaign language; not the most prominent element; no implied endorsement by Google; same size or larger than other store badges. Approval needed only for TV / out-of-home / > 1 M impressions. |
| Notice (required with any Google brand feature) | EN `Google Play and the Google Play logo are trademarks of Google LLC.` · ES `Google Play y el logotipo de Google Play son marcas de Google LLC.` · DE `Google Play und das Google Play-Logo sind Marken von Google LLC.` (wording is ours; Google requires a notice but gives no sample text) |

Evidence: https://play.google.com/intl/en_us/badges/ → 302 →
https://partnermarketinghub.withgoogle.com/brands/google-play/google-play/lockups-icons-badges/#badges (size, clear
space, colour and localization rules), `?folder=65627`, `?folder=86641`, `?folder=86642` (png list),
https://partnermarketinghub.withgoogle.com/support/faq/ (approval thresholds),
https://partnermarketinghub.withgoogle.com/brands/google/trademarks-and-terms/terms-and-conditions/ ("Any use of the
Google Brand Features must be accompanied by a notice" … "trademarks or distinctive brand features of Google LLC"),
https://partnermarketinghub.withgoogle.com/brands/google/trademarks-and-terms/trademark-guidelines-for-proper-usage/.
**Unverified:** the Spanish and German file names come from a summary of the long png list (English was quoted
directly); the wording printed on those badges was not seen; the download links are signed (24 h) and the Hub may ask
for a partner application — if CR2 cannot download the artwork, it stops and asks the coordinator (no hand-made badge).
Animation: no rule mentions video; the storyboards only fade the whole unaltered badge in.

## Other material in the videos and screenshots
- App UI and icon: Robia (own work).
- Demo garment photos: per `~/.local/share/robia-planning/r5/captures/demo-sources.md` (CAP; CC0/Unsplash/Pexels or
  Manu's own photos) — not credited on screen; listed there with URLs.
- Tooling: brag skill (MIT, `latent-spaces/brag`), Hyperframes — no credit required in the output.

## Credit block for YouTube description / share copy
```
Music: "Happy Beats / Business Moves Vol. 1" by Sascha Ende – ende.app (CC BY 4.0)
Sound effects: Kenney (kenney.nl), CC0
Font: Inter (SIL Open Font License 1.1)
Google Play and the Google Play logo are trademarks of Google LLC.
```
