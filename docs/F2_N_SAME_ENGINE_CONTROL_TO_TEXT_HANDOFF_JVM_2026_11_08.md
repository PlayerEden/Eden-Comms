# F2-N — same-engine TLS control-to-text handoff (JVM rehearsal)

**November 8, 2026 · debug-only future Bluetooth Classic testing · not a live radio feature**

## What this actually verifies

The existing code had demonstrated:

- Two mutually pinned JVM TLS 1.3 engines can exchange the required
  five **encrypted control-record sizes** (F2-D).
- Two actual JVM TLS engines can separately exchange encrypted
  bounded TEXT and peer-app ACK data in both directions (F2-G).
- A future-only Closeable session owner can bound a connection and
  its teardown (F2-H/I), and a bundle can clean up its TLS adapter,
  radio resource and engine despite shutdown races (F2-M).

Those tests had **not** established that the same, already-handshaken
TLS 1.3 session can finish encrypted control records, retire its control
adapter, and immediately exchange TEXT and ACK on **the identical TLS
engine objects and undisturbed underlying byte streams**.

F2-N adds that continuity test inside the existing
`DebugPairedTlsEngineInteropTest` fixture, which uses temporary JDK
test certificate keypairs and blocking, split-read-capable memory pipes.

The new end-to-end JVM scenario:

1. Mutually pins both real TLS 1.3 engines, completing their actual
   handshakes against the respective ephemeral test certificates.
2. Sends the five strict encrypted control **record lengths** in both
   directions using `DebugTlsControlTransport`. The plaintext of
   these packets is generated synthetic data, NOT the actual QR,
   exporter, transcript or two-approval protocol.
3. Explicitly retires both fixed-control adapters.
4. Creates `DebugBluetoothFutureTlsTextLab` on the **same original
   engines and byte pipes**. No new handshake or replacement session.
5. Exchanges a real encrypted TEXT and matching peer-app ACK in both
   directions, enforcing the existing strict 16–528-byte frame and
   finite per-session message ledger. The ACK means app-frame
   acceptance only, not human reading.
6. Wraps the exact engine objects and per-side pipes in
   `DebugBluetoothFutureChannelBundle`, each admitted to the
   previously established **future-only** observation owner using
   synthetic boolean claims/context. Exercises expiry on one side and
   user cancellation on the other; verifies adapters/ledgers, socket
   pipes and engine outbound are disabled and cannot resume.
7. A separate negative test leaves an encrypted control packet
   queued and tries to parse it as future TEXT. Strict codec validation
   must reject it and permanently disable that lab instance. There
   is no magic-byte scanning or acceptance of a leftover handshake
   control record as application text.

## What this does NOT verify

**Critical:** these are **JVM tests**, not real Bluetooth pairing.
The fixture uses arbitrary fixed control packet contents and arbitrary,
caller-constructible future-owner observations. It does NOT perform the
actual live QR nonce challenges, TLS exporter exchange/commitment or
the two people’s APPROVE/CONFIRM actions in the same new test.
Passing does not establish that a production engine handoff is
authenticated, correctly ordered or safe under every Android provider.

This PR does NOT connect the lab/owner to
`DebugRfcommLinkController` or `DebugRfcommTlsProbe`. The real RFCOMM
probe still closes immediately after both people approve. There is no
Bluetooth TEXT/voice UI, no new Android permission, key, saved trust,
server, background operation or production release flag.

## Next engineering/review gate

The next handoff design must be sourced from the **real same-socket
verified probe** and preserve its engine, TLS record receive buffer,
both pins, challenge/exporter transcript, permission, foreground token,
monotonic time and human consent state without deriving permission
from a fabricated boolean or a printed diagnostic receipt.

An **independent cryptographic/protocol review** and actual **two-phone
Android 12+ Bluetooth Classic RFCOMM + Conscrypt** matrix must precede
enabling any live retained socket or messaging. Hardware tests should
include both sides' approvals/declines, wrong or expired QRs, OEM TLS
provider behavior, lost Bluetooth radio/permissions, background,
cancel/retry and blocked writer/reader teardown; never upload private
QRs, keys, MACs or ciphertext logs.

**Source/build acceptance:** paired JVM SSL-engine tests,
Python call-site/security guards and full Android CI, followed by
green post-merge main CI. They are NOT physical security acceptance.
Signed Preview 200008 predates F2-N; no new APK is useful for this
pure JVM rehearsal.

**Rollback:** revert F2-N PR. No runtime, signing or migration change.
