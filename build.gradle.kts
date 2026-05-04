

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Firebase ve Google servisleri için gerekli yol
        classpath("com.google.gms:google-services:4.4.1")
    }
}

plugins {
    // Projenin ana eklentilerini burada tanımlıyoruz
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}