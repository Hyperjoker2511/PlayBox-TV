plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// Toolchain upgrade (2026-11-25): AGP 9.5.2 / Kotlin 2.4.21 / Gradle 9.6.0 /
// Compose BOM 2026.11.01 / compileSdk 37.
// AGP 9 has built-in Kotlin support, so the org.jetbrains.kotlin.android
// plugin is REMOVED — applying it is a build error on AGP 9. The Compose
// compiler plugin version IS the Kotlin version: applying it at 2.4.21
// makes AGP compile with Kotlin 2.4.21 (AGP 9.5 defaults to 2.2.10).
