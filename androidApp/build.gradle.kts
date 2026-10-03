plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "sonora.multiroom.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "sonora.multiroom.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("sonora.versionCode").get().toInt()
        versionName = providers.gradleProperty("sonora.versionName").get()
    }

    // One committed debug key for every machine, so debug APKs update each other instead of failing
    // with INSTALL_FAILED_UPDATE_INCOMPATIBLE (see keystore/README.md).
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
}
