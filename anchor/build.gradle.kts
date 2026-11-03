// The Android Gradle Plugin is applied only in :app, so the pure-Kotlin :core module can be
// built and tested on any JVM (for example with -PcoreOnly on a machine without the Android SDK).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
