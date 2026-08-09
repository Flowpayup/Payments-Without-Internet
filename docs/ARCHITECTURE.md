# Flowpay architecture

Flowpay is a single-module Android app that puts a smartphone UI on top of
two offline UPI rails — `*99#` USSD and UPI 123Pay IVR — and confirms
payment outcomes by reading the bank's confirmation SMS locally. There is no
backend, no `INTERNET` permission, and no telemetry. This document is the
map: how the pieces fit, where the load-bearing invariants live, and which
known simplifications are deliberate.

## The one invariant that matters

**A payment is only ever marked SUCCESS by a confirming bank SMS — never by
call state or call duration.** Everything below serves this. A dialed call
that connects and ends "normally" proves nothing; only the bank's own
confirmation SMS, matched to the amount that was sent, promotes a payment to
SUCCESS. When no SMS arrives before the deadline, the attempt is discarded
and nothing is shown — an outcome the bank never confirmed is not one this
app can report on, and it is never silently assumed either way.

## Composition root

[`FlowpayApplication`](../app/src/main/java/com/flowpay/app/FlowpayApplication.kt)
owns a single [`AppContainer`](../app/src/main/java/com/flowpay/app/di/AppContainer.kt)
— the manual, framework-free composition root. It constructs and holds the
process-scoped graph:

```
AppContainer
├── appScope: CoroutineScope           (SupervisorJob + Dispatchers.Default)
├── settingsRepository
├── callStateCoordinator               (the ONE telephony listener)
└── paymentSessionManager              (the ONLY writer of payment state)
        └── store = TransactionRepository (implements PaymentTransactionStore)
```

Receivers and services reach it via `FlowpayApplication.from(context)`.
DI is hand-wired on purpose: the graph is small, and for a payments app,
construction a reader can follow by eye beats annotation-generated
indirection. `TransactionRepository`, `AppDatabase`, and `TransactionDetector`
remain thread-safe, application-context-keyed `getInstance()` singletons;
the container references them rather than duplicating their lifecycle.

## Payment lifecycle state machine

[`PaymentSessionManager`](../app/src/main/java/com/flowpay/app/payment/PaymentSessionManager.kt)
is the single writer of payment state and the most carefully-built part of
the app. It takes its dependencies as interfaces (`PaymentTransactionStore`,
`CallStateSource`) plus an injectable clock, which is what makes it fully
unit-testable without a device (see
[`PaymentSessionManagerTest`](../app/src/test/java/com/flowpay/app/payment/PaymentSessionManagerTest.kt)).

```
begin(phone, amount)          [QR: begin("", amount?, upiId, source=QR)]
   │  writes a PENDING row BEFORE anything is dialed, so a process death
   │  mid-call still leaves a record of the attempt
   ▼
Initiating ──OFFHOOK──▶ InProgress ──call ends──▶ WaitingForVerification
   │                        │  (short call <5s ⇒ Cancelled, never SUCCESS)     │
   │                        │                                                   │
   └── dial failed /        └───────────── confirming bank SMS ────────────────┤
       call never started              (onSmsConfirmed)                         │
              │                                                                 ▼
              ▼                              ┌── amount matches ⇒ Success ──────┤
           Cancelled                         ├── amount mismatch ⇒ dropped,     │
       (PaymentWindowObserver also           │   window stays open for the      │
        closes the SMS window, so a          │   real confirmation              │
        later debit can't be adopted         └── failure keyword ⇒ Failed       │
        onto the cancelled row)                                                 │
   no SMS before the 10-minute deadline ⇒ Timeout, and the PENDING row is DELETED
                                             │
                    Nothing is surfaced: a payment the bank never confirmed
                    leaves no record. The SMS window outlives the deadline by
                    30s, so a late-but-genuine confirmation still lands — with
                    no row to adopt it is saved as a standalone transaction.
```

Every terminal transition `join()`s the pending-insert coroutine first, so
the PENDING row always exists before it's updated. Stale PENDING rows left by
a killed process are discarded lazily (`reconcileStalePending` on app start).
If the process died mid-payment and the confirming SMS arrives after restart,
the ingestion pipeline *reattaches* it: the session txnId is persisted in the
operation window at `begin()`, and a no-live-session confirmation updates that
still-PENDING row instead of inserting a duplicate. Adoption is guarded to
PENDING rows in SQL, so a confirmation can never rewrite a row the user
already cancelled. The QR flow runs through the same session lifecycle — it
used to bypass it entirely. The `PaymentState` sealed hierarchy carries
exactly the states the machine emits — dead QR/retry variants were removed so
the type reflects reality.

## SMS ingestion — two pipelines, one parser

The confirmation SMS is money-outcome truth, so its handling is the second
most careful area.

