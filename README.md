# raw_edit

Offline Android app that turns a voiceover (and optional SRT subtitles) into a 1920x1080, 30 fps typography reel MP4 rendered directly on your phone.

## Features

- **100% On-Device & Offline**: No server, no login, no ads, no `INTERNET` permission.
- **Simple Guided Flow**:
  1. **Add voice**: Pick MP3, M4A, WAV, or AAC audio (1s to 20m, up to 100 MB), optional `.srt` subtitle file, and optional topic.
  2. **Copy prompt**: Generates a tailored prompt for Claude requesting a self-contained `window.RAW` HTML typography composition.
  3. **Code paste & sandboxed validator**: Checks static safety rules (no external URLs or network APIs) and runs a dry-run inside a network-blocked WebView (`https://raw.local/` and `https://assets.local/`). Includes a built-in **6s sample test** (`assets/sample.html`).
  4. **Simple layer editor (optional)**: Live 16:9 scrubbable preview, drag-to-move, corner-handle resize (aspect-locked for images), opacity slider, z-order controls, text content edit, add PNG/JPG/WEBP images, timeline strip snapping to 0.1 s, and 50-step undo/redo.
  5. **On-device render & result**: Foreground service renders 1920x1080 @ 30 fps H.264 + 128 kbps AAC MP4, saves to `Movies/raw_edit` via MediaStore, and shares via `FileProvider`.

## Architecture

```
com.rawedit.app/
  RawEditApplication.kt          # Enables WebView slow whole-document draw & notification channel
  MainActivity.kt                # Compose host, Ink-bloom & Ghost-word transitions, navigation
  data/
    CacheManager.kt              # Audio/image import, 512px avatar storage, MediaStore export, temp cleanup
    local/
      Entities.kt                # Room ProjectEntity & VideoEntity
      RawEditDatabase.kt         # Room DAOs and DB singleton
      UserPreferences.kt         # DataStore user name, avatar path, join date, onboarding flag
    model/RawLayer.kt            # RAW.layers serialization & stage export helper
    repository/RawEditRepository.kt
  domain/
    SrtParser.kt                 # Defensive SRT parser (cue cap, control-char stripping, 60 KB snippet)
    PromptGenerator.kt           # Claude prompt builder with/without SRT
    CodeValidator.kt             # Static checks + 10s network-blocked WebView dry-run
  render/
    LocalAssetServer.kt          # intercepts https://raw.local & https://assets.local, returns 403 elsewhere
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
    motion/RawMotion.kt          # Ink-bloom, ghost-word wipe, kinetic headings, swipe slider, confetti
    components/                  # Bottom nav pill, circle avatar cropper, shared widgets
    screens/                     # Onboarding, Setup, Home, CreateHub, Steps, Editor, Rendering, Result, Reels, Profile
```

## Render Engine Notes

- Captures each frame inside a `1920x1080`, `160 dpi` (`1dp = 1px`) `VirtualDisplay` + `Presentation` WebView (with `createWindowContext` on Android 12+ and an offscreen layout fallback).
- Evaluates deterministic `RAW.seek(t)`, waits for visible `<img>` `.decode()` + two `requestAnimationFrame` ticks + `WebView.postVisualStateCallback`, draws onto a single reusable `1920x1080 ARGB_8888` `Bitmap`, and uploads via `GLUtils.texSubImage2D` to `MediaCodec` Surface input with explicit nanosecond timestamps (`eglPresentationTimeANDROID`).
- Bitrates scale by duration (`5.0 Mbps` <= 60s, `3.5 Mbps` <= 3m, `2.8 Mbps` > 3m) to keep 5-6 minute reels around 100-150 MB and short clips crisp.
- Note: Frame-by-frame WebView capture is CPU/GPU intensive on long clips; the render code is isolated in `render/` so capture strategies can be adjusted cleanly without touching UI or data layers.

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
