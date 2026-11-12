# raw_edit

On-device Android Studio app that turns a voiceover (and optional SRT subtitles) plus local media assets into a 1920x1080, 30 fps typography reel MP4 rendered locally on your phone.

## Features

- **Offline On-Device Render Stage (Android 9.0+ / API 28+)**: The WebView render engine (`LocalAssetServer`) strictly blocks all external network loads (`blockNetworkLoads = true`), runs with bundled OFL fonts and local files, and requires zero server or ads. Optional in-app AI features use the **Groq OpenAI-compatible API (`api.groq.com`)** with automatic failover across `meta-llama/llama-4-scout-17b-16e-instruct` & `meta-llama/llama-4-maverick-17b-128e-instruct` (vision, max 5 frames/call) and `llama-3.3-70b-versatile`, `openai/gpt-oss-120b`, & `llama-3.1-8b-instant` (text & patch repair).
- **Groq Live Quota Meter & Key Switching**: Parses live `x-ratelimit-remaining-requests`, `x-ratelimit-limit-requests`, `x-ratelimit-remaining-tokens`, and `x-ratelimit-limit-tokens` headers from Groq API responses, displays remaining quota across **Profile**, **Groq API Key Dialog**, **Palette**, **Asset Locker**, and **Contact Sheet**, and provides instant **"Switch key"** flow (`console.groq.com/keys`) whenever RPD/TPM quota or credits run out.
- **Warm Wallet UI & Zero-Lag Motion**: Dark obsidian `#0D0D0F` canvas with Cream (`#F7ECE6`), Tan (`#CAA07D`), `Bricolage Grotesque`, and `JetBrains Mono` typography, borderless `28.dp` wallet cards, `FeatheredScrollEdges`, slide-to-start Big Type Home screen, 5-tab navigation dock (`Home`, `Palette`, `Create`, `Reels`, `Profile`), and interactive Canvas pixel mascots (`TV_BOT`, `PEEKING_CAT`, `MIC_BOT`, `ZIP_BOT`, `CODE_BOT`, `LENS_BOT`, `PHANTOM_BUDDY`).
- **Offline Asset Locker (`assets/` Interception + HTTP 206 Range Streaming)**: Import photos, transparent PNG cutouts (checkerboard preview), and MP4 video clips (with 3-frame storyboard strips). Intercepted cleanly offline under `assets/<filename>` via `LocalAssetServer`, including `206 Partial Content` byte-range streaming for `<video>` seeking.
- **In-App Web Capture Browser & Icon Finder**: Built directly into the Asset Locker (`WebBrowserAssetScreen`) with Mobile/Desktop `1280px` viewport toggling so tutorial creators can log in, snap crisp UI screenshots (with automatic interactive DOM button/field coordinates mapped for cursor & highlight animations), extract page `<svg>`/`<img>` elements, and search open-source SVG icon sets via Iconify.
- **Automatic PNG Cutout Studio with Groq Vision AI Verification**: Rasterizes sanitized `<svg>` assets (with expanded safe viewBox margins and optional monochrome color tinting) or removes uniform raster mattes via feathered boundary keying (`CutoutProcessor`). When a Groq key is configured, `GroqClient.auditCutout` compares the original and cutout side-by-side to detect unnecessary edge clipping or accidentally erased interior highlights and automatically refines padding/tolerance.
- **Groq Vision Auto-Description & Safe Text Zones**: Analyzes imported images, tutorial UI screenshots, or video clip storyboards via Groq Vision to record a concise visual description and safe typography placement zone, injected directly into the Cloud AI prompt.
- **Studio Palette Generator & Card Exporter (`#0D0D0D` 3-Swatch Card)**: Generates fresh 3-color editorial palettes via Groq AI, renders the `01 / 02 / 03` numbered dark editorial palette card with contrast-aware ink, saves/shares high-res `1080x1440` PNG cards ready for Cloud AI, and injects active palette tokens automatically into `PromptGenerator`.
- **`RAW.syncVideo` Video Sync Helper & Bundled Style Pack ZIP (`kinetic-explainer-style.zip`)**: Ships `kinetic-explainer-style/SKILL.md`, `engine/raw-helpers.js` (`RAW.syncVideo(videoEl, t, start, end, speed)`), `docs/ASSETS_GUIDE.md`, and tokens inside `app/src/main/assets/`. Auto-injected at runtime by `LocalAssetServer` and inspectable inside Step 3.
- **Pre-Render Keyframe Contact Sheet & Two-Stage Groq Frame Auto-Fix**: Samples every keyframe (`Every 0.5s`, `Dense 0.25s`, `Scene beats`) with deterministic live DOM auto-fit (`__rawAutoFitStage` + `tag#id.class@[x,y,wxH]` diagnostics), exports timestamped PDF / ZIP contact sheets, builds targeted **Prompt B** (`<<<SEARCH ... ===REPLACE ... >>>` patches + in-app patch applier), and runs optional **Two-Stage Groq Frame Auto-Fix** (`<= 5` flagged frames in Stage 1 Vision JSON Inspector + up to 2 Stage 2 text-only repair rounds with snapshot rollback on validation/determinism failure).
- **On-Device Hardware MP4 Rendering**: Foreground service renders 1920x1080 @ 30 fps H.264 + 128 kbps AAC MP4, saves to `Movies/raw_edit` via MediaStore, and shares via `FileProvider`.

