plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.edenpractical.comms"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    // Public source revision identifies the exact CI checkout in each test APK.
    // A local/IDE build with no injected Git revision says "unavailable".
    val sourceRevision = providers.gradleProperty("edenSourceRevision").orNull ?: "unavailable"
    require(sourceRevision == "unavailable" || sourceRevision.matches(Regex("[0-9a-f]{40}"))) {
        "Build source revision must be a 40-character lowercase Git SHA or unavailable"
    }

    // Simple SIDeload preview workflow. Public AOSP test key is permitted ONLY
    // on an isolated debug .preview application ID, never the release package.
    // The existing ordinary/debug and protected private internal-test paths
    // remain unchanged unless this exact opt-in build property is provided.
    val offlinePreview = providers.gradleProperty("edenOfflinePreview").orNull
    val offlinePreviewCodeText =
        providers.gradleProperty("edenOfflinePreviewVersionCode").orNull
    require(offlinePreview == null || offlinePreview == "true") {
        "edenOfflinePreview must be true when supplied"
    }
    require((offlinePreview == "true") == (offlinePreviewCodeText != null)) {
        "Offline preview requires its isolated package switch and versionCode together"
    }
    val offlinePreviewCode = offlinePreviewCodeText?.let { raw ->
        require(raw.matches(Regex("[1-9][0-9]{0,8}"))) {
            "Invalid offline preview versionCode"
        }
        val code = raw.toIntOrNull() ?: error("Offline preview versionCode overflow")
        require(code in 200_001..2_100_000_000) {
            "Offline preview versionCode outside allowed bounds"
        }
        code
    }

    defaultConfig {
        buildConfigField("String", "SOURCE_REVISION", "\"$sourceRevision\"")
        applicationId = "com.edenpractical.comms"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.2.12-approval-review-only"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["edenAppLabel"] = "Eden Comms"
    }

    // Optional, CI-authorized internal test APK version override. The normal
    // PR/push debug APK keeps the fixed baseline version and disposable signer.
    // Signing happens separately, AFTER Gradle, so private keys never enter
    // the project's Gradle runtime. No production capabilities are unlocked.
    val internalTestVersionCode = providers.gradleProperty("edenInternalTestVersionCode").orNull
    require(offlinePreviewCode == null || internalTestVersionCode == null) {
        "Do not combine public offline preview with private internal-test signing"
    }
    if (internalTestVersionCode != null) {
        require(internalTestVersionCode.matches(Regex("[1-9][0-9]{0,8}"))) {
            "Internal test version must be a canonical positive integer"
        }
        val code = internalTestVersionCode.toIntOrNull()
            ?: error("Internal test version out of range")
        require(code > 11 && code <= 2_100_000_000) {
            "Internal test version must exceed the baseline without Android overflow"
        }
        defaultConfig.versionCode = code
        defaultConfig.versionName =
            (defaultConfig.versionName?.substringBefore('-') ?: "0.2.12") + "-internal-" + code
    }

    if (offlinePreviewCode != null) {
        defaultConfig.versionCode = offlinePreviewCode
        defaultConfig.versionName =
            (defaultConfig.versionName?.substringBefore('-') ?: "0.2.12") +
                "-preview-" + offlinePreviewCode
    }

    buildTypes {
        debug {
            if (offlinePreviewCode != null) {
                // An *independent* Android package: cannot replace, sign or
                // reset the existing com.edenpractical.comms installation.
                applicationIdSuffix = ".preview"
                manifestPlaceholders["edenAppLabel"] = "Eden Comms Preview"
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Fully offline, in-app QR generation and camera-based scanning.
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}


// F2-D: the paired TLS-provider interop lab can reveal failures that a
// single-source assertion cannot. Keep full failed-test stack traces in CI
// (without printing test private keys or application payloads). This does
// not change Android runtime behavior or release packaging.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
