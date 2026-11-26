# Zeph Player

A Material 3 video & music player for Android TV — local files, network shares, and DLNA. No sign-in, no ads, no Google Play Services. Built for the big screen, and for the 2 GB Jio set-top box.

- **Package:** `com.playbox.tv` — version 2.0.0 (versionCode 30), minSdk 26, targetSdk 36
- **License:** MIT (see `LICENSE`)

## What it plays

- **Local files:** videos and music on the device, USB drives and folders you add.
- **Network:** SMB / NAS servers, FTP and SFTP, NFS shares, and stream links (HTTP, HTTPS, HLS, DASH, RTSP, RTMP).
- **Formats:** MP4, MKV, AVI, MOV, WMV, FLV, WebM, VOB, RM/RMVB, DIVX, M2V, OGV, F4V and more. Formats Media3 cannot play route to LibVLC automatically.

## Two engines, your choice

- **Media3 / ExoPlayer** is the default — fast and stable.
- **LibVLC** handles the advanced side: FTP/SFTP, RTSP/RTMP, NFS, HDR tone mapping, and formats Media3 cannot play.
- **Auto** (the default) picks the right engine per file. You can also pick the engine per file on the Details page, in the player, or as the default in Settings.
- **HDR:** HDR10, Dolby Vision and HLG files go to LibVLC for tone mapping — they look washed out on Media3.

## The app

- **Home** — greeting, clock, hero carousel, Continue watching, quick actions (network stream, local storage, network shares, shuffle all music), recently played albums.
- **Video** — grid/list, filters (All / In progress / Unwatched), sorts (Newest / A–Z / Longest), progress on every card.
- **Audio** — songs, albums and artists; shuffle and play-all; the song you're hearing shows a live equalizer animation.
- **Browse** — local storage with real free space, USB drives, network servers, and one folder view for local and network folders.
- **Playlists** — your playlists with cover collages, total time, play and shuffle.
- **Search** — videos, songs, albums and artists; trending suggestions; an on-screen keyboard built for the remote, plus voice search.
- **Now Playing** — slide-up full screen with a live audio visualizer, sleep timer, queue, repeat and shuffle, and per-track volume.
- **Player** — full-screen video with the transport, speed, subtitles, audio tracks, aspect and the full VLC settings in the gear menu. Resume works per file; subtitles from external `.srt` / `.vtt` / `.ass` files or embedded tracks.

Everything runs on Material 3 motion — fade-through screen changes, rising cards, a compact left rail on TV and phone alike. The UI targets 60 fps on the 2 GB box: transform/opacity-only animations, no live blur over video.

## Install

Get the APK from the [Releases](../../releases) page. Pick the one for your device (most phones and the Jio TV are **arm64**):

| APK | For | Size |
| --- | --- | --- |
| `app-arm64-v8a-release.apk` | 64-bit ARM — most phones, Jio STB | ~57 MB |
| `app-armeabi-v7a-release.apk` | 32-bit ARM — older phones | ~41 MB |
| `app-x86_64-release.apk` | 64-bit Intel | ~63 MB |
| `app-x86-release.apk` | 32-bit Intel | ~51 MB |

All splits are signed, zipaligned and 16 KB page-aligned, so one build works on both 4 KB and 16 KB page-size devices.

## Jio Store

This app is built for the Jio App Store (developer.jio.com). It targets the Jio STB (JMSD200A, Android 11, 2 GB RAM) and works on any size TV — the layout is responsive, never hard-coded to 1920×1080. No Google Play Services, no ads, no sign-in.

## Build on your PC (Android Studio)

1. Clone this repo and open it in Android Studio.
2. Let Gradle sync (needs internet the first time — it downloads Media3 from Google Maven).
3. Run tests: `./gradlew :app:testDebugUnitTest`
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

GitHub Actions also builds the signed release: unit tests run on every push, and the release job needs these repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Status

**2.0.0 (in progress)** — a full Material 3 rebuild of the app, one patch at a time with a CI test after each. Done so far: the shell (compact rail, Roboto Flex, M3 motion), Home, Video + Audio, Browse, Playlists + Now Playing, and Search. Still to come in 2.0.0: the Player and Settings.

Still open: HDMI audio passthrough, and the 24-hour stability / CPU check on the Jio box.
