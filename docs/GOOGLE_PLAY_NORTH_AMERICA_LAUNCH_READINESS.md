# Eden Comms — Google Play & North America Launch Readiness

**Created:** 2026-11-04  
**Goal:** Eventually release Eden Comms on **Google Play** to users in **North America**. For launch planning, explicitly scope this to **United States, Canada, and Mexico**, with a *staged rollout*, not an automatic commitment to simultaneous distribution. Other North American countries/territories require separate country-selection and legal review.

**Status:** PLANNED / NOT CLEARED. This is a development checklist, not legal advice or a claim of compliance. No account creation, publication, app signing, release submission, regulatory filings, or store settings have been performed.

**Owner goal:** A privacy-first, offline-capable Android communications app with no required Eden Comms account/cloud service. Keep user messaging/voice disabled until authentic peer security, physical-device tests, and independent review are complete. The public GitHub repo and uploaded APK artifacts merit export classification review **before** Google Play.

## Proposed launch order

1. **US controlled testing and first production rollout** only once independent crypto review and device QA are accepted. Developer account, store declarations, export review, release signing and regression tests must all be complete.
2. **Canada** after verifying federal/provincial privacy scope, English/French user disclosures where needed, user-facing support and devices available in target provinces.
3. **Mexico** after confirming current federal privacy-law applicability, local-language privacy/terms/support needs, any relevant distribution requirements, and Mexican network/device testing.

Each region can be configured independently via Play Console **Test and release → Production → Countries/regions**. Google Play country availability generally follows the user's **Play account country**, not current GPS location. Internal testing track is not country-targeted in the same way. Do not block previously selected release regions by mistake.

## Timeline gates (start early, not the week of release)

| Stage | Due relative to launch | Gate / deliverables | Evidence owner |
| --- | --- | --- | --- |
| L0 — now | During prototype | Inventory every used crypto implementation/library, license, country availability, stored data, build artifact, connected service, and support contact. Keep explicit issues: external audit, export classification, PIPEDA/Quebec/Mexico, Play policy, public GitHub artifacts | Engineering + legal reviewer |
| L1 — before public beta | Before collecting external testers | Choose **Personal vs Organization** developer account; establish verified Google Play identity, support email/website/public details; ensure stable public privacy-policy webpage in app and listing; draft Data safety answers supported by actual SDK/network/storage audit; decide audience, 13+ vs wider age policy | Publisher |
| L2 — security/technical release gate | Before production capability flags change | Independent cryptographic/protocol review, two actual phones on WAN-disabled LAN, hostile/incorrect peer/QR/replay cases, no plaintext/message leakage, safe cancel/rotation/background, correct retention, tested minimum Android versions, permissions/network audit | Security reviewer + engineering |
| L3 — external app testing | Before asking for production access | Signed **Android App Bundle (.aab)** using Play App Signing and repeatable CI; internal → closed testing and crash/UX/accessibility checks. **If a personal developer account was created after 2023-11-13**, meet Google's current requirement of **at least 12 opted-in testers continuously for 14 days** before applying for production access. Confirm exact current account eligibility and policy in Play Console | Publisher + QA |
| L4 — US readiness | Before initial US production launch | Check current required target SDK (**API 36 for new apps/updates as of 2026-11-04**, subject to change); account verification, listing/content rating, privacy policy + Data safety, app access explanation (no login), sensitive permissions, pricing, merchant/trader status if applicable, encryption export findings, third-party licenses, signed AAB, screenshots, support & abuse/report channel, rollback and staged release | Publisher + counsel |
| L5 — Canada readiness | Before adding Canada | Check PIPEDA, substantially similar **Quebec/Alberta/BC** privacy laws where relevant; Quebec Law 25 and French-language/accessibility/localization obligations with local counsel. Verify applicability based on data the developer actually processes and whether distributing to Quebec; English/French privacy/support experience as needed | Canadian privacy counsel + publisher |
| L6 — Mexico readiness | Before adding Mexico | Check Mexico's current private-sector personal-data law (new LFPDPPP published 2025-03-20), any required *aviso de privacidad*, Spanish-language listing/policy/support and terms, applicable consumer/communications rules and tax/payment obligations if monetization is added. Verify whether Eden or third parties actually collect/process personal data | Mexican legal/localization reviewer + publisher |
| L7 — post-launch | Continuous | Monitor Play policy updates, crashes, security advisories, vulnerabilities, actual device compatibility, support reports, privacy notice accuracy and legal changes; maintain incident response, quick rollback, release history, third-party license/SBOM and per-country controls | Publisher + engineering |

## Legal research checklist — not a substitute for counsel

### United States / export compliance
- [ ] **Identify encryption** in the whole app (TLS 1.3, public-key identification, channel binding, future E2E text/audio, all libraries and codecs if applicable).
- [ ] Obtain a qualified **U.S. export-control classification/notification assessment** under BIS EAR: assess publicly available encryption source vs object code, mass-market rules, any one-time self-classification/CCATS, reporting, `742.15` notice or applicable exemption. Do **not** assume public/free app means zero obligations.
- [ ] Address the fact that the repo and previous APK test artifacts are already **public**, not merely future store distribution.
- [ ] Determine publisher legal structure/contact, trademark/name availability, copyright/license notices, and developer vs organization account implications.
- [ ] Check appropriate U.S. state consumer privacy, cybersecurity/data-breach, consumer protection, marketing, child-directed/COPPA and **voice recording consent** laws as relevant to actual features and intended audience.

