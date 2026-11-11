# Continue Eden Comms

**Canonical source:** `main` of `PlayerEden/Eden-Comms`. Read this page before
coding in a new chat; use live GitHub commit and CI state, not the last chat's
claims or a local temporary workspace.

**Before coding or changing product direction:** read the
[consolidated Product & Design Bible](docs/EDEN_COMMS_DESIGN_BIBLE.md)
and [full repository quality audit](docs/OVERALL_QUALITY_AUDIT_2026_11_10.md).
The Bible is a working UX/product reference; the Privacy and Security
Charter is still normative and cannot be overridden by styling decisions.

1. [Current handoff](docs/CURRENT_HANDOFF.md) — what is really integrated,
   blocked, deferred and the **next** task.
2. [Current work log](docs/WORK_LOG.md) — chronological **who/what/where/why**,
   exact PR/commit, tests, failures, rollback and the next action.
3. [Full core delivery roadmap](docs/FULL_FUNCTION_DEVELOPMENT_TEST_ROADMAP_2026_11_07.md)
   and [product decisions](docs/DECISIONS.md).
4. [Security/privacy contract](docs/PRIVACY_SECURITY_CHARTER.md),
   [local retention policy](docs/DATA_RETENTION_AND_EXPIRY.md),
   [peer trust policy](docs/TRUST_RETENTION_AND_HELP_PLAN.md).
5. [GitHub Actions](https://github.com/PlayerEden/Eden-Comms/actions)
   and [pull requests](https://github.com/PlayerEden/Eden-Comms/pulls).

## Development operating agreement

The owner's current instruction is **implement the useful core functions in
source, run CI after meaningful passes, and do ONE consolidated real-phone
test later**. Do not create a stream of Preview APK teasers or repeatedly
request a two-phone test. Use a focused branch/PR, preserve release guards,
and merge only after the exact final head SHA passes Android CI.

Keep three separate statuses for every transport: **source integrated**,
**automated checks passed**, **physical and independent security acceptance**.
These are never interchangeable. Release `CAN_CONNECT`, `CAN_SEND_MESSAGES`
and `CAN_TRANSMIT_VOICE` remain **false**. The app must not be described as
a production encrypted messenger or suitable for secrets/emergencies yet.

Every sizable PR updates the **current handoff** and appends one concise
**work log entry** documenting intent, decisions, files, test evidence,
remaining gaps, and rollback. Don't duplicate a full history in the README.
Preserve source/QA receipts in GitHub for future chat continuity.

Inspired by the owner’s `SudokuTogether/CONTINUE_HERE.md` and
`GridMates-Word-Search/docs/START_HERE.md` workflow; no game runtime,
keys, assets or unrelated code is copied.
