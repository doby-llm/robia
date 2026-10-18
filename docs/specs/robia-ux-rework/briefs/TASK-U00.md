# TASK-U00 — Move-only split of `ui/RobiaApp.kt`

## Goal
`ui/RobiaApp.kt` has 5569 lines and every UI task of this round edits it, which would force all of them to run one after another. Split it by MOVING whole declarations into five new files of the same package, with zero behaviour change, so later tasks own disjoint files.

## Read first
- TS§0 (table of which declaration goes where).
- `ui/RobiaApp.kt` top-level declaration list: `grep -n "^\(private \|internal \)\?\(fun\|data class\|class\|enum class\|object\|val\|const val\|sealed\)\|^@Composable" app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt`.
- Tests reading it: `grep -rn "RobiaApp.kt" app/src/test`.

## Steps
1. Write `tools/split-check/declarations.py` FIRST: given a git ref, read every `app/src/main/java/com/gusanitolabs/robia/ui/*.kt` at that ref (`git show ref:path`), split into top-level declarations (a declaration starts at column 0 with optional annotations/visibility and ends before the next column-0 declaration or EOF), normalise by removing a leading `private `/`internal ` and trailing whitespace, and print a sorted list of `(name, sha1(normalised text))`. Mode `compare A B` prints `EQUAL` when the multisets match, allowing extra identical copies of private helpers (printed as `DUP-OK <name>`), otherwise the differing names. Imports and `package` lines are ignored.
2. Run it on the base (`origin/main`) to save the list.
3. For each row of the TS§0 table, cut the declaration (including its KDoc and annotations) from RobiaApp.kt and paste it into the new file. Move private helpers that are used ONLY by moved code together with it (grep each helper name across `ui/`). Constants used by both stay in RobiaApp.kt and become `internal`.
4. Fix visibility: if a moved declaration is called from another file, change `private` → `internal`. Exception: a small private helper needed by two files whose `internal` version would clash with a private function of the same signature elsewhere in `ui/` (e.g. `String.toComposeColor()`): put a byte-identical private copy in the destination file. Nothing else.
5. Fix imports in every touched file (unused imports may stay; do not reformat).
6. Tests: follow TS§0 "Tests" exactly (anchor in the new file; drop `private ` from anchors of declarations that became `internal`; fix end markers). The known anchors are listed there. For whole-app `app.contains(...)` checks add to each of the three test files a helper
   ```kotlin
   private fun uiSource(): String = Path.of("app/src/main/java/com/gusanitolabs/robia/ui").let { dir ->
       val d = if (Files.isDirectory(dir)) dir else Path.of("..").resolve(dir)
       Files.list(d).use { s -> s.filter { it.toString().endsWith(".kt") }.sorted().map { Files.readString(it) }.toList() }.joinToString("\n")
   }
   ```
   and replace `source("…/ui/RobiaApp.kt")` with `uiSource()` in every test whose anchors now live in a moved file. Order assertions (`indexOf(a) < indexOf(b)`) whose two anchors end up in different files: read the single file that contains both, or assert on the one function that holds both; never delete an assertion.
7. Run `python3 tools/split-check/declarations.py compare origin/main HEAD` → must print `EQUAL`.
8. Push, CI green.

## Tests
No new behaviour tests. Existing tests must stay green with no assertion removed.

## Done when
V-U00-1 (script prints EQUAL, pasted in the receipt) and V-U00-2 (CI green).

## Do not
Rename, reformat, reorder statements inside a declaration, change signatures (other than visibility), touch `RobiaShell`/`RobiaNavHost`/cloud dialogs/restore overlay, edit any non-`ui/` source file, or delete any assertion.
