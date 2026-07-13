# Release checklist — physical-device gate

Layers 1–3 in [TESTING.md](TESTING.md) are hermetic: they run without a SIM
or a bank, and they can't catch a telco changing `*99#` behavior or a bank
quietly reformatting its SMS template. This checklist is Layer 4 — the
one human-in-the-loop check that catches what the hermetic layers can't,
run once per release against real hardware before publishing.

This is deliberately manual and deliberately honest about being manual:
no CI can dial a real Indian SIM.

## Prerequisites

- A physical Android device, API 29+, with an Indian SIM in slot 1 (dual-SIM
  optional but recommended — it also re-validates the voice-SIM-mismatch
  warning in `MainActivityHelper.warnIfVoiceSimMismatch`).
- A UPI-linked bank account on that SIM, with a small balance (a few rupees
  covers both real-money checks below).
- The release-candidate build installed (`./gradlew installRelease` with a
  keystore configured, or a signed APK from the draft GitHub Release).

## Checklist

- [ ] **First-run setup** completes: bank selection, primary SIM selection,
      disclaimer, connectivity test.
- [ ] **`*99#` USSD flow**: initiate a payment, confirm the call dials, confirm
      the on-screen overlay appears and tracks call state correctly, confirm
      the call terminates cleanly (both a normal hangup and the in-app
      "End call" button).
- [ ] **One real ₹1 (or similarly small) UPI 123Pay transaction**, end to
      end: dial → overlay → bank SMS arrives → `PaymentResultActivity` shows
      the correct amount/status → the row appears correctly in Transaction
      History.
- [ ] **QR scan flow**: scan a real merchant UPI QR (camera + ZXing decode
      pipeline), confirm the parsed VPA/amount are correct, confirm it feeds
      into the same downstream flow as manual entry.
- [ ] **SMS confirmation timing**: note how long the bank SMS actually took
      to arrive; confirm it's comfortably inside `PaymentSessionManager`'s
      10-minute verification deadline on this operator.
- [ ] **Failure path**: deliberately trigger a failure (e.g. exceed the
      123Pay per-transaction cap, or cancel from the overlay mid-call) and
      confirm the app reports it correctly rather than hanging or
      mis-reporting success.
- [ ] **Dual-SIM mismatch warning** (if testing on a dual-SIM device): set
      the default calling SIM to something other than the UPI-registered
      one and confirm the warning toast appears before dialing.
- [ ] **Permissions**: fresh install, confirm the app only ever requests the
      permissions listed in `AndroidManifest.xml` (phone, SMS, camera,
      overlay, contacts) and that each request has a clear, contextual
      trigger — no permission requested before it's needed.
- [ ] **No new lint/detekt findings** and `./gradlew test :app:lintDebug detekt`
      is green on the exact commit being released (should already be true
      from CI, but re-confirm on the release commit specifically).

## After the checklist

Record which carrier(s) and bank(s) this checklist was run against in the
release notes — that's useful signal for anyone hitting a carrier-specific
issue later. If any step fails, do not publish; file the failure as an
issue first (see the bug report template, which specifically asks for
carrier/SIM details) and fix or explicitly document the limitation before
cutting the release.