```
                 ┌─────────────────────────────┐
  bank SMS ─────▶│ SimpleSMSReceiver           │  priority-999 broadcast
                 │ (RECEIVE_SMS)               │  receiver; goAsync + 8s cap
                 └──────────────┬──────────────┘
                                │
                 shouldProcessSMS() + tryClaimSms()
                                │
                                ▼
                        SmsTransactionParser.parse()   ← PURE, Context-free, tested
                                            │
                                SmsIngestionPipeline.ingest()
                                            │
                     ┌──────────────────────┴───────────────────────┐
                     ▼                                               ▼
          active session? update its row              no session (QR/legacy)?
          via PaymentSessionManager.onSmsConfirmed     insert a fresh row
                     └──────────────────┬──────────────────────────┘
                                        ▼
                            launch PaymentResultActivity
```

- [`SmsTransactionParser`](../app/src/main/java/com/flowpay/app/payment/sms/SmsTransactionParser.kt)
  holds all the bank-SMS matching logic — pure, Context-free, and tested
  against a per-bank corpus. [`TransactionDetector`](../app/src/main/java/com/flowpay/app/helpers/TransactionDetector.kt)
  is the stateful shell around it: the SharedPreferences-backed *operation
  window* (only SMS arriving while a payment is in flight are eligible;
  sized to the verification deadline plus a grace margin, so a slow bank SMS
  is never dropped here while the session still awaits it) and the dedup
  that stops a redelivered SMS broadcast from double-processing one message.
- **Dedup** is `tryClaimSms` (a body-only normalized-key claim) plus
  `@Synchronized processSMS` re-checking the operation window under lock.
- [`SmsIngestionPipeline`](../app/src/main/java/com/flowpay/app/receivers/SmsIngestionPipeline.kt)
  is the single path from a claimed SMS to an outcome — parsing, session
  confirmation or orphaned-row reattach, persistence, broadcasts, the result
  notification, and the result-screen launch. The debug SMS-injection tool
  drives the same pipeline, so tests exercise the exact production path (see
  [TESTING.md](TESTING.md)).
- There used to be a second ingestion path, a `NotificationListenerService`
  fallback for when `RECEIVE_SMS` was denied. It was removed: the setting
  that would have enabled it was never wired to anything in the UI, so it
  could never actually run, while still sitting in the manifest as an
  exported `BIND_NOTIFICATION_LISTENER_SERVICE` component. `RECEIVE_SMS` is
  the only SMS ingestion path now.

## Telephony and the call overlay

[`CallStateCoordinator`](../app/src/main/java/com/flowpay/app/telephony/CallStateCoordinator.kt)
is the single app-wide call-state listener; `PaymentSessionManager` consumes
its events. [`CallManager`](../app/src/main/java/com/flowpay/app/managers/CallManager.kt)
places the actual `ACTION_CALL` dial (the DTMF 123Pay string is built and
validated by the pure, tested
[`Upi123CallStringBuilder`](../app/src/main/java/com/flowpay/app/payment/Upi123CallStringBuilder.kt))
and manages call audio.
[`CallOverlayService`](../app/src/main/java/com/flowpay/app/services/CallOverlayService.kt)
draws a `TYPE_APPLICATION_OVERLAY` window during the call so the user has a UI
anchor, and mirrors `PaymentState` into result dialogs. Its overlay watchdog
and the user "End call" path notify the session **before** any best-effort
UI/audio cleanup, and can't be skipped by an exception in that cleanup.

## UI

Screens are Jetpack Compose. `MainActivity`, `SettingsActivity` and
`TransactionHistoryActivity` follow the intended pattern: **ViewModel +
StateFlow, collected in Compose**, with Activity↔Compose one-shot events
carried by a shared ViewModel
([`MainViewModel`](../app/src/main/java/com/flowpay/app/viewmodel/MainViewModel.kt))
rather than static callbacks.

`SetupActivity` and `TestConfigurationActivity` are older and have **no
ViewModel**: they hold screen state in local `remember { mutableStateOf }` and
talk to their helpers through a `UICallback` interface. They are listed under
"Known deliberate simplifications" below — they are first-run screens with no
state worth surviving process death, and converting them buys little.

All permission and activity results go through `ActivityResultContracts`
launchers — there are no `onActivityResult` / `onRequestPermissionsResult`
overrides and no static mutable state bridging the UI.

Two screens stay classic Views by design: `QRScannerActivity` (a CameraX
`PreviewView` is a View regardless) and the `CallOverlayService` overlay
(`ComposeView` inside a `TYPE_APPLICATION_OVERLAY` window needs hand-rolled
lifecycle/saved-state owners — a known bug source, unacceptable in the window
that supervises a live payment).

