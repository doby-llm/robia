# TASK-VIS — Browse/detail small visuals: white garments, dev-only sync row, 4-column pill, sign-in badges

## Goal
R3-02, R3-03 (answer 5A), R3-09 (answer 4A), R3-15. See mockups/browse-r3.html.

## Read first
`mockups/browse-r3.html`; `artifacts/inv-r3-a.md` §A2, §A5, §A6; `artifacts/inv-r3-b.md` §B1; `ui/GarmentSwatchBackground.kt`; `ui/BrowseCardText.kt` l.63-82; `ui/BrowseGrid.kt` l.556-561, 673-734; `ui/RobiaApp.kt` `resolveForConnection` l.3195-3221, attention cards l.926-929; `ui/ItemDetailScreen.kt` l.305.

## Files
`ui/GarmentSwatchBackground.kt`; `ui/BrowseGrid.kt`; `ui/BrowseCardText.kt`; `ui/ItemDetailScreen.kt`; `ui/RobiaApp.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/GarmentSwatchBackgroundTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BrowseCardTextTest.kt`; `app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Swatch: `isNearWhite(label, hex)` + neutral stops.
2. Detail: wrap `GarmentCloudStatusRow` in `if (showSyncDetails)`.
3. Pill: pass `columns` to `GarmentCloudStatusBadge`; ≥ 4 → icon only, `contentDescription` = label.
4. Sign-in: Browse knows whether the SignInRequired card is shown; when it is, Waiting/AuthBlocked badges are suppressed; `resolveForConnection` keeps mapping but the badge layer decides.

## Tests
V-VIS-1, V-VIS-2; contract test anchors updated only where these blocks moved.

## Done when
V-VIS-1, V-VIS-2, V-VIS-3, V-VIS-D pass; CI green.

## Do not
Touch theme tokens or the header indicator.

## PLAN-CHANGE 2026-10-23 (feedback item 1): Drive not added, no Waiting badge
Source: feedback/2026-10-23-manu-telegram.md item 1. Evidence: artifacts/inv-r3-drive-badge.md (premise matches; this case is not covered by steps 1-4).

5. Badge rule: a pure function next to `cardBadgeFor` in ui/BrowseCardText.kt. When the Drive setting is NotConfigured or Disabled, Dirty, Queued and FailedRetryable give no badge. Add one boolean `driveEnabled` to `UiWardrobeItem`, set it in `toUiWardrobeItems` next to `waitingForConnection`, and read it where ui/BrowseGrid.kt calls `cardBadgeFor`. Keep `resolveForConnection` unchanged (GarmentConnectionStateTest pins it).
6. Tests: BrowseCardTextTest table. NotConfigured and Disabled x {Dirty, Queued, FailedRetryable} -> no badge. Connected x Dirty -> Waiting. Disconnected x Queued -> sign-in (unchanged). Running -> Uploading. Photo attention unchanged. Leave `badgeForMatchesTheStatusTable` as it is.
7. Do NOT change the signed-out (Disconnected) "Sign in" badges in this task. Open question for Manu.
