# Eden Comms — M2s first visible text conversation UI

**Checkpoint:** November 5, 2026  
**Status:** Native Android chat layout wired to Home → Text tile; automated CI pending.  
**Real text exchange / authenticated authorization:** NOT IMPLEMENTED.  
**Physical two-phone validation:** NOT TESTED.

## Why this milestone is different

The original [Product Vision](PRODUCT_VISION.md) promises a usable **Connect → Communicate → Disconnect** experience. M2n–M2r largely improved debug-LAN correctness and error behavior, but did not let a person experience a text conversation screen. M2s implements the first **real app-facing conversation presentation**, while deliberately isolating it from unreleased peer messaging.

### What is working now

- The **Home → Text** tile opens **Text preview**, a native Compose conversation layout with status header, chat bubbles, local time labels, multiline composer, character counter, and session controls.
- Text entered in the composer can be added to a **local-only bubble**, with explicit **“Local preview • not sent”** status. The UI has no backend, socket, send queue, packet encoder, text transport or received message path. No misleading **sent/delivered/read** claim is made.
- The optional **Show sample incoming layout** button displays two fictional example bubbles, separately labeled **“Example peer / FICTIONAL”** and **“Example only • not received”**. The examples are not network events and cannot prove a peer was online.
- Conversation is **in memory**, `remember` (never `rememberSaveable`). At most 12 preview bubbles of 280 characters each are visible. **Clear** erases immediately; **End preview** terminates the ledger and clears payload. Reopening starts empty. Leaving the screen disposes the content; moving the app into the background closes/erases the preview and its unsent draft.
- Accessory controls have minimum 48–52dp touch sizes and stack when viewport width or font scaling is constrained. Screen reader announcement provides an updated plain-language local-only status.
- A separate button links to the existing **two-phone fixed-phrase QR/TLS test**; no shortcut from a local preview into an “authenticated” conversation exists.
- Privacy & data now explicitly discloses the local composer, in-memory preview, keyboard/screenshot/OS retention limitations, and that real text is not exchanged.

## Test and security evidence

- `LocalChatPreviewTest` validates no preexisting history, strict 280-character entry length, empty rejection, monotonic IDs, 12-item visible cap and ordering, immutable updates, no false network receipt status, clearly labeled opt-in fictional incoming examples, immediate clear, irreversible end, and completely fresh new-session state.
- `scripts/check_security_review_boundary.sh` now rejects network, protocol, TLS/authorization, and persistent-store imports in either chat preview Kotlin source file; it also guards against `rememberSaveable`, SharedPreferences, SQLite, or direct packet/socket sends in the preview.
- The **real** `ReleaseCapabilities.CAN_CONNECT`, `CAN_SEND_MESSAGES`, and `CAN_TRANSMIT_VOICE` flags remain **false**. The existing debug QR/TLS fixed-phrase system is unchanged.
- Existing Android/JVM/permission/data/backup/dependency APK audits remain the merge gates.

## What is explicitly NOT finished

| Product milestone | Current capability | Missing gate |
| --- | --- | --- |
| Text conversation UI | **Local native preview implemented** | Real remote incoming/outgoing messages, IME/keyboard and screenshot review on device |
| Mutual acceptance and secure session | Separate proposal / test-only proof | Authenticated, independently reviewed bilateral human approval; live adapter |
| Real encrypted 1:1 text | **Not enabled** | Reviewed message framing, encryption/authentication and no-WAN two-phone delivery tests |
| Message states | Only truthful `LOCAL_ONLY` and `FAKE_SAMPLE` | Real sent/delivered statuses tied to verified transport receipts; no invented acknowledgments |
| Session-only chat | Preview memory cleanup implemented | Real-message lifecycle and privacy test on devices; no chat persistence leakage |
| Bluetooth / Wi-Fi Direct / PTT | Future milestones | Real drivers, permissions, reviewed session interface, hardware + security QA |
| Optional 1–30-day trust/history | Policy and model pieces only | Explicit opt-in, integrity/encryption, clock-rollback/failure handling, independent review |

## Next actual feature delivery sequence

1. **M2t:** Build the transport-independent *conversation presentation contract*: message author/source proof categories, safe status transitions, send eligibility and explicit typed UI mapping—with no transport adapter yet. Don't permit a local fake event to masquerade as verified network receipt.
2. **M2u (review gate):** Complete and independently assess the chosen cryptographic protocol/library, live authenticated reciprocal consent and message framing; test hostile/failure scenarios before enabling arbitrary text.
3. **M2v:** Integrate real LAN two-way text behind approved authorization and prove no-WAN operation on both devices, then replace the UI preview's local-only behavior through a separately reviewed implementation. Only there do real `SENT/DELIVERED` labels become meaningful.
4. After proven LAN chat, implement opt-in discovery, Bluetooth and Wi-Fi Direct transport adapters, then PTT, and separate peer/message retention as the original milestones prescribe.

Do not postpone every security review until app-store release. The current fixed-phrase LAN [two-phone worksheet](M2K_BATCH_PHONE_QA_WORKSHEET.md), [issue #26](https://github.com/PlayerEden/Eden-Comms/issues/26), and durable APK signer [issue #27](https://github.com/PlayerEden/Eden-Comms/issues/27) remain mandatory, with real-device behavior still **NOT TESTED**.

**Privacy note:** The app does not intentionally store these preview texts, but Android keyboards, screenshots, Recents previews and someone photographing the screen can retain visible content. Avoid secrets in this development UI. There is no guarantee of physical memory erasure.
