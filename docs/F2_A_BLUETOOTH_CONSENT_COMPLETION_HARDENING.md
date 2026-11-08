# F2-A — Bluetooth consent exit and callback hardening (November 7, 2026)

**Scope:** real source change in the Android **debug-only** Bluetooth controller, with
JVM policy tests and source regressions. This is the first narrow readiness step
after merged F1 LAN peer-app ACK. It is **not** F2 Bluetooth text, a review of
the custom TLS engine, device interoperability evidence or a release feature.

## Observed source risks and remediation

1. `DebugRfcommTlsProbe.run` could return successfully after the authenticated
   bilateral consent exchange while the app had lost foreground or Bluetooth
   CONNECT permission. The controller previously always requested
   `CONSENT_CONFIRMED` on return. A new pure `DebugRfcommCompletionPolicy`
   rechecks both prerequisites at completion. If either is absent, it records
   BACKGROUND or PERMISSION_LOST rather than a misleading success. The existing
   attempt-token stop guard still prevents any stale worker from winning.
2. Approval/decline actions now also verify live `LINK_OPEN`, foreground, and
   Bluetooth CONNECT permission at tap time instead of relying only on the
   asynchronously posted UI's state.
3. A delayed terminal UI post from an older stopped socket previously checked
   only `active == null` and `STOPPED`. If a *newer* attempt had also stopped
   before that old post ran, the old terminal reason could overwrite the newest.
   The controller now associates stopped-result UI posts with the exact most
   recently stopped token; earlier posts are ignored.
4. Wrong-role and rejected-start paths now immediately close one-shot QR
   probe input, wiping copied ephemeral peer pins/nonces rather than leaving
   them reachable until ordinary cleanup.

## Automated checks and acceptance boundary

- `DebugRfcommCompletionPolicyTest` covers the approval outcome truth table
  and consent-button readiness for all UI phases, foreground and permission
  combinations; release feature flags remain false.
- `test_f2_a_bluetooth_completion_boundary.py` checks real controller call
  sites, stopped-token fencing, one-shot QR cleanup, debug-only scope and
  continued socket closure.
- Existing M3n-B1 and M3n-C1 source boundaries continue to reject Bluetooth
  message/voice paths; test expectations are updated to the fail-closed policy.
- **Still required:** PR Android compile/JVM/source/security/permission CI,
  fresh merged-main CI, owner two-phone negative/positive behavior, independent
  protocol and `SSLEngine`/RFCOMM record adapter review.

No transport is extended or bypassed. No user messages, contact trust, new
permissions, data storage, release capabilities, package/signing identity or
radio-background behavior are added. The Bluetooth diagnostic still closes its
socket after approval.

**Next F2 step:** inspect TLS record framing/provider handling and its
state-machine limits before any post-consent session retention, then integrate
bounded encrypted chat only behind the already defined external-review/device
acceptance gates. Do not skip those gates by simply removing the diagnostic close.

**Rollback:** revert the F2-A PR. No migration or persistent data.
