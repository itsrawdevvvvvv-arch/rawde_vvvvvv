# RAWEDIT (raw_edit) — COMPLETE APP SPECIFICATION & SYSTEM BLUEPRINT
*Version: 2.4-Production • Target: Android 14+ (API 34) • Engine: Deterministic Web-to-Video Engine*

---

## 1. EXECUTIVE SUMMARY & PRODUCT VISION

### 1.1 What is RawEdit?
**RawEdit** is an offline-first, native Android motion-graphics design studio and automated reel creation suite. It enables solo content creators, educators, SaaS founders, and short-form video editors to produce studio-grade, viral-style 9:16 vertical motion graphic reels (such as kinetic explainer reels in the style of high-production YouTube Shorts, Instagram Reels, and TikToks) directly on a mobile phone without requiring desktop software (After Effects, Premiere Pro, Blender) or expensive cloud rendering subscriptions.

### 1.2 Core Design Philosophy
1. **Zero-Cloud Local Rendering:** Video rendering, HTML execution, audio stitching, and frame capture occur 100% locally on the user's phone via an embedded HTTP server (`127.0.0.1:8765`), headless Android Chromium WebView, and hardware `MediaCodec` H.264 video encoder. Private audio and brand assets never leave the device during video generation.
2. **Deterministic Time-Driven Animation (`seek(t)`):** Unlike standard web animations that rely on real-time browser clocks (`requestAnimationFrame`, CSS keyframes, `setTimeout`), RawEdit animations are mathematical functions of elapsed time `t`. Any frame at timestamp `t` can be rendered with bit-for-bit repeatability, allowing non-real-time offline rendering at perfect 60/30 fps with zero dropped frames.
3. **AI Prompt Discipline ("Spend tokens on fixing, not talking"):** Rather than burning LLM context windows on conversational filler, RawEdit formats structured, constraint-dense prompts designed for frontier LLMs (Claude 3.5 Sonnet / Claude 3.7 Sonnet, OpenAI GPT-4o, Groq Llama 3.3 70B). It forces models to output production-ready, single-file HTML documents.
4. **Continuous Smooth Movement (Zero Static Frames):** Every scene mandates subtle ambient motion, breathing scale, progress strokes, or floating kinetic elements so no frame ever freezes flat during voiceover narration.

---

## 2. TECHNICAL ARCHITECTURE & STACK

### 2.1 Technology Stack Matrix
- **Language & Framework:** 100% Kotlin 1.9.22, Jetpack Compose, Material Design 3.
- **Minimum SDK:** API 26 (Android 8.0 Oreo) • **Target SDK:** API 34 (Android 14 UpsideDownCake).
- **Architecture Pattern:** Clean Architecture / MVI (Model-View-Intent) with Coroutines and StateFlow.
- **Local Persistence:**
  - **Room Database (`RawEditDatabase`):** Tables for `projects`, `videos` (exported MP4s), and `assets` (media catalog).
  - **Jetpack DataStore Preferences (`raw_edit_prefs`):** User profile, onboarding status, Groq API keys, model routing preference, rate-limit headers.
- **Local HTTP Asset Server:** Custom embedded server (`LocalAssetServer`) running on `127.0.0.1:8765`, serving bundled fonts, imported custom `.ttf`/`.otf` fonts, local project assets, and rendered HTML without internet access.
- **Render & Video Pipeline:**
  - **Headless WebView Surface:** Android `WebView` mounted in an off-screen `VirtualDisplay` via `Presentation`.
  - **Graphics & Hardware Encoding:** Android OpenGL `InputSurface` paired with hardware `MediaCodec` AVC (H.264 High Profile Level 4.1) using Variable Bit Rate (VBR).
  - **Container & Audio Muxer:** Android `MediaMuxer` combining H.264 video tracks with AAC/PCM audio tracks.
  - **Background Execution:** Android `ForegroundService` (`RenderService`) with CPU `WakeLock` and persistent notification tracking.
- **AI Integrations:**
  - **Groq API Cloud Engine (`api.groq.com`):** Direct REST integration via `HttpURLConnection` and `org.json`. Supports `llama-3.3-70b-versatile`, `llama-3.1-8b-instant`, `llama-3.2-11b-vision-preview`, and `whisper-large-v3`.
  - **Claude / LLM Prompt Engineering Bridge:** Structured prompt generator with interactive questionnaire.

