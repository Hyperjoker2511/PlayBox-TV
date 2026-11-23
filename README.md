# PlayBox TV

A clean video player for Android TV and the Jio set-top box. Local and USB videos, seek that actually works. No sign-in, no ads, no Google Play Services.

- **Package:** `com.playbox.tv` — version 1.4.6 (versionCode 19), minSdk 26, targetSdk 36
- **License:** MIT (see `LICENSE`)

## What it plays

- **Local files:** videos on the device, USB drives and folders you add. Music too.
- **Network:** SMB / NAS servers, FTP and SFTP, NFS shares, and stream links (HTTP, HTTPS, HLS, DASH, RTSP, RTMP).
- **Formats:** MP4, MKV, AVI, MOV, WMV, FLV, WebM, VOB, RM/RMVB, DIVX, M2V, OGV, F4V and more. Formats Media3 cannot play route to LibVLC automatically.

## Two engines, automatic

- **Media3 / ExoPlayer 1.6.1** is the default — fast, stable, and the one that never crashes on open.
- **LibVLC 3.7.7** handles the advanced side: FTP/SFTP, RTSP/RTMP, NFS, HDR tone mapping, and formats Media3 cannot play.
- **Auto-fallback:** if Media3 can't play a file (a missing codec, or an MKV with image subtitles), it retries on LibVLC by itself instead of showing an error.
- **HDR:** HDR10, Dolby Vision and HLG files go to LibVLC for tone mapping — they look washed out on Media3.
- You can also pick the engine per file (Details page) or in Settings → Playback → Player.

## Three designs

Pick the whole-app look in Settings → Appearance → App design:

- **Nova** (default) — the from-scratch design: violet and mint, collapsible left rail, feature hero on Home.
- **Qwen** — the kept option: glass look, left rail on TV, rotated glass play tile on the hero.
- **Aura** — lavender on dark, Material 3 + ColorOS 17 style, expanding rail, big purple hero.

The player dock has its own look, separate from the app design: Settings → Playback → Player — Liquid Glass, Prism, Lumen, Nova or Aura. Pick the matching one for a full look, or mix them.

Other appearance options: Liquid / Solid surface (Solid is flat and safe on the 2 GB Jio box; Liquid is a light glass, never a heavy blur), Reduce motion.

## Player

Full-screen video, edge to edge. Everything lives in one bottom dock that appears on a press and fades after 5 seconds idle: seek bar, −10s / +10s, previous / next, play / pause, speed, subtitles, audio track, aspect, settings. The transport is symmetric — the play button sits dead centre.

The gear menu holds the full VLC-style set: speed, loop, shuffle, A–B repeat, jump to time, sleep timer, frame step, bookmarks, picture-in-picture, lock, save playlist, play as audio, subtitle and audio track pickers with delays, equalizer, volume to 200%, aspect, rotation, deinterlace, brightness / contrast / saturation, chapters and video info.

Resume works per file (Continue watching, ordered by last-watched). Subtitles: external `.srt` / `.vtt` / `.ass` files, embedded tracks, and styled subtitles.

## Install

Get the APK from the [Releases](../../releases) page. Pick the one for your device (most phones and the Jio TV are **arm64**):

| APK | For | Size |
| --- | --- | --- |
| `app-arm64-v8a-release.apk` | 64-bit ARM — most phones, Jio STB | ~55 MB |
| `app-armeabi-v7a-release.apk` | 32-bit ARM — older phones | ~41 MB |
| `app-x86_64-release.apk` | 64-bit Intel | ~63 MB |
| `app-x86-release.apk` | 32-bit Intel | ~51 MB |

Installs as an update over 1.4.5 and earlier 1.4.x builds (same signing key). All splits are signed, zipaligned and 16 KB page-aligned, so one build works on both 4 KB and 16 KB page-size devices.

## Jio Store

This app is built for the Jio App Store (developer.jio.com). It targets the Jio STB (JMSD200A, Android 11, 2 GB RAM) and works on any size TV — the layout is responsive, never hard-coded to 1920×1080. No Google Play Services, no ads in v1, no sign-in.

## Build on your PC (Android Studio)

1. Clone this repo and open it in Android Studio.
2. Let Gradle sync (needs internet the first time — it downloads Media3 from Google Maven).
3. Run tests: `./gradlew :app:testDebugUnitTest` (90 tests)
4. Debug APK: `./gradlew :app:assembleDebug`

On Linux/macOS, if `./gradlew` says "permission denied", run `chmod +x gradlew` once — files pushed through GitHub's web/API upload lose the executable bit.

## Signed release

1. Create your release keystore **on your PC only** — never commit it or its passwords to GitHub:
   ```
   keytool -genkeypair -v -keystore playbox-release.keystore -alias playbox -keyalg RSA -keysize 2048 -validity 10000
   ```
   Put the `.keystore` file in the project root, and keep a backup somewhere safe.
2. Copy `keystore.properties.example` to `keystore.properties` and fill in your passwords.
3. Build: `./gradlew :app:assembleRelease`
4. The signed APKs are at `app/build/outputs/apk/release/`.

`keystore.properties`, `*.keystore` and `*.jks` are in `.gitignore` — they must never be pushed.

GitHub Actions also builds the signed release: push a tag like `v1.4.6`, or run it manually from the Actions tab (workflow_dispatch). Unit tests run on every push and pull request. The release job needs these repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Status

**1.4.6 (current)** — Aura design, VLC Patches B (auto-fallback), C (NFS) and D (HDR to LibVLC). Tests 90/90.

Still open: HDMI audio passthrough, and the 24-hour stability / CPU check on the Jio box. The two UI switches (App design vs Player UI) are banked for a later update to make them clearer.
