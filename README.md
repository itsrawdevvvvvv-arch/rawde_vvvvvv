# raw_edit

Offline Android app that turns a voiceover (and optional SRT subtitles) into a 1920x1080, 30 fps typography reel MP4 rendered directly on your phone.

## Features

- **100% On-Device & Offline (Android 9.0+ / API 28+)**: No server, no login, no ads, no `INTERNET` permission. Optimized for smooth 60 fps UI animations and fast video rendering even on low-end Android 9+ devices.
- **Minimal 3-Step Guided Flow & Smooth Morphing UI**:
  1. **Add voice**: Pick MP3, M4A, WAV, or AAC audio (1s to 20m, up to 100 MB), optional `.srt` subtitle file, and optional topic.
  2. **Copy prompt**: Generates a detailed, layout-guarded prompt for Claude requesting a self-contained `window.RAW` HTML typography composition with safe horizontal margins, character-count font-size caps, high-contrast palettes, and guaranteed visible inline SVG icons.
  3. **Paste code (unlimited length) & sandboxed validator**: Accepts unlimited HTML length via Clipboard paste or `.html`/`.txt` file picker (with zero-lag preview above 20k chars and overflow file storage for Android 9 compatibility), strips markdown fences and chatter around `<!DOCTYPE html>...</html>`, checks offline safety rules, and dry-runs inside a network-blocked WebView (`https://raw.local/` and `https://assets.local/`). Includes a built-in **6s sample test** (`assets/sample.html`).
  4. **On-device render & result**: Foreground service renders 1920x1080 @ 30 fps H.264 + 128 kbps AAC MP4, saves to `Movies/raw_edit` via MediaStore, and shares via `FileProvider`.

## Architecture

```
com.rawedit.app/
  RawEditApplication.kt          # Enables WebView slow whole-document draw & notification channel
  MainActivity.kt                # Compose host, morphing transitions, persistent liquid dock nav bar
  data/
    CacheManager.kt              # Audio/image import, unlimited HTML overflow files, MediaStore export
    local/
      Entities.kt                # Room ProjectEntity & VideoEntity
      RawEditDatabase.kt         # Room DAOs and DB singleton
      UserPreferences.kt         # DataStore user name, avatar path, join date, onboarding flag
    repository/RawEditRepository.kt
  domain/
    SrtParser.kt                 # Defensive SRT parser (cue cap, control-char stripping, 60 KB snippet)
    PromptGenerator.kt           # Claude prompt builder with/without SRT
    CodeValidator.kt             # Static checks + 15s network-blocked WebView dry-run
  render/
    LocalAssetServer.kt          # Intercepts https://raw.local & https://assets.local, returns 403 elsewhere
    InputSurface.kt              # EGL14 + OpenGL ES 2.0 Bitmap -> MediaCodec Surface helper
    SurfaceEncoder.kt            # MediaCodec H.264 Surface encoder + MediaMuxer
    AudioPipeline.kt             # Media3 Transformer AAC 128 kbps audio transcode + MediaExtractor muxer
    FrameRenderer.kt             # VirtualDisplay/Presentation 1920x1080 frame capture loop
    RenderService.kt             # Foreground service (mediaProcessing/dataSync), wake lock, cancel, onTimeout
    RenderStateHolder.kt         # Shared StateFlow for live frame preview, progress, and ETA
  ui/
    theme/Theme.kt               # Monochrome #0F0F0F / #1C1C1C dot-grid theme, Inter & JetBrains Mono
    icons/RawIcons.kt            # Custom drawn Compose Canvas icon set (zero emojis)
    icons/RawIllustrations.kt    # Animated white line-art with stroke reveal & tilt/scroll parallax
    motion/RawMotion.kt          # Morphing screen & tab transitions, liquid dots, swipe slider, confetti
    components/                  # Gliding pill bottom nav bar, circle avatar cropper, shared widgets
    screens/                     # Onboarding, Setup, Home, CreateHub, Steps, Rendering, Result, Reels, Profile
```

## Render Engine Notes

- Captures each frame inside a `1920x1080`, `160 dpi` (`1dp = 1px`) `VirtualDisplay` + `Presentation` WebView (with `createWindowContext` on Android 12+ and an offscreen layout fallback).
- Evaluates deterministic `RAW.seek(t)`, waits for two `requestAnimationFrame` ticks + `WebView.postVisualStateCallback`, draws onto a single reusable `1920x1080 ARGB_8888` `Bitmap`, and uploads via `GLUtils.texSubImage2D` to `MediaCodec` Surface input with explicit nanosecond timestamps (`eglPresentationTimeANDROID`).
- Bitrates scale by duration (`5.0 Mbps` <= 60s, `3.5 Mbps` <= 3m, `2.8 Mbps` > 3m) to keep 5-6 minute reels around 100-150 MB and short clips crisp.

## Building the Debug APK

1. Open `C:\Users\Dell\.gemini\antigravity\scratch\raw_edit` in **Android Studio** (JDK 17, Android SDK 36 installed).
2. Let Gradle sync (uses `gradle/libs.versions.toml` and Gradle `8.13` wrapper).
3. Run from terminal or Android Studio:
   - **Windows**: `.\gradlew.bat assembleDebug`
   - **macOS / Linux**: `./gradlew assembleDebug`
4. Output APK path:
   `app\build\outputs\apk\debug\app-debug.apk`

## Open-Source & Font Licenses

- **Bundled Fonts** (`app/src/main/assets/fonts/`, **SIL Open Font License 1.1**):
  - Montserrat (700, 800, 900)
  - Open Sans (400, 600)
  - Poppins (600, 800)
  - Inter (400, 500, 600, 700, 800)
  - Bebas Neue (400)
  - Anton (400)
  - Oswald (600)
  - Playfair Display (700, 900)
  - Space Grotesk (700)
  - JetBrains Mono (400, 500, 700)
- **Libraries** (**Apache License 2.0**):
  - AndroidX Core, Lifecycle, Activity Compose, Jetpack Compose & Material 3
  - AndroidX Navigation Compose, Room, DataStore Preferences
  - AndroidX Media3 (ExoPlayer, Transformer, Effect, Common, UI)
  - Coil Compose (`io.coil-kt:coil-compose`)
  - Kotlin Coroutines & `kotlinx.serialization`
  - EGL/GLES2 surface encoder pattern adapted from AOSP Grafika (Apache 2.0)
