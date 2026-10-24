plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hananel.fast.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Must equal the phone app's applicationId: the Data Layer only connects
        // apps that share a package name and signing key.
        applicationId = "com.hananel.fast"
        minSdk = libs.versions.minSdkWear.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Offset from the phone's versionCode: Play rejects two APKs of one app with the same code.
        versionCode = 1_000_008
        versionName = "0.5.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:tracking"))
    implementation(project(":core:data"))
    implementation(project(":core:gnss"))
    implementation(project(":core:diagnostics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.wear.ongoing)
    // Ambient (always-on) mode, so a measurement stays on screen when the watch dims.
    implementation(libs.androidx.wear)
    // Holds the @WearPreviewDevices annotations used in main sources, so it cannot be debug-only.
    implementation(libs.androidx.wear.compose.ui.tooling)

    implementation(libs.androidx.health.services.client)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