## Architecture

```
com.rawedit.app/
  RawEditApplication.kt          # Enables WebView slow whole-document draw & notification channel
  MainActivity.kt                # Compose host, morphing transitions, 5-tab dock & Groq API Key dialog
  data/
    CacheManager.kt              # Audio/image/video locker import, HTTP media storage, unlimited script/SRT/HTML storage
    local/
      Entities.kt                # Room ProjectEntity, VideoEntity & LocalAssetEntity (DB v2)
      RawEditDatabase.kt         # Room DAOs, DB singleton & MIGRATION_1_2
      UserPreferences.kt         # DataStore user name, avatar, palette state, Groq API key, model route & live quota headers
    repository/RawEditRepository.kt
  domain/
    SrtParser.kt                 # Defensive SRT parser (up to 4 MB / 20,000 cues, control-char stripping)
    PromptGenerator.kt           # Style-pack, Asset Locker, Studio Palette & RAW.syncVideo Prompt A builder
    PaletteEngine.kt             # PaletteState, WCAG contrast ink math, JSON codec & 1080x1440 PNG exporter
    CutoutMath.kt                # Pure-Kotlin SVG sanitizer/viewBox padding, flood-key math & UI coord formatter
    CutoutProcessor.kt           # Offline WebView SVG batch rasterizer, PNG cutout flood-keyer & comparison cards
    WebAssetFetcher.kt           # Iconify open-source SVG search & safe HTTP/data-URI media downloader
    GroqCodec.kt                 # Groq OpenAI-compatible JSON codec, x-ratelimit-* parser, model router & SEARCH/REPLACE patch engine
    GroqClient.kt                # Resilient Groq AI client (models verify, two-stage vision+patch auto-fix, quota tracking & fallback)
    CodeValidator.kt             # Static checks + 20s network-blocked WebView dry-run + JS error capture
    KeyframeSheetGenerator.kt    # Pre-render timestamped keyframe capture, PDF/ZIP export, <=5 flagged frame selector & Prompt B fix prompt
  render/
    LocalAssetServer.kt          # Offline https://raw.local & https://assets.local interceptor, HTTP 206 Range, __rawSyncVideo & DOM auto-fit
    InputSurface.kt              # EGL14 + OpenGL ES 2.0 Bitmap -> MediaCodec Surface helper
    SurfaceEncoder.kt            # MediaCodec H.264 Surface encoder + MediaMuxer
    AudioPipeline.kt             # Media3 Transformer AAC 128 kbps audio transcode + MediaExtractor muxer
    FrameRenderer.kt             # VirtualDisplay/Presentation 1920x1080 frame capture loop
    RenderService.kt             # Foreground service (mediaProcessing/dataSync), wake lock, cancel, onTimeout
    RenderStateHolder.kt         # Shared StateFlow for live frame preview, progress, and ETA
  ui/
    theme/Theme.kt               # Warm Wallet #0D0D0F obsidian theme, Cream/Tan accents, Bricolage Grotesque & JetBrains Mono
    icons/RawIcons.kt            # Custom drawn Compose Canvas icon set (zero emojis)
    icons/RawPixelSprites.kt     # Interactive monochrome white pixel-art mascots & sparkles
    motion/RawMotion.kt          # Zero-lag GPU screen & tab transitions, feathered edges, swipe slider, wallet cards
    components/                  # 5-tab liquid dock bottom nav, circle avatar cropper, shared widgets
    screens/                     # Onboarding, Setup, Home, Palette, Reels, Profile, CreateHub, AssetLocker, WebBrowser, CutoutStudio, Steps, KeyframeSheet, Rendering, Result
```

## Building the Debug APK

1. Open `C:\Users\Dell\.gemini\antigravity\scratch\raw_edit` in **Android Studio** (JDK 17, Android SDK 36 installed).
2. Let Gradle sync (uses `gradle/libs.versions.toml` and Gradle `8.13` wrapper).
3. Run from terminal or Android Studio:
   - **Windows**: `.\gradlew.bat assembleDebug testDebugUnitTest`
   - **macOS / Linux**: `./gradlew assembleDebug testDebugUnitTest`
4. Output APK path:
   `app\build\outputs\apk\debug\app-debug.apk`

## Open-Source & Font Licenses

- **UI Fonts (`app/src/main/res/font/`, SIL OFL 1.1)**:
  - Bricolage Grotesque & Bricolage Grotesque Condensed
  - JetBrains Mono
- **Bundled WebView Stage Fonts (`app/src/main/assets/fonts/`, SIL OFL 1.1)**:
  - Montserrat (700, 800, 900), Open Sans (400, 600), Poppins (600, 800), Inter (400, 500, 600, 700, 800), Bebas Neue (400), Anton (400), Oswald (600), Playfair Display (700, 900), Space Grotesk (700), JetBrains Mono (400, 500, 700)
- **Libraries (Apache License 2.0)**:
  - AndroidX Core, Lifecycle, Activity Compose, Jetpack Compose & Material 3
  - AndroidX Navigation Compose, Room, DataStore Preferences
  - AndroidX Media3 (ExoPlayer, Transformer, Effect, Common, UI)
  - Coil Compose (`io.coil-kt:coil-compose`)
  - Kotlin Coroutines & `kotlinx.serialization`
  - EGL/GLES2 surface encoder pattern adapted from AOSP Grafika (Apache 2.0)
