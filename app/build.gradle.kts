
// app/build.gradle.kts -- modulo app de MiMoo
// AGP 9.x: kotlin-android integrado, kotlinOptions sustituido por kotlin.compilerOptions
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.chaquopy)
}

// local.properties no se auto-carga para claves personalizadas: AGP
// solo garantiza propiedades reservadas (sdk.dir). Causa raiz del 403
// "unregistered caller" del Build #21 (YOUTUBE_API_KEY llegaba vacia
// porque project.findProperty no resolvia la clave del workflow).
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

android {
    namespace  = "com.miguelaetxio.mimoo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.miguelaetxio.mimoo"
        minSdk        = 26
        targetSdk     = 36
        // versionCode dinamico: recibido como -PversionCode=N desde
        // el workflow (github.run_number, siempre creciente). Sin
        // esto, cada build de CI generaba el mismo versionCode fijo
        // y Android bloqueaba las actualizaciones como bajada de
        // version, mostrando "conflicto con un paquete" en vez de un
        // aviso claro de downgrade (causa real S004, no la firma).
        // Fallback a 2 si se compila localmente sin pasar la property.
        versionCode = (project.findProperty("versionCode") as String?)
            ?.toIntOrNull() ?: 2
        versionName   = "0.2"
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // Client ID OAuth tipo Android (H06, Google Drive backup) --
        // no es secreto (va embebido en el APK igual que cualquier
        // Client ID de Android, su seguridad depende del SHA-1 dado
        // de alta en Google Cloud, no de mantenerlo oculto), pero se
        // inyecta vía local.properties/secret de CI para no repartir
        // el valor literal por el repositorio y poder rotarlo sin
        // tocar código.
        buildConfigField(
            "String",
            "GOOGLE_OAUTH_ANDROID_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_OAUTH_ANDROID_CLIENT_ID") ?: ""}\""
        )

        ndk {
            // Chaquopy requiere abiFilters explicito. Solo arm64-v8a:
            // unico target real de dispositivo (ver decision tecnica
            // de binarios nativos del Hito 02 original).
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        getByName("debug") {
            // Explícito, no implícito -- bug real (2026-07-05): Android
            // Developer Console rechazaba el APK con "firma diferente"
            // pese a que habíamos verificado que ~/.android/debug.keystore
            // tenía la huella SHA-256 correcta en cada build. Esa
            // verificación solo confirmaba que EL ARCHIVO era correcto
            // -- nunca que Gradle lo estuviera usando de verdad para
            // firmar, ya que sin este bloque AGP resuelve la ruta del
            // keystore de debug de forma implícita, sin ninguna
            // garantía de que coincida exactamente con la ruta que
            // restaura el workflow. Con esta ruta explícita
            // (System.getProperty("user.home") = mismo $HOME que usa
            // "~/.android/debug.keystore" en bash), no hay ambigüedad
            // posible.
            // ---
            // Explicit, not implicit -- real bug (2026-07-05): Android
            // Developer Console rejected the APK with "different
            // signature" despite having verified that
            // ~/.android/debug.keystore had the correct SHA-256
            // fingerprint on every build. That check only confirmed THE
            // FILE was correct -- never that Gradle was actually using
            // it to sign, since without this block AGP resolves the
            // debug keystore's path implicitly, with no guarantee it
            // matches exactly the path the workflow restores it to.
            // With this explicit path (System.getProperty("user.home")
            // = the same $HOME that bash's "~/.android/debug.keystore"
            // uses), there's no ambiguity left.
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
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

    packaging {
        jniLibs {
            // Force extraction of .so files to the filesystem so that
            // libffmpeg_bin.so is accessible as a real file at
            // applicationInfo.nativeLibraryDir and can be executed.
            // Without this, Android 14+ loads .so from the APK zip
            // directly (compressed) and File.exists() returns false.
            // ---
            // Forzar la extraccion de archivos .so al sistema de
            // archivos para que libffmpeg_bin.so sea accesible como
            // archivo real en nativeLibraryDir y pueda ejecutarse.
            // Sin esto, Android 14+ los carga comprimidos del APK
            // directamente y File.exists() devuelve false.
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        // Version fijada explicitamente: Chaquopy exige que buildPython
        // (interprete de la maquina de build) coincida en version mayor
        // con el interprete embebido en el APK. Sin esto, Chaquopy busca
        // el Python por defecto (3.10) en el runner de GitHub Actions,
        // que no lo garantiza preinstalado -> fallo
        // "Couldn't find Python 3.10" (Build #18, S002-H02).
        version = "3.11"
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

    // WorkManager + HiltWorker (Hito 02: motor de descarga)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Identity.getAuthorizationClient() (H06 PASO 2) -- autorización
    // de scopes Google (Drive), separada de la autenticación
    // (Credential Manager, no usado aquí: no hace falta saber quién
    // es el usuario, solo pedir permiso sobre su Drive). Versión
    // verificada en línea en S006 contra
    // developer.android.com/identity/authorization.
    implementation(libs.play.services.auth)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)

    testImplementation(libs.junit)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
