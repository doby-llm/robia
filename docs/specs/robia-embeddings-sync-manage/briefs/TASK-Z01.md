# TASK-Z01 — Final device validation

## Goal
Prove on Manu's Poco F3, with the latest `main` staging APK, that the user-visible promises hold.

## Steps
1. Write `docs/specs/robia-embeddings-sync-manage/artifacts/device-checklist.md`: for each row V-Z01…V-Z11 in `verification-matrix.md`, numbered steps a non-technical user can follow, the expected result, and a blank "Result:" line. For V-Z01 give the exact adb commands (`adb shell dumpsys gfxinfo com.gusanitolabs.robia reset`, scroll 20 s, `adb shell dumpsys gfxinfo com.gusanitolabs.robia | grep -i janky`).
2. Send Manu the APK link and the checklist; record his answers in the file; open a PR with the filled checklist.
3. Any failed row → open one follow-up task per failure (do not fix inside Z01).
