# Eden Comms — rolling implementation work log

This log complements the engineering details in `docs/F*_*.md` and
GitHub PR/CI history. New chats should read its **latest entry** and the
[handoff](CURRENT_HANDOFF.md) first. Append, don't overwrite.

## 2026-11-09 — F5-C: Bluetooth and Wi-Fi Direct private local options

**Source/PR:** [#150](https://github.com/PlayerEden/Eden-Comms/pull/150)
on `feature/f5-c-authenticated-bluetooth-p2p-privacy-2026-11-09`.
No Preview APK build triggered. Branch is not hardware/security accepted.

**Why:** The user's core-first plan requires the same opt-in history and
public-peer retention decisions on all actual debug chat transports,
without separate half-working demos or copied persistence implementations.

**What changed:** One shared DEBUG privacy owner + UI is invoked only by
post-approval, same-channel Bluetooth Classic or Wi-Fi Direct session
adoption. The original pinned TLS/QR/exporter/commitments and both human
APPROVE/CONFIRM records still gate the connection; metadata never flows
back into TLS/QR, discovery or authorization. The owner uses existing
separate F5-A history and F5-B public-peer encrypted stores, session-only
default, explicit 1h–30d history and 1–30d remembered fingerprint, view and
independent delete, per-session token/async fences and serialized storage
operations for consent/off/expiry ordering.

**QA:** Dedicated Python F5-C source regressions + fail-closed shell guards;
use [GitHub Actions](https://github.com/PlayerEden/Eden-Comms/actions)
for the exact head's Android/JVM/security result. At writing this log
the exact-head result and final merge are **not yet verified**. Prior
F5-A/F5-B unit tests guard expiry, AEAD tampering, and no silent renewal.

**Still missing:** physical Android 12+ Bluetooth/P2P/Keystore checks,
independent cryptographic protocol review, transport voice beyond existing
LAN, simplified cross-transport navigation. Do NOT claim reviewed secure
communication, dependable message persistence or flash erasure.

**Next/rollback:** Fix and verify exact-head CI; merge and check main,
then prioritize real voice + cohesive connection UI. Reverting the PR
doesn't erase ciphertext already stored by a test installation; use
in-app delete/Forget and avoid touching F5-A/B unrelated data.

## 2026-11-09 — F5-B: Remember this phone, separate consent and identity proof

**Who/what/where/why:** The owner wants all core functions implemented
in source before one consolidated phone test. After F5-A local history,
build the independent peer-identity retention decision, not a remembered
contact shortcut around cryptographic or human checks. Source branch
`feature/f5-b-explicit-peer-memory-2026-11-09`, [PR #149](https://github.com/PlayerEden/Eden-Comms/pull/149).

**What changed:** Pure peer-memory 1–30-day policy, 8-peer bound and
clock rollback/expiry fail-closed; separate DEBUG Keystore AES-GCM key,
AAD, ciphertext and no-backup directory holding only the public peer
fingerprint, this phone's public fingerprint, and expiry/integrity metadata.
Only the real LAN **APPROVAL_COMPLETE** callback may display previously
remembered metadata. A separate choice inside active verified LAN chat
lets the user remember 1–30 days, shorten but never silently extend,
forget this peer, or forget all. Session end invalidates callbacks and
resets UI. Identity reset separately purges experimental peer cache and
F5-A history. RELEASE stores are hard no-op.

**Safety / real acceptance:** Saved public key fingerprints are NOT
trusted connections. All future peers must still complete fresh QR/TLS,
session challenge, both approvals and final confirmations. No names,
addresses, phone numbers, message text, voice, backend or auto-reconnect;
no unaudited auth-capability flag changed. Real Android device, backup
rules, clock rollback, identity reset, two-phone P2P/Classic and independent
cryptographic/security review remain outstanding.

**QA receipt:** [PR #149](https://github.com/PlayerEden/Eden-Comms/pull/149)
and [Actions](https://github.com/PlayerEden/Eden-Comms/actions).
Policy unit tests, separate ciphertext sourcing and explicit consent
source gates require final-head green Android CI. Post-merge CI must be
checked separately; don't claim either before seeing the result.

**Next:** Integrate the independent privacy/peer-memory choices into
verified Bluetooth and Wi-Fi Direct sessions (never discovery); then
unify connection UX and plan one meaningful physical-device test.
**Rollback:** revert PR #149 without rolling back existing F5-A history;
running code cannot retroactively clear ciphertext already saved on
previously installed test devices. App Forget controls are provided.

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
