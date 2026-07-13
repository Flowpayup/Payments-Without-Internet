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
SUCCESS. When no SMS arrives before the deadline, the payment ends
`UNVERIFIED` — visibly distinct from both success and failure, never
silently assumed either way.

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
begin(phone, amount)
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
           Cancelled                         ├── amount mismatch ⇒ NeedsReview  │
                                             └── failure keyword ⇒ Failed       │
   no SMS before the 10-minute deadline ⇒ Timeout, and the PENDING row ⇒ UNVERIFIED
```

Every terminal transition `join()`s the pending-insert coroutine first, so
the PENDING row always exists before it's updated. Stale PENDING rows left by
a killed process are reconciled to `UNVERIFIED` lazily (`reconcileStalePending`
on app start). The `PaymentState` sealed hierarchy carries exactly the states
the machine emits — dead QR/retry variants were removed so the type reflects
reality.

## SMS ingestion — two pipelines, one parser

The confirmation SMS is money-outcome truth, so its handling is the second
most careful area.

```
                 ┌─────────────────────────────┐
  bank SMS ─────▶│ SimpleSMSReceiver           │  priority-999 broadcast
                 │ (RECEIVE_SMS, primary)      │  receiver; goAsync + 8s cap
                 └──────────────┬──────────────┘
                                │
  (fallback, opt-in / when      │      ┌──────────────────────────────┐
   RECEIVE_SMS ungranted) ─────▶│      │ FlowpayNotificationListener  │
                                │      └───────────────┬──────────────┘
                 shouldProcessSMS() + tryClaimSms()    │  (cross-pipeline dedup)
                                └───────────┬──────────┘
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
  window* (only SMS arriving while a payment is in flight are eligible) and
  the dedup that stops the two pipelines from double-processing one message.
- **Dedup** is two-layered: `tryClaimSms` (a normalized-key claim, so the
  notification listener's truncated text and the receiver's full PDU converge
  to the same key) and `@Synchronized processSMS` re-checking the operation
  window under lock — whichever pipeline enters first consumes it.
- Both pipelines share [`SmsIngestionPipeline`](../app/src/main/java/com/flowpay/app/receivers/SmsIngestionPipeline.kt),
  which is also what the debug SMS-injection tool drives, so tests exercise
  the exact production path (see [TESTING.md](TESTING.md)).

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

Screens are Jetpack Compose (`MainActivity`, `SetupActivity`,
`TestConfigurationActivity`, `SettingsActivity`, `TransactionHistoryActivity`)
following one pattern: **ViewModel + StateFlow, collected in Compose**, with
Activity↔Compose one-shot events carried by a shared ViewModel
([`MainViewModel`](../app/src/main/java/com/flowpay/app/viewmodel/MainViewModel.kt))
rather than static callbacks. All permission and activity results go through
`ActivityResultContracts` launchers — there are no `onActivityResult` /
`onRequestPermissionsResult` overrides and no static mutable state bridging
the UI.

Two screens stay classic Views by design: `QRScannerActivity` (a CameraX
`PreviewView` is a View regardless) and the `CallOverlayService` overlay
(`ComposeView` inside a `TYPE_APPLICATION_OVERLAY` window needs hand-rolled
lifecycle/saved-state owners — a known bug source, unacceptable in the window
that supervises a live payment).

## Persistence

Room, local-only, encrypted at rest with SQLCipher. The database passphrase
is a random key wrapped by a **non-exportable Android Keystore key**, so it
never leaves hardware. Schema is at version 3 with exported schemas and
migration tests (`MIGRATION_1_2`, `MIGRATION_2_3`) run on-device in CI. The
raw SMS body is never persisted — only a constructively-built, privacy-safe
excerpt from extracted fields (a CI grep gate blocks reintroducing raw-body
logging).

## Components (AndroidManifest)

- **Activities**: `MainActivity` (sole LAUNCHER, exported), plus Setup /
  TestConfiguration / QRScanner / PaymentResult (`singleTask`) /
  TransactionHistory / Settings (all `exported=false`).
- **Services**: `CallOverlayService`; `FlowpayNotificationListener`
  (notification-listener, opt-in SMS fallback).
- **Receiver**: `SimpleSMSReceiver` (priority-999, guarded by `BROADCAST_SMS`).
- **Debug only**: `DebugSmsInjectionReceiver` lives in `src/debug/` and is
  absent from release builds entirely.

Permissions are deliberately minimal: phone, `RECEIVE_SMS` (never `READ_SMS`
— the inbox is never read), camera, overlay, contacts, vibrate. No `INTERNET`.

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
  deprecated per-call `PhoneStateListener`. Both are used only by the
  `TestConfiguration` connectivity-test screen, whose USSD/123Pay dial flow
  can't be exercised without a real Indian SIM. Rewiring it to the
  app-scoped `CallStateCoordinator` is deferred rather than done unverified.
- **`PaymentResultActivity` remains a classic-View screen.** It's launched
  from background receivers with specific window/`singleTask` behavior; a
  blind Compose rewrite of a payment-outcome screen that can't be
  device-tested isn't worth the regression risk for pattern purity alone.
- The **permissive SMS matcher** (substring bank keywords, generic amount
  fallback) is intentional — banks phrase confirmations inconsistently, and
  the downstream tiers (`NeedsReview` on amount mismatch, `Failed` on a
  failure keyword) are the safety net that keeps permissiveness from ever
  producing a false SUCCESS. Growing the test corpus is the guardrail.
