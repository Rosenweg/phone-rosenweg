// Rosenweg (25.11.2026): SIP-Kern aus Rosenweg Geraet (android-sip-rosenweg, Sipdroid-Fork)
// fuer das persoenliche Konto der angemeldeten Person. Pakete org.sipdroid.* / org.zoolu.*
// bleiben wie im Original; die applicationId der App ist ch.rosenweg4303.phone
// (ADDITIONAL_TERMS.txt, Punkt 2). Lizenz: GPLv3 mit den Zusatzbedingungen von Sipdroid.
plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "org.sipdroid.sipua"
    // gleiche NDK wie Rosenweg Geraet (AGP 8.2): die alten Codec-Quellen bauen mit clang aus NDK 28 nicht mehr
    ndkVersion = "25.1.8937393"
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        val v = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = v
        targetCompatibility = v
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}
