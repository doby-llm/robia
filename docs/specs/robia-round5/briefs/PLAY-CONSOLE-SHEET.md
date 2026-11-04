# Robia: Google Play Console answer sheet

Status: draft for a browser agent (Claude Haiku in Chrome), prepared 2026-11-04. Manu is present for every step.
Scope: Play Console forms only. Not a legal opinion. Labels marked CONFIRM-WITH-MANU were not on a primary Google page and must be read off the live screen.

## Sources

Short tags used in the "Basis" column:

- [DS] `~/.local/share/robia-planning/workers/r5-docs/docs/specs/robia-round4/artifacts/data-safety.md` (round 4, Data safety facts)
- [PR] `~/.local/share/robia-planning/workers/r5-docs/docs/specs/robia-round4/artifacts/inv-r4-play-readiness.md` (round 4, readiness audit)
- [MAN] `~/.local/share/robia-planning/workers/r5-docs/app/src/main/AndroidManifest.xml`
- [POL] https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md (checked live 2026-11-04; local repo is clean and in sync with origin)
- [DEL] https://github.com/doby-llm/robia-privacy-policy/blob/main/DELETE_ACCOUNT.md (checked live)
- Google Play Help (primary, fetched 2026-11-04):
  - [G-CLOSED] https://support.google.com/googleplay/android-developer/answer/14151465 (closed test, Apply for production)
  - [G-DS] https://support.google.com/googleplay/android-developer/answer/10787469 (Data safety)
  - [G-APPC] https://support.google.com/googleplay/android-developer/answer/9859455 (App content page)
  - [G-CREATE] https://support.google.com/googleplay/android-developer/answer/9859152 (create app, store contact)
  - [G-STORE] https://support.google.com/googleplay/android-developer/answer/9866151 (store settings)
  - [G-AUD] https://support.google.com/googleplay/android-developer/answer/9867159 (target audience)
  - [G-IARC] https://support.google.com/googleplay/android-developer/answer/9898843 and /9859655 (content rating)
  - [G-TRACK] https://support.google.com/googleplay/android-developer/answer/9845334 (closed testing track)
  - [G-SIGN] https://support.google.com/googleplay/android-developer/answer/9842756 (app signing key)
  - [G-API] https://developers.google.com/android-publisher/getting_started (service account)

Status key: **CERTAIN** = the label and the answer are both confirmed (primary page, or code and privacy policy). **CONFIRM-WITH-MANU** = the answer is a judgment call, or the label was not on a primary page.

## 0. Rules for the browser agent

1. Manu signs in himself. Never type or paste a password, 2FA code or recovery code. If a sign-in or 2FA screen appears, stop and wait.
2. Stop and ask Manu before clicking any of these: **Create app**, **Invite user**, **Send for review**, **Start rollout** (any track), **Publish**, **Apply** (Apply for production), **Submit** (Data safety and content rating). They publish, claim a name, send an invite, or cannot be undone.
3. Save drafts freely (**Save**, **Save draft**, **Save changes**). After each saved form, take a screenshot and log the form name and time as evidence.
4. If a label, menu path, or field differs from this sheet, stop and report it: send a screenshot and the visible text. Do not guess a label.
5. Type only the values listed here. Use only the contact addresses in section 1. Do not put any other e-mail address in a form.
6. Do not change the package name. Do not upload to production. Closed testing only.
7. Pre-flight: targetSdk is already 36 on main (`app/build.gradle.kts:53`, round 4 P01), so the old B1 is resolved. The release AAB comes from PL2 (round 5). If Play rejects an upload, stop and report the exact message.

## 1. Fixed values

| Field | Value | Basis | Status |
|---|---|---|---|
| App name | Robia | [PR]:226 (`app_name`) | CERTAIN |
| Package name | com.gusanitolabs.robia | [PR] §2, `build.gradle.kts:51` | CERTAIN |
| Developer name | GusanitoLabs | brief | CERTAIN |
| Developer account | Personal, created after 2023-11-13 | [G-CLOSED] | CERTAIN |
| Contact e-mail (store, feedback, IARC, privacy) | the.gusanito.lab@gmail.com | [POL]:41, [DEL]:48 | CERTAIN |
| Privacy policy URL | https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md | [POL] | CERTAIN (see CONFIRM item 6) |
| Data deletion URL | https://github.com/doby-llm/robia-privacy-policy/blob/main/DELETE_ACCOUNT.md | [DEL] | CONFIRM-WITH-MANU (item 8) |
| Category | Lifestyle | brief | CERTAIN |
| Price | Free, no ads, no in-app purchases | brief, [MAN]:4 | CERTAIN |
| Target audience | 18 and over | brief | CERTAIN |
| Version | versionName 0.1.0, versionCode 1 | [PR]:54-55 | CERTAIN |

