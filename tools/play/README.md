# tools/play: Google Play Developer API CLI

Reads track status, uploads the signed AAB with release notes, and pushes the EN/ES/DE store listing for `com.gusanitolabs.robia`.

- Setup: Python 3.10+ and uv. `cd tools/play && uv sync`. Put the service-account key at the default path in `robia_play.py` (or set `ROBIA_PLAY_KEY`), mode 600.
- Dry run: every command that changes Play data is a dry run unless you pass `--commit`. A dry run deletes its edit and commits nothing.
- Production guard: a `production` track (including `wear:production`) needs `--production` and `ROBIA_PLAY_ALLOW_PRODUCTION=1`, or the command exits 3.
- Commands: `status`, `upload-bundle --aab ... --track ... --notes-dir ...`, `listing --dir ...`, `release-notes --dir ...` (offline).
- Asset layout and checks: `docs/specs/robia-round5/briefs/TASK-PL3.md` step 5.
- Exit codes: 0 ok; 1 usage or key; 2 local check failed; 3 production guard; 4 Google API or network; 5 `edits.validate` errors.
- Tests, no network: `uv run pytest -q`.
