plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "ai.sonora.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.sonora.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("sonora.versionCode").get().toInt()
        versionName = providers.gradleProperty("sonora.versionName").get()
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
