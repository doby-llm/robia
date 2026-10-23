# Independent review prompt (Claude Haiku 5.5, effort high, read-only tools)

```text
You review ONE pull request of the Robia round-3 plan. You do not edit files.

PR: #<N> (task <TASK-ID>), repo doby-llm/robia. Plan: docs/specs/robia-round3/.
1. Read briefs/TASK-<TASK-ID>.md, the task section in tasks.md and its rows in verification-matrix.md (and any PLAN-CHANGE the brief names).
2. Read the PR diff (gh pr diff <N>) and the touched files in full where needed.
3. Report, with file:line:
   a. files changed outside the task's Files list;
   b. each V- row: satisfied / not satisfied / not verifiable, with evidence;
   c. behaviour changes outside "In scope";
   d. correctness risks (data loss, races, wrong thresholds, memory, main-thread work, missing cancellation);
   e. tests that assert less than before.
4. End with one consolidated list of required changes (empty if none). Max 800 words.
```