---

## 3. COMPLETE SCREEN-BY-SCREEN FUNCTIONALITY

### 3.1 Home Screen (`HomeScreen.kt`)
- **Quick Action Cards:**
  - **"Start New Reel"**: Launches the step-by-step project creation wizard.
  - **"Color Studio"**: Quick launcher for generating and copying studio palettes.
  - **"Asset Locker"**: Quick launcher to manage images, SVGs, and custom fonts.
- **Recent Projects Carousel:** Displays in-progress and finished reels with duration, cue count, and last edited timestamp.
- **Profile Banner:** Shows user avatar, display name, and Groq API status pill.

### 3.2 Project Creation Flow (`CreateHubScreen.kt` & `StepScreens.kt`)

#### Step 1: Add Voice & Synchronized Cues (`AddVoiceScreen`)
- **Audio File Upload:** Supports `.mp3`, `.wav`, `.m4a`, and `.aac`. Automatically measures audio duration down to milliseconds.
- **Word-by-Word Groq Whisper Transcription:**
  - Upload audio and tap **"Transcribe Word-by-Word (Groq)"**.
  - Calls Groq's speech-to-text API (`whisper-large-v3`) with `verbose_json` and `timestamp_granularities[]=word`.
  - Parses word-level start/end timestamps and automatically synthesizes a SubRip (.srt) subtitle script.
- **Manual Input Fallbacks:**
  - Text area for pasting raw SubRip (.srt) cues.
  - Text area for topic / narration script notes.

#### Step 2: Visual Assets Selection (`PickAssetsScreen`)
- Select images, transparent cutouts, logos, UI screenshots, or video b-roll from the Asset Locker to attach to the project.
- Automatically calculates aspect ratios, natural dimensions, and relative paths (`assets/...`).

#### Step 3: Studio Palette (`PickPaletteScreen`)
- Assigns a color scheme to the project from the Studio Palette generator.
- Guarantees high-contrast pairing between typography, accent pills, and surface backgrounds.

#### Step 4: Claude Prompt Generation (`CopyPromptScreen`)
- Generates a production-grade motion-graphics prompt containing:
  - Exact audio duration, file name, and dimensions (1920x1080, 30 fps).
  - Selected color palette hex codes and pairing rules.
  - Full SRT cue timestamps and narrated topics.
  - Manifest of attached assets with safe text zone coordinates.
  - List of bundled offline fonts and user-imported custom fonts.
  - **Interactive 3-Choice Questionnaire:** Forces the LLM to ask for Palette choice, Font pairing, and Animation Style (Simple ZIP vs Apple-Style Bouncy) before generating code.
  - **Deterministic Engine Rules & Motion Directives:** Mandates zero static frames, continuous ambient drift, and pure `seek(t)` compliance.
- One-tap button to copy the prompt to clipboard with haptic confirmation.

#### Step 5: HTML Import & Live Code Validation (`PasteHtmlScreen` & `CodeValidator.kt`)
- Pasting HTML automatically triggers **Markdown Fence Stripping** and **Static Security/Syntax Auditing**:
  - Detects and rejects forbidden tags: `<script src="...">`, `<link>`, `@import`, `fetch()`, `XMLHttpRequest`, `WebSocket`, dynamic `import()`, external HTTP/HTTPS URLs.
- **Headless Dry-Run Execution:**
  - Loads the HTML into a sandboxed offscreen WebView.
  - Verifies presence of `window.RAW` with `duration`, `width`, `height`, `fps`, and `seek(t)`.
  - Runs a **Determinism Test**: calls `seek(0)`, `seek(duration / 2)`, and `seek(0)` again, verifying that the DOM state remains identical without leaking memory or accumulating child elements.
  - Intercepts JavaScript console errors and runtime exceptions via `WebChromeClient`.

#### Step 6: Keyframe Contact Sheet & Visual AI Auditor (`KeyframeSheetScreen.kt` & `KeyframeSheetGenerator.kt`)
- Renders an **18-frame 3x6 contact sheet** sampling key moments across the entire reel duration.
- **DOM & Visual Heuristic Checks:**
  - Warns on bounding-box collisions, element overlaps, and cards extending into forbidden safe zones (below `y 930` where the bottom progress bar sits).
  - Detects text overflows, auto-fitted font shrinking, and missing asset references.
