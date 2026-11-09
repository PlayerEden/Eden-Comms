# Eden Comms — rolling implementation work log

This log complements the engineering details in `docs/F*_*.md` and
GitHub PR/CI history. New chats should read its **latest entry** and the
[handoff](CURRENT_HANDOFF.md) first. Append, don't overwrite.

## 2026-11-09 — F5-A: optional encrypted local history + simpler project handoff

**Why:** The owner wants the actual usable core functions implemented in code
before the big two-phone test, and fewer hollow Preview checkpoints. The
Sudoku project uses a one-page `CONTINUE_HERE.md`, exact milestone work
logs, QA receipts and an explicit current handoff. Word Search uses
`docs/START_HERE.md`. Eden Comms had valuable but scattered milestone docs.

**Who/what/where:** Source development in the public
`PlayerEden/Eden-Comms` GitHub repo, branch
`feature/f5-a-opt-in-encrypted-local-history-and-handoff-2026-11-09`.
Introduced F5-A pure retention policy and tests, a DEBUG Android Keystore
AES-GCM history store in `noBackupFilesDir`, a RELEASE no-op store, and
actual LAN confirmed-TLS text callback → opt-in save. A user can select
Session only (default), 1h, 24h, 7d, 14d or 30d, and view/delete records
for the currently reverified peer. The short local disappearing timer
pauses persistence; peer trust is completely separate.

**Safety:** No auto-save, no drafts/voice, no remembered addresses, no
automatic trusted reconnect, no release capability switch, no new cloud.
Expiry filters before display and rollback/corruption fail closed.
Manual deletion is best effort on local app files, not flash/screenshot/
recipient copies. Independent crypto/device/backup verification remains
REQUIRED and no production security claim is made.

**QA source receipt:** [PR #148](https://github.com/PlayerEden/Eden-Comms/pull/148).
New pure JUnit expiration and real AES-GCM tamper/negative tests, alongside
Python security source assertions and shell release/backup gates. Full
PR check history appears in [GitHub Actions](https://github.com/PlayerEden/Eden-Comms/actions).
Prior runs surfaced stale "no history" privacy assertions; after correcting
the UI disclosures and tests, verify the **final head** and post-merge CI
before describing the pass as green. No real-device security acceptance.

**Next:** make final-source Android CI pass, merge the reviewed PR, check
main CI; then tackle F5-B opt-in **peer remembering** with a separate
1–30-day expiry and human/cryptographic reauthentication every time.

**Rollback:** revert the F5-A code PR; this does NOT revoke stored data
from an already installed test app. Provide the in-app delete control.

## 2026-11-09 — F3-C: Wi-Fi Direct authenticated text

**PR:** [#147](https://github.com/PlayerEden/Eden-Comms/pull/147),
main merge `2d40b67987605fee0ff5ebab34c1bd61bdfaf71a`.
**Evidence:** [Post-merge Android CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42572812574).
**Change:** moved live three-QR preparation onto host/join Android radio
screens, bound fresh TCP socket to OS P2P interface, reused exact mutual
TLS/exporter/consent protocol and encrypted TEXT/ACK worker; added
foreground/timeout/cancellation guards. **Not phone/security accepted.**
**Next then:** optional encrypted history and peer retention.

## 2026-11-09 — F2-Q: Bluetooth authenticated text

**PR:** [#146](https://github.com/PlayerEden/Eden-Comms/pull/146),
main merge `e2fd6eea3ee4e67fd14d64e433db4e6e1e1b8096`.
**Evidence:** [Post-merge Android CI passed](https://github.com/PlayerEden/Eden-Comms/actions/runs/42568879327).
**Change:** kept actual reciprocal-QR, mutually pinned TLS 1.3 RFCOMM
socket alive after both confirmations and connected encrypted typed TEXT,
receive and peer-app ACK UI. Added paired JVM tests; no production claim,
no Bluetooth voice, no physical acceptance.
