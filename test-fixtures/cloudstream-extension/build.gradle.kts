plugins {
    id("com.android.library")
}

android {
    namespace = "com.mutinymedia.fixture"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(project(":cloudstream-runtime"))
}

val fixtureJar = layout.buildDirectory.file("fixture-classes/classes.jar")
val unpackFixture by tasks.registering(Sync::class) {
    dependsOn("bundleDebugAar")
    from({ zipTree(layout.buildDirectory.file("outputs/aar/cloudstream-extension-debug.aar").get().asFile) })
    include("classes.jar")
    into(fixtureJar.map { it.asFile.parentFile })
}

val dexDir = layout.buildDirectory.dir("fixture-dex")
val sdk = androidComponents.sdkComponents.sdkDirectory
val buildTools = android.buildToolsVersion
val dexFixture by tasks.registering(Exec::class) {
    dependsOn(unpackFixture)
    inputs.file(fixtureJar)
    outputs.dir(dexDir)
    doFirst {
        val output = dexDir.get().asFile.apply { mkdirs() }
        val tools = sdk.get().asFile.resolve("build-tools/$buildTools")
        val windows = System.getProperty("os.name").startsWith("Windows")
        val d8 = tools.resolve(if (windows) "d8.bat" else "d8").absolutePath
        val launcher = if (windows) listOf("cmd", "/c", d8) else listOf(d8)
        commandLine(launcher + listOf("--min-api", "26", "--output", output.absolutePath, fixtureJar.get().asFile.absolutePath))
    }
}

val fixtureOutput = layout.buildDirectory.dir("cs3")
val packageProviderFixture by tasks.registering(Zip::class) {
    dependsOn(dexFixture)
    from(dexDir)
    from("manifests/provider")
    from("fixture-assets")
    destinationDirectory.set(fixtureOutput)
    archiveFileName.set("mutiny-cloudstream-fixture.cs3")
}

val packageFailureFixture by tasks.registering(Zip::class) {
    dependsOn(dexFixture)
    from(dexDir)
    from("manifests/failure")
    destinationDirectory.set(fixtureOutput)
    archiveFileName.set("mutiny-cloudstream-failure.cs3")
}

tasks.register("assembleCloudStreamFixtures") {
    dependsOn(packageProviderFixture, packageFailureFixture)
}
