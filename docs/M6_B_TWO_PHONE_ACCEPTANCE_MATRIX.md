# M6-B — One consolidated two-phone acceptance session

**Prepared:** 2026-11-09. **Execution:** NOT TESTED. **Source baseline:** M6-A merged PR #153 at `b9166cdc4f2809146c5aeeb8eac4f34397a860a7`. No device pass, security certification or release authorization is implied.

This is the successor to the earlier LAN-only [M2K worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md). Do not re-run the whole historic LAN worksheet as a prerequisite. Test the actual unified Connect → Text/Talk → Disconnect experience across the three debug transports **once**, after a compatible, consistently signed build is selected. No separate preview APK is required to write or maintain this plan.

## Preconditions and evidence safety

- Two consenting phones A/B, Android versions and model names only. Record app version, source SHA, package ID and signing certificate digest on both; **never publish** IMEI, serial, MAC, local/private IP, live QR, raw cert/identity pins, conversation contents, raw audio or crash logs with secrets.
- Before upgrading, compare signer and package with installed apps. **Never uninstall automatically** to solve a signature mismatch: existing local Keystore identity, saved history and remembered-peer state may be lost. See [test APK update safety](TEST_APK_UPDATE_SAFETY.md). If safe upgrade cannot be established, mark upgrade tests BLOCKED.
- Keep both devices foreground unless the test explicitly says otherwise. Give camera, Bluetooth/nearby Wi-Fi and microphone permissions only when requested by the relevant feature; record permission denial without granting automatically.
- Use harmless sample phrases and non-sensitive audio (e.g. counting); no emergencies or confidential information. Release capabilities must remain disabled. Start each transport with **fresh** QR and two independent human approvals.
- Results use exactly **PASS / FAIL / BLOCKED / NOT TESTED** for each phone. PASS requires visible expected behavior on both devices, not merely a connected label or a CI result. Record a short sanitized observation, reproduction steps and Android version for failures.

## Core cross-transport matrix

Repeat IDs C01–C13 for **LAN**, **Bluetooth Classic**, and **Wi-Fi Direct**. For Wi-Fi Direct run both explicit Host/Join orientations; for Bluetooth verify both sides' approved connection path. Mark a feature BLOCKED if a transport lacks it, never manufacture a pass.

| ID | Action | Expected result |
| --- | --- | --- |
| C01 | From fresh app Home choose Connect, then selected transport | Real existing entrypoint opens; no auto-radio discovery, automatic trust or fake Connected state |
| C02 | Deny required transport permission, retry after granting | Clear actionable failure; no approval or sending until permission; recovery without app reset |
| C03 | Pair with fresh in-person QR, approve only A first | A/B show pending independent approval; neither text nor microphone available |
| C04 | Approve B and complete verified TLS/CONFIRM | Both show actual session ready; Text/Talk/Disconnect appears only now |
| C05 | Send sample text A→B then B→A | Exact ordered text and real peer-app ACK; no phantom delivery or duplicate |
| C06 | A holds Talk, B listens; then reverse | Mic starts only with authenticated floor; live audible audio **while held**, no overlap; up to 3 seconds |
| C07 | Both press Talk nearly simultaneously | Host arbitration resolves collision; denied/pending side mic remains OFF; no double playback |
| C08 | Joiner requests Talk then releases before grant | CANCEL wins; delayed grant does not start mic; new attempt needs new floor request |
| C09 | Release Talk and rapidly send text | END/RELEASE complete, mic stops, text remains usable on same approved session |
| C10 | Use Disconnect on A while B is active | Both close within bounded time; no further text, mic, speaker or stale session actions |
| C11 | Reconnect with fresh QR after disconnect | No silent reapproval from remembered public pin or previous TLS/QR |
| C12 | Background, lock or revoke permission during talk | Capture/playback stops; no background or phantom connected state; fresh consent required |
| C13 | Expired/mismatched/self QR or declined approval | Fail closed; no Text/Talk, no automatic fallback to unverified/raw radio |

## Transport-specific checks

| ID | Method | Action and expected result |
| --- | --- | --- |
| L01 | LAN | Same router, WAN/cellular off: paired text/ACK and live PTT still work locally |
| L02 | LAN | Wi-Fi drop or router change: bounded close and fresh QR needed |
| B01 | Bluetooth | Pair on real radios; ensure no insecure RFCOMM fallback or saved MAC shortcut |
| B02 | Bluetooth | Bluetooth off mid-talk: mic and approved socket close, no stale resume |
| W01 | Wi-Fi Direct | Host A / Join B without router, using actual Android P2P group interface |
| W02 | Wi-Fi Direct | Host B / Join A; verify permissions and group teardown on both |
| W03 | Wi-Fi Direct | Group loss mid-text and mid-talk: socket/group closed; retry uses fresh QR |
| X01 | All | Test mixed Android/OEM models and note TLS/Keystore/provider incompatibility rather than bypassing pinning |
| X02 | All | Rotate, portrait/landscape, 200% font, TalkBack, keyboard and hardware Back during session; no hidden controls or unintended send |

## Privacy and retention (separate from trust)

| ID | Action | Expected result |
| --- | --- | --- |
| P01 | New verified session with no choices | Chat transcript remains RAM-only; no peer identity retained automatically |
| P02 | Opt in to short history expiry for one approved peer | Only approved text events saved encrypted locally, bounded expiry; no audio persisted |
| P03 | Remember verified peer public fingerprint with explicit expiry | It is visible only as optional metadata, never auto-pairs or skips new QR/approval |
| P04 | Delete saved history for one peer, then clear all | History gone on both screens after refresh; peer-memory choice remains independent |
| P05 | Delete remembered peer, then clear all | Public peer record gone; previously saved history remains independent until deleted |
| P06 | Advance time past configured expiry or use controlled test clock | Expired records unavailable; record clock/rollback limitations, no false secure-wipe claim |
| P07 | App background/force-stop and restart without opting in | No stale message, microphone capture, trusted session or QR restored |
| P08 | Inspect Android backup/data-transfer configuration | No intentional saved messages, keys or identity included in cloud/device transfer; verify on actual OS |

## Evidence sheet — duplicate per run

```text
Run ID:                     Date/time zone:
Source SHA / artifact URL:   Package/version:
Phone A model / Android:     Phone B model / Android:
Signer digest A/B match:     Existing-install upgrade safe?:
Method and host/join role:   Router/WAN conditions:
IDs run and A/B status:     (PASS/FAIL/BLOCKED/NOT TESTED)
Observed result (sanitized):
Expected vs actual:
Reproduction steps:
Evidence location (no secrets):
Bug issue/PR:
Retest result:
```

## Stop conditions and exit gate

**Stop the test** for an unexpected mic recording, unauthorized send, QR/approval bypass, plaintext/raw-socket fallback, peer identity exposure, lost ability to stop audio, crash loop or unexplained stored data. Disconnect both devices, preserve only sanitized observations and open a focused GitHub issue.

A green CI run is **source regression evidence only**. M6-B is accepted only after C01–C13 and relevant transport/privacy rows have real paired-device PASS evidence, with BLOCKED/FAIL entries tracked and retested. A separate independent cryptographic/security review remains mandatory before enabling release CAN_CONNECT, CAN_SEND_MESSAGES or CAN_TRANSMIT_VOICE. Do not treat this worksheet as that review.
