# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- **CI no longer publishes a debug APK.** On a now-public repository, that
  artifact was world-downloadable, `debuggable=true`, signed with the public
  Android debug key, and carried an unguarded exported SMS-injection
  receiver — installable by a stranger and able to fabricate a "Payment
  Successful" screen with no permissions of its own.
- **A message carrying the right amount is no longer treated as a
  confirmation.** A shopping promo, an OTP, a spoofed sender, and a genuine
  balance alert could each produce a false "Payment Successful" if their
  body happened to contain the expected amount. Confirmation now requires a
  verb that actually says money moved.
- Scan QR is now locked behind the same `*99#` test Pay Contact already
  required, and Pay Contact refuses transfers above ₹4,999 inline.
- The dead notification-listener SMS fallback is removed. The setting that
  would have enabled it was never wired to any UI, so it could never
  actually run — it just sat in the manifest as the app's only exported
  `BIND_NOTIFICATION_LISTENER_SERVICE` component. `RECEIVE_SMS` is the only
  SMS ingestion path now; the payment-outcome notification is unaffected.

### Fixed
- A failed payment's result screen no longer says "Paid to" — that heading
  now only appears on a genuine success; other outcomes read "To"/"From".
- The Scan QR waiting screen no longer ends the payment session when it
  times out. It used to self-close after 150 seconds and, in doing so,
  cancel the session and close the SMS confirmation window — discarding a
  real payment if the user was still working through the `*99#` menus. The
  screen's own timeout is now 10 minutes, matching the session's
  verification deadline, and dismissing the screen never disarms the window.
- A bank SMS reporting a failure or decline with no verb before the payee
  (`"...to BIG MERCHANT has failed."`) no longer absorbed the status words
  into the recorded payee name, and a long enough version no longer dropped
  the payee entirely.
- `Run detekt` failing on an unused test import no longer takes the whole
  Build workflow red; the pull-request secret scan no longer fails with
  HTTP 403 on every PR.
- Backing out of setup no longer lands on a blank black screen. The launch
  gate sent first-run users to setup without finishing the home activity, so
  it stayed in the back stack having never drawn anything — one back press
  showed an empty window, and a second was needed to leave the app.
- The back link on the connectivity test screen now returns you where you
  came from. Reaching it by tapping a locked "Set up *99#" / "Set up UPI 123
  IVR" button offered "Back to Setup", which dropped you into onboarding you
  had already finished; it now reads "Back to Home" and goes there. Arriving
  from setup itself is unchanged.

### Changed
- A transaction's detail view now shows one identifier — the bank
  reference — instead of showing it twice under two different labels
  (`Bank reference` and `Transaction ID` held the same value for every
  SMS-confirmed payment).
- Recent Payments marks a failed or cancelled payment with a cross instead
  of the same outgoing arrow a successful payment gets.
- The Payment Setup row was removed from Settings; the underlying setup
  flow is unchanged and still reachable from first run.
- The in-app disclaimer is reworded for a plainer, more consistent voice:
  the "provided as is, without warranty" paragraph is removed (README's
  legal section already carries the full warranty and liability terms),
  the non-affiliation statement is kept and moved next to the sentence
  naming NPCI, and every em dash is replaced with a full sentence.
- The Scan QR waiting-screen copy moved from inline string literals to
  string resources, closing a gap both CI copy gates structurally missed —
  `updateBlackScreenStatus` calls whose literal opened on a following line
  were invisible to a line-oriented grep. Reworded in the same pass: no
  more exclamation mark, less telecom jargon.
- README's `## Legal & disclaimer` section moved to `LEGAL.md`, unedited,
  with a one-line pointer left in its place — the README was spending a
  quarter of its length on liability text after the "What it does" and
  "Why it was built" sections that should be a reader's last impression.
  `## Technical challenges` retitled to `## Engineering notes`; the content
  is unchanged, only the framing (constraints endured vs. problems solved).
  Two lines that stated the same fact as both a shortfall and a strength —
  "does not automate the menu walk" and "a thin wrapper around the call
  intent" — were corrected to describe what the code actually does: builds
  and validates the DTMF payload, places the call, tracks call state, and
  keeps an on-screen guide in front of the user.

### Removed
- `release.yml`, the CI workflow that attached an unsigned APK to a draft
  GitHub Release, is deleted — the repository has no tags and never has,
  so it had not executed once. The repository ships source; building a
  release is documented in README's "Signed release build".

## 1.0.0 - 2026-08-05

