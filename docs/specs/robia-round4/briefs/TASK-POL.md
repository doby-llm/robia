# TASK-POL — Update the public privacy-policy repo

## Goal
The app's privacy-policy link (P02) points to `https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md`,
a separate PUBLIC repo so it stays reachable if `doby-llm/robia` becomes private (R4-DEC-013). Its August text misses
the runtime model downloads and collections, and `DELETE_ACCOUNT.md` promises that GusanitoLabs deletes the user's
Drive backup — impossible, since only the app (with the user's authorization) can access the Drive app-data folder.

## Read first
`artifacts/privacy-policy-changes.md` (the closed edit list); `artifacts/data-safety.md` (from P03); the two files of
`doby-llm/robia-privacy-policy`.

## Steps
1. Fresh clone `https://github.com/doby-llm/robia-privacy-policy.git` under `~/.local/share/robia-planning/workers/r4-pol`
   (git user doby-llm), branch `docs/robia-round4`.
2. Apply edits 1–5 of `privacy-policy-changes.md` exactly; for edit 3 use the facts in `artifacts/data-safety.md`.
3. Push, open a PR in that repo titled "Robia round 4: model downloads, collections, accurate deletion steps".
   The repo has no CI. Receipt = PR comment listing each edit 1–5 with the changed lines.

## Done when
V-POL-1 (coordinator diff check), then the coordinator merges.

## Do not
Touch other files, enable GitHub Pages, or change the contact address.
