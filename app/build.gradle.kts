// app/build.gradle.kts -- modulo app de MiMoo
// AGP 9.x: kotlin-android integrado, kotlinOptions sustituido por kotlin.compilerOptions
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.chaquopy)
}

android {
    namespace  = "com.miguelaetxio.mimoo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.miguelaetxio.mimoo"
        minSdk        = 26
        targetSdk     = 36
        versionCode   = 1
        versionName   = "0.1"
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${project.findProperty("YOUTUBE_API_KEY") ?: ""}\""
        )

        ndk {
            // Chaquopy requiere abiFilters explicito. Solo arm64-v8a:
            // unico target real de dispositivo (ver decision tecnica
            // de binarios nativos del Hito 02 original).
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }
}

chaquopy {
    defaultConfig {
        // yt-dlp embebido para resolucion de streaming (Hito 01) y
        // descarga a Opus (Hito 02). Sustituye al plan original de
        // binario nativo standalone, inviable en Android sin Termux
        // (decision corregida en S002-H02, ver mimoo-annex-v01).
        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

    testImplementation(libs.junit)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