- **AI Auto-Fix Engine:**
  - Generates a targeted fix prompt for Claude or Groq Vision.
  - Injects the instruction: *"Go through the visuals in detail and give the final downloadable fixed html , fixing all the errors. There are more other errors than the listed find all and fix."*
  - Allows 1-tap auto-patching via Groq API models.

#### Step 7: Video Rendering & Export (`RenderingScreen.kt` & `RenderService.kt`)
- Executes as an Android **Foreground Service** with CPU WakeLock.
- Steps through time `t` from `0` to `duration` in increments of `1 / 30` s.
- Captures WebView frames onto an OpenGL `InputSurface`, encodes video via hardware `MediaCodec` H.264 VBR, and muxes with the original audio track into an MP4 file.
- Displays a real-time progress bar, percentage, and estimated time remaining.

### 3.3 Asset Locker & Studio Tools (`AssetLockerScreen.kt`)
- **Asset Catalog:** Filter assets by Images, Cutouts, Videos, and Custom Fonts.
- **Custom Font Importer (.ttf / .otf):**
  - Users can tap **"Import Custom Font"** and select any font file from their device.
  - Fonts are stored in internal app storage (`custom_fonts/`).
  - `LocalAssetServer` dynamically registers `@font-face` rules for the humanized family name and file basename at weights 400 and 700.
  - One-tap button to copy the CSS `font-family` name.
- **Cutout Studio Dialog (`CutoutStudioDialog.kt` & `CutoutProcessor.kt`):**
  - **Chroma-Key Background Removal:** Automatic detection of uniform corner background colors.
  - **Flood-Fill & Edge Feathering:** Adjustable tolerance (0–100) and custom padding ratios (top, bottom, left, right).
  - **Alpha Trimming:** Automatically crops transparent borders around the subject.
- **Web Asset Fetcher & Icon Browser (`WebBrowserAssetScreen.kt` & `WebAssetFetcher.kt`):**
  - Search over 100,000 vector icons via the Iconify API directly within the app.
  - Live preview with custom color tinting.
  - Download as vector SVG or rasterize to transparent PNG with custom resolution.
- **Circle Avatar Cropper (`CircleAvatarCropper.kt`):**
  - Interactive pan/zoom circular mask cropper for creator profile pictures and testimonial avatars.

### 3.4 Studio Palette Generator (`PaletteScreen.kt` & `PaletteEngine.kt`)
- Generates 3-to-5 color palettes tailored for motion graphics.
- **WCAG Contrast Checker:** Calculates relative luminance and ensures minimum 4.5:1 contrast ratios for legibility.
- **Palette Roles:** Defines semantic roles (e.g., `01 Display Headline`, `02 Accent Pill`, `03 Background Surface`).
- **Copy Palette Prompt:** Copies color hex codes, contrast notes, and pairing tips to the clipboard.
- **PNG Card Export:** Generates a downloadable high-resolution palette swatch image.

### 3.5 My Reels Library (`MyReelsScreen.kt` & `ResultScreen.kt`)
- Grid of all exported MP4 reels with video thumbnails, duration, and file size.
- **Built-in Video Player:** Fullscreen video preview with loop and scrub controls.
- **Export Options:**
  - One-tap save to Android System Gallery (`MediaStore.Video`).
  - Native Android Share Sheet (send directly to Instagram, TikTok, YouTube Shorts, WhatsApp, Drive).
  - Delete and manage video storage.

### 3.6 Profile & Settings (`ProfileScreen.kt` & `GroqKeyDialog.kt`)
- Custom creator name and pixel-art avatar.
- **Groq API Key Manager:**
  - Securely stores the user's Groq API key (`gsk_...`) in encrypted DataStore.
  - **Zero-Token Connection Test:** Authenticates against `GET /openai/v1/models` without consuming completion tokens.
  - **Rate Limit Tracking:** Displays remaining daily requests and tokens based on response headers without annoying toast popups.
  - **Model Route Selector:** Choose between Auto Routing, Llama 3.3 70B, Llama 3.1 8B, or Preview models.

---

## 4. DETERMINISTIC RENDERER ENGINE SPECIFICATION

