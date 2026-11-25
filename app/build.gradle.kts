import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// API 37.1 platform (2026-11-25): compileSdk 37 + compileSdkMinor 1,
// Compose BOM 2026.11.01 (Compose 1.13, Material 3 1.5.0). The platform
// DOES exist — it is in Google's repository2-3.xml (platform-37.1_r01.zip,
// SHA-1 62aeba1ec09c2b1fdf888e79549daf678fa0d75f), not the older
// repository2-1.xml that tops out at android-36. Installed locally at
// ~/Android/Sdk/platforms/android-37.1; CI installs it via sdkmanager.
// Kotlin 2.4.21 (>= 2.2, as M3 1.5.0 requires). targetSdk stays 36 —
// compile-only change.

android {
    namespace = "com.playbox.tv"
    compileSdk = 37
    compileSdkMinor = 1
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.playbox.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "1.5.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing — reads keystore.properties (NOT committed to GitHub).
    // Create it from keystore.properties.example. Without it, release builds unsigned.
    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val props = Properties().apply { load(keystorePropsFile.inputStream()) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    // ABI splits — Patch 7 fix: the first 1.2.0 build was one universal
    // APK of 181 MB because LibVLC ships its native engine once per ABI
    // (~40-51 MB each x 4). One APK per ABI keeps each file in the
    // user's 50-70 MB range with no feature cut. arm64-v8a is the file
    // for the user's phone and the Jio TV (Amlogic S905X4, arm64).
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            // R8 on (Tool Kit self-checklist). Rules in app/proguard-rules.pro.
            // Verified by the 1.2.0 CI release build (signed APK, 54/54 tests).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (rootProject.file("keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // No kotlinOptions block: with AGP 9 built-in Kotlin the Kotlin JVM
    // target follows compileOptions.targetCompatibility (17) above.

    buildFeatures {
        compose = true
    }
}

// AGP 9: applicationVariants is removed — the per-ABI version code and
// the ZephPlayer file name now live here, in the androidComponents API.
// One output per ABI (splits above, no universal APK): vc24 becomes
// 241/242/243/244, and the file is "ZephPlayer-1.5.4-arm64-v8a.apk".
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType.name == "ABI" }
                ?.identifier
            val abiCode = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86" -> 3
                "x86_64" -> 4
                else -> 0
            }
            output.versionCode.set(output.versionCode.get() * 10 + abiCode)
            output.outputFileName.set(
                "ZephPlayer-${output.versionName.get()}-${abi ?: "universal"}.apk"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    // DocumentFile — SAF folder browsing (Batch 1.2)
    implementation("androidx.documentfile:documentfile:1.1.0")
    // Media3 / ExoPlayer — Batch 1 playback engine (Google Maven, verified 2026-11-22)
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    // MediaSession — Batch 2: background audio + system media controls
    implementation("androidx.media3:media3-session:1.11.1")
    // jcifs-ng — Batch 2: SMB/NAS browsing (SMB1/2/3, pure Java)
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    // Patch 7: LibVLC — the second engine, for formats Media3 cannot
    // play, and for audio/subtitle delay, deinterlace, video adjust,
    // volume above 100% and chapters.
    implementation("org.videolan.android:libvlc-all:3.7.8")
    // Patch 7: FTP (Apache Commons Net — its group is commons-net,
    // not org.apache.commons) and SFTP (JSch fork) browsing.
    implementation("commons-net:commons-net:3.13.0")
    implementation("com.github.mwiede:jsch:0.2.26")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
