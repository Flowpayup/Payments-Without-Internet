# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-30

The first published release.

Everything below this entry is **pre-release history**: internal milestones
built and versioned locally while the app was still private, never tagged and
never distributed. The `1.x`/`2.x` numbers in that history were working
labels, not releases, which is why the public version line restarts here.
`versionCode` does not restart — it continues upward from those builds, since
Android refuses to install a lower one.

### One rule for confirmations: the bank's SMS decides

#### Fixed
- **A genuine confirmation is no longer discarded when the bank names both
  sides of the payment.** Many banks write one payment as *"A/c XX556 debited
  for Rs 500.00; KIRANA STORE credited"*. Direction was read on the first
  credit word found, so these outgoing confirmations looked like incoming
  money, and the pipeline dropped them as unrelated — the SMS arrived and the
  payment still never appeared, leaving the user to retry a payment that had
  already gone through. Direction is now read debit-first, and the dual-verb
  templates are in the parser corpus.
- **Cancelling a payment now closes the SMS window with it.** Cancelling ended
  the session but left the window armed for the rest of its 10.5 minutes, so a
  later debit for the same amount — the user re-paying through another app,
  say — was adopted onto the cancelled row and reported as a success. Every
  cancel route now closes the window (`PaymentWindowObserver`), and, as a
  backstop, a confirmation can only ever land on a row still awaiting one.
- **An unrelated incoming credit during a payment can no longer surface as
  that payment's success.** A credit arriving in the window (salary, refund,
  someone paying you) was saved and flashed a "Payment successful" screen for
  its own amount while the real payment was still pending. It is now ignored,
  and the window stays open for the genuine confirmation.
- **Abandoning the QR flow no longer leaves a payment live.** A failed dial or
  leaving the scanner mid-payment kept the session running, which also refused
  the next scan as "a payment is already in progress".

#### Changed
- **No confirmation means no record.** A payment the bank never confirmed is
  discarded rather than kept as `UNVERIFIED` — the old behavior fired a
  full-screen "we couldn't verify this, check your bank" result and a
  notification some ten minutes after the fact, about a payment the app knew
  nothing about. A confirmation arriving a little late is still recorded.
  Existing `UNVERIFIED` rows keep rendering as before.
- In-flight payments no longer appear under Recent Payments on the home
  screen, where they read as completed. Transaction History still shows them.

#### Added
- Tapping a row under Recent Payments opens its detail dialog — the same
  surface Transaction History already offered.

---

## Pre-release history (never published)

Local development milestones, kept for provenance. None of these were tagged
or distributed; see the note under `[1.0.0]` above. The earliest entry is
labelled `0.1.0-dev` — it was written as `1.0.0` at the time, renamed here so
the published `1.0.0` above is unambiguous.

## [2.1.0] - 2026-07-17

### The publish-readiness pass — one money-path correctness fix, and three release blockers closed

#### Fixed
- **A bank SMS is reflected only when it is genuinely this payment's confirmation.**
  Previously, an unrelated failure alert arriving in the operation window (e.g. a
  card decline for a different amount) was recorded as *this* payment's FAILED
  with a retry offered — inviting a double payment — and it also consumed the
  window, so the genuine confirmation was dropped. `SmsTransactionParser` now
  drops any debit whose amount doesn't match the expected amount (it isn't our
  confirmation), leaving the window open for the real one; only a matching debit
  is reflected, as SUCCESS or FAILED. `NEEDS_REVIEW` is no longer produced from
  the SMS path. (Found on a physical device driving the debug SMS pipeline.)
- **`POST_NOTIFICATIONS` is now actually requested at runtime.** It was declared
  and relied on as the "guaranteed-reachable" payment-outcome path, but never
  requested, so on Android 13+ the fallback silently no-opped for fresh users.
  It is now asked once, contextually and non-blocking, at the first payment, with
  a Settings toggle for later recovery.
- **A failed database-encryption migration can no longer leak a plaintext copy.**
  The set-aside `.unreadable` file (and `.encrypting` temp) are now excluded from
  cloud backup and device transfer, and a plaintext leftover is deleted outright
  rather than retained on disk.
