# Security Policy

Flowpay handles UPI 123Pay payment flows, USSD dialing, and SMS parsing. Please treat security issues with appropriate care: **do not file public GitHub issues for vulnerabilities.**

## Supported versions

Security fixes land on the latest tagged release and on `main`. Older releases
are not backported — update to the newest release.

| Version             | Supported |
|---------------------|-----------|
| 1.0.x               | ✅        |
| `main` (unreleased) | ✅        |
| < 1.0               | ❌ pre-release, never distributed |

## Verifying you have a genuine build

**No APK has been published.** This repository distributes source only —
build it yourself with [Running it](README.md#running-it). Any APK you
encounter claiming to be Flowpay did not come from here.

If a signed build is ever published, the signing certificate is the trust
anchor. Fake UPI apps are common in India, and an APK claiming to be Flowpay
can come from anywhere; the fingerprint below is how you would tell. It is
committed now, ahead of any release, so it cannot be back-dated later.

```
Release signing certificate SHA-256:
  64:28:F5:18:05:90:8F:85:2F:A3:63:76:A4:D2:A9:53:31:D1:1D:0A:03:DC:16:12:25:06:EA:E5:D3:68:A8:82

Subject: CN=Flowpay, O=Flowpay, L=Bengaluru, C=IN
```

Check any APK against it:

```bash
apksigner verify --print-certs flowpay-vX.Y.Z.apk
```

Compare against the fingerprint committed **here**, not against the release
notes — release notes are written by whoever published the release, so checking
them against themselves proves nothing. The fingerprint above is tamper-evident
through git history.

A change to this fingerprint in a future version means the signing key changed.
That is not normal; ask before installing.

Building from source remains fully supported and needs none of this — see
[Running it](README.md#running-it).

## Reporting a vulnerability

Email **`flowpay28@gmail.com`** with the subject line:

> `Flowpay UPI — Security report — <one-line summary>`

Please include:

1. A clear description of the issue and the affected component (file path, screen, or flow).
2. Steps to reproduce on a debug build (`./gradlew assembleDebug`).
3. Impact — what an attacker can read, modify, or trigger.
4. Suggested fix, if you have one.
5. Your contact info if you want credit in the eventual fix commit.

If the issue involves a leaked secret in the codebase or git history, include the commit SHA.

## Disclosure timeline

- **Within 72 hours** — acknowledgement that the report was received.
- **Within 14 days** — initial assessment shared with the reporter.
- **Within 90 days** — fix landed in `main` and credit posted (if requested), or a written explanation of why disclosure should be delayed.

Industry-standard 90-day window. If a fix lands sooner, credit is posted sooner.

## Scope

In scope:

- The Android app source under `app/src/main/`.
- The Gradle build configuration.
- The Room database schema and migrations.
- The USSD dialer flow, SMS parser, and call-overlay service.

Out of scope:

- Issues with third-party Android system APIs we use (`TelecomManager`, `SmsManager`, etc.) — those belong to Google/AOSP.
- Issues with the user's bank or UPI provider — those belong to the bank/NPCI.
- Theoretical issues that require an already-rooted device.

## Safe harbour

Researchers acting in good faith, sticking to the disclosure timeline above and not exfiltrating real user data, will not be pursued legally for the testing itself.

## Data handling & threat model

What the app stores, where, and for how long:

| Data | Where | Lifetime | Leaves the device? |
|------|-------|----------|--------------------|
| Transaction rows (amount, status, bank, bank ref, counterparty name, privacy-safe summary) | Room DB `flowpay_database`, app-private storage | Until the user deletes them or uninstalls | **Never** — excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`) |
| Raw bank SMS bodies | **Not stored** (since schema v3) | — | The verbatim SMS stays in the user's SMS inbox app only |
| Active payment-operation window (expected amount, recipient number, ~10-min deadline) | `payment_operation` SharedPreferences | Cleared on confirmation or timeout | Never — excluded from backup |
| UPI PIN | **Never seen by the app** — entered into the bank's IVR/dialer flow | — | — |

Design rules the code enforces:

- **No INTERNET permission.** The app cannot transmit anything, by construction.
- **`READ_SMS` is not requested.** The app only receives incoming SMS (`RECEIVE_SMS`) while a payment operation is active; it never reads the inbox.
- **SMS are only inspected inside an explicit payment window** — a ~10-minute operation (a 10-minute verification deadline plus a 30-second grace margin) started when the user initiates a transfer; outside it, incoming messages are never read. Bank matching itself is deliberately permissive keyword matching (field-proven against real bank templates), so the operation window — not sender authentication — is the primary control.
- **Stored summaries are built constructively** from parsed fields (amount/status/bank/ref), so account numbers and balances in message prose can never reach the database.
- **The notification-listener fallback is opt-in** when `RECEIVE_SMS` is granted, halving the SMS ingestion surface by default.
- CI rejects log statements that interpolate SMS bodies in payment-critical packages.

## Known deferred issues

The CI baselines (`app/lint-baseline.xml`, `app/detekt-baseline.xml`) freeze
pre-existing static-analysis findings so new code must come in clean. Two are
worth naming because they look security-relevant and are deliberate:

- **`StaticFieldLeak` in `CallOverlayService`** — the service holds a static
  reference to itself so the overlay can be addressed from a broadcast context.
  It is cleared in `onDestroy()`, so the reference is bounded by the service
  lifecycle rather than leaked indefinitely.
- **`ExportedReceiver` on `DebugSmsInjectionReceiver`** — an unguarded exported
  receiver, in the **debug** source set only. It exists so a developer can
  replay a bank SMS through the live pipeline via `adb` without a real SIM, and
  requiring a permission would defeat that. It is absent from release builds
  entirely (`src/debug/AndroidManifest.xml`), not merely disabled at runtime.
  Verified by inspecting a built release APK, not just by reading the manifest.

Two more are deliberate rather than baselined, and are named here because both
look like oversights:

- **A `SharedPreferences` read on the main thread in the SMS path.**
  `SimpleSMSReceiver.onReceive` checks whether a payment window is open before
  its `goAsync()` hop, so the first such read in a process does synchronous
  disk I/O on the main thread — for every SMS the device receives, not just
  bank ones. StrictMode is enabled in debug builds with `penaltyLog` and does
  flag it. The file is tiny and the read is cached thereafter; moving the check
  into the coroutine touches the money path's entry point, which is not a
  change worth making outside a release with a device gate behind it.

- **`-assumenosideeffects` strips `Log.e` as well as the rest.** Release builds
  therefore carry no logging at all, including the "Keystore unusable"
  diagnostic. This is intentional — logs are the main way a payments app leaks
  PII, and the app has no crash reporting and no `INTERNET` permission — but it
  does mean a user-reported problem comes with a stack trace and nothing else.
  The R8 `mapping.txt` archived by the release workflow is what makes that
  trace readable.

If you spot something else with security implications hiding behind a baseline,
please report it.
