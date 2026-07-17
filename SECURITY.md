# Security Policy

Flowpay handles UPI 123Pay payment flows, USSD dialing, and SMS parsing. Please treat security issues with appropriate care: **do not file public GitHub issues for vulnerabilities.**

## Supported versions

Only the `main` branch is supported. There are no tagged releases yet.

| Branch | Supported |
|--------|-----------|
| `main` | ✅        |

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

The CI lint baseline (`app/lint-baseline.xml`) captures pre-existing findings.

- ~~`CallManager.endCall` uses `TelecomManager.endCall` without holding `ANSWER_PHONE_CALLS`.~~ Fixed: the permission is now declared and requested with the phone-permission group; without it the in-call End button degrades to a "hang up manually" prompt.

If you spot something else with security implications hiding behind the baseline, please report it.
