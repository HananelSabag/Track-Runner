plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hananel.fast.mobile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Must equal the watch app's applicationId: the Data Layer only connects
        // apps that share a package name and signing key.
        applicationId = "com.hananel.fast"
        minSdk = libs.versions.minSdkMobile.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 10
        versionName = "0.6.0"
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
}
