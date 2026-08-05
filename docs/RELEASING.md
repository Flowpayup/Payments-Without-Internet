# Releasing Flowpay

> **The signing key exists** (generated 2026-08-05, valid to 2053) and its
> certificate fingerprint is committed in [SECURITY.md](../SECURITY.md). The
> key and its password live only on the maintainer's machine and are never
> committed — `keystore.properties` and `*.jks` are gitignored.
>
> **No release has been published yet.** Before the first one, the physical
> device gate in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) must be run — it
> never has been.

The release pipeline is deliberately split: **CI builds, the maintainer signs.**
The signing key never leaves the maintainer's machine, so CI has no secrets to
leak — and because CI therefore cannot produce the artifact users install, it
attaches **nothing** to the GitHub Release. Everything on the release page is
uploaded by the maintainer, signed.

## Build environment

Releases are built with a pinned toolchain:

- **JDK:** Temurin 17 (the same distribution CI uses)
- **Android SDK:** `compileSdk 35`, `buildToolsVersion 35.0.0` (pinned in
  `app/build.gradle.kts` — aapt2 and zipalign differ between revisions)
- **Gradle:** the version and SHA-256 pinned in
  `gradle/wrapper/gradle-wrapper.properties`

`dependenciesInfo` is disabled in `app/build.gradle.kts`, so APKs contain no
Play-Store metadata blob. With the toolchain above held constant, two builds of
the same commit are expected to match outside `META-INF/`; this has not yet been
confirmed by an independent reproducer, so treat it as an intent rather than a
guarantee until someone reports a byte-for-byte match.

## Release checklist

1. **Bump the version** in `app/build.gradle.kts`:
   - `versionCode`: +1, always.
   - `versionName`: semantic version (`MAJOR.MINOR.PATCH`).
2. **Update `CHANGELOG.md`**: move `[Unreleased]` items under the new version
   heading with today's date.
3. **Run the local gate**:
   ```bash
   ./gradlew test :app:lintDebug detekt koverVerify assembleRelease
   ```
   With `keystore.properties` in place this produces the signed APK directly.
   Without it, `assembleRelease` fails by design rather than emitting an
   uninstallable unsigned APK — add `-PallowUnsigned` if you only want to
   check that the release variant compiles and shrinks:
   ```bash
   ./gradlew test :app:lintDebug detekt koverVerify assembleRelease -PallowUnsigned
   ```
4. **Run the on-device release checklist**: [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)
   (real *99# and 123Pay transactions on physical hardware).
5. **Commit and tag**:
   ```bash
   git commit -am "Release vX.Y.Z"
   git tag vX.Y.Z
   ```
6. **Push the tag.** The `Release` workflow runs the unit tests, builds the
   unsigned APK, and opens an **empty draft** GitHub Release with generated
   notes. The unsigned APK and the R8 `mapping.txt` are kept as *workflow
   artifacts* — they are deliberately **not** attached to the release, because
   an unsigned APK is uninstallable and a user who downloaded it would hit
   `INSTALL_PARSE_FAILED_NO_CERTIFICATES`.
7. **Build and sign locally.** With `keystore.properties` in place this is one
   step and produces the artifact users install:
   ```bash
   ./gradlew clean assembleRelease
   cp app/build/outputs/apk/release/app-release.apk flowpay-vX.Y.Z.apk
   apksigner verify --print-certs flowpay-vX.Y.Z.apk
   ```
8. **Generate the checksum over the signed APK** — the file users actually
   download. Never publish a checksum computed over the CI artifact:
   ```bash
   shasum -a 256 flowpay-vX.Y.Z.apk > SHA-256SUMS
   cat SHA-256SUMS
   ```
9. **Upload `flowpay-vX.Y.Z.apk` and `SHA-256SUMS`** to the draft release,
   paste the certificate SHA-256 fingerprint from step 7 into the notes, and
   publish. The release page must contain exactly these two files.

## Verifying a release (anyone)

Run both, from the directory holding the downloaded files:

```bash
shasum -a 256 -c SHA-256SUMS                        # must report: OK
apksigner verify --print-certs flowpay-vX.Y.Z.apk   # fingerprint must match SECURITY.md
```

Compare the certificate fingerprint against the copy committed in
[SECURITY.md](../SECURITY.md), **not** against the release notes — release
notes are mutable by whoever published the release, so checking them against
themselves proves nothing. The committed fingerprint is tamper-evident through
git history.

To reproduce the build: check out the tag and build with the pinned toolchain
above. A third-party reproducer has no signing key, so pass `-PallowUnsigned`
to opt out of the signed-release guard:

```bash
./gradlew assembleRelease -PallowUnsigned
```

Then compare everything but `META-INF/` against the released APK — that
directory holds the signature block, which only the maintainer's key can
produce.