## 2. Create app (Play Console home > **Create app**)

Basis for all rows: [G-CREATE] (fields and declarations; the page names them but not the exact dropdown option text).

| Field or control | Answer | Status |
|---|---|---|
| App name | Robia | CERTAIN |
| Default language | English (United States), en-US | CONFIRM-WITH-MANU (dropdown text) |
| App or game | App | CONFIRM-WITH-MANU (option text) |
| Free or paid | Free | CONFIRM-WITH-MANU (option text) |
| Contact email | the.gusanito.lab@gmail.com | CERTAIN |
| Declarations (Developer Program Policies, US export laws, Play App Signing Terms of Service) | Manu ticks these himself. They are attestations. | CONFIRM-WITH-MANU |
| Button | **Create app**. STOP and ask Manu first. | CONFIRM-WITH-MANU |

## 3. Store settings (Grow users > Store presence > Store settings)

[G-CREATE] calls the section "Store listings" and [G-STORE] calls it "Store settings". Confirm the menu label on screen.

| Field | Answer | Status |
|---|---|---|
| App category | Lifestyle | CONFIRM-WITH-MANU (field label) |
| Tags | Leave empty unless Manu gives tags | CONFIRM-WITH-MANU |
| Support email address (Contact Details, required) | the.gusanito.lab@gmail.com | CERTAIN (label from [G-CREATE]) |
| Website URL | Leave empty | CONFIRM-WITH-MANU |
| Phone number | Leave empty | CONFIRM-WITH-MANU |
| External marketing checkbox | Leave as it is unless Manu says otherwise | CONFIRM-WITH-MANU ([G-STORE]) |

## 4. App content (Policy > **App content**)

Each card has **Start** (first time) or **Manage** (later). Basis for the page layout: [G-APPC].

### 4.1 Privacy policy

- Click **Start declaration** (or **Manage**). Enter the policy URL from section 1. **Save**.
- Basis: [G-APPC] (label, "Start declaration"); [POL] (URL). CERTAIN.

### 4.2 App access ("Sign-in details")

| Question or field | Answer | Basis | Status |
|---|---|---|---|
| Are all features available without special access (no login)? | Yes. Robia has no account and no login. | [DEL]:5-8; [MAN]:4 (INTERNET only) | CONFIRM-WITH-MANU (answer label) |
| Instructions (**+ Add new instructions**) | "Robia has no account and no sign-in. All features work without signing in. Google Drive backup is optional. It uses the reviewer's own Google account and is not needed to test the app." | [POL]:15, [DEL]:5-8 | CERTAIN (text is a draft; Manu may adapt) |
| Any other instructions | Leave empty | | CERTAIN |

### 4.3 Ads

- Click **Start declaration**. Answer **No** (the label is probably "No, my app does not contain ads").
- Basis: [MAN]:4 (no ad permission); [PR] §6 (no ads SDK); [POL]:21 (no third-party ads). Label: CONFIRM-WITH-MANU.

### 4.4 Content rating (questionnaire)

[G-IARC] and [G-APPC] give the flow but not the option labels. Option labels are CONFIRM-WITH-MANU.

1. **Start** on the App content page. Enter the IARC e-mail: the.gusanito.lab@gmail.com. Click **Next**.
2. Category: "Utility, productivity, communication, or other" (label from memory, CONFIRM).
3. Answer "No" to: violence, sexual content, profanity, controlled substances, gambling, user-to-user communication, sharing the user's location, sharing personal info, digital purchases, and ads. Basis: [MAN]:4 (no location or camera permission); [PR] §1 (no IAP); [PR] §8 (no ads SDK).
4. User-generated content sharing: "No". Robia has no feed and no other users. Export and share are user-initiated, through the Android share sheet. CONFIRM-WITH-MANU.
5. Review the summary. **Submit** only after Manu confirms. STOP.

### 4.5 Target audience and content

- **Start** (or **Manage**). Under **Target age**, select **Ages 18 and over** only. CERTAIN. Basis: [G-AUD] (label); brief (18+).
- Child-appeal question, if shown: "No". Ads: "No" (matches 4.3). CONFIRM-WITH-MANU (labels).
- **Save**.

