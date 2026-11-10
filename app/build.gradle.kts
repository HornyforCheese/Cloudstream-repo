import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.mutinymedia.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mutinymedia.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-debug"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(project(":cloudstream-runtime"))
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Only the instrumentation APK carries the independently dexed fixture archives.
abstract class StageCloudStreamFixtures : DefaultTask() {
    @get:InputDirectory abstract val fixtures: DirectoryProperty
    @get:OutputDirectory abstract val assets: DirectoryProperty
    @get:Inject abstract val files: FileSystemOperations

    @TaskAction
    fun stage() {
        files.sync { from(fixtures); into(assets) }
    }
}

val stageCloudStreamFixtures by tasks.registering(StageCloudStreamFixtures::class) {
    dependsOn(":cloudstream-extension:assembleCloudStreamFixtures")
    fixtures.set(project(":cloudstream-extension").layout.buildDirectory.dir("cs3"))
    assets.set(layout.buildDirectory.dir("generated/cloudstreamTestAssets"))
}

androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(stageCloudStreamFixtures, StageCloudStreamFixtures::assets)
}
