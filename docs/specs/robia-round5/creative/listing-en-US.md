# Play listing — en-US · CR1

Text inside each ```text block is the exact field value (CR2 copies it to `play-assets/en-US/{title,short,full}.txt`,
PL3 layout; release notes go to the PL2/PL5 release). Counts = Python `len()` of the stripped text, checked by
`listing_check.py` (see PR). Facts only from `briefs/TASK-CR.md` §Facts; no ranking words, no numbers except the real
model size.

## Title (25/30)
```text
Robia: Wardrobe Organizer
```

## Short description (74/80)
```text
Snap your clothes, find them by meaning. On-device AI. No account, no ads.
```

## Full description (1476/4000)
```text
You own more clothes than you remember. Robia turns them into a wardrobe you can search, right on your phone.

SNAP A PHOTO
Take or pick a photo of a garment. Robia removes the background on your phone and suggests a category, occasions and colors. Change anything with a tap.

ADD MANY AT ONCE
Pick several photos from your gallery and Robia processes them as a batch.

SEARCH BY MEANING
Type what you need the way you would say it, like "something warm for a wedding", and Robia sorts your garments by how well they match. The search uses an AI model that runs on your phone. It is downloaded once (about 388 MB).

KEEP IT TIDY
• Duplicates: Robia points out possible duplicates so you can keep just one.
• Tag review: when you add or change a category or occasion, Robia checks your garments and suggests updates. Nothing changes until you accept.
• Collections: group garments any way you like, such as a trip, a capsule wardrobe or a gift list. A garment can be in several collections.
• Statistics: see how your wardrobe is spread across categories, colors, occasions and seasons.
• Share cards: export a garment as a clean card to share.

PRIVATE BY DESIGN
• All AI features run on your phone.
• No account to create. No ads. No analytics. No developer server.
• Optional backup to your own Google Drive, in a private app folder.

THE DETAILS
• Free.
• In English, Spanish and German.
• Two color palettes: Tobacco & Bordeaux (default) and Earth & Steel.
• For Android.
```

## Offline sentence — append to the search paragraph only if shot F passed (65 characters; full description with it: 1543/4000)
```text
Once the model is downloaded, search works even in airplane mode.
```

## Release notes 1.0.0 (470/500)
```text
First public release of Robia.
• Add garments from photos: the background is removed on your phone; category, occasions and colors are suggested.
• Add many photos at once.
• Search by meaning with an AI model that runs on your phone (one-time download, about 388 MB).
• Duplicate detection, tag review, collections, statistics and share cards.
• Optional backup to your own Google Drive.
• New default color palette Tobacco & Bordeaux; Earth & Steel is still available.
```

## Notes
- Title: "Outfits" (the brief's example) is not used because Robia has no outfit feature.
- The offline sentence is a separate block on purpose: append it to the end of the SEARCH BY MEANING paragraph only
  if shot F passed at CAP (`shotlist.md` §F). Otherwise the listing ships without it.
- "No account" means no Robia account; the optional Drive backup uses the user's own Google account.
