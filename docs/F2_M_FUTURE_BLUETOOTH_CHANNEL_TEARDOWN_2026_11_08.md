# F2-M — future same-channel Bluetooth resource cleanup rehearsal

**November 8, 2026 · test-only Bluetooth Classic session-owner preparation**

## The missing contract

The actual debug Bluetooth Classic flow currently exchanges fresh reciprocal
QR proofs over an authenticated RFCOMM TLS 1.3 engine, verifies exporter and
transcript, requires two separate human approvals and encrypted CONFIRM
records, and **closes its socket after diagnostic success**. There is no
approved live Bluetooth TEXT, ACK or PTT path.

F2-H/I introduced a *future-only* one-use `Closeable` owner and
context-comparison rules. That model did not explicitly represent three
separate resources a future retained session must tear down together:

1. The TLS record adapter must stop accepting application work.
2. The radio socket must close to unblock a pending native read/connect.
3. The associated **same** TLS engine must cease producing outbound records.

Skipping a resource after an earlier close throws, or allowing two race
callbacks to perform duplicate teardown, could leave a future connection in
an ambiguous state.

## What F2-M added

`DebugBluetoothFutureChannelBundle` is a **pure debug-only Closeable**
that holds caller-supplied references to a radio `Closeable`, an
`SSLEngine` and an `AutoCloseable` TLS adapter. Its one-way,
thread-safe close:

- Takes exclusive custody of the three references, clears retained fields
  and marks the bundle closed atomically.
- Closes the record adapter, then socket, then engine outbound independently
  with best-effort exception handling. It never pretends a hard disconnect
  received a TLS close_notify; real TLS truncation handling needs review.
- Refuses reference matching after closure. Matching references is **only
  correlation of caller-supplied objects**, never identity, TLS, QR,
  consent or send authorization.
- Offers **no read, write, send, transcript, permission or peer API**.

New JVM tests exercise a real JDK `SSLEngine` object alongside synthetic
socket/record resources. They verify each component is cleaned once,
an adapter/socket close failure does not skip engine cleanup, cleanup
order, stale generation rejection, wrong transcript context,
one-use owner refusal and cancellation/expiry/close races. A Python
source guard enforces that **the real live Bluetooth RFCOMM
controller and probe do not import or construct this bundle**, and
that their current close-after-consent and production security
restrictions remain intact.

## Why this is not a Bluetooth chat feature yet

This is an unwired resource-ownership **rehearsal**, not a working
same-channel TLS handoff. Tests can create arbitrary engine/socket
objects; doing so **does not prove** the objects came from the real
mutually pinned connection, or that either human approved anything.
The future owner still uses caller-supplied observations and cannot
grant messaging rights. A separately designed and independently reviewed
handoff must take the **actual already-verified** RFCOMM socket and live TLS
engine at exactly the right point after the two encrypted confirmations,
with one bounded lifecycle and no duplicate or cross-session authority.

Blocking gates before a live-text PR:

- Independent security review of the exact pinned TLS/QR/exporter/
  commitment/consent state machine, TLS 1.3 record adapter, session
  ownership transition, error paths, and strict encrypted TEXT/ACK framing.
- Physical two-phone Android 12+ Bluetooth Classic tests (actual RFCOMM,
  Android provider/exporter, approvals, decline, background,
  permission/radio loss, forced shutdown, malformed/late packets).
- No plaintext fallback, saved peers, automatic reconnection or
  production messaging flag based merely on a debug receipt.

**Current live behavior:** no change. The diagnostic socket still closes
immediately after verified consent. No Preview signing, permission,
package, stored-data, runtime Bluetooth path or UI changed.
The current signed Preview **200008** predates F2-L and F2-M.
A new signed APK is unnecessary for this test-only owner slice.

**Rollback:** revert the F2-M PR. No data migration or protocol impact.
