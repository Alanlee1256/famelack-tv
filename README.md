# Famelack TV 📺

Android app that wraps [Famelack.com](https://famelack.com) — free live TV & radio from around the world.

Works on **Android phones** + **Fire TV Stick** + **Android TV**.

## 📱 Phone: Already Works as PWA

Famelack is already a **Progressive Web App** (PWA). No app needed on your Samsung S24 Ultra:

1. Open **Chrome** on your phone
2. Go to **https://famelack.com/tv/uk**
3. Tap the **⋮ menu** → **Add to Home screen**
4. It installs like a native app with its own icon

No account, no ads, no tracking. Done ✅

## 🔥 Fire TV Stick: Install the APK

For Fire TV, PWAs don't work. Use this Android app instead:

### Option A: Build via GitHub Actions (easiest)

1. Push this repo to GitHub
2. GitHub Actions auto-builds the APK
3. Download from Actions → Artifacts
4. Sideload to Fire TV via **Downloader** app or `adb install`

### Option B: Build locally

Requires Android Studio or Android SDK:

```bash
./gradlew assembleRelease
```

APK lands at `app/build/outputs/apk/release/app-release.apk`

### Sideload to Fire TV

1. Enable **Apps from Unknown Sources** on Fire TV: Settings → My Fire TV → Developer Options
2. Install the **Downloader** app from the Amazon Appstore
3. Push the APK to a file host (or use ADB)
4. Open Downloader → enter the URL → install

Or use ADB:
```bash
adb connect <fire-tv-ip>
adb install app-release.apk
```

## Features

- Full-screen WebView with immersive mode
- DPAD remote navigation (arrows scroll, Enter clicks)
- Back button goes back in history, then exits
- Splash screen with fade-out animation
- Hardware-accelerated video playback
- Works with Fire TV remote, game controllers, Android TV remotes
- Also works on Android phones in portrait mode

## Build Requirements

- Android SDK 34
- JDK 17
- Gradle 8.4 (wrapper included)

## License

This app is a WebView wrapper for Famelack.com — all content belongs to Famelack and the respective stream providers.
