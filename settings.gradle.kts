pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.Blatzar")
                includeGroup("com.github.teamnewpipe")
                includeGroup("com.github.teamnewpipe.NewPipeExtractor")
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}
rootProject.name = "MutinyMediaAndroid"
include(":app", ":cloudstream-runtime")
include(":cloudstream-extension")
project(":cloudstream-extension").projectDir = file("test-fixtures/cloudstream-extension")

include(":cloudstream-fixture")
