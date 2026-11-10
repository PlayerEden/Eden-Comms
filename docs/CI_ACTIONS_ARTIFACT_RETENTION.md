# GitHub Actions storage policy — Eden Comms

- Automatic debug builds: **3 days**.
- Internal signed test APK (persistent signer/installation continuity): **7 days**.
- Manual emulator screenshots: **3 days**.
- Offline preview APK: **3 days**; QA evidence/test reports: **2 days**.
- Source, logs in GitHub code, signer pin documentation, and pull requests are unaffected.
- Save an important installation/milestone APK off Actions before its retention expires.
- This change does **not** delete older existing artifacts: GitHub Actions → run → Artifacts → delete obsolete items. GitHub billing budgets require account-owner review.
