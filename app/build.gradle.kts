import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Release signing is read from keystore.properties (gitignored). Copy
// keystore.properties.example to keystore.properties and fill it in to produce
// a signed release build; without it, release builds are left unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// A missing or misspelled key used to throw a bare NullPointerException from
// the unchecked `as String` casts below, at configuration time, with no hint
// which key was the problem. Name it instead.
fun keystoreProp(key: String): String = keystoreProperties[key] as? String
    ?: throw GradleException(
        "keystore.properties is missing '$key' (see keystore.properties.example)"
    )

android {
    namespace = "com.flowpay.app"
    compileSdk = 35
    // Pinned, not "whatever AGP resolves": aapt2 and zipalign differ between
    // build-tools revisions, so leaving this floating makes the reproducible
    // -build claim in docs/RELEASING.md impossible to honour.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.flowpay.app"
        minSdk = 29
        targetSdk = 35
        // versionCode is monotonic and independent of versionName: builds with
        // code 4 exist on test hardware, and Android refuses downgrades, so
        // this only ever goes up even though the public name restarts at 1.0.0.
        versionCode = 5
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProp("storeFile"))
                storePassword = keystoreProp("storePassword")
                keyAlias = keystoreProp("keyAlias")
                keyPassword = keystoreProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Sign with the release key when keystore.properties is present;
            // otherwise the release build is left unsigned.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real Android phones are ARM; Intel exited the Android space in
            // 2016, so the x86 ABIs only ever serve emulators. Restricting
            // RELEASE to arm64-v8a + armeabi-v7a roughly halves the native
            // payload (SQLCipher ships a .so per ABI) for every actual user.
            // armeabi-v7a is kept deliberately: 32-bit-only Android Go-edition
            // devices are exactly this app's target market. Debug is left
            // alone so the x86_64 emulator matrix in instrumented.yml still
            // runs the app it's built for.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            buildConfigField("boolean", "DEBUG_LOGS", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Baseline freezes pre-existing lint issues; new issues will still fail builds.
    lint {
        disable += "NullSafeMutableLiveData"
        // Dependency freshness is Dependabot's job; lint's advisory would
        // otherwise go stale in the baseline every week.
        disable += "GradleDependency"
        // All hardcoded UI strings have been extracted to resources; keep it
        // that way by failing the build on any new one.
        error += "HardcodedText"
        error += "SetTextI18n"
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        // android.util.Log etc. return defaults instead of throwing in JVM unit tests
        unitTests.isReturnDefaultValues = true
    }
    // Reproducibility: don't embed the Google-Play dependency metadata blob
    // (a signed, opaque protobuf) into APKs — this app is not Play-distributed.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Fail an actual RELEASE build when it would be silently UNSIGNED, unless the
// caller explicitly opts in with -PallowUnsigned. This fires only when a
// release-packaging task actually runs — a configuration-time throw would break
// debug/test builds (e.g. CI) that legitimately run without a keystore.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        if (!keystorePropertiesFile.exists() && !project.hasProperty("allowUnsigned")) {
            throw GradleException(
                "Release build would be UNSIGNED and uninstallable: keystore.properties is missing. " +
                    "Copy keystore.properties.example and fill it in to sign the release, or pass " +
                    "-PallowUnsigned to build an intentionally unsigned APK."
            )
        }
    }
}

// Static analysis: detekt with the ktlint-style formatting ruleset. The
// baseline freezes findings that predate detekt's adoption — new code must
// come in clean rather than growing the baseline.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

// Coverage floor scoped to the money-critical code — the payment lifecycle,
// SMS parsing, the operation-window/dedup orchestrator, and the telephony
// authority — where an untested regression can silently misreport a payment
// outcome. No app-wide threshold: a blanket UI-coverage number would just
// reward screenshot-test theater, not payment safety.
kover {
    reports {
        filters {
            includes {
                packages(
                    "com.flowpay.app.payment",
                    "com.flowpay.app.payment.sms",
                    "com.flowpay.app.telephony"
                )
                classes("com.flowpay.app.helpers.TransactionDetector*")
            }
        }
        verify {
            rule {
                minBound(85)
            }
        }
    }
}

dependencies {
    detektPlugins(libs.detekt.formatting)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Constraint Layout
    implementation(libs.androidx.constraintlayout)

    // LocalBroadcastManager — carries the payment-result broadcast in
    // SmsIngestionPipeline and QRScannerActivity. Declared explicitly because
    // it was previously reaching the money path only as a 4th-level transitive
    // of com.google.android.material (material -> transition/dynamicanimation
    // -> legacy-support-core-utils), so a Material bump that trimmed that
    // chain would have broken payment results. Deprecated upstream; the two
    // call sites should eventually move to a shared flow.
    implementation(libs.androidx.localbroadcastmanager)

    // QR Code Scanning — ZXing core: pure Java, Apache 2.0, no proprietary
    // model blob (unlike ML Kit, which this replaced for FOSS purity).
    implementation(libs.zxing.core)

    // CameraX
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.core)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SQLCipher — at-rest encryption for the transaction database.
    // @aar pulls no transitives, so androidx.sqlite is declared explicitly.
    implementation("net.zetetic:sqlcipher-android:${libs.versions.sqlcipher.get()}@aar")
    implementation(libs.androidx.sqlite)

    // Material Design
    implementation(libs.material)
    implementation(libs.androidx.cardview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real android.net.Uri etc. in JVM tests (QRCodeParser)
    testImplementation(libs.robolectric)
}