- **The result screen no longer fails open to "success"** — an unexpected status
  now renders neutral (unverified) instead of the green success look.

#### Added
- Third-party attribution in `NOTICE` for SQLCipher (BSD-3-Clause), the bundled
  OpenSSL, ZXing, and the Apache-2.0 AndroidX/Kotlin/Material set.

#### Changed
- Docs corrected: README's permission list and the QR stack (ZXing, not ML Kit);
  SECURITY.md's operation-window duration (~10 minutes, not 5).

### The interface pass — one design system, and a result screen that never lies

#### Fixed
- **The result screen re-renders on a new outcome.** It is `singleTask`, so a
  second confirmation (e.g. a FAILED arriving while a prior SUCCESS is still
  shown) was delivered to the live instance but never re-read — the screen kept
  showing the stale, wrong outcome under a fresh, correct notification. It now
  adopts the new intent and re-renders, and every branch sets all its visuals so
  a re-render can't inherit the previous status's icon or explainer. (Found on a
  physical device driving the debug SMS pipeline.)

#### Changed
- **One design system.** All ~200 inline `Color(0x…)` literals across the
  Compose screens, and the hex in the three XML layouts, now reference named
  tokens (`ui/theme/Color.kt` / `colors.xml`); the ~14 near-duplicate greys and
  the copy-pasted `getStatusColor()` functions collapse to one value / one
  `statusColor()` each. Remaining hardcoded Compose copy moved to `strings.xml`.
  New CI gates fail the build on an inline color or a single-line hardcoded
  `Text("…")`, so it can't drift back.
- **Result screen styling is status-coherent** — a failed/needs-review/unverified
  outcome colors its circle, heading and amount together (red / amber / grey)
  instead of a red error icon inside a blue "success" circle; success keeps the
  brand-blue circle and blue action button.

#### Removed
- Two never-instantiated `TransactionData` classes (and a dead
  `navigateToSuccessScreen`) that still carried the `rawMessage`/`balance` fields
  the v3 migration removed from storage for privacy.

### The hardening pass — money-path edge cases closed and tested

#### Fixed
- **Keystore key loss no longer crash-loops the app.** If the wrapping key is
  invalidated (OS update, keystore corruption, some restores) while the
  wrapped passphrase blob survives, `DatabaseKeyManager` now recovers — stale
  blob discarded, key regenerated, unreadable database set aside — instead of
  throwing on the app's first database touch at every launch.
- **Slow bank SMS no longer produce false UNVERIFIED.** The SMS operation
  window (was 5 min) is now derived from the 10-minute verification deadline
  plus a grace margin, so a confirmation at t+6 min still lands.
- **A process death mid-payment no longer duplicates the record.** The session
  txnId is persisted with the operation window; a confirmation arriving after
  restart reattaches to the original PENDING/UNVERIFIED row instead of
  inserting a second SUCCESS row.
- **Cross-pipeline SMS dedup actually dedupes.** The claim key is now built
  from the message body only — the receiver sees the DLT header while the
  notification listener sees the app display name, so the old sender-qualified
  keys never collided.
- **Confirmations claim the session atomically.** The check and terminal
  transition in `onSmsConfirmed` were two separate locks; two simultaneous
  SMS could both write an outcome, last-writer-wins.
- The notification listener now runs the shared `SmsIngestionPipeline`
  instead of a drifted reimplementation (which passed the bank ref, not the
  session txnId, to the result screen), and no longer force-mutes call audio
  post-success (a path that could only fire for the wrong message type).
- Cold start no longer opens the encrypted database on the main thread: the
  session store and `TransactionViewModel` resolve it lazily on IO. Debug
  builds run StrictMode with penaltyLog as a regression tripwire.

#### Changed
- **The QR flow now runs through the payment session** — PENDING row,
  verification deadline, UNVERIFIED on silence — instead of bypassing the
  lifecycle and leaving no trace when no SMS arrived. The bank SMS fills the
  amount the QR flow didn't know yet; the recorded VPA survives sparser SMS.
