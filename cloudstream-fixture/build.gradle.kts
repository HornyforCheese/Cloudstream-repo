plugins { id("com.android.library") }
android {
    namespace = "org.mutinymedia.fixture"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { compileOnly(project(":cloudstream-runtime")) }
val dexDirectory = layout.buildDirectory.dir("fixture-dex")
val dexFixture by tasks.registering(Exec::class) {
    dependsOn("bundleLibRuntimeToJarDebug")
    inputs.files(tasks.named("bundleLibRuntimeToJarDebug").map { it.outputs.files })
    outputs.dir(dexDirectory)
    doFirst {
        val jar = tasks.named("bundleLibRuntimeToJarDebug").get().outputs.files.files.first { it.extension == "jar" }
        dexDirectory.get().asFile.mkdirs()
        val sdk = androidComponents.sdkComponents.sdkDirectory.get().asFile
        commandLine("$sdk/build-tools/36.0.0/d8", "--min-api", "26", "--lib", "$sdk/platforms/android-36/android.jar", "--output", dexDirectory.get().asFile.absolutePath, jar.absolutePath)
    }
}
tasks.register<Zip>("packageFixture") {
    dependsOn(dexFixture)
    from(dexDirectory)
    from("manifest.json")
    archiveFileName.set("legal-fixture.cs3")
    destinationDirectory.set(layout.buildDirectory.dir("fixture-assets"))
}
