// No plugins on the root classpath: :app loads the Android and Kotlin plugins together (so
// Kotlin's Android support can see AGP's classes), and the pure-Kotlin :core builds and tests on
// any JVM without Google Maven or an Android SDK (use -PcoreOnly).
