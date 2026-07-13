# Releasing Flowpay

The release pipeline is deliberately split: **CI builds, the maintainer signs.**
The signing key never leaves the maintainer's machine — CI has no secrets to
leak, and anyone can rebuild the unsigned APK and compare it byte-for-byte
against a release.

## Build environment

Releases are built with a pinned toolchain so builds are reproducible:

- **JDK:** Temurin 17 (the same distribution CI uses)
- **Android SDK:** compileSdk 35, build-tools as resolved by AGP
- **Gradle:** the version pinned in `gradle/wrapper/gradle-wrapper.properties`

`dependenciesInfo` is disabled in `app/build.gradle.kts`, so APKs contain no
Play-Store metadata blob and two builds of the same commit should be
byte-identical modulo the signature block.

## Release checklist

1. **Bump the version** in `app/build.gradle.kts`:
   - `versionCode`: +1, always.
   - `versionName`: semantic version (`MAJOR.MINOR.PATCH`).
2. **Update `CHANGELOG.md`**: move `[Unreleased]` items under the new version
   heading with today's date.
3. **Run the local gate**:
   ```bash
   ./gradlew test :app:lintDebug detekt assembleRelease
   ```
4. **Run the on-device release checklist** (see `docs/RELEASE_CHECKLIST.md`
   once it exists — real *99# and 123Pay transactions on physical hardware).
5. **Commit and tag**:
   ```bash
   git commit -am "Release vX.Y.Z"
   git tag vX.Y.Z
   ```
6. **Push the tag** (when a remote is in use). The `Release` workflow builds
   the unsigned APK, generates `SHA-256SUMS`, and attaches both to a **draft**
   GitHub Release.
7. **Sign locally**:
   ```bash
   # zipalign first if using apksigner on the unsigned CI artifact
   zipalign -v 4 app-release-unsigned.apk app-release-aligned.apk
   apksigner sign --ks <your-keystore> --out flowpay-vX.Y.Z.apk app-release-aligned.apk
   apksigner verify --print-certs flowpay-vX.Y.Z.apk
   ```
   A locally built `./gradlew assembleRelease` with `keystore.properties`
   present produces the same signed result in one step.
8. **Upload the signed APK** to the draft release, paste the certificate
   SHA-256 fingerprint from `apksigner verify --print-certs` into the release
   notes, and publish.

## Verifying a release (anyone)

```bash
apksigner verify --print-certs flowpay-vX.Y.Z.apk   # fingerprint must match release notes
shasum -a 256 -c SHA-256SUMS                        # checksum must match
```

To reproduce the build: check out the tag, build `assembleRelease` with the
pinned toolchain above, and compare everything but `META-INF/` against the
released APK.
