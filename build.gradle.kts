// build.gradle.kts -- raiz del proyecto MiMoo
// Con AGP 9.x kotlin-android viene integrado en AGP, no se declara aqui
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.hilt)                apply false
}