- **Parser hardening:** body bank-keywords match on word boundaries with
  "YES"/"BOB" requiring the full bank phrase (a promo "Say YES to win Rs
  5000!" can no longer enter the pipeline); amount matching is paise-exact
  (was ±₹0.99); amount extraction skips balance figures ("Avl Bal Rs …").
- **Payment outcomes are also posted as high-priority notifications** — the
  direct result-screen launch from a background receiver can be silently
  blocked without the overlay permission; the notification is the
  guaranteed-reachable path (and doubles as a receipt).

#### Added
- Tests: Keystore-loss recovery (Robolectric), orphaned-row reattach
  decision, truly-concurrent confirmation race, QR session lifecycle,
  promo-"YES" rejection, paise-boundary matching, balance-first extraction,
  `TransactionDetector` window/dedup/consumption, and `CallStateCoordinator`
  state/duration/ref-counting suites. The kover floor now also covers
  `TransactionDetector` and the telephony package.

### The truth pass — the app's behavior now matches its honesty premise

#### Fixed
- **UNVERIFIED outcomes now reach the user.** A payment that completes with no
  confirming bank SMS is surfaced on the result screen at the deadline (neutral
  grey, never a green "success") via a process-scoped observer, instead of only
  being written to the row while the user saw a stale "Request Sent" dialog.
- **"Clear App Data" now clears the transaction database**, not just settings.
- **The payee VPA is wiped from the clipboard** when the QR flow ends (and
  flagged sensitive on Android 13+) instead of lingering for other apps.
- **The audio indicator is truthful**: "Call volume lowered" shown only when the
  volume change actually succeeds; the post-success mute no longer zeroes (and
  strands) the ring/notification streams.

#### Changed
- Transaction history rows show a colored status pill; PaymentStatus no longer
  collapses UNVERIFIED/NEEDS_REVIEW into PENDING.
- Honest copy: "Cancel payment" (was "TERMINATE"), "Step N of 2" (was "of 3",
  with no third step), a single connecting message, delete confirmation on
  transactions, and consent before the connectivity test places a real call.

#### Removed
- The uncalled fake-progress engine and no-op health monitor in
  `CallOverlayService`, the fabricated "Almost there!" QR status messages, the
  dead `stopOverlayReceiver`, the hidden gallery-import affordance, and the
  QR activity's unused `showOnLockScreen`/`turnScreenOn` flags.

## [2.0.0] - 2026-07-13

The quality release: a sustained pass over architecture, testing, build
engineering, FOSS purity, and documentation. Every dependency is now FOSS,
the riskiest code (bank-SMS parsing, the payment state machine) is tested
against production code, the UI is one consistent pattern with no static
mutable state, hardcoded strings in the classic-View screens are extracted,
and the whole system is documented in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). The versions below (1.1.0–1.2.0)
chart that pass; 2.0.0 is where it lands.

### Added
- `docs/ARCHITECTURE.md` (system map), `docs/FAQ.md` (trust/permissions Q&A), `docs/TESTING.md` (four-layer verification), `docs/RELEASE_CHECKLIST.md`, `docs/RELEASING.md`.
- `di/AppContainer`: an explicit, framework-free composition root owned by `FlowpayApplication`.
- `payment/PaymentInputValidator` with unit tests — pure phone/amount validation extracted from `CallManager`.
- `MainViewModel` carrying Activity↔Compose one-shot events.
- A curated "good first contributions" list in CONTRIBUTING and a gitleaks secret-scan CI step.

### Changed
- **Zero static mutable state on `MainActivity`'s companion object.** Removed all six `@Volatile` static callback fields and both deprecated result overrides; all permission and activity results go through `ActivityResultContracts` launchers, with one-shot Activity→Compose events flowing through `MainViewModel`.
- **Hardcoded UI strings in the classic-View screens extracted** to `strings.xml` (layouts, `setText` calls, the manifest label); runtime placeholders became design-time `tools:text`. `HardcodedText`/`SetTextI18n` are now error-level lint checks with zero baselined findings — lint baseline dropped 85 → 45.

### Removed
- Five never-constructed `PaymentState` variants (`Retrying`, all five `QRPayment*`), three dead `CallManager` validation/sanitization methods, unreachable dialogs, and the obsolete permission request-code constants and helper result-handlers.

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

## [0.1.0-dev] - 2026-07-12

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
