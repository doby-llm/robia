# Changes to doby-llm/robia-privacy-policy (task POL)

The public policy repo already exists (`https://github.com/doby-llm/robia-privacy-policy`, created 2026-08-10, files
`README.md` = privacy policy, `DELETE_ACCOUNT.md` = data deletion; contact the.gusanito.lab@gmail.com). It stays public
even if `doby-llm/robia` becomes private (R4-DEC-013). Policy URL used by the app and Play Console:
`https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md`.

Apply exactly these edits (one docs PR in that repo; keep the existing tone and headings):

1. README.md — new section after "Information you add to Robia":
   ## Optional on-device models
   To offer smart features (garment suggestions, search, precise background removal) Robia can download machine-learning
   model files from Hugging Face and GitHub after you agree. These downloads only request the model files: no photo,
   wardrobe data or personal information is sent. All analysis runs on your device.
2. README.md — "Information you add to Robia": add "collections" to the list of optional details
   ("…tags, collections, categories, seasons…").
3. README.md — new section before "Children":
   ## Analytics and third-party services
   Robia contains no analytics, advertising or tracking SDKs. It uses Google Play services components on your device
   (Google sign-in and on-device ML Kit features), which are governed by Google's privacy policy.
   (POL must cross-check this sentence with `artifacts/data-safety.md` from P03; if P03 found an SDK that transmits
   data, describe it instead.)
4. DELETE_ACCOUNT.md — correct a factual error: GusanitoLabs has no access to a user's Google Drive app-data folder
   (only the app, with the user's own authorization, can read or delete it), so "we will delete the Robia backup" is not
   possible. Replace the "Request deletion…" and "Data that will be deleted" sections with:
   - In Robia: Settings › Google Drive › "Delete backup from Google Drive" (removes photos, wardrobe data and sync
     metadata from the private app-data area), then "Disconnect".
   - Without the app: Google Drive on the web › Settings › Manage apps › Robia › Options › "Delete hidden app data",
     then remove Robia's access at myaccount.google.com/permissions.
   - Questions: email the.gusanito.lab@gmail.com with subject "Robia data deletion request"; we will guide you, as we
     hold no copy of your data.
   Keep "Data stored on your device" and "Contact"; drop "Retention period" (nothing is retained by us).
5. Update the effective date in both files to the merge date.
