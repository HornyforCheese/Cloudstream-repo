plugins { id("com.android.library") }
android {
    namespace = "com.lagradost.api"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api("androidx.core:core-ktx:1.17.0")
    api("com.github.Blatzar:NiceHttp:0.4.13")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api("me.xdrop:fuzzywuzzy:1.4.0")
    api("org.mozilla:rhino:1.8.0")
    api("com.github.teamnewpipe:NewPipeExtractor:v0.24.6")
    api("com.uwetrottmann.tmdb2:tmdb-java:2.11.0")
    api("org.jsoup:jsoup:1.18.3")
    api("org.jetbrains.kotlin:kotlin-reflect:2.2.10")
}
