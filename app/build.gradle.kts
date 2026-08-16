plugins {
    // AGP 9 provides Kotlin support itself; applying kotlin-android is an error.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nomercylabs.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nomercylabs.browser"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // No abiFilters and no splits: the app ships zero native code, so one
        // artifact runs on every processor Android TV uses.
    }

    signingConfigs {
        create("upload") {
            val storePath: String? = System.getenv("NM_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("NM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NM_KEY_ALIAS")
                keyPassword = System.getenv("NM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("NM_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
