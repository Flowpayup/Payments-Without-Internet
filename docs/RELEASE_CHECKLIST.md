# Release checklist — physical-device gate

> **Status: never completed.** This gate has not been run end to end against
> the current code. Parts of it have been exercised — the money path via
> injected SMS, and a minified release build running the camera — but the
> items needing a live UPI-linked SIM have not.
>
> A signed release build now exists, so nothing except this list stands
> between the code and a published APK. That makes running it the gate, not a
> formality. The README's *Project status* section lists the unproven items,
> so anyone installing knows what they are ahead of.

Layers 1–3 in [TESTING.md](TESTING.md) are hermetic: they run without a SIM
or a bank, and they can't catch a telco changing `*99#` behavior or a bank
quietly reformatting its SMS template. This checklist is Layer 4 — the
one human-in-the-loop check that catches what the hermetic layers can't,
run once per release against real hardware before publishing.

This is deliberately manual and deliberately honest about being manual:
no CI can dial a real Indian SIM.

This is the **only** device gate — it absorbed the former `DEVICE_QA.md`, so
there is one list to work through rather than two overlapping ones. The
outcome-surface items below can be driven without a SIM using the debug
SMS-injection tool; see the money-path scenarios in
[TESTING.md](TESTING.md#money-path-scenarios-worth-re-running). Only the
`*99#`, real-₹1, and QR items genuinely require a live UPI-linked SIM.

If an item can't be run on the hardware you have, leave it unchecked and say
so in the release notes. An unchecked box is information; a checked box that
wasn't actually verified is a lie the next maintainer inherits.

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
      pipeline), confirm the parsed VPA/amount are correct, and confirm it
      dials `*99*1*3#` (the USSD scan-to-pay branch) — note this is a
      *different rail* from manual entry, which places a 123Pay IVR call.
      Run this on a non-Jio SIM; `*99#` USSD does not exist on Jio.
- [ ] **Release build, not debug.** Run at least the QR scan and one payment
      against a **minified release** APK. R8 shrinking is only exercised in the
      release variant, and the CameraX config bootstrap it touches is
      reflective — a crash here would appear on users' phones and nowhere else.
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
      permissions listed in `AndroidManifest.xml` (phone — call + phone state +
      answer, SMS, camera, contacts, overlay, notifications, vibrate,
      modify-audio-settings) and that each request has a clear, contextual
      trigger — no permission requested before it's needed. On Android 13+ the
      notifications prompt is expected; it backs the payment-result
      notification.
- [ ] **No new lint/detekt findings** and
      `./gradlew test :app:lintDebug detekt koverVerify` is green on the exact
      commit being released (should already be true from CI, but re-confirm on
      the release commit specifically).

## Outcome surfaces

- [ ] **An unconfirmed payment surfaces nothing.** Start a payment, let the
      call end normally, and never send the confirmation SMS (with the debug
      tool: run `START_OPERATION` and simply don't inject). At the verification
      deadline there must be **no** result screen and **no** notification, and
      the row must be gone from Transaction History — no `PENDING` row left
      behind, and certainly no green "Payment Successful".

      This is the visible half of the *no confirmation, no record* rule: a
      payment the bank never confirmed is one the app cannot report on, so it
      leaves no trace rather than an outcome the user can't act on. The app
      used to show an `UNVERIFIED` "No Confirmation Received" screen here; if
      you see one, this rule has regressed. (Rows written by older builds still
      render as `UNVERIFIED` in history by design — that is not a regression.)

- [ ] **A late-but-genuine confirmation still lands.** The SMS operation window
      deliberately outlives the verification deadline by 30 seconds. Inject a
      matching confirmation just after the deadline: it must be recorded as a
      standalone transaction rather than dropped.

## Hardware-only behavior

These need a real device and can't be reproduced on an emulator — OEM audio
routing, overlays over the system dialer, and real clipboard behavior.

- [ ] **"Call volume lowered" pill is truthful.** During a 123Pay call the pill
      appears only after the volume is actually lowered; if the volume change
      fails, the pill stays hidden.
- [ ] **Ringer/notifications survive a payment.** Across a full payment
      (including via the notification-listener fallback), the phone's ring and
      notification volumes are unchanged, and the in-call volume is restored
      when the call ends. Only `CallManager` should ever touch call audio.
- [ ] **VPA clipboard is wiped.** After a QR payment flow ends, the payee VPA
      is no longer on the clipboard (paste into a notes app to confirm). On
      Android 13+, verify the clip was flagged sensitive during the flow.
- [ ] **Clear App Data empties history.** Make a few payments, then
      Settings → Clear App Data; after relaunch, Transaction History is empty
      and the app returns to Setup.

## After the checklist

Record which carrier(s) and bank(s) this checklist was run against in the
release notes — that's useful signal for anyone hitting a carrier-specific
issue later. If any step fails, do not publish; file the failure as an
issue first (see the bug report template, which specifically asks for
carrier/SIM details) and fix or explicitly document the limitation before
cutting the release.
