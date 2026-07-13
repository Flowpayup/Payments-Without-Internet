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
Flowpay is distributed as a directly-installed APK and (being fully FOSS) is
suitable for F-Droid-style stores. See the README's "Verify a build" note for
how to check a release APK against its signing certificate.

### How can a payment succeed if the app can't reach the internet?

It isn't the app that reaches your bank — it's your phone's cellular
connection, over the same rails a feature phone uses. Flowpay dials `*99#` or
the 123Pay IVR for you and gives you a smartphone UI to start from; the
actual authorization happens between you, your telecom operator, your bank,
and NPCI, exactly as if you had dialed the code yourself.

### What does "UNVERIFIED" mean on a transaction?

It means the payment call completed but **no confirmation SMS arrived from
your bank before the deadline**. Flowpay deliberately never guesses: it won't
call a payment successful just because the call connected or lasted a while.
UNVERIFIED means "we don't know" — the payment may or may not have gone
through, so check your bank statement or SMS inbox before retrying. This is
the honest-by-design behavior; see [ARCHITECTURE.md](ARCHITECTURE.md).

### What are the carrier / USSD limitations?

`*99#` is a synchronous telco menu walk over GSM signalling. Each round trip
takes roughly a minute, sessions can time out, and behavior varies by
operator and SIM. Flowpay accounts for the timeouts but can't make the
underlying rail faster. Dialing `*99#` or 123Pay may incur a small charge
depending on your plan — check with your operator.

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
