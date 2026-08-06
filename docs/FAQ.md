# FAQ

### Why does the app have no `INTERNET` permission?

Because it genuinely never talks to the network. Flowpay is a client over
payment rails that already exist on the device — `*99#` USSD and UPI 123Pay
IVR — which run over the cellular voice/signalling channel, not IP. There is
no backend, no analytics, and no telemetry, so there is nothing for an
`INTERNET` permission to do. The absence of the permission is the strongest
possible proof of that: an app without it *cannot* exfiltrate your data, no
matter what its code claims.

### Why `RECEIVE_SMS` but not `READ_SMS`?

`RECEIVE_SMS` lets the app see an SMS *as it arrives*; `READ_SMS` would let
it read your existing inbox. Flowpay only needs the former, and only while a
payment is in progress, to catch the bank's confirmation message. It never
reads your inbox, so it never requests `READ_SMS`. The confirmation SMS is
parsed locally and only a privacy-safe excerpt (built from extracted fields,
never the raw message body) is stored — a CI check blocks any code that
would log the raw body.

### Does the app ever see my UPI PIN?

No. Your PIN is entered only inside your bank's own `*99#` / IVR flow.
Flowpay triggers the dialer and shows a UI anchor; it uses **no accessibility
service**, so it cannot read your screen, capture input, or see other apps.
There is no path by which it could observe your PIN.

### Why isn't it on the Google Play Store?

Google Play's restricted-permission policy effectively bars apps that use SMS
permissions for this kind of use case. Rather than compromise the design,
Flowpay is published as source that you build yourself. Being fully FOSS, it
would also suit an F-Droid-style store, but it is not in one today and no
prebuilt APK is distributed from here.

### How can a payment succeed if the app can't reach the internet?

It isn't the app that reaches your bank — it's your phone's cellular
connection, over the same rails a feature phone uses. Flowpay dials `*99#` or
the 123Pay IVR for you and gives you a smartphone UI to start from; the
actual authorization happens between you, your telecom operator, your bank,
and NPCI, exactly as if you had dialed the code yourself.

### Why did a payment I started not show up at all?

Because **no confirmation SMS arrived from your bank**. Flowpay records a
payment only when the bank confirms it — it deliberately never guesses, and
won't call a payment successful just because the call connected or lasted a
while. If the confirmation never comes, the attempt is discarded rather than
left behind as an outcome you can't act on, so check your bank statement or
SMS inbox to see whether the money actually moved. Confirmations that arrive
a little late are still picked up. This is the honest-by-design behavior; see
[ARCHITECTURE.md](ARCHITECTURE.md).

Transactions marked `UNVERIFIED` in your history are from an older version,
which recorded unconfirmed attempts instead of discarding them; they mean
the same thing — Flowpay doesn't know whether that payment went through.

### What are the carrier / USSD limitations?

`*99#` is a synchronous telco menu walk over GSM signalling. Each round trip
takes roughly a minute, sessions can time out, and behavior varies by
operator and SIM. Flowpay accounts for the timeouts but can't make the
underlying rail faster.

`*99#` also does not work on Jio at all — USSD rides the legacy GSM signalling
channel, and Jio is an all-IP (VoLTE) network. That is exactly the gap UPI
123Pay was created to close, which is why Flowpay ships the 123Pay IVR rail
alongside USSD: Jio users pay through IVR with the full feature set, no
compromise.

### Why does it need my carrier / SIM in setup?

The 123Pay IVR call goes out on the device's **default voice SIM**, which may
not be the UPI-registered SIM you intend to pay from. On dual-SIM devices,
Flowpay warns you before dialing if the two don't match, so a payment doesn't
fail mysteriously.

### Is it safe to build and run myself?

Yes — and you're encouraged to. `./gradlew assembleDebug` produces a working
APK with no device, and `./gradlew installDebug` runs it on one. See
[TESTING.md](TESTING.md) for how payment outcomes are verified (including a
debug tool that replays a bank SMS through the live pipeline without needing
a real bank), and [CONTRIBUTING.md](../CONTRIBUTING.md) to get started.
