plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.hananel.fast.core.diagnostics"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // The lower of the two apps, so both can depend on it.
        minSdk = libs.versions.minSdkMobile.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