The first public **source** release: the code is opened, and that is the whole
of it. No APK is published and no build is distributed — you build it yourself,
per [Running it](README.md#running-it).

Flowpay could not go on Google Play in any case, because its SMS-permission
usage falls outside Play's restricted-permission policy.

**The physical-device gate in [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)
has been run end to end against real hardware** — the `*99#` flow, a real
IVR payment, and a live QR scan included — and every flow works.

Everything below this entry is **pre-release history**: internal milestones
built and versioned locally while the app was still private, never tagged and
never distributed. The `1.x`/`2.x` numbers in that history were working
labels, not releases, which is why the public version line restarts here.
`versionCode` does not restart — it continues upward from those builds, since
Android refuses to install a lower one.

### Found by running it on a real phone

Six defects that no amount of reading the code surfaced. All were caught in a
single device session and none of them are visible statically.

#### Fixed
- **The home screen showed an error on every launch.** "Failed to load
  transactions: k0 was cancelled" — `TransactionViewModel` cancels its own
  previous query on each refresh, then rendered that cancellation as a
  user-facing failure, with an R8-obfuscated class name standing in for the
  cause. Cancellation is now rethrown rather than reported, at all five call
  sites.
- **Multi-word payees were cut to one word.** "KIRANA STORE" recorded as
  "Kirana", "BIG MERCHANT" as "Big" — the recipient pattern ended a name at
  the first space, and it did so on the *most common* HDFC template. A shared
  name terminator now ends a name on a following keyword, punctuation, or the
  end of the body.
- **Failure templates absorbed their own status words.** "Rs.200 paid to
  Kirana Store has failed" recorded the payee as "Kirana Store Has Failed".
  Stop-words end the name, with a word boundary so real names that merely
  begin with one — Hasty Traders, Hasmukh Patel, Ismail Khan — stay intact.
- **The same amount rendered three different ways.** ₹1,00,000 appeared as
  `₹1,00,000.00` in history, `₹100,000.00` on the result screen and
  `₹100000.00` in the notification. `String.format("%,.2f")` was the cause and
  cannot be fixed by passing a Locale: its `,` flag takes only the separator
  and always groups in threes. All five money surfaces now go through one
  formatter.
- **Values collided with their labels** on the result screen
  ("Transaction ID512233…") — five `TextView`s had no start margin.

### Scan a QR from your gallery

#### Added
- **The scanner can read a QR out of a picked image**, for when the payee sent
  the code as a photo or it is on a screen the camera cannot focus on. It
  decodes through the same path the live camera uses, so a gallery scan and a
  camera scan are indistinguishable downstream. Uses Android's photo picker,
  which grants a one-shot read on the single chosen image — no storage
  permission is requested or held. Large photos are downsampled before
  decoding; a QR stays readable far below 100MP.

  (The decoder had been able to do this for a while. Nothing in the UI could
  reach it, which is why a previous entry below records removing a "hidden
  gallery-import affordance" — that was the dead entry point, not this
  feature.)

### All user-visible copy lives in strings.xml, and now it is enforced

#### Fixed
- **Thirty-nine hardcoded strings were still in Kotlin**, and both copy gates
  were structurally unable to see them: lint's `HardcodedText` never leaves
  XML, and the CI ratchet only matched Compose `Text(`. Everything in the View
  layer sat in the gap — Toasts, `TextView` assignments, and the app's own
  `showToast()`/`showError()` helpers.
- **Twelve of them interpolated an exception message.** In a minified release
  that is an R8-obfuscated class name shown to the user; several also passed
  `e.message` to the log instead of the throwable, discarding the stack trace.
  Exception detail now goes to `Log.e` with the throwable, and the user gets a
  fixed sentence.
- **The 123Pay cap message stated one number twice.** "Maximum ₹4999 per
  payment — the UPI 123Pay IVR does not accept ₹5,000 or more" substituted the
  cap from `AppConstants` and then wrote a second, unrelated copy of it into
  the sentence, free to disagree if the ceiling ever moved. It is substituted
  once and rendered through the shared formatter.

#### Changed
- `Upi123CallStringBuilder` and `QRCodeParser` return a `Reason` enum instead
  of an English sentence. Both are pure and unit-tested — one decides a live
  payment's DTMF string — so neither should carry a `Context` or user-facing
  copy; small extension functions map the reason to a string at the UI edge.
- The CI copy gate covers all three forms now, and was verified to fail on a
  reintroduced literal rather than merely passing.

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

### Install size — 62 MB down to 16 MB

#### Changed
- **The release APK is a third of its former size.** Three blanket ProGuard
  keeps (`androidx.compose.**`, `androidx.camera.**`, `kotlinx.coroutines.**`)
  disabled R8 shrinking across those namespaces entirely — `material-icons-
  extended` alone contributed 10+ MB for 30 icons actually used. Removed with
  no replacement: every reflective anchor those libraries need (Room's
  database impl lookup, CameraX's config bootstrap, coroutines' field
  updaters) already ships in the libraries' own consumer rules or in the
  rules aapt generates from the manifest/layouts. A release build now also
  targets `arm64-v8a` + `armeabi-v7a` only — real phones are ARM, x86 only
  ever served emulators, which now build from the (unaffected) debug variant
  instead. `armeabi-v7a` is kept: 32-bit Android Go-edition devices are this
  app's target market. Two unused proguard blocks left from the pre-ZXing/
  pre-Gson dependency graph were also removed. This app's whole audience is
  entry-level phones on metered data, so `build.yml` now gates on a release
  build staying under an 18 MB budget, ratcheted like the lint/detekt
  baselines.
- A missing or misspelled key in `keystore.properties` now fails with a
  message naming the key, instead of a bare `NullPointerException` from an
  unchecked cast.

### Release readiness

#### Fixed
- **A cancel landing at the same instant as the bank's SMS could leave the
  transaction record contradicting the screen.** `finishSession` and
  `onVerificationDeadline` queued their database write *before* claiming the
  terminal state, so on a multi-threaded dispatcher the losing caller's write
  still executed: history could read `CANCELLED` (or lose the row entirely)
  while the user was looking at "Payment successful". Both now claim and write
  inside one lock, exactly as `onSmsConfirmed` already did. The DAO's
  compare-and-set guards remain as a second line of defence. Covered by a
  regression test that races the two paths on real threads.
- **CI publishes no uninstallable artifact.** `release.yml` attached the
  *unsigned* APK to the draft release along with a `SHA-256SUMS` computed over
  it — so the checksum described a file users must not install, and verifying
  the signed APK per `RELEASING.md` could never succeed. CI now keeps the
  unsigned build as a workflow artifact only and opens an empty draft; the
  maintainer uploads the signed APK and a checksum generated over *that* file.
  The R8 `mapping.txt` is archived too — release crash traces were previously
  undecodable.
- **The security-disclosure link in the issue template was a 404.** It pointed
  at an un-substituted `https://github.com/.github/blob/main/SECURITY.md`; with
  blank issues disabled, a researcher's only route led nowhere. The reporting
  address is now inlined in the template so a broken link cannot hide it.
- **README named the wrong payment rail.** It claimed QR scan and manual entry
  "both feed the same `*99#` flow". Manual entry places a UPI 123Pay IVR call;
  QR scan dials `*99*1*3#`. The README now says which rail each entry point
  uses, and explains *why* there are two: `*99#` USSD does not exist on Jio's
  all-IP network, which is the gap 123Pay was created to close.

#### Changed
- Every GitHub Action is pinned to a commit SHA, and `contents: write` is
  scoped to the one job that opens the release rather than the whole workflow.
- The dead Room migrations (`MIGRATION_1_2`, `MIGRATION_2_3`), their
  hand-authored v1/v2 schema JSON, and `MigrationTest` are removed. The first
  commit of this codebase already declared `version = 3`, so no v1 or v2
  database has ever existed and the migrations could never run; the emulator
  matrix gating every release was guarding unreachable code. `docs/TESTING.md`
  records what must return with the next schema change.
- `LocalBroadcastManager` — which carries the payment-result broadcast — is
  declared explicitly instead of arriving as a fourth-level transitive of
  Material, where a dependency bump could have silently removed it.
- Static-analysis baselines cut from 662 to 466 (detekt) and 44 to 39 (lint),
  with the CI budgets ratcheted to match exactly — the detekt gate previously
  allowed 170 findings of slack. Real accessibility gaps were fixed rather than
  re-baselined: four missing `contentDescription`s and the overlay's touch
  handler now forwarding to `performClick`.
- Build toolchain pinned for reproducibility (`buildToolsVersion`, Gradle
  distribution SHA-256). `docs/RELEASING.md` no longer promises byte-identical
  rebuilds as fact — no independent reproducer has confirmed one yet.
- Every Kotlin source file carries an SPDX licence header; per-file scanners
  previously reported all 71 as unlicensed.

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
  dead `stopOverlayReceiver`, the hidden gallery-import affordance (an entry
  point that reached nothing — the working gallery scan under `[1.0.0]` above
  is a different, later thing), and the QR activity's unused
  `showOnLockScreen`/`turnScreenOn` flags.

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
