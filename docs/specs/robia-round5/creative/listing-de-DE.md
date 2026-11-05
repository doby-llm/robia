# Play listing — de-DE · CR1

Text inside each ```text block is the exact field value (CR2 copies it to `play-assets/de-DE/{title,short,full}.txt`,
PL3 layout; release notes go to the PL2/PL5 release). Counts = Python `len()` of the stripped text, checked by
`listing_check.py` (see PR). Duzen, same terms as the app (Kleidungsstück, Kleiderschrank, Sammlung, Einstellungen).

## Title (29/30)
```text
Robia: Kleiderschrank digital
```

## Short description (78/80)
```text
Kleidung fotografieren, nach Bedeutung finden. KI auf dem Handy, ohne Werbung.
```

## Full description (1735/4000)
```text
Du hast mehr Kleidung, als du denkst. Robia macht daraus einen Kleiderschrank, den du durchsuchen kannst – direkt auf deinem Handy.

FOTO MACHEN
Mach oder wähle ein Foto von einem Kleidungsstück. Robia entfernt den Hintergrund auf deinem Handy und schlägt Kategorie, Anlässe und Farben vor. Alles lässt sich mit einem Tippen ändern.

VIELE AUF EINMAL
Wähle mehrere Fotos aus deiner Galerie, und Robia verarbeitet sie als Stapel.

NACH BEDEUTUNG SUCHEN
Schreib, was du brauchst, so wie du es sagen würdest, zum Beispiel „etwas Warmes zur Hochzeit“, und Robia sortiert deine Kleidungsstücke danach, wie gut sie passen. Die Suche nutzt ein KI-Modell, das auf deinem Handy läuft. Es wird einmal heruntergeladen (etwa 388 MB).

ALLES IM GRIFF
• Duplikate: Robia zeigt dir mögliche Duplikate, damit du nur eines behältst.
• Tag-Prüfung: Wenn du eine Kategorie oder einen Anlass hinzufügst oder änderst, prüft Robia deine Kleidungsstücke und schlägt Änderungen vor. Nichts ändert sich, bevor du zustimmst.
• Sammlungen: Gruppiere Kleidungsstücke, wie du willst – eine Reise, eine Capsule-Garderobe, eine Geschenkliste. Ein Kleidungsstück kann in mehreren Sammlungen sein.
• Statistik: Sieh, wie sich dein Kleiderschrank auf Kategorien, Farben, Anlässe und Jahreszeiten verteilt.
• Karten zum Teilen: Exportiere ein Kleidungsstück als saubere Karte zum Teilen.

PRIVAT VON ANFANG AN
• Alle KI-Funktionen laufen auf deinem Handy.
• Kein Konto nötig. Keine Werbung. Keine Analyse-Tools. Kein Server des Entwicklers.
• Optionale Sicherung in deinem eigenen Google Drive, in einem privaten App-Ordner.

DIE DETAILS
• Kostenlos.
• Auf Englisch, Spanisch und Deutsch.
• Zwei Farbpaletten: Tabak & Bordeaux (Standard) und Erde & Stahl.
• Für Android.
```

## Offline sentence — append to the search paragraph only if shot F passed (73 characters; full description with it: 1810/4000)
```text
Sobald das Modell geladen ist, funktioniert die Suche sogar im Flugmodus.
```

## Release notes 1.0.0 (487/500)
```text
Erste öffentliche Version von Robia.
• Kleidungsstücke per Foto hinzufügen: Hintergrund wird auf dem Handy entfernt, Kategorie, Anlässe und Farben vorgeschlagen.
• Viele Fotos auf einmal hinzufügen.
• Nach Bedeutung suchen – mit KI auf deinem Handy (einmaliger Download, etwa 388 MB).
• Duplikaterkennung, Tag-Prüfung, Sammlungen, Statistik und Karten zum Teilen.
• Optionale Sicherung im eigenen Google Drive.
• Neue Standard-Farbpalette Tabak & Bordeaux; Erde & Stahl bleibt verfügbar.
```

## Notes
- Palette names follow SPEC §1.2 (DE: "Tabak & Bordeaux", "Erde & Stahl").
- The app uses both "Kleiderschrank" (search hint) and "Garderobe" (title "Deine Garderobe"); the listing and the
  videos use "Kleiderschrank", the more common search term.
- The offline sentence is appended to the end of the NACH BEDEUTUNG SUCHEN paragraph only if shot F passed at CAP.
- "Kein Konto" = no Robia account; the optional Drive backup uses the user's own Google account.
