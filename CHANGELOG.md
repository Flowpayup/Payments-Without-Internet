# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1) and this changelog.
- README note on verifying a release APK against the signing certificate.
- Gradle version catalog (`gradle/libs.versions.toml`); build scripts converted to Kotlin DSL.
- detekt static analysis (with ktlint-style formatting rules) enforced in CI behind a frozen baseline.
- CI workflows: CodeQL (weekly + PR), dependency review on PRs, tag-triggered release drafts with SHA-256 checksums, and emulator-based instrumented tests (API 29 + 35) for database changes.
- Dependabot for Gradle and GitHub Actions updates.
- `docs/RELEASING.md`: pinned build environment, sign-locally release flow, verification steps.

### Changed
- Dependency refresh: coroutines 1.7.3 → 1.10.2, core-ktx 1.13.1 → 1.16.0, activity-compose 1.9.3 → 1.10.1, androidx.test runner/ext bumps.
- Lint baseline regenerated (85 frozen findings) and the CI budget now ratchets down instead of allowing growth to 400; `GradleDependency` advisories disabled in lint since Dependabot owns dependency freshness.
- Release APKs no longer embed the Play-Store `dependenciesInfo` metadata blob (reproducibility).

### Fixed
- Stale clone-directory name in CONTRIBUTING.md.

## [1.0.0] - 2026-07-12

Baseline — the state of the app when this changelog was introduced.

### Added
- Offline UPI payments over the `*99#` USSD and UPI 123Pay IVR rails, with QR scan and manual entry feeding the same downstream flow.
- Payment lifecycle state machine (`PaymentSessionManager`): a PENDING row is written before dialing, SUCCESS is reachable only via a confirming bank SMS, and sessions that never get an SMS end as UNVERIFIED.
- Bank-confirmation SMS detection across ~15 Indian banks: priority-999 broadcast receiver plus an opt-in notification-listener fallback, with cross-pipeline dedup.
- Local-only Room persistence with SQLCipher at-rest encryption; the database key is wrapped by a non-exportable Android Keystore key.
- On-call overlay with progress steps and result dialogs during the payment call.
- First-run setup (bank, primary SIM, disclaimer) and a post-setup connectivity test.
- CI: unit tests, lint with a frozen baseline, a PII-log grep gate, and debug-APK artifacts on every push.
- Release signing via a gitignored `keystore.properties` (see `keystore.properties.example`).