Colors and copy each have a single source of truth. Compose colors come from
the tokens in [`ui/theme/Color.kt`](../app/src/main/java/com/flowpay/app/ui/theme/Color.kt)
(one named value per visual role; `statusColor(status)` maps a transaction
status to its color everywhere), layout colors from `colors.xml`, and all
user-visible strings from `strings.xml`. Two CI gates enforce it: no inline
`Color(0x…)` outside `ui/theme` (or hex in a layout), and no single-line
hardcoded `Text("…")` — so a new screen can't quietly reintroduce the drift
that once spread ~14 near-identical greys across the codebase.

## Persistence

Room, local-only, encrypted at rest with SQLCipher. The database passphrase
is a random key wrapped by a **non-exportable Android Keystore key**, so it
never leaves hardware. If that wrapping key is ever lost or invalidated (OS
update, keystore corruption, some device restores) while the wrapped blob
survives, [`DatabaseKeyManager`](../app/src/main/java/com/flowpay/app/data/DatabaseKeyManager.kt)
recovers rather than throwing — it discards the stale blob, regenerates the
key, and the migrator sets the now-unreadable database aside so the app
starts fresh instead of crash-looping on its first database touch. This runs
lazily on `Dispatchers.IO`, never the main thread. Schema is at version 3 —
the only version this codebase has ever produced — with the exported schema
committed under `app/schemas/`. There are no migrations to carry yet;
`fallbackToDestructiveMigration` is deliberately absent, so the next schema
change must ship a real migration and a test for it rather than silently
wiping payment history. The raw SMS body is never persisted — only a
constructively-built, privacy-safe excerpt from extracted fields (a CI grep
gate blocks reintroducing raw-body logging).

## Components (AndroidManifest)

- **Activities**: `MainActivity` (sole LAUNCHER, exported), plus Setup /
  TestConfiguration / QRScanner / PaymentResult (`singleTask`) /
  TransactionHistory / Settings (all `exported=false`).
- **Services**: `CallOverlayService`.
- **Receiver**: `SimpleSMSReceiver` (priority-999, guarded by `BROADCAST_SMS`).
- **Debug only**: `DebugSmsInjectionReceiver` lives in `src/debug/` and is
  absent from release builds entirely.

Permissions are deliberately minimal and payment-scoped: `CALL_PHONE`,
`READ_PHONE_STATE`, `ANSWER_PHONE_CALLS` (the overlay's End-call button),
`RECEIVE_SMS` (never `READ_SMS` — the inbox is never read), `CAMERA`,
`READ_CONTACTS`, `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `VIBRATE`, and
`MODIFY_AUDIO_SETTINGS`. No `INTERNET`, no location, no storage.

## Threat model & trust boundaries

See [SECURITY.md](../SECURITY.md) for the full disclosure policy and threat
table. In short: the app triggers the dialer and reads confirmation SMS
locally; the UPI PIN is entered directly into the bank's IVR/dialer and is
never seen by Flowpay; no accessibility service is used, so the app cannot
read the screen or other apps.

## Known deliberate simplifications

Honest about what isn't consolidated, and why:

- **`CallManager` is instantiated per-caller** (main flow, test screen,
  overlay service) and the legacy generic `initiateCall()` path still uses a
  deprecated per-call `PhoneStateListener`. Both are reachable only from the
  `TestConfiguration` connectivity-test screen, a narrow, low-traffic surface
  that doesn't justify rewiring to the app-scoped `CallStateCoordinator` for
  its own sake.
- **`PaymentResultActivity` remains a classic-View screen.** It's launched
  from background receivers with specific window/`singleTask` behavior that a
  Compose rewrite would have to reproduce exactly for no user-facing gain —
  pattern purity alone isn't worth the regression surface on a
  payment-outcome screen.
- The **permissive SMS matcher** is intentional — banks phrase confirmations
  inconsistently, and the downstream tiers (a debit with a mismatched amount
  is dropped outright — it isn't this payment's confirmation, and the
  operation window stays open for the real one; `Failed` on a failure
  keyword) are the safety net that keeps permissiveness from ever producing
  a false SUCCESS. It is permissive, not naive: body
  keywords match on word boundaries (with "YES"/"BOB" requiring the full bank
  phrase so promo SMS can't enter the pipeline), amount comparison is
  paise-exact, and extraction skips balance figures ("Avl Bal Rs …") in
  favour of the transaction amount. Growing the test corpus is the guardrail.
  Direction is read debit-first: many banks narrate both sides of one payment
  ("A/c XX556 debited for Rs 500; KIRANA STORE credited"), and taking such a
  body for an incoming credit would make the pipeline drop our own
  confirmation as unrelated.
