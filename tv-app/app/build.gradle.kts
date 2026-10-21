plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.rosetta.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.rosetta.tv"
        // Fire OS 6 (API 25) and up; Fire OS 7 = API 28, Fire OS 8 = API 30.
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Empty = bundled demo household. Set after `cdk deploy` (see infra/README.md).
        buildConfigField("String", "ROSETTA_API_URL", "\"${project.findProperty("rosettaApiUrl") ?: ""}\"")
        buildConfigField("String", "ROSETTA_WEB_URL", "\"${project.findProperty("rosettaWebUrl") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.zxing.core)
    implementation(libs.okhttp)
}