Any HTML generated for RawEdit must adhere to the **`window.RAW` runtime contract**:

```javascript
window.RAW = {
  duration: 15.5,   // Total reel duration in seconds
  width: 1920,      // Stage width in pixels (vertical 9:16 is 1080x1920, stage canvas is 1920x1080 rotated or 1080x1920)
  height: 1080,     // Stage height in pixels
  fps: 30,          // Frame rate (30 fps)
  seek: function(t) {
    // Pure mathematical function of t in [0, duration]
    // Updates style transforms, opacities, and SVG attributes
  }
};
```

### 4.1 Strict Engine Rules
1. **DOM Built Once at Load:** All elements, SVGs, and cards must be created on initialization. `seek(t)` is strictly prohibited from calling `innerHTML`, `appendChild`, or `removeChild`.
2. **Deterministic & Idempotent:** Calling `seek(2.5)` ten times in a row must yield identical DOM state. Calling `seek(5.0)` and then `seek(1.0)` must cleanly rewind all animations.
3. **No Browser Clocks or Animations:** Strictly forbidden: CSS `@keyframes`, CSS `transition`, `requestAnimationFrame`, `setTimeout`, `setInterval`, `Date.now()`, `Math.random()`.
4. **Layout Geometry & Safe Areas:**
   - Safe rendering area: `x 120..1800`, `y 70..930`.
   - Bottom progress bar reservation: `y 972..1080` (nothing may overlap below `y 930`).
   - Bracket clearance: Visual brackets extend `34 px` outside cards; bounding boxes must account for this offset.
5. **Bundled Offline Fonts:**
   - Default: `Poppins`, `Montserrat`, `Open Sans`, `Inter`, `Bebas Neue`, `Anton`, `Oswald`, `Playfair Display`, `Space Grotesk`, `JetBrains Mono`.
   - Custom: Any user-imported font served dynamically by `LocalAssetServer`.

---

## 5. COMPLETE DATA MODELS & STORAGE SCHEMA

### 5.1 Room Database Entities (`Entities.kt`)

#### `ProjectEntity` (`projects`)
- `id: Long` (Primary Key)
- `title: String`
- `audioUri: String`, `audioName: String`, `audioSizeBytes: Long`, `audioDurationSec: Double`
- `srtText: String`, `srtCueCount: Int`
- `topic: String`
- `code: String` (The complete self-contained HTML/JS document)
- `layersJson: String` (Optional visual layer metadata)
- `codeDurationSec: Double`
- `hasEditableLayers: Boolean`
- `attachedAssetIds: String` (Comma-separated asset IDs)
- `state: String` ("IN_PROGRESS" | "FINISHED")
- `createdAt: Long`, `updatedAt: Long`

#### `VideoEntity` (`videos`)
- `id: Long` (Primary Key)
- `projectId: Long` (Foreign key to project)
- `title: String`
- `durationSec: Double`
- `sizeBytes: Long`
- `fileUri: String` (File path in app internal cache)
- `thumbnailPath: String` (JPEG thumbnail of first frame)
- `savedToGalleryUri: String?` (MediaStore URI if exported to camera roll)
- `createdAt: Long`

#### `LocalAssetEntity` (`assets`)
- `id: Long` (Primary Key)
- `fileName: String`, `relativePath: String` (e.g. `assets/cutout_123.png`)
- `kind: String` ("IMAGE", "CUTOUT", "VIDEO", "SVG")
- `mimeType: String`
- `width: Int`, `height: Int`
- `durationSec: Double` (For video assets)
- `sizeBytes: Long`
- `thumbPath: String`
- `description: String` (Visual description for AI prompts)
- `safeZone: String` (Bounding box coordinates to keep clear of text)

---

## 6. BRAINSTORMING VECTORS FOR THIRD-PARTY AI TOOLS

Use the prompts below in **Claude 3.7 Sonnet**, **GPT-4o**, or **DeepSeek-R1** to brainstorm feature expansions, viral growth tactics, and architectural upgrades for RawEdit.

### Prompt 1: Feature Expansion & Next-Gen Creator Capabilities
> *"Act as a Principal Product Architect for mobile creative tools (CapCut, Canva, Procreate). Review the RawEdit specification above. RawEdit uniquely renders deterministic, code-driven HTML motion graphics offline on Android. What are 10 high-leverage features that would make RawEdit an indispensable tool for short-form creators? Focus on audio-driven visuals, viral kinetic typography trends, and workflow acceleration."*

