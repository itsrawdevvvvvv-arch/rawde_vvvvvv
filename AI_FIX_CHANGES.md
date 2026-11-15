# Raw Edit v1.2.0 Major Update Summary

1. **Contact Sheet Complete Toggle (`OK` ↔ `FLAGGED`) & Issue Circle Annotation**:
   - Every generated frame in the Contact Sheet now has an interactive two-state toggle (`✓ OK` and `⚑ FLAGGED`) so users can switch any image from OK to Flagged or Flagged to OK at any time.
   - When an image is Flagged, users can tap **Circle Issue Area** and drag/tap directly on the 16:9 frame to circle the exact problem area in red (with Undo, Clear, and optional issue note).
   - Exported/shared **PDFs** and **ZIPs** (as well as the targeted Fix Prompt and Scene Chatbot) embed the red-circled images and exact `1920x1080` stage coordinates for AI tools.
2. **Instagram-Style Carousel View & Scenes View with Scene Fix Chatbot**:
   - Replaced the contact sheet grid view completely with an **Instagram-style Carousel View (`HorizontalPager`)** featuring swipe navigation, `1 / N` counter badge, pagination dots, and a direct **Select Image (`#01 .. #NN`)** strip.
   - Added two top-level modes in Contact Sheet: **Contact Sheet** and **Scenes**.
   - In **Scenes** mode, users can select any Scene (`Scene 1`, `Scene 2`, ...), inspect and flag/circle its beats, and use the **Scene AI Fix Chatbot** directly below the scene to describe the fix and automatically patch & re-verify the HTML code.
3. **Removals Completed**:
   - Completely removed the legacy **AI Frame Review & Auto-Fix** card from the Contact Sheet.
   - Completely removed **Swiss Grid Motion** style (`swiss-grid-motion-style.zip`, preview assets, and prompt generator branches).
   - Completely removed the **Gemini Free API Key** option/banner from the top of the **Color Palette** feature (`PaletteScreen.kt`).
4. **100% Complete SRT Generator Fix**:
   - Upgraded `SrtParser` to parse all timestamp variants (`HH:MM:SS,mmm`, `MM:SS,mmm`, 1–3 millisecond digits, and flexible arrows) line-by-line so cues are never dropped.
   - Set `maxOutputTokens = 8192/16384` in `GeminiWaterfallRouter` and implemented automatic multi-pass duration-aware continuation + 100% script-to-SRT synchronization (`SrtParser.generateFullScriptSrt`) so SRT generation covers 100% of the script/audio instead of stopping at 25%–50%.
5. **App-Wide AI Chatbot (`ChatbotScreen.kt` + `ChatbotEngine.kt`)**:
   - Accessible across the app (`HomeScreen`, `CreateHubScreen`, `CopyPromptScreen`, `ProfileScreen`).
   - Reads user-pasted SRTs and scripts across projects to suggest viral ideas for the next reel.
   - Customizes both the **Cloud AI Prompt** and files inside **`kinetic-explainer-style.zip`** directly from chat.
   - Includes a **Local File Upload button** supporting files up to **500 MB** (`250–500 MB` range), streamed and stored 100% locally on the phone.
6. **Custom Font Upload (`.ttf`, `.otf`, and Font `.zip`)**:
   - Available directly in **Copy Prompt (Step 3)**, **Asset Locker (Step 2)**, and **Profile / Settings**, supporting `.ttf`, `.otf`, and `.zip` font packs, automatically injected into the offline WebView renderer, Prompt, and Style Pack ZIP.
7. **GitHub Access Token & GitHub Tool/Skill Fetching**:
   - Added GitHub Personal Access Token configuration and skill downloader in **Profile / Settings**, allowing the app and Chatbot (`GitHubToolFetcher.kt`) to fetch skills and tools from GitHub and store them locally.
8. **Full Local App Data Backup (Export & Import)**:
   - Added **Export Data** and **Import Data** (`BackupManager.kt`) in **Profile / Settings** to back up and restore all projects, scripts, SRTs, HTML code, assets, custom fonts, GitHub skills, and preferences locally as a `.zip` archive.
