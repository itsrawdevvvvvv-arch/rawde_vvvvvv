# raw_edit

Offline Android app that turns a voiceover (and optional SRT subtitles) into a 1920x1080, 30 fps typography reel MP4 rendered directly on your phone.

## Features

- **100% On-Device & Offline (Android 9.0+ / API 28+)**: No API keys, no server, no login, no ads, no `INTERNET` permission. Optimized for smooth morphing UI animations and fast video rendering even on low-end Android 9+ devices.
- **Playful Typography Home & Animated Pixel Mascots**: Clean typography-led home screen (`Let's make a reel!`) featuring custom Canvas-rendered, interactive white pixel-art mascots and companions across tabs and creation steps, while keeping all drafts and finished MP4s neatly organized in **My reels** with visible one-tap Delete controls across `MyReelsScreen`, `CreateHubScreen`, and `ResultScreen`.
- **Bundled Style Pack ZIP (`kinetic-explainer-style.zip`)**: Ships the complete kinetic cream-and-crimson explainer skill pack (`SKILL.md`, `STYLE_BIBLE.md`, `BEAT_SHEETS.md`, design tokens, components, reference sheets, and deterministic scene engine) inside `app/src/main/assets/`. Users can download or share the ZIP directly from Step 2 and attach it to Cloud AI alongside the prompt.
- **Minimal 3-Step Guided Flow & Smooth Morphing UI**:
  1. **Add voice**: Pick MP3, M4A, WAV, or AAC audio (1s to 20m, up to 100 MB), optional `.srt` subtitle file (up to 4 MB / 20,000 cues), and unlimited-length voiceover script / topic notes (overflow-persisted on disk cleanly for Android 9 `CursorWindow` compatibility).
  2. **Copy prompt & style ZIP**: Download `kinetic-explainer-style.zip` (MediaStore `Downloads/raw_edit` on Android 10+ or SAF document saver on Android 9) or Share it directly into Cloud AI, and copy/share a strict, layout-guarded prompt that keeps AI HTML compact (`window.PROJECT` + deterministic `seek(t)` + `window.RAW`), prevents horizontal text clipping, ensures visible inline SVGs, and avoids token-limit truncation.
  3. **Paste code (unlimited length), Live DOM Auto-Fit & Contact Sheet**: Accepts unlimited HTML length via Clipboard paste or `.html`/`.txt` file picker, unwraps multi-fence / chatter markdown responses cleanly, auto-bridges `window.PROJECT + window.seek` into `window.RAW`, applies deterministic runtime DOM auto-fit (`__rawAutoFitStage`) so oversized/wrapping text stays inside frame and off right-hand cards/progress bars on both preview and render, and opens a **Pre-Render Keyframe Contact Sheet** (`Every 0.5s`, `Dense 0.25s`, `Scene beats`) with timestamped multi-page PDF / JPG+Report ZIP export + smart targeted AI fix prompt.
  4. **On-device render & result**: Foreground service renders 1920x1080 @ 30 fps H.264 + 128 kbps AAC MP4, saves to `Movies/raw_edit` via MediaStore, and shares via `FileProvider`.

## Architecture

```
com.rawedit.app/
  RawEditApplication.kt          # Enables WebView slow whole-document draw & notification channel
  MainActivity.kt                # Compose host, morphing transitions, persistent liquid dock nav bar
  data/
    CacheManager.kt              # Audio/image import, unlimited script/SRT/HTML storage, style ZIP export
    local/
      Entities.kt                # Room ProjectEntity & VideoEntity
      RawEditDatabase.kt         # Room DAOs and DB singleton
      UserPreferences.kt         # DataStore user name, avatar path, join date, onboarding flag
    repository/RawEditRepository.kt
  domain/
    SrtParser.kt                 # Defensive SRT parser (up to 4 MB / 20,000 cues, control-char stripping)
    PromptGenerator.kt           # Style-pack-aware Cloud AI prompt builder with anti-clipping & contract rules
    CodeValidator.kt             # Static checks + 20s network-blocked WebView dry-run + JS error capture
    KeyframeSheetGenerator.kt    # Pre-render timestamped keyframe capture, PDF/ZIP contact sheet & AI fix prompt
  render/
    LocalAssetServer.kt          # Intercepts https://raw.local & https://assets.local, font aliases, RAW bridge, DOM auto-fit
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
    icons/RawPixelSprites.kt     # Interactive monochrome white pixel-art mascots & sparkles
    motion/RawMotion.kt          # Morphing screen & tab transitions, liquid dots, swipe slider, confetti
    components/                  # Gliding pill bottom nav bar, circle avatar cropper, shared widgets
    screens/                     # Onboarding, Setup, Home, CreateHub, Steps, KeyframeSheet, Rendering, Result, Reels, Profile
```

## Render Engine Notes

- Captures each frame inside a `1920x1080`, `160 dpi` (`1dp = 1px`) `VirtualDisplay` + `Presentation` WebView (with `createWindowContext` on Android 12+ and an offscreen layout fallback).
- Evaluates deterministic `RAW.seek(t)` (or auto-bridged `window.seek(t)` + `window.PROJECT.duration`), waits for two `requestAnimationFrame` ticks + `WebView.postVisualStateCallback`, draws onto a single reusable `1920x1080 ARGB_8888` `Bitmap`, and uploads via `GLUtils.texSubImage2D` to `MediaCodec` Surface input with explicit nanosecond timestamps (`eglPresentationTimeANDROID`).
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
