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

    signingConfigs {
        // Release key: supplied through the environment, never stored in the repo (tools/release.sh, docs/SECURITY.md).
        // Without COCKPIT_KEYSTORE the release build falls back to the machine's debug key (a warning is printed by tools/release.sh).
        val keystore = System.getenv("COCKPIT_KEYSTORE")
        if (!keystore.isNullOrBlank()) create("cockpitRelease") {
            storeFile = file(keystore)
            storePassword = System.getenv("COCKPIT_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("COCKPIT_KEY_ALIAS")
            keyPassword = System.getenv("COCKPIT_KEY_PASSWORD") ?: System.getenv("COCKPIT_KEYSTORE_PASSWORD")
            enableV3Signing = true      // lets the key be rotated later without reinstalling (minSdk 28 = Android 9, which has v3)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("cockpitRelease") ?: signingConfigs.getByName("debug")   // debug key only when no release key is configured
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
