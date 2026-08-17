plugins {
    // AGP 9 provides Kotlin support itself; applying kotlin-android is an error.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nomercylabs.arrowz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nomercylabs.arrowz"
        minSdk = 28
        targetSdk = 36
        // CI decides both, the same way the KMP app does: the date is what a
        // tester can read back to us, and the code is the run number, which only
        // ever goes up. Hand-maintained versions are one edit away from a
        // release Play refuses, and 0.1.1 already collided that way.
        //
        // The offset covers versionCodes 1 and 2, which were published by hand
        // before this existed. Play requires them to increase forever, so the
        // floor can never be lowered.
        versionCode = (System.getenv("NM_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("NM_VERSION_NAME") ?: "dev"

        // A hold on BACK cannot be reproduced from adb: --longpress sets the
        // framework flag and takes a path that already worked, and sendevent
        // needs root retail hardware does not give. An instrumented test can
        // deliver the exact sequence a remote sends, so one exists.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

            // The one native library here, libandroidx.graphics.path.so, arrives
            // from a third-party AAR already stripped to .dynsym, so this finds
            // no .debug_* to embed and the bundle carries no symbol metadata.
            // Declared anyway because it costs nothing and starts working the
            // day a dependency ships unstripped; the workflow uploads the
            // stripped libraries separately, which buys exported function names
            // in a native crash rather than bare addresses.
            ndk {
                debugSymbolLevel = "FULL"
            }

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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    // The android.jar on the unit-test classpath stubs org.json and throws from
    // every method. The real implementation is the same API, so the parser under
    // test is the one that ships rather than a rewrite that avoids the stub.
    testImplementation(libs.json)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
