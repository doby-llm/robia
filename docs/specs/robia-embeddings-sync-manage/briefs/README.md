# Worker briefs

One file per task. Each brief says **why** the task exists (Goal), **what to read**, **how to implement it step by step**, **which tests to add**, and **when it is done**.

Authority order when two documents disagree: `tasks.md` (files, scope, acceptance) > this brief (how) > `technical-specification.md` (design detail). If you find a real contradiction, stop and report it — do not choose.

Shared facts for every task:
- Repo `doby-llm/robia`, base branch `main`. Package root `app/src/main/java/com/gusanitolabs/robia/` (written `…/` below). Tests in `app/src/test/java/com/gusanitolabs/robia/`.
- Builds and tests run only in GitHub Actions (`Android APK CI`). Never run Gradle on the Raspberry Pi.
- Strings: always add to `app/src/main/res/values/strings.xml` (EN), `values-es/`, `values-de/`.
- Planner data on the Pi (read-only for workers, except TASK-E02 reads it): `~/.local/share/robia-planning/` (`model/`, `precision-data/`, `planner-workspace/`).

Order: see `../implementation-plan.md` (waves). A task may start only when all its predecessors are merged in `main`.
