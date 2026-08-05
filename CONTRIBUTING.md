# Contributing to Flowpay

Thanks for your interest in contributing. This guide covers everything you need to get a development build running and to submit a change.

## Prerequisites

- **JDK 17** (Temurin recommended — matches CI)
- **Android SDK** with `compileSdk = 35`, `minSdk = 29`
- **Android Studio Hedgehog (2023.1.1) or later** for the IDE experience
- A device or emulator running **Android 10 (API 29)** or higher

## First-time setup

```bash
git clone <repo-url>
cd Flowpay_v1
```

Create `local.properties` at the repo root (it is gitignored):

```properties
sdk.dir=/absolute/path/to/your/Android/sdk
```

That single line is all the local configuration the build needs.

## Build & run

```bash
./gradlew assembleDebug              # build a debug APK
./gradlew installDebug               # install on a connected device/emulator
./gradlew test                       # run JVM unit tests
./gradlew :app:lintDebug             # run lint (must pass)
```

JVM unit tests live in `app/src/test/` (`Upi123CallStringBuilderTest`, `PaymentSessionManagerTest`, `SmsTransactionParserTest`, `SmsParsingRegexTest`, `SmsIngestionPipelineTest`, `TransactionDetectorTest`, `CallStateCoordinatorTest`, `PaymentWindowObserverTest`, `PaymentInputValidatorTest`, `DatabaseKeyManagerRecoveryTest`, `TransactionMappingTest`, `QRCodeParserTest`, `QRCodeAnalyzerDecodeTest`). There are no instrumented tests at present — see [docs/TESTING.md](docs/TESTING.md) Layer 2 for why, and what must come back with the next schema change. CI runs `./gradlew test` on every push, so keep them green and add coverage for new logic where it makes sense. See [docs/TESTING.md](docs/TESTING.md) for the full verification story, including a debug-only tool for replaying bank SMS through the live pipeline without a real bank.

### Adding a bank SMS template

The SMS parser is only as good as its corpus of real bank confirmation formats, and every bank's template is different — new samples are one of the most valuable contributions.

1. Take a real confirmation SMS from your bank and **redact it**: replace account digits with `**1234`-style masks, real names with placeholders (`KIRANA STORE`, `Rahul Sharma`), and reference numbers with obviously fake ones (`123456789012`). Keep the exact wording, punctuation, and field order — that's what the parser matches on.
2. Note the sender ID it arrived from (e.g. `VK-HDFCBK`) — DLT sender codes matter for bank detection.
3. Add it as a test case next to the existing samples in `app/src/test/` and run `./gradlew test`. If the parser mishandles it, file an issue with the redacted SMS and sender ID instead — that alone is a useful bug report.

## Project layout

```
app/src/main/java/com/flowpay/app/
├── MainActivity.kt                  # entry screen
├── SetupActivity.kt                 # first-run setup
├── TestConfigurationActivity.kt     # post-setup USSD/UPI test gate
├── constants/                       # AppConstants, PermissionConstants
├── data/                            # Room entities, repositories
├── di/                              # AppContainer (composition root)
├── features/qr_scanner/             # QR scanner (CameraX + ZXing)
├── helpers/                         # business-logic helpers
├── managers/                        # CallManager, PermissionManager, etc.
├── payment/                         # PaymentSessionManager, SMS parser, validators
├── receivers/                       # SMS BroadcastReceiver + ingestion pipeline
├── services/                        # call-overlay, notification listener
├── ui/                              # Compose screens + theme
└── utils/                           # small utilities
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the pieces fit — the payment state machine, the dual SMS-ingestion pipeline, the composition root, and the deliberate simplifications.

## Branching

- `main` — protected; only PRs land here.
- `feat/<short-name>` — new features
- `fix/<short-name>` — bug fixes
- `chore/<short-name>` — refactors, build/CI, deps

## Pull requests

Before opening a PR:

1. `./gradlew assembleDebug` must succeed.
2. `./gradlew :app:lintDebug` must pass (a baseline absorbs pre-existing issues; new issues will fail CI).
3. If your change touches UI, attach a screenshot or short clip.
4. Link any related issue (`Closes #123`).
5. Keep the diff focused — one logical change per PR.

CI runs on every push and PR. A green run is required before merge.

## Code style

- Kotlin official style (4-space indent). Wildcard imports are banned except for Compose's DSL packages (`androidx.compose.foundation.layout.*`, `material3.*`, `runtime.*`, …), where a screen legitimately pulls dozens of symbols from each; the exception is declared in `.editorconfig` and `app/config/detekt/detekt.yml`.
- An `.editorconfig` at the repo root captures the conventions; most IDEs respect it automatically.
- `detekt` (with the ktlint-style formatting ruleset) is enforced in CI: run `./gradlew detekt` locally before pushing. Pre-existing findings are frozen in `app/detekt-baseline.xml`; new code must come in clean. `./gradlew detekt --auto-correct` fixes the formatting ones for you.
- **Every new `.kt` file needs the licence header** — the first two lines, before the `package` declaration:
  ```kotlin
  // SPDX-License-Identifier: Apache-2.0
  // Copyright 2026 Flowpay
  ```
  The repo-root `LICENSE` doesn't travel with a file someone copies out, so per-file SPDX is what licence scanners actually read.

## About the lint and detekt baselines

`app/lint-baseline.xml` and `app/detekt-baseline.xml` freeze pre-existing findings so CI can gate on *new* ones. Both baselines only ratchet **down**: CI fails if either grows, so any finding your change introduces must be fixed, not baselined. Shrinking them is a welcome contribution — the remaining entries are mostly formatting and View-layer items in the older screens.

## Good first contributions

If you're looking for a place to start:

- **Add a bank SMS template** to the parser corpus (see "Adding a bank SMS template" above) — the single most valuable contribution, and low-risk.
- **Shrink a detekt/lint baseline entry** — pick one finding, fix it, drop it from the baseline (see above).
- **Improve a `docs/` page** — a clearer FAQ answer, a diagram, a fixed link.
- **Reduce a god-file** — `MainActivity.kt` and `CallOverlayService.kt` are large; extracting a self-contained composable or helper (with no behavior change) is a good scoped task.
- **Report a carrier/USSD quirk** — the bug template asks for carrier and SIM; a well-documented report is itself a contribution.

## Filing issues

Use the templates under [.github/ISSUE_TEMPLATE](.github/ISSUE_TEMPLATE) when opening an issue. For security reports, see [SECURITY.md](SECURITY.md) — do not file public issues for vulnerabilities.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
