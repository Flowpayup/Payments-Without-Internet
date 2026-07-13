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
  every terminal transition (Success/Failed/Cancelled/NeedsReview/Timeout),
  and — since Phase 3 — that `onUserCancelled`/`onCallNeverStarted` reach a
  correct, idempotent terminal state even when called defensively more than
  once (the scenario `CallOverlayService`'s exception-handling paths rely on).
- **`Upi123CallStringBuilderTest`** — the DTMF dial-string builder, golden
  strings plus injection-neutralization cases.
- **`QRCodeParserTest`** + **`QRCodeAnalyzerDecodeTest`** — UPI QR URI
  parsing, and (since the ML Kit → ZXing swap) a decode corpus that encodes
  those same URIs into real QR bitmaps and decodes them back through the
  live analyzer pipeline.

## Layer 2 — Instrumented tests (CI, emulator, path-filtered + weekly)

Real Android framework classes on an emulator, via
`./gradlew connectedDebugAndroidTest`. Slower and occasionally flaky, so
this doesn't run on every push — see
[instrumented.yml](../.github/workflows/instrumented.yml).

- **`MigrationTest`** — every Room schema migration
  ([Migrations.kt](../app/src/main/java/com/flowpay/app/data/migrations/Migrations.kt))
  against `MigrationTestHelper`, confirming existing rows and columns
  survive a real upgrade.

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
# 1. Install a debug build
./gradlew installDebug

# 2. Start a payment operation window (same as tapping "Pay" in the app)
adb shell am broadcast -a com.flowpay.app.debug.START_OPERATION \
  --es operation_type UPI_123 \
  --es expected_amount 500 \
  --es phone_number 9876543210

# 3. Inject a bank confirmation SMS — any redacted sample from the
#    SmsTransactionParserTest corpus works
adb shell am broadcast -a com.flowpay.app.debug.INJECT_SMS \
  --es sender VK-HDFCBK \
  --es body "Rs.500.00 sent to KIRANA STORE from HDFC Bank A/c **1234 via UPI ref 512233440091"
```

Step 3 should launch `PaymentResultActivity` showing a successful ₹500
payment, and the transaction should appear in Transaction History — the
same result a real bank SMS produces, reached without a SIM, a bank, or a
phone call.

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
