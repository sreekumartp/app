pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Anchor"

include(":core")

// The Android app needs an Android SDK (ANDROID_HOME or local.properties sdk.dir).
// Build the pure-Kotlin core on its own with -PcoreOnly, e.g. in CI without an SDK.
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
