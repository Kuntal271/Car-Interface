plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.kuntal.cockpit"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kuntal.cockpit"
        minSdk = 28
        targetSdk = 30          // revisit after M4: match the unit's real Android version
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")   // personal sideload only
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        // Play Store rule; this app is sideloaded onto an Android 11 head unit, and targetSdk 30 is deliberate (see targetSdk above).
        disable += "ExpiredTargetSdkVersion"
    }
}

kotlin { jvmToolchain(17) }

// Compose compiler reports (unstable classes, non-skippable composables): `tools/gb.sh -PcomposeReports=true :app:assembleRelease`,
// output in app/build/compose_compiler/. Off by default so normal builds are unchanged.
if (providers.gradleProperty("composeReports").isPresent) {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

dependencies {
    implementation(project(":vehicle"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
