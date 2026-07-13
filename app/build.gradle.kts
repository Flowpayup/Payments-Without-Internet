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

android {
    namespace = "com.flowpay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.flowpay.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
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
    // Exported Room schemas as androidTest assets so MigrationTestHelper can load them
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
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

// Static analysis: detekt with the ktlint-style formatting ruleset. The
// baseline freezes findings that predate detekt's adoption — new code must
// come in clean rather than growing the baseline.
detekt {
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
}

// Coverage floor scoped to the payment lifecycle + SMS-parsing packages —
// the riskiest code in the app, where an untested regression can silently
// misreport a payment outcome. No app-wide threshold: a blanket UI-coverage
// number would just reward screenshot-test theater, not payment safety.
kover {
    reports {
        filters {
            includes {
                packages("com.flowpay.app.payment", "com.flowpay.app.payment.sms")
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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
