plugins {
    id("com.android.library")
}

android {
    namespace = "com.lagradost.api"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "MDL_API_KEY", "\"\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The v4.5.4 Android release uses Kotlin DefaultImpls and abstract interface descriptors.
kotlin {
    compilerOptions { jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.DISABLE) }
}

dependencies {
    api("com.github.Blatzar:NiceHttp:0.4.13")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    implementation("org.mozilla:rhino:1.8.0")
    api("com.github.teamnewpipe:NewPipeExtractor:v0.24.6")
    implementation("com.uwetrottmann.tmdb2:tmdb-java:2.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
}
