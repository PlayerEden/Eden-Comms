# F2-O — actual QR/commitment/consent protocol on one JVM TLS session

**November 8, 2026 · debug-only test architecture · no live Bluetooth change**

## Why this test is materially different

F2-N demonstrated the continuity of **the same two real, pinned JDK
TLS 1.3 engine instances** across encrypted control *record sizes* and
later TEXT/ACK frames. But the contents of those control records were
synthetic bytes, so the test did not exercise the actual source functions
that negotiate the QR challenges, TLS exporter comparison, session
commitment or bilateral APPROVE and CONFIRM.

F2-O upgrades that existing JVM integration fixture:

1. Two ephemeral, independently generated test X.509 identities run
   the real TLS 1.3 handshake and exact mutual certificate pin check.
2. `SessionProofExchange.runHost/runJoin` exchanges four **real**
   ordered QR-binding challenge/echo records over the same encrypted
   `DebugTlsControlTransport` on those two engine objects.
3. `DebugExporterBindingFrame.exchange` performs the real encrypted
   role-specific exporter comparison. **Its 32-byte value is fixed by
   the test fixture, not obtained from SSLEngines on Android.** This
   does not establish real same-channel exporter binding.
4. `DebugApprovalCommitment.derive` creates a domain-separated
   digest of the real test QR challenges, public pins, nonces and
   synthetic test exporter. Both sides run the real encrypted
   `DebugBluetoothCommitmentFrame.exchange` protocol and compare
   derived commitments.
5. `DebugBluetoothConsentExchange.run` executes the actual encrypted
   APPROVE and final CONFIRM state machines. Each side's
   `onApprovalRequired` callback is **auto-approved by the test**
   (not a human). The two completion callbacks are counted and both
   derived transcript commitments are required to match.
6. After that complete, ordered protocol, both control adapters are
   cleared. On the **same, uninterrupted and originally handshaken
   TLS engines and memory byte pipes**, the F2-G encrypted TEXT and
   peer-app ACK code runs in both directions.
7. The already-tested future-only F2-M channel bundle and F2-H owner
   close their test streams and adapters after one user's cancellation
   and the other owner's deadline. Application frame writes fail after
   shutdown. A completed TLS close_notify is never implied.
8. A separate negative integration test uses different test-exporter
   values across the two peers. It must reject the actual encrypted
   exporter comparison before the auto-approval callbacks can run.

The existing F2-N leftover-control-negative case still runs in the
same JVM test suite. Extra Python source checks confirm actual
protocol code-path ordering, zeroization and that the real Android
Bluetooth controller/probe remains unwired.

## What this does NOT prove

This is **still a JVM simulation, not a real Android RFCOMM test**:
the exporter is test-supplied, rather than derived using Android
`SSLEngines.exportKeyingMaterial`; the QR pins/nonces come from test
fixtures rather than two user-scanned screens; the approvals are
programmatic rather than human gestures; and the TLS implementation
and streams are JDK/memory pipes, not Android Conscrypt/RFCOMM.

Even a perfectly functioning JVM transcript does **not** prove
Bluetooth radio interoperability or that the same cryptographic
objects can be safely handed to a live Android session. In particular,
the test's future-session `Observations` booleans are constructed
by the test and therefore have no authorization significance.

**Real production and debug device state remains unchanged:**
`DebugRfcommTlsProbe` still closes its TLS engine and
`DebugRfcommLinkController` closes the exact RFCOMM socket after
two confirmed human approvals. No Bluetooth text/voice, new permission,
stored trust, plaintext fallback, background agent, or release flag
is enabled here.

## Next review gate before a real same-connection handoff

A separately designed and independently reviewed actual handoff must
source its socket, TLS engine and transcript from the **real on-device
probe** after both CONFIRM records, not a copy of this fixture's
`ByteArray` or arbitrary `FutureSessionContext`. Its record handling
must preserve TLS state and cancellation, and must not treat the
diagnostic completion receipt as send authority.

Before enabling a real debug Bluetooth TEXT/ACK path, test on two
physical Android 12+ devices: actual RFCOMM radio connection,
Keystore mutual pinning, TLS exporter, QR expiry and replay,
approve/decline, simultaneous cancellation, background, Bluetooth
permission/radio loss and OEM packet fragmentation/close behavior.
Require an **independent cryptographic/protocol review** of the actual
same-engine owner, framing and state transitions. Never share keys,
raw QR, MAC/device identifiers, private messages or transcripts in CI.

**Build acceptance:** Android build, JUnit paired TLS tests,
Python structure/security checks and fresh merged-main CI.
No signed Preview needed for a JVM-only change. Preview **200008**
remains the latest installation checkpoint and does not include this.

**Rollback:** revert F2-O. There is no persistent state or wire change.