### 4.6 News apps

- Answer: not a news or magazine app. Basis: brief ("no news"). CONFIRM-WITH-MANU (option label).

### 4.7 Government apps

- Answer: not an app for any government body. Basis: brief. CONFIRM-WITH-MANU (option label).

### 4.8 Financial features

- Choose "My app doesn't provide any financial features" (label from a third-party guide). Basis: brief (no financial features); [PR] §6 (no payments). CONFIRM-WITH-MANU (label).

### 4.9 Health apps

- Choose "My app does not have any health features" (label from a third-party guide). Basis: brief (no health); [PR]:227 (no health claims). CONFIRM-WITH-MANU (label).

### 4.10 Advertising ID

- Location: most likely on the App content page, under Ads or a separate card. CONFIRM-WITH-MANU.
- Answer: "No". The app does not use the Advertising ID. Basis: [MAN]:4 (only INTERNET is declared); [PR]:39-40 (merged manifest has no `com.google.android.gms.permission.AD_ID`); [PR] §6 (no analytics or ads SDK).

### 4.11 Data safety (App content > Data safety > **Start**)

Steps and wording from [G-DS]. Each row gives the answer.

**Step 1, Overview.** Click **Next**.

**Step 2, "Data collection and security".** Read the list of required user data types, then:

| Question (wording from [G-DS]) | Answer | Basis | Status |
|---|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | [G-DS] (collect = transmitting data off the device). The Drive backup sends photos off the device ([DS]:65; [POL]:15). | CERTAIN |
| Whether or not all of the user data collected by your app is encrypted in transit | **Yes** | [DS]:69; [MAN]:4 (no cleartext, no `usesCleartextTraffic`); [PR]:36 | CERTAIN |
| Whether or not you provide a way for users to request that their data is deleted | **Yes** | In-app disconnect and delete backup ([DS]:70; [PR] §5, `RobiaApp.kt:495`, `:521`); [DEL] | CERTAIN (answer). Deletion URL: CONFIRM-WITH-MANU (item 8) |

Click **Next**.

**Step 3, "Data types".** Select exactly these two. Leave every other type unselected.

- Photos and videos > **Photos** ([DS]:65)
- App activity > **Other user-generated content** (garment names, notes, tags, colours, categories, seasons, occasions, fit preferences; [DS]:66, [POL]:9). This is the closest official type. CONFIRM-WITH-MANU (item 3).

Click **Next**.

**Step 4, "Data usage and handling".** Click **Start** next to each type.

| Question | Photos | Other user-generated content | Basis | Status |
|---|---|---|---|---|
| Collected | Yes | Yes | [G-DS] | CERTAIN |
| Shared | **No** | **No** | [G-DS] exception for user-initiated transfers; the Drive backup goes only to the user's own Drive appdata ([POL]:21, [DS]:73). See item 2. | CONFIRM-WITH-MANU |
| Is this data processed ephemerally? | No (kept in the Drive backup until deleted) | No | [POL]:25 | CERTAIN |
| Required or optional | **Optional** (user must connect Drive and turn on backup; off by default) | Optional | [MAN]:6-7 ("explicit opt-in"); [POL]:15 | CERTAIN |
| Purpose | **App functionality** | App functionality | [DS]:65 | CERTAIN (label) |

Click **Next**.

**Step 5, Review.** Check the "Store listing preview". Click **Save as draft**. Click **Submit** only after Manu confirms. STOP.

