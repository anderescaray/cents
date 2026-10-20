plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

// Generates the app's baseline profile and measures its startup and scrolling, on a connected
// phone. Nothing here runs in CI, which has no device: see docs/testing.md.
android {
    namespace = "io.github.anderescaray.cents.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Baseline profiles need API 28 to be generated; the app itself still runs from 26.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The id the benchmark builds install under (app/build.gradle.kts), never the owner's.
        testInstrumentationRunnerArguments["targetAppId"] = "io.github.anderescaray.cents.benchmark"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // The Pixel, not a managed emulator: an emulator lies about performance (CLAUDE.md §5).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