### Canada
- [ ] Map collection/processing (if any) by Eden, Google Play, Android/Google services, added crash-report/analytics SDKs, and any hypothetical cloud features.
- [ ] Review **PIPEDA** and the possible application of provincial legislation, especially Quebec, Alberta and British Columbia. **Quebec Law 25** and French-language public-facing disclosures should be checked specifically; do not assume all Canadian jurisdictions impose identical requirements.
- [ ] If needed, publish English and French disclosures, contact channels, and translations of any trust-sensitive controls. Review Quebec language requirements and consumer protection applicability, rather than claiming French is universally mandatory for every app.
- [ ] Assess any Canadian encryption import/export/security or telecommunication issues relevant to actual distribution and feature set. Document counsel conclusion.

### Mexico
- [ ] Review the current **LFPDPPP** (new federal private-sector data-protection law published March 20, 2025) for applicability to this publisher and data flows, not just to users' peer-to-peer content.
- [ ] Verify privacy notice, consent, rights request channel, international transfers and required disclosures **if** processing triggers these rules; obtain a Mexican-law reviewer before rollout.
- [ ] Prepare Spanish Mexico app listing, help, security warnings, and privacy/terms documents as appropriate. Evaluate consumer protection, marketplace, taxes/billing and any local communications regulation based on actual product features.
- [ ] Test Mexican Play availability and device/network differences before enabling the country.

### Global/platform matters
- [ ] Document exact factual claims: *works on verified same-LAN without WAN* only after testing; never promise untraceability, anonymity, unlimited range, 100% secure encryption, complete deletion from recipient device, zero network traffic or universal Android support.
- [ ] Decide whether to target **children**; if so, review COPPA, Google Play Families and Canadian/Mexican children's data laws before enabling target age brackets.
- [ ] Compare third-party SDK data and default Android system behavior with each Play **Data safety** declaration; review again on every new SDK, messaging storage, cloud, ads or analytics feature.
- [ ] Draft publicly accessible, app-specific **privacy policy** (HTML webpage; within app and Play listing), plus terms, license disclosures and support/abuse contact. No fictional privacy policy URL in Play Console.
- [ ] Decide whether free-only launch or in-app purchases/subscriptions; if monetized, investigate **Google Play Billing, taxes, merchant status and country-specific payment restrictions**. No monetization assumed yet.
- [ ] Establish release key custody, Play App Signing, package name ownership, app integrity, dependency SBOM/license inventory, automated binary permissions/traffic checks, incident reporting and takedown/patch process.
- [ ] Add **application-specific** Google Play functionality walkthrough for reviewers who won't necessarily have two nearby devices. Provide a lawful working way for reviewers to exercise the published core feature; **do not** misrepresent a test-only APK as a messaging product.
- [ ] Check latest Android target API deadline at launch. API 37/Android 17 local network permission changes are a planned migration decision and must be tested before targeting API 37.

## Launch blockers / non-blockers

**Block production release:** unreviewed cryptography, no two-phone acceptance, real messages or voice enabled without secure authorization, wrong privacy claims, missing required Play disclosures/identity/testing, unresolved export/export-notification classification, unsuitable age/permissions declarations, missing release signing and rollback.

**Doesn't block continued development:** open legal questions explicitly tracked with owner/date; development-only mock/preset tests behind disabled production gates; planning the marketing/localization; investigating Wi-Fi Direct/other transports without enabling claims.

## Regional launch decision record

| Market | Target | Status today | Future go/no-go decision |
| --- | --- | --- | --- |
| United States | Phase 1 | Intended, **NOT CLEARED** | Confirm Play/security/export/privacy/QA release gates |
| Canada | Phase 2 | Conditional, **NOT CLEARED** | Canadian provincial/federal counsel + English/French scope + local QA |
| Mexico | Phase 3 | Conditional, **NOT CLEARED** | Mexican counsel + Spanish/localization + local QA |

**Country availability != regulatory compliance.** Google Play provides geographic controls but does not certify that a developer has met all local legal duties.

## Official resources to recheck at release

- Play country rollouts: https://support.google.com/googleplay/android-developer/answer/7550024
- Play target SDK: https://support.google.com/googleplay/android-developer/answer/11926878
- New personal-account production testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Play privacy policy/listing: https://support.google.com/googleplay/android-developer/answer/9859455
- Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Play account verification: https://support.google.com/googleplay/android-developer/answer/13628312
- BIS encryption software publicly available / EAR: https://www.bis.gov/learn-support/encryption-controls/encryption-items-not-subject-to-ear
- Canada PIPEDA/provincial distinctions: https://www.priv.gc.ca/en/privacy-topics/privacy-laws-in-canada/the-personal-information-protection-and-electronic-documents-act-pipeda/r_o_p/prov-pipeda
- Mexico 2025 federal private-sector privacy law: https://www.ordenjuridico.gob.mx/Documentos/Federal/html/wo125102.html
- Background project security research: `docs/EDEN_COMMS_TECH_SECURITY_PRIVACY_LEGAL_RESEARCH_2026-11.md`

**Planning only.** Treat the listed legal items as research and counsel-review tasks, not completed filing, a final determination of legal applicability, or permission to launch.
