# PUB1: Release 1.0.1 to Google Play

Preconditions: BI1, BS1, DM1, REL2 and DU1 are merged, and the CI run on main for the last merge SHA is green.

## 1. Build and verify the release AAB (coordinator, host)
1. Run `gh workflow run android-apk.yml -R doby-llm/robia --ref main -f release=true`. Watch the run in a background
   job with notify and timeout >= 1800 (never in the foreground).
2. Download it with `gh run download <run> -n robia-release-aab -D ~/.local/share/robia-planning/r6-exec/aab-<sha7>/`
   (a new, empty directory).
3. Check:
   - `release-metadata.txt` shows the main SHA, `versionName=1.0.1` and a `versionCode` > 992.
   - `sha256sum app-release.aab` equals the `.sha256` file.
   - `unzip -tq` passes.
   - The manifest still has no `AD_ID` and no ads library (search the bytes, UTF-8 and UTF-16). This keeps the
     Advertising ID answer "No" true.
4. Record the run id, versionCode, sha256 and size in the tracking issue.

## 2. Upload to Play Console (needs the Hermes browser, `browser_exec local=True`)
If the coordinator has no browser tool, it stops here. It writes a HANDOFF comment in the issue with the AAB path,
sha256, versionCode and the release notes below, then tells Manu. Hermes does steps 2a and 2b.
Recipe and selectors: Hermes skill `authenticated-browser-operations`, `references/play-console-youtube-cdp.md`.
- **Account check first.** The account chooser must show GusanitoLabs. The app must be **Robia**, app id
  `4974857252493318652`, package `com.gusanitolabs.robia`. **Never touch Packly** (`4974234077303148494`, production).
- **2a. Internal testing:** Create new release, upload the AAB, wait for the row `<versionCode> (1.0.1)`, paste the
  notes below (counter `3 of 3 languages`), then Next, read the warnings (missing native debug symbols does not
  block), then Save and publish and confirm. Verify that the track page shows `Available to internal testers` with the new versionCode.
- **2b. Closed testing (Alpha):** Create new release, Add from library (the same AAB, no second upload), add the notes,
  then Next and Save. In the Publishing overview, read the whole change list. It may contain only the Alpha release
  (plus App content items, if Play lists them). Any Production or full-rollout line means stop. Then Send changes for review.
  If Alpha is still in review with an earlier release, still submit. If Play refuses, stop and report the exact
  message: Internal testing already delivers the update.
- Never: Production, the trader declaration, accepting new terms, or messaging testers.

## 3. Release notes (each under 500 characters; no mention of developer mode, R6-DEC-009)
```
<en-US>
• Adding many photos at once is faster and now shows its progress.
• Garments in a batch show which details Robia suggested, like when adding one garment.
• New in Settings › Smart features: Check for duplicates.
• Fixes and improvements.
</en-US>
<es-ES>
• Añadir muchas fotos a la vez es más rápido y ahora muestra el progreso.
• En el lote, cada prenda muestra qué datos sugirió Robia, igual que al añadir una sola.
• Nuevo en Ajustes › Funciones inteligentes: Buscar duplicados.
• Correcciones y mejoras.
</es-ES>
<de-DE>
• Viele Fotos auf einmal hinzufügen geht schneller und zeigt jetzt den Fortschritt.
• Im Stapel zeigt jedes Kleidungsstück, welche Angaben Robia vorgeschlagen hat – wie beim Hinzufügen eines einzelnen.
• Neu in Einstellungen › Intelligente Funktionen: Nach Duplikaten suchen.
• Fehlerbehebungen und Verbesserungen.
</de-DE>
```
If DU1 changes the row label, update the three notes to the final label in `values*/strings.xml` before uploading.

## 4. Close
- Update the issue to show PUB1 done (versionCode, Internal = available, Alpha = in review).
- Send Manu, in Spanish: the update is out in Internal testing, it is in review in Alpha, and the 4 device checks at
  the end of `tasks.md` (silence = OK).
