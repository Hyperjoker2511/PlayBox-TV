plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// Toolchain upgrade (2026-11-25): AGP 9.5.2 / Kotlin 2.4.21 / Gradle 9.6.0.
// AGP 9 has built-in Kotlin support, so the org.jetbrains.kotlin.android
// plugin is REMOVED — applying it is a build error on AGP 9. The Compose
// compiler plugin version IS the Kotlin version: applying it at 2.4.21
// makes AGP compile with Kotlin 2.4.21 (AGP 9.5 defaults to 2.2.10).
// NOTE: Compose BOM stays at 2024.12.01 and compileSdk at 36 — the new
// BOM 2026.11.01 needs compileSdk 37, but Google has not published the
// API 37 platform (repository tops out at android-36), so it cannot be
// installed in CI. When API 37 ships, bump compileSdk/targetSdk to 37
// and the BOM to 2026.11.01 (which brings Material 3 1.5.0).
