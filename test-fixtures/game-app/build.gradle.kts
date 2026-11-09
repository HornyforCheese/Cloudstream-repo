plugins { id("com.android.application") version "9.1.1" }
android {
    namespace = "com.mutinymedia.fixturegame"
    compileSdk = 36
    defaultConfig { applicationId = "com.mutinymedia.fixturegame"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
