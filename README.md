# PlayBox TV

A clean video player for Android TV / Jio set-top box. Local and USB videos, seek that actually works.

- Package: `com.playbox.tv` — version 1.2.0 (versionCode 7), minSdk 26, targetSdk 36
- Engine: Media3 / ExoPlayer 1.6.1 (Batch 1)
- UI: Material 3, full-screen player, left navigation rail
- No sign-in, no ads, no Google Play Services
- License: MIT (see `LICENSE`)

## Build on your PC (Android Studio)

1. Clone this repo and open it in Android Studio.
2. Let Gradle sync (needs internet the first time — it downloads Media3 from Google Maven).
3. Run tests: `./gradlew :app:testDebugUnitTest`
4. Debug APK: `./gradlew :app:assembleDebug`

On Linux/macOS, if `./gradlew` says "permission denied", run `chmod +x gradlew` once — files pushed through GitHub's web/API upload lose the executable bit.

## Signed release (for Jio Store)

1. Create your release keystore **on your PC only** — never commit it or its passwords to GitHub:
   ```
   keytool -genkeypair -v -keystore playbox-release.keystore -alias playbox -keyalg RSA -keysize 2048 -validity 10000
   ```
   Put the `.keystore` file in the project root, and keep a backup somewhere safe.
2. Copy `keystore.properties.example` to `keystore.properties` and fill in your passwords.
3. Build: `./gradlew :app:assembleRelease`
4. The signed APK is at `app/build/outputs/apk/release/app-release.apk`.

`keystore.properties`, `*.keystore` and `*.jks` are in `.gitignore` — they must never be pushed.

GitHub Actions also builds the signed release: push a tag like `v1.0.3`, or run it manually from the Actions tab. Unit tests run on every push and pull request.

## Status

Batch 1.4 — Qwen review #3 polish: "May not play" badges for WMV/ASF/AVI/FLV, wave progress reuses one Path with dp units, status messages clear after ~4s, FF/RW follow the skip setting, speed 0.5x in Settings, in-player speed is temporary (no longer overwrites the default), Continue watching ordered by last-watched, music keeps playing when the screen turns off. Batch 2 adds LibVLC formats, network stream / SMB, and PlayBox Cast.
