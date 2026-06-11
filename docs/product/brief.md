# Robia product brief

Robia is an Android digital wardrobe app for Google Play. It helps users photograph garments, maintain a local wardrobe catalog, attach and manage tags, and browse or edit clothing items from a polished mobile interface.

## MVP scope

- Android app built with Jetpack Compose and Gradle unless architecture work identifies a strong reason to change direction.
- Local-first wardrobe/catalog data for browsing, editing, capture, tags, and filters.
- Multilingual user experience from the start: English, Spanish, and German, with the default language following the system locale.
- User-visible strings must be localized rather than hardcoded.
- GitHub Actions should produce an APK artifact for validation and manual installation.

## UX direction

The tracked UX source lives in `docs/ux/` and should be followed closely. Those assets currently use the placeholder name “Closet Harmony”; product implementation should adapt them to Robia.

Required adaptations:

- Use the app name Robia.
- Remove the left logo/spa icon from the top bar.
- Use a settings icon on the right side that opens language and data-sync options.
- Keep the Earth & Steel visual system, refined wardrobe flows, tag management, filtering, gallery browsing, capture, and item detail patterns as the baseline.

## Constraints

- Do not use OpenHands.
- Use Codex-backed or Hermes worker workflows where available; if Codex CLI is unavailable, document that and continue as the worker.
- Do not run local Android Gradle compile, lint, test, APK, or AAB commands on this host. Android builds must be validated through GitHub Actions only.
- Kanban workers cannot push; local commits must be reviewed and pushed by a human or authorized process.
- Do not create Android scaffolding until architecture and UX handoffs are ready.

## Future / on hold

- Google Drive sync is future work. Design seams and settings entry points are acceptable, but Drive sync must not be implemented in the MVP bootstrap.
- AAB and Google Play release automation are future work. The current target is a CI APK artifact only.
