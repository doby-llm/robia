# DU1 independent review (Opus 5.5 high, read-only clone `workers/r6-rev` pinned at the PR head)

Launch: `claude -p --model claude-opus-5-5 --effort high --permission-mode bypassPermissions --disallowedTools Edit Write < prompt.txt`.

```text
You review PR #<N> (task DU1, Robia round 6) read-only. Head <SHA>. Plan: docs/specs/robia-round6/ on main (tasks.md section DU1, decision-log R6-DEC-007/008, artifacts/inv-r6-duplicates.md).
Always allowed, never a finding: the three strings.xml files and new test files.
Walk through these explicitly and name the lines:
1. The automatic scan (LaunchedEffect "REQ-DU-04") and a forced tap interleaved, in both orders, also with the wardrobe changing mid-scan. Can a stale result overwrite a newer one? Can both run at once? Is the lock released on cancel and on failure?
2. Is every runCatching or catch(Throwable/Exception) on a suspend path rethrowing CancellationException?
3. Signature bookkeeping: can the forced scan record a signature that makes the automatic scan skip a wardrobe it never scanned successfully?
4. Dismissals: are stored "Not duplicates" pairs loaded before the forced scan and honoured after it?
5. Navigation: does the user ever land on an empty "Possible duplicates" screen? Leaving Settings mid-scan: is the scan cancelled or kept? Does the result still behave as specified?
6. Main thread: is there any hashing, DB or vector work outside Dispatchers.IO/Default?
7. Each DU0 CERTAIN fix the PR applied: does it change the behaviour of another feature (Browse banner rules, sync attention cards, backfill)?
8. Would each new test fail on the old code (mentally run the old code)? Name any vacuous test.
BLOCKING classes (only these): (a) out-of-order or stale publish; (b) dismissed pair returns; (c) cancellation swallowed or lock left held; (d) unbounded main-thread work; (e) signature skips an unscanned wardrobe; (f) empty review screen; (g) any DB write other than the existing dismissal path; (h) a DU0 fix that changes another feature's behaviour; (i) a file outside DU1's Files.
Everything else goes in DEFERRED. Output: "Verdict: SAFE|NOT SAFE", the BLOCKING list (file:line, scenario, minimal fix, test that would catch it), then DEFERRED. 500 words max.
```

Round 2: give the reviewer the old and new heads, the previous BLOCKING list only, and ask for `git diff --stat <old> <new>`.
Tell it to check only those items, plus new BLOCKING findings in code added since. Previously deferred items: "do not re-raise".
