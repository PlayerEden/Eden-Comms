# Eden Comms — Privacy Policy Working Draft (NOT PUBLISHED)

**Drafted:** November 4, 2026  
**Status:** INTERNAL PRODUCT/LEGAL WORKING DRAFT. **Not the governing Google Play privacy policy.** Do not link to this GitHub draft from the app or Play Console. The publisher's identity, public contact, final network/SDK behavior, data retention and supported regions remain under review. This draft applies to the *current development prototype only*, not features promised for later releases.

## Publication blockers

- [ ] Confirm the publisher's exact legal name/entity matching Play Console, its real monitored privacy contact method, and how privacy inquiries will be answered.
- [ ] Audit actual debug/release runtime data flows, device telemetry, SDK processing, transitive dependencies, backup/restore and local storage; do not extrapolate from permission declarations alone.
- [ ] Decide final user-facing features: whether accounts, saved messages, optional public peer identity retention, voice, new wireless transports, or ads exist. Rewrite applicable sections, not just add a blanket future-features disclaimer.
- [ ] Legal review for U.S. distribution and, separately, Canada and Mexico before enabling those countries; identify translations required for the selected audience.
- [ ] Publish a **stable HTTPS webpage**, globally reachable with no login/geofence and non-editable to readers, not a PDF or editable GitHub view. Test in an anonymous browser.
- [ ] Add the **same verified URL** inside the app and Play Console. Check page content, privacy contact, date and app name against store listing; complete Data safety separately.
- [ ] Ensure release artifacts, Play targeting, support inbox, security claim review and incident process are ready. No guessed emails or nonexistent website URLs.

Google's policies (verify before release):
- https://support.google.com/googleplay/android-developer/answer/9859455
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/10787469

---

# PRIVACY POLICY DRAFT — CONTENT FOR REVIEW, NOT PUBLICATION

**Eden Comms Privacy Policy**  
**Publisher:** [CONFIRM EXACT LEGAL PUBLISHER / APP-DEVELOPER NAME]  
**Effective date:** [DATE WHEN FINAL POLICY GOES LIVE]  
**Privacy inquiries:** [CONFIRM MONITORED EMAIL OR OTHER VALID CONTACT METHOD]  
**Policy URL:** [HOSTED HTTPS URL, TO BE PROVIDED WHEN VERIFIED]

## 1. About Eden Comms

Eden Comms is an Android application being developed to connect nearby devices. The present **development preview** supports an experimental local QR pairing and mutual TLS connection test, with only predefined example phrases. It does not yet permit messages written by a user, voice calls, saving message history, or long-term remembered peers. This preview should not be relied upon for sensitive communications.

Before publication, replace this section with a precise description of the capabilities that actually ship. An app distributed through Google Play must not advertise development-only functionality as a production messenger.

## 2. Information the current preview accesses

**Local device identity.** If you choose to create an identity for pairing, the app creates a local private key in Android Keystore and uses its public certificate/fingerprint in connection verification. Private-key persistence can survive the end of a test session; deleting a temporary conversation does not delete this identity.

**QR and nearby-network connection details.** During pairing, the app displays/scans QR data that includes an address on the current local Wi-Fi network, a public certificate fingerprint, and ephemeral values used for pairing. This information is disclosed to whoever can see or scan the QR code. The local connection also exposes connection metadata to the Wi-Fi infrastructure and participating phone.

**Development test payloads.** The app can exchange a restricted set of predefined test-phrase identifiers over a temporary mutually authenticated TLS session. The preview does not accept a custom user message. The in-app phrase view is temporary and is cleared when the test ends; this is not a guarantee of secure deletion from every operating system component or another person's device.

**Appearance preference.** The app stores your selected light/dark theme locally on your device. Android Keystore identity and this setting are separate from ephemeral test messages.

**Camera.** The app uses camera permission only when you choose QR code scanning. The design does not intentionally save camera frames; confirm actual SDK behavior before public use.

**App-controlled backend / accounts.** The preview does not implement publisher-operated accounts or cloud messages. This statement does not guarantee your operating system, network, app store, or future third-party SDKs never communicate with external services.

## 3. Permissions and how they are used

The preview requests Android `INTERNET` for **local** TCP connections, `ACCESS_NETWORK_STATE` to inspect the network used for pairing, and `CAMERA` to scan a nearby phone's QR code. It currently does not request microphone, location, contacts, or Bluetooth permissions. AndroidX may merge a package-specific receiver protection permission into the final manifest; review the actual built artifact.

Before release, make this section accurate for the exact signed production build and newly added features/permissions.

## 4. Information shared, collected, or disclosed

Current app functions transmit public pairing data and temporary encrypted test data directly to the selected nearby peer; nearby network infrastructure can also observe connection metadata. Recipients may photograph, save, or copy visible content independently of Eden Comms. Whether any third-party SDK accesses or transmits device/user information, and what Google Play's Data safety form considers collection or sharing, **requires an independent runtime/SDK review**. Do not claim the app collects no information or shares no information until reviewed.

[COUNSEL/PUBLISHER TO CONFIRM: legal meaning of collection, third-party disclosures, cross-border storage, lawful basis as relevant to actual countries and publisher operations.]

## 5. Storage, retention and deletion

Development test phrases and counters are intended to remain in temporary session/UI memory and disappear when the session ends. The app's Android Keystore identity may remain until reset or uninstall/invalidation according to Android behavior. The theme preference remains stored locally. The application configures backup and device-transfer exclusions; actual results depend on Android/OEM and require testing. A recipient's copies, external photographs, and some OS-level artifacts cannot be erased remotely.

[FOR REVIEW: precise reset identity path, OS backup/restore and future account/message/retention choices; add a workable request mechanism if the publisher processes personal data.]

## 6. Security and limitations

The preview experiments with mutual TLS 1.3, public identity verification, QR pairing and session-only transport. These mechanisms have **not passed independent security review or full two-device adversarial testing**. No system can guarantee a device is uncompromised, that a recipient will not copy content, or that nearby observers cannot learn that devices are communicating. Do not use the development preview for confidential or emergency-critical communications.

## 7. Children, regions, inquiries and policy updates

[CONFIRM TARGET AUDIENCE, MINORS POLICY AND AGE-DIRECTED FEATURES BEFORE RELEASE.]
[CONFIRM US, CANADA AND MEXICO LEGAL APPLICABILITY AND COUNTRY-SPECIFIC NOTICES/TRANSLATIONS.]
[CONFIRM PRIVACY INQUIRY CONTACT AND THE PROCESS FOR UPDATES/COMPLAINTS.]
[CONFIRM EFFECTIVE DATE, FUTURE POLICY VERSIONING AND WHERE USERS WILL SEE CHANGES.]

---

**Stop rule:** Do not promote this Markdown into a live public policy, create a fake support address, or mark Google Play declaration tasks complete. Final accuracy must be reviewed against the **release** APK/AAB, its SDKs, actual processing and the selected countries.
