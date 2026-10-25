plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * The builds the benchmarks install: `benchmarkRelease` measures, `nonMinifiedRelease` generates
 * the baseline profile. Both install under their own application id, so a benchmark never
 * touches the owner's Cents or its data, and both carry `src/benchmark`, which fills them with an
 * invented ledger before anything is measured.
 */
val benchmarkBuildTypes = setOf("benchmarkRelease", "nonMinifiedRelease")

android {
    namespace = "io.github.anderescaray.cents"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.anderescaray.cents"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Through isMinifyEnabled rather than `optimization`: the baseline profile plugin turns
            // minifying off for the profile's build through this one, and `optimization` would
            // override it, leaving a profile of R8's renamed classes that matches nothing.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The two builds the baseline profile plugin adds for `:baselineprofile`, signed with the
        // debug key so they install. See the variants below for the rest of what sets them apart.
        configureEach {
            if (name in benchmarkBuildTypes) signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric needs the merged Android resources to run Room on the JVM
            isIncludeAndroidResources = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType in benchmarkBuildTypes) {
            variant.applicationId.set("io.github.anderescaray.cents.benchmark")
            variant.sources.java?.addStaticSourceDirectory("src/benchmark/java")
            variant.sources.manifests.addStaticManifestFile("src/benchmark/AndroidManifest.xml")
        }
    }
}

ksp {
    // Every schema version is exported to app/schemas and committed, so migrations can be
    // written and tested against the real previous schema.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.emoji.picker)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    // Declared rather than inherited: the type-safe navigation routes have always used
    // kotlinx.serialization through navigation-compose, and the export writes JSON with it
    // directly. A backup file is exactly the thing not to hand-roll an escaper for.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)
    implementation(libs.vico.compose)
    // Installs the baseline profile on devices that do not get it from Play, which is every one
    // this app is on: it is sideloaded.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
