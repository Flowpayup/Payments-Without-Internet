# Device QA checklist

Some behavior can only be verified on a real Android device with an Indian SIM
(and sometimes a UPI-linked bank account) — the `*99#`/123Pay call flow, the
in-call overlay over the system dialer, background-activity launches, and OEM
audio/process behavior are not reproducible on an emulator without a SIM. CI
and JVM/Robolectric tests cover the pure logic; the items below are the manual
checks a maintainer should run against a device before a release. Each links to
the change that introduced it.

## Payment outcome surfaces

- [ ] **UNVERIFIED reaches the screen.** Start a payment, let the call end
      normally, and withhold the confirmation SMS (or use the debug
      SMS-injection tool and simply don't inject). At the verification deadline
      the result screen must appear showing "No Confirmation Received" in
      neutral grey with a question glyph — **never** a green "Payment
      Successful". (Phase 1: UNVERIFIED surfacing.)
- [ ] **Result screen reachable with overlay permission revoked.** Same flow
      with "Draw over other apps" denied — confirm whether the result screen
      still launches; if not, this is the gap Phase 2.8 (notification fallback)
      addresses.

## In-call overlay

- [ ] **"Call volume lowered" pill is truthful.** During a 123Pay call the pill
      appears only after the volume is actually lowered; if the volume change
      fails the pill stays hidden. (Phase 1: honest audio indicator.)
- [ ] **Ringer/notifications survive a successful payment.** After a confirmed
      UPI 123 success (notification-listener path), the phone's ring and
      notification volumes are unchanged — only the in-call voice stream is
      silenced. (Phase 1: AudioStateManager fix.)

## QR / clipboard

- [ ] **VPA clipboard is wiped.** After a QR payment flow ends, the payee VPA is
      no longer on the clipboard (paste into a notes app to confirm). Verify on
      an Android 13+ device that the clip was flagged sensitive during the flow.
      (Phase 1: clipboard hygiene.)

## Settings

- [ ] **Clear App Data empties history.** Make a few payments, then
      Settings → Clear App Data; after relaunch the transaction history is
      empty and the app returns to Setup. (Phase 1: real data wipe.)