**Data types to leave unselected** (not collected by the app's own code):

| Type | Reason | Status |
|---|---|---|
| Location (any) | No location permission ([MAN]:4; [PR] §1). "Locations" in [POL]:9 means the garment storage location, not GPS. | CONFIRM-WITH-MANU (item 7) |
| Personal info, including name and e-mail | The Drive e-mail stays on the device ([DS]:67). See item 1. | CONFIRM-WITH-MANU (item 1) |
| Financial info, health and fitness, messages, contacts, calendar, audio, web browsing | Not requested ([DS]:72) | CERTAIN |
| Files and docs | The zip export is user-initiated and saved where the user picks ([PR] §1, `DriveSettingsScreen.kt:89`). Not collected. | CERTAIN |
| App info and performance (crash logs, diagnostics) | No crash SDK ([PR] §6). ML Kit, ORT and Play services telemetry not yet verified. | CONFIRM-WITH-MANU (item 4) |
| Device or other IDs | No `AD_ID` ([MAN]:4; [PR]:39-40). ML Kit and transport telemetry not verified. | CONFIRM-WITH-MANU (item 4) |

**Not declared, with reasons** (from [DS] §5):

- Model downloads from huggingface.co and github.com: no user data from the app ([DS]:68). Not declared.
- Google account e-mail: see item 1.

## 5. Countries and regions

- Route: the track's **Countries/regions** setting, or the production page. [G-TRACK] refers to a separate guide and does not show the label.
- Answer: all countries and regions (the default). CONFIRM-WITH-MANU.

## 6. Closed testing track (Test and release > Testing > **Closed testing**)

Basis: [G-TRACK].

1. Open the existing closed track, or click **Create track**. Track name: "Closed testing" (suggestion). CONFIRM-WITH-MANU.
2. Click **Manage track**, then the **Testers** tab.
3. Under **Testers**, click **Create email list**. List name: "Robia testers". Add addresses separated by commas, or click **Upload CSV file**. Click **Save changes**, then **Create**. Addresses come from Manu (section 7, item 16).
4. Under **Testers**, select **Email** and tick the list.
5. Feedback field: enter the.gusanito.lab@gmail.com. The field label is not on the primary page. CONFIRM-WITH-MANU.
6. Copy the shareable link. Click **Save changes**.
7. **Create new release**: upload the signed AAB (from the CI `release-aab` job, [PR]:95). Click **Save**. STOP before **Start rollout**; ask Manu.

Notes from [G-TRACK]: the opt-in link appears only once the app status is "Published". Google Groups members must join the group before they opt in.

## 7. Apply for production (Dashboard > **Apply for production**)

Basis: [G-CLOSED]. The button stays disabled until the criteria are met.

Requirements:

- At least **12 testers** opted in **continuously for 14 days** ([G-CLOSED]).
- A tester who opts out and later opts back in needs a new 14-day period ([G-CLOSED]).
- Earliest apply date: 14 days after the day the 12th tester was opted in and stayed opted in. Compute it and write it down.

The application has three sections, in this order ([G-CLOSED]): **About your closed test**, **About your app/game**, **About your production readiness**. Draft answers (Manu adapts them; the placeholders must be filled with real facts, not invented):

**A. About your closed test**

- How testers were recruited: "[FILL: number] adults (18+) invited by e-mail list starting [FILL: date]. Each opted in and stayed opted in for 14 days or more."
- Feedback received and changes made: "[FILL: the real feedback items and the fixes made. If none, say so.]"
- Test period: "[FILL: start date] to [FILL: end date]."

**B. About your app/game**

- What it does: "Robia is a free digital wardrobe app. Users add photos of garments, cut out the item, and organise their wardrobe. Photo cutout and local AI search run on the device. There are no ads, no in-app purchases and no sign-in."
- Audience: "Adults 18 and over. Languages: English, Spanish, German."
- Data: "Optional Google Drive backup to the user's own Google Drive app data folder (drive.appdata). Privacy policy: https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md"

**C. About your production readiness**

- Crashes and ANRs: "[FILL: crash rate and ANR rate from Android vitals, or 'no data yet'.]"
- Release process: "Signed AAB built in CI, versioned. [FILL: confirm whether the R8 mapping is uploaded.]"
- Support: "the.gusanito.lab@gmail.com"

Character limit per answer: third-party guides say about 300 characters. Not confirmed (CONFIRM-WITH-MANU). **Apply**: STOP and ask Manu.

## 8. Signing key SHA-1 (for Google Drive sign-in)

- Path: in Play Console, open the app, then "Protected with Play" > "Play Store protection" > "Manage Play app signing" ([G-SIGN]). Scroll to **App signing key** and copy the **SHA-1** certificate fingerprint. Another route seen in third-party guides is "Setup" > "App integrity". CONFIRM-WITH-MANU (menu label).
- Use the **App signing key** fingerprint, not the upload key ([G-SIGN]; [PR] B4).
- Use it in Google Cloud Console, not Play Console: create an Android OAuth client for package com.gusanitolabs.robia with that SHA-1 ([PR]:17; `docs/google_drive_sync_setup_plan.md:52`). Stop before creating the client and ask Manu.
- This only exists after Play App Signing is enabled, which happens on the first AAB upload. CONFIRM-WITH-MANU.

## 9. Service account for the Play Developer API (Users and permissions)

Basis: [G-API] (Cloud steps and "Invite new users"). The App permissions tab and the permission labels are from third-party guides. CONFIRM-WITH-MANU.

Cloud steps (Manu does these, or confirms them): enable the Google Play Android Developer API, create the service account, copy its e-mail. `<SA_EMAIL>` is supplied by Manu. The browser agent does not create it.

Play Console steps:

1. Users and permissions > **Invite new users**.
2. Email address: `<SA_EMAIL>`.
3. **App permissions** tab > **Add app** > Robia > **Apply**.
4. Tick only these permissions (labels CONFIRM-WITH-MANU):
   - View app information (read-only): **yes**
   - Manage store presence: **yes**
   - Release to testing tracks: **yes**
   - Manage testing track releases: **yes**
   - Release to production: **no, do not tick**
   - Exclude devices, financial data, orders and subscriptions, manage orders: **no**
   - Account-level permissions, including Admin: **no**
5. **Invite user**: STOP and ask Manu.

## 10. CONFIRM-WITH-MANU list

1. **Google account e-mail read from Drive.** Read from `drive/v3/about` ([DS]:67; [PR]:195; `GoogleDriveWardrobeRepository.kt:1863`) and kept on the device (`SettingsRepository.kt:262-267`). The app does not send it to the developer or any third party. Recommendation: do not declare it, since data that only comes from Google and stays on the device is not "collected" under [G-DS]. Manu decides.
2. **Photos "Shared: No".** The transfer goes to the user's own Drive appdata, so the user-initiated exception in [G-DS] should apply. Manu confirms that a Google Drive backup is not "sharing". The conservative alternative is "Shared: Yes" (see [DS]:73).
3. **Garment metadata category.** Chosen: App activity > Other user-generated content. Alternative: Personal info > Other info. Manu picks the official type.
4. **Telemetry in the APK.** Check the ONNX Runtime `TelemetryInitializer`, the ML Kit and transport CCT backend, and Play services before answering "No" to App info and performance and to Device or other IDs ([PR] §4, §6; [PR] S8, [DS] §6). Until checked, keep those two unselected and mark the answer as to verify.
5. **Privacy policy vs Data safety.** [POL]:21 says "We do not share your wardrobe data with third parties other than the optional storage of the backup in the Google Drive account". That is close to a sharing statement. Align the policy wording with the "Shared: No" answer, or change the answer.
6. **Privacy policy date.** The local README reads "Effective date: 10/08/2026". A summary of the live page read "01/11/2026". The local repo is clean and in sync with origin. Check the live page and use an unambiguous date (YYYY-MM-DD).
7. **"Locations" in [POL]:9.** Confirm it means the garment storage location, not device location. Location stays unselected.
8. **Deletion URL.** [DEL] asks users to e-mail a request, with processing within 30 days ([DEL]:42). [POL]:17 says "when that feature is available". Decide whether the Data safety deletion answer needs the deletion URL. Confirm the exact option label on screen.
9. **Create app declarations.** Manu ticks these himself. Manu confirms the default language and the app name "Robia".
10. **Store settings.** App category label, tags (empty?), website and phone (empty?), external marketing (default?).
11. **Content rating.** Category label, each answer label, and the IARC e-mail.
12. **Ads and Advertising ID.** Answer labels and the location of the Advertising ID question.
13. **News, government, financial and health option labels.** Labels come from third-party guides.
14. **Countries and regions.** Default (all) or a restricted list.
15. **Closed track.** Track name, feedback field label, and whether the Countries/regions setting is per track.
16. **Tester list.** Manu supplies at least 12 Gmail addresses (or Google Groups). A buffer of 15–20 is third-party advice. Any tester who opts out restarts their 14-day count ([G-CLOSED]).
17. **Production application answers.** Real feedback, real crash and ANR figures, and the character limit (about 300, unverified).
18. **Users and permissions.** The App permissions tab, the exact permission labels, and whether an uploaded APK is needed first (third-party guide).
19. **Signing key SHA-1.** Menu path (two variants seen). The OAuth client is created in Google Cloud Console, not Play Console.
20. **targetSdk 35.** [PR] B1 says Play requires API 36 for updates from 2026-08-31 ([PR]:14). That date is from the round 4 audit and was not re-verified here. If the upload is rejected, stop. The fix is in `app/build.gradle.kts:53` and is out of scope for this sheet.
21. **Release AAB.** Not built or verified ([PR] B3). The closed test upload needs it, and Play App Signing enrolment happens on that first upload.
22. **Create app is hard to undo.** Manu confirms the name "Robia" and the developer account before the button is clicked.
