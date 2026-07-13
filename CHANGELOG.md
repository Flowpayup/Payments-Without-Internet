# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `docs/ARCHITECTURE.md`: the payment state machine, dual SMS-ingestion pipeline, composition root, component map, and deliberate simplifications.
- `di/AppContainer`: an explicit, framework-free composition root owned by `FlowpayApplication`.
- `payment/PaymentInputValidator` with unit tests — pure phone/amount validation extracted from `CallManager`.
- `MainViewModel` carrying Activity↔Compose one-shot events.

### Changed (string hygiene)
- Extracted every hardcoded UI string in the classic-View screens (payment-result, call-overlay, QR-scanner layouts and their `setText` calls, plus the manifest `Settings` label) into `strings.xml`; runtime-overwritten placeholders became design-time `tools:text`. `HardcodedText` and `SetTextI18n` are now error-level lint checks with zero baselined findings, so any new hardcoded string fails the build. Lint baseline dropped 85 → 45.

### Changed
- **All permission and activity results now go through `ActivityResultContracts` launchers.** Removed every `onActivityResult` / `onRequestPermissionsResult` override and all six static `@Volatile` callback fields on `MainActivity`'s companion object (the headline architecture cleanup); one-shot Activity→Compose events flow through `MainViewModel` instead.

### Removed
- Five never-constructed `PaymentState` variants (`Retrying`, all five `QRPayment*`), three dead `CallManager` validation/sanitization methods, and the obsolete permission request-code constants and helper result-handlers.

## [1.2.0] - 2026-07-13

### Added
- **`SmsTransactionParser`**: the bank-SMS matching logic extracted from `TransactionDetector` into a pure, Context-free object, directly unit-testable without Robolectric. `TransactionDetector` is now a thin stateful orchestrator (operation window, cross-pipeline dedup) that delegates all matching to it.
- **`SmsTransactionParserTest`**: a 14-bank corpus exercising `parse()` end-to-end (success/failure/credit/non-UPI-debit-alert shapes, amount-format edge cases including Indian lakh grouping, deterministic transaction-ID generation under an injected clock). `SmsParsingRegexTest`'s previously-duplicated inline regex logic now calls production code directly.
- Six new `PaymentSessionManagerTest` cases for `onUserCancelled`/`onCallNeverStarted`, including idempotency under a defensive double-call — the exact scenario the `CallOverlayService` hardening below relies on.
- **Debug SMS-injection tool** (`DebugSmsInjectionReceiver`, debug-build-only source set): replays a bank SMS through the live ingestion pipeline via `adb shell am broadcast`, without a SIM, a bank, or a real call. `SimpleSMSReceiver`'s persistence/broadcast/launch logic was extracted into a shared `SmsIngestionPipeline` so the debug tool exercises identical code, not a reimplementation.
- `docs/TESTING.md` (the four-layer verification story) and `docs/RELEASE_CHECKLIST.md` (the physical-device release gate).
- Kover coverage floor (85%, currently ~98%) scoped to the `payment`/`payment.sms` packages only, enforced in CI via `koverVerify`. No app-wide threshold.

### Fixed
- `CallOverlayService.startTimeoutTimer`'s 40-second watchdog ran with no exception handling on the main looper — an uncaught exception there would have crashed the app *and* skipped `onCallNeverStarted()`, stranding a payment session until the 10-minute deadline. Now caught and the session notification always fires.
- `CallOverlayService.handleTerminateCall` now calls `onUserCancelled()` unconditionally before any audio/telecom cleanup, so a failure in that cleanup can never prevent the session from being marked cancelled.
- Deleted five `PaymentState` variants (`Retrying`, `QRPaymentInitiating/InProgress/WaitingForVerification/Success/Failed`) that were never constructed anywhere in the codebase — dead states that misrepresented the state machine's actual surface.
- A real discrepancy the extraction surfaced: a duplicated-regex test in `SmsParsingRegexTest` asserted a promotional SMS wasn't bank-detected, but its body text accidentally collided with the `YES` (Yes Bank) keyword — the test only ever passed because its local duplicate `detectBank` didn't include that keyword. Fixed the test body; the production behavior was correct all along.

## [1.1.0] - 2026-07-13

### Added
- `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1) and this changelog.
- README note on verifying a release APK against the signing certificate.
- Gradle version catalog (`gradle/libs.versions.toml`); build scripts converted to Kotlin DSL.
- detekt static analysis (with ktlint-style formatting rules) enforced in CI behind a frozen baseline.
- CI workflows: CodeQL (weekly + PR), dependency review on PRs, tag-triggered release drafts with SHA-256 checksums, and emulator-based instrumented tests (API 29 + 35) for database changes.
- Dependabot for Gradle and GitHub Actions updates.
- `docs/RELEASING.md`: pinned build environment, sign-locally release flow, verification steps.
- QR decode corpus test (`QRCodeAnalyzerDecodeTest`): encodes the UPI URIs already covered by `QRCodeParserTest` into real QR bitmaps and decodes them through the live pipeline — closes the "parsing tested, decoding never" gap.

### Changed
- **Every dependency is now FOSS.** Replaced ML Kit's bundled (proprietary-model) barcode scanner with `com.google.zxing:core` — the app's only remaining non-platform QR dependency is now pure Java, Apache-2.0 licensed, with no proprietary binary blob. Both the live camera analyzer and the gallery-image scan path (previously two separate ML Kit call sites) now share one ZXing decode pipeline.
- Replaced Gson with `org.json` (Android platform API, zero new dependency) for the one class that used it (`TestResultsManager`); no more reflection-based (de)serialization in the app.
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
