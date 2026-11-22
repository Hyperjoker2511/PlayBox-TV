# PlayBox TV

A clean video player for Android TV / Jio set-top box. Local and USB videos, seek that actually works.

- Package: `com.playbox.tv` — version 1.0.3 (versionCode 4), minSdk 26, targetSdk 36
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

Batch 1.3 — player for Jio Store submission, with Qwen review #3 fixes: play-from-start that really starts over, resume for folder/USB videos, up-next follows the folder you opened, screen stays on while playing, Library keeps its place when you come back, controls stop hiding while you navigate, folder/subtitle work moved off the main thread, USB refresh (auto + button), resume keys are the video path itself, Gradle wrapper, CI tests on every push, R8 blanket keeps removed, 320×180 PNG banner. Batch 2 adds LibVLC formats, network stream / SMB, and PlayBox Cast.
