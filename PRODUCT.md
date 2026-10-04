# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Primary: everyday Indian smartphone users who need to complete a UPI payment with zero or unreliable internet — dead zones, rural areas, basement shops, crowded events. Includes users on carriers where `*99#` USSD doesn't work at all (confirmed: Jio), who are routed to the UPI 123Pay IVR rail instead. Skews toward lower-end/Android-Go hardware (the release build is deliberately size-budgeted and ABI-restricted to arm64-v8a/armeabi-v7a for this reason) and includes users who may be less smartphone-fluent — the whole reason USSD/IVR rails still matter to them is that they, or the moment they're in, predate or fall outside always-on data. [Inferred from the shipped app's own README, copy, and build constraints; not confirmed with the user directly.]

Job: send or receive a real UPI payment *right now*, at the counter or on the spot, despite having no data connection, while trusting that the app never sees or intercepts their UPI PIN.

## Product Purpose
Flowpay is a smartphone-native UI wrapped around two existing, officially-sanctioned offline UPI rails that already ship on every Indian SIM but are normally buried behind unusable menus: `*99#` USSD (dialed for "Scan QR") and UPI 123Pay IVR (dialed for "Pay Contact"). It places the call, drives the DTMF/menu interaction, and infers the payment outcome from the bank's own confirmation SMS, because neither telecom rail hands back a clean success/failure callback.

## Positioning
The only consumer app shipping *both* offline rails together, so it works on every Indian SIM including Jio (which carries no USSD signalling at all — 123Pay IVR is Jio users' only offline path). No backend, no account creation, no server-side anything: the shipped manifest requests zero `INTERNET` permission, which is a factual, checkable claim, not marketing copy.

## Operating Context
The user is mid-transaction: at a shop counter handing the phone to a shopkeeper as proof, in a queue, or on the move — usually wanting this done in seconds, not minutes. For the IVR rail specifically, the user ends up on an actual phone call with their bank while Flowpay's overlay floats on top of the system dialer; for USSD, the user is bounced between the app and the native USSD reply dialog. In both cases the "app" experience is interrupted by a real telecom UI it doesn't control, and outcome confirmation can lag behind the call itself by up to a minute or two while an SMS arrives.

## Capabilities and Constraints
- Kotlin 2.1 + Jetpack Compose/Material 3 for most screens; QR Scanner, Payment Result, and the Call Overlay are still classic Android View/XML (ConstraintLayout/RelativeLayout) — candidates for migration, not necessarily required.
- Min SDK 29 (Android 10) → target/compile SDK 35. Must stay smooth on low-end/Android-Go hardware.
- Zero network access, ever. Local-only persistence (Room + SQLCipher). `supportsRtl="true"`.
- Dark-only theme today by deliberate choice (no Material You dynamic color — the team explicitly rejected wallpaper-derived theming as off-brand). Open to reconsidering, not a fixed constraint.
- 15 screens/dialogs total: Splash → Setup (bank/SIM/disclaimer) → Test Configuration (connectivity test) → Home → {QR Scanner, Pay Contact + Contact Picker} → Call Overlay (floats over the system dialer) → Payment Result (4 states: success/failed/needs-review/unverified) → Transaction History + Detail → Settings. Full spec already documented in this conversation's history.
- Real bank confirmation SMS parsing is the actual, sole source of truth for a payment's outcome — not the call itself.

## Brand Commitments
- Name **"Flowpay"** stays.
- Existing wordmark is a real, already-designed asset (blue logotype, "Flowpay," with a flow/wave stroke running through "lo") — evolve deliberately, don't discard casually.
- Existing accent blue (`#4A90E2` / `#5B8DEF` family) is the confirmed current brand anchor; open to a bolder color strategy but the current blue is not an accident to erase without reason.
- Apache-2.0 licensed, open-source project.

## Evidence on Hand
- Real current-shipped screenshot: `docs/home.png`.
- Full, real UX copy deck already reviewed: `app/src/main/res/values/strings.xml`.
- Real, verified manifest: no `INTERNET` permission; permission set is phone/SMS/camera/contacts/overlay/notifications/vibrate only.
- No user research, testimonials, or usage metrics on file. None should be invented.

## Product Principles
1. **Trust is the product.** Every visual decision should make "this is really my bank, my PIN never touched this app" more legible — never less, never by accident.
2. **Native fluency over novelty.** This is an Operate-mode, native Android surface: brand expresses through Material 3's own theming (color roles, shape, type scale, motion), not by fighting the platform or importing another OS's idioms.
3. **Built for the edge, not the showcase device.** Must read clearly and stay performant on small, low-end, Android-Go hardware, at large system font scales, for an audience that may be less smartphone-fluent than the median app user.
4. **Two rails, one confident interface.** Scan QR (`*99#`) and Pay Contact (123Pay IVR) hit two different, non-programmatic, occasionally flaky telecom backends — the UI is the thing that must feel like one coherent, trustworthy product regardless.

## Accessibility & Inclusion
48×48dp minimum touch targets, no icon-only actions (always pair icon + label), layouts tested at 130-150% system font scale, dark theme remains first-class if a light theme is ever added.
