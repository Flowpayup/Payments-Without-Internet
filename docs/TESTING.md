# Testing Flowpay

Flowpay's core claim — a payment outcome is only ever recorded from a
confirming bank SMS, never from call duration — can't be end-to-end tested
the way a normal app is: there's no test double for an Indian bank, a UPI
switch, or a telco's IVR menu. This is Flowpay's honest answer to "how do
you know it works" without one.

Verification happens in four layers, each covering what the layer below it
cannot.

## Layer 1 — Hermetic unit tests (CI, every push)

Pure logic, no Android framework, no device. Runs in seconds via
`./gradlew test`.

- **`SmsTransactionParserTest`** — the bank-SMS matching pipeline
  ([SmsTransactionParser.kt](../app/src/main/java/com/flowpay/app/payment/sms/SmsTransactionParser.kt)),
  exercised end-to-end via `parse()` across a corpus covering every
  supported bank, success/failure/credit/non-UPI-debit-alert shapes, and
  amount-format edge cases. `SmsParsingRegexTest` covers the individual
  regex-backed building blocks (bank detection, dedup key convergence,
  amount tolerance) the same way.
- **`PaymentSessionManagerTest`** — the payment lifecycle state machine
  ([PaymentSessionManager.kt](../app/src/main/java/com/flowpay/app/payment/PaymentSessionManager.kt)),
  driven with `kotlinx-coroutines-test` and fakes for the call-state source
  and the persistence layer. Covers the PENDING-before-dial invariant,
  every terminal transition (Success/Failed/Cancelled/Timeout — where a
  Timeout discards the unconfirmed row and a later SMS cannot resurrect a
  cancelled one),
  and — since Phase 3 — that `onUserCancelled`/`onCallNeverStarted` reach a
  correct, idempotent terminal state even when called defensively more than
  once (the scenario `CallOverlayService`'s exception-handling paths rely on).
- **`Upi123CallStringBuilderTest`** — the DTMF dial-string builder, golden
  strings plus injection-neutralization cases.
- **`QRCodeParserTest`** + **`QRCodeAnalyzerDecodeTest`** — UPI QR URI
  parsing, and (since the ML Kit → ZXing swap) a decode corpus that encodes
  those same URIs into real QR bitmaps and decodes them back through the
  live analyzer pipeline.

## Layer 2 — Instrumented tests (none at present)

There are currently **no instrumented tests**, and no emulator job in CI.

The only one that ever existed was `MigrationTest`, covering Room migrations
`1→2` and `2→3`. Those were removed: the first commit of this codebase already
declared schema `version = 3`, so no v1 or v2 database has ever existed on any
device, the migrations could never execute, and the v1/v2 schema JSON they were
validated against had been written by hand rather than emitted by the Room
compiler. Keeping an emulator matrix in the release path to guard unreachable
code was cost without coverage.

This layer comes back with the next schema change, which must land together
with its migration, the compiler-generated schema JSON, a `MigrationTest`, and
the emulator workflow to run it. All four are recoverable from git history.

## Layer 3 — Debug SMS injection (any emulator, on demand)

Layer 1 proves the parsing logic is correct in isolation. It does not prove
the *wiring* — that a broadcast reaching `SimpleSMSReceiver` actually flows
through session confirmation, persistence, and the result screen. Layer 3
closes that gap by driving the **live** pipeline
([SmsIngestionPipeline.kt](../app/src/main/java/com/flowpay/app/receivers/SmsIngestionPipeline.kt))
end-to-end on any emulator, without a real bank or a real call.

`DebugSmsInjectionReceiver` is compiled only into debug builds — the whole
`app/src/debug/` source set, including its manifest declaration, is absent
from release APKs. It is not gated by a runtime check; it does not exist in
a release build to gate.

```bash
# 1. Install a debug build and launch the app (a force-stopped app
#    receives no broadcasts at all)
./gradlew installDebug
adb shell am start -n com.flowpay.app/.MainActivity

# 2. Start a payment operation window (same as tapping "Pay" in the app)
adb shell "am broadcast -n com.flowpay.app/.receivers.DebugSmsInjectionReceiver \
  -a com.flowpay.app.debug.START_OPERATION \
  --es operation_type UPI_123 \
  --es expected_amount 500 \
  --es phone_number 9876543210"

# 3. Inject a bank confirmation SMS — any redacted sample from the
#    SmsTransactionParserTest corpus works
adb shell "am broadcast -n com.flowpay.app/.receivers.DebugSmsInjectionReceiver \
  -a com.flowpay.app.debug.INJECT_SMS \
  --es sender VK-HDFCBK \
  --es body 'Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091'"
```

Two details in those commands are load-bearing, both learned the hard way
on real hardware:

- **The `-n` component targeting is mandatory.** Android does not deliver
  *implicit* broadcasts (`am broadcast -a …` alone) to manifest-declared
  receivers — the command completes, prints a result, and the app never
  hears it. There is no error to notice; the injection just silently
  doesn't happen.
- **The whole `am` command is quoted** so it reaches the *device* shell as
  one string. Unquoted, the spaces in the SMS body are split into separate
  arguments on the device side and the broadcast carries a truncated body.

Step 3 should launch `PaymentResultActivity` showing a successful ₹500
payment, and the transaction should appear in Transaction History — the
same result a real bank SMS produces, reached without a SIM, a bank, or a
phone call.

### Money-path scenarios worth re-running

The same two broadcasts cover the cases that are easy to regress:

- **Dual-verb template.** Inject `AD-ICICIB` / *"ICICI Bank Acct XX556
  debited for Rs 500.00 on 29-Jul-26; KIRANA STORE credited. UPI:512233440091"*
  into a ₹500 window. It must confirm. Read as an incoming credit (the bug
  this guards) it would be dropped as unrelated and the payment would never
  appear.
- **Mismatched amount.** The same template for ₹900 against a ₹500 window
  must produce nothing and leave the window open, so a following ₹500
  confirmation still lands.
- **Unrelated incoming credit.** A genuine `credited`-only body during an
  open window must be ignored without closing the window.
- **Cancel really cancels.** Start a payment from the app, hit *Cancel
  payment* on the overlay, then inject a matching debit. Nothing should
  surface, and the row must stay CANCELLED —
  `adb shell run-as com.flowpay.app cat shared_prefs/payment_operation.xml`
  should show the window cleared.
- **No SMS, no record.** Open a window, inject nothing, and let the deadline
  pass: no result screen, no notification, and no row in either list.

## Layer 4 — Physical-device release gate (human, per release)

The three layers above are hermetic by design and deliberately cannot
prove the parts that actually touch a telco or a bank: whether `*99#`
still dials cleanly on a given operator, whether 123Pay's IVR flow still
matches the expected DTMF timing, and whether a bank's SMS template still
matches what the corpus expects. That requires a real device with a real
SIM. See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

## What this doesn't cover

Layers 1–3 are hermetic and can lag reality if a bank changes its SMS
template — that's exactly what Layer 4 exists to catch before a release
ships, and exactly why growing the Layer 1 corpus (see "Adding a bank SMS
template" in [CONTRIBUTING.md](../CONTRIBUTING.md)) is one of the most
valuable contributions this project can take.