### Prompt 2: Advanced Visual Component Library
> *"Review the RawEdit `window.RAW` deterministic renderer specification. Design 8 modular, pure-SVG and CSS deterministic visual components that can be injected into the prompt generator (e.g., animated subscriber count counters, dynamic audio waveform oscilloscopes, glowing code-terminal windows, 3D card tilts, swipe-up notification banners). Provide exact mathematical equations for calculating motion as a function of elapsed time `t`."*

### Prompt 3: Monetization & Pro Tier Strategy
> *"Analyze the RawEdit architecture (100% client-side rendering, user-provided Groq API keys, local font/asset management). Design a sustainable monetization model that does not incur high server rendering costs. Consider Pro templates, cloud asset packs, team collaboration, desktop companion apps, and licensing options for agency creators."*

### Prompt 4: AI Model Evaluation & Self-Correction Pipeline
> *"RawEdit uses a 3x6 Keyframe Contact Sheet to audit generated HTML for visual glitches, DOM overlaps, and contrast failures before video export. Design an automated, multi-turn self-correction agent loop using Groq Llama 3.2 Vision that inspects the contact sheet images, calculates exact pixel bounding errors, and outputs surgical unified search-and-replace patches to fix the reel automatically."*

---

## 7. RESOLVED ARCHITECTURAL AUDIT: GROQ API KEY ROUTING & VISION REPAIR

### 7.1 Root Cause of "Groq API returned empty output"
1. **Hidden Reasoning Token Exhaustion**: In previous builds, the "Test & Save Key" dialog triggered a probe completion with `max_completion_tokens = 64` across a sequential model fallback chain (`llama-3.3-70b-versatile -> meta-llama/llama-4-scout-17b-16e-instruct -> llama-3.1-8b-instant -> openai/gpt-oss-120b`). When routed through reasoning models (`openai/gpt-oss-120b` or Llama variants), the model spent all 64 tokens inside internal reasoning blocks (`reasoning` / `<think>`), generating an empty visible `content` string with `finish_reason = length`.
2. **Strict Regex Stripping**: The response extractor previously treated empty `content` as a fatal failure and stripped `<think>` blocks without extracting inner fallback content, resulting in the error:
   `"Groq API returned empty output. (tried llama-3.3-70b-versatile -> meta-llama/llama-4-scout-17b-16e-instruct -> llama-3.1-8b-instant -> openai/gpt-oss-120b)"`
3. **Decommissioned Vision Endpoints**: Meta's legacy preview models (`llama-3.2-11b-vision-preview` and `llama-3.2-90b-vision-preview`) were decommissioned on `api.groq.com`. Because `isVisionModel()` strictly filtered for the word `"vision"`, active multimodal models (`meta-llama/llama-4-scout-17b-16e-instruct` and `qwen/qwen3.8-27b`) were rejected from vision routing and accidentally included in text chains, causing Contact Sheet Review to fail and burn retry quotas.

### 7.2 The Permanent Fix
1. **Decoupled Key Authentication**: `testConnectionDetailed()` validates the API key directly via `GET https://api.groq.com/openai/v1/models`. If `/models` returns HTTP 200, the key is authenticated, available models are cached, and the key is saved immediately. The subsequent chat probe uses 128 tokens with temperature 0.5 and is non-blocking, so probe hiccups never prevent saving a valid key.
2. **Native Multimodal Routing**: Updated `VISION_MODELS` and `isVisionModel()` to recognize `meta-llama/llama-4-scout-17b-16e-instruct`, `meta-llama/llama-4-maverick-17b-128e-instruct`, and `qwen/qwen3.8-27b` as first-class vision models on Groq.
3. **Smart Content & Thinking Extraction**: `extractResponseText()` now checks `isNotBlank()` across `content`, `reasoning`, and `reasoning_content`, falling back to inner thought content if tags are stripped, preventing false "empty output" exceptions.
4. **Quota Hardening**: `GroqLimiter.markReadyIfNotHardBlocked()` clears stale rate window blocks upon key re-testing, ensuring zero false-positive quota locks.

