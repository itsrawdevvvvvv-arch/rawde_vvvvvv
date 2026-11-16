#!/usr/bin/env python3
"""Repair pass for raw_edit exports.

The generated exports keep dropping the same handful of things: the version
bump, the navigation-compose pin, the signing config, a few imports, the
CutoutMath regex overload, one bad Groq key reference, a missing Context, the
isDanger parameter, a couple of style-picker imports and a missing RawColors
entry. Every step is idempotent and safe to run on every merge.

Usage (repo root): python3 tools/export_fixups.py <versionCode> <versionName>
"""
import re
import sys
import pathlib

code = sys.argv[1] if len(sys.argv) > 1 else None
name = sys.argv[2] if len(sys.argv) > 2 else None
root = pathlib.Path(".")


def read(p):
    return p.read_text() if p.exists() else None


def package_of(src):
    m = re.search(r'^package\s+([\w.]+)', src, re.M)
    return m.group(1) if m else ""


# ---------------------------------------------------------------- versions
def fix_version():
    if not code or not name:
        return
    p = root / "app/build.gradle.kts"
    s = read(p)
    if s is None:
        return
    s = re.sub(r'versionCode = \d+', f'versionCode = {code}', s, count=1)
    s = re.sub(r'versionName = "[^"]*"', f'versionName = "{name}"', s, count=1)
    p.write_text(s)


def fix_navigation():
    p = root / "gradle/libs.versions.toml"
    s = read(p)
    if s is None:
        return
    s = re.sub(r'^navigationCompose = "[^"]*"', 'navigationCompose = "2.9.8"', s, count=1, flags=re.M)
    p.write_text(s)


SIGN_BLOCK = '''    // One committed keystore signs every build, so a new APK installs as an
    // update over the previous one instead of failing with a signature mismatch.
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("keystore/raw_edit_debug.p12")
            storeType = "PKCS12"
            storePassword = "rawedit2026"
            keyAlias = "raw_edit"
            keyPassword = "rawedit2026"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {'''


def fix_signing():
    p = root / "app/build.gradle.kts"
    s = read(p)
    if s is None:
        return
    if "signingConfigs" not in s:
        s = s.replace("    buildTypes {", SIGN_BLOCK, 1)
    s = s.replace(
        "        debug {\n            isMinifyEnabled = false\n        }",
        '        debug {\n            isMinifyEnabled = false\n            signingConfig = signingConfigs.getByName("dev")\n        }',
    )
    p.write_text(s)


# ---------------------------------------------------------------- imports
COMMON_IMPORTS = [
    (r'\.imePadding\(', 'androidx.compose.foundation.layout.imePadding'),
    (r'\.size\(', 'androidx.compose.foundation.layout.size'),
    (r'\.width\(', 'androidx.compose.foundation.layout.width'),
    (r'\.height\(', 'androidx.compose.foundation.layout.height'),
    (r'\.offset\(', 'androidx.compose.foundation.layout.offset'),
    (r'\.navigationBarsPadding\(', 'androidx.compose.foundation.layout.navigationBarsPadding'),
    (r'\.statusBarsPadding\(', 'androidx.compose.foundation.layout.statusBarsPadding'),
    (r'\.verticalScroll\(', 'androidx.compose.foundation.verticalScroll'),
    (r'\.horizontalScroll\(', 'androidx.compose.foundation.horizontalScroll'),
    (r'rememberScrollState\(', 'androidx.compose.foundation.rememberScrollState'),
    (r'\.clip\(', 'androidx.compose.ui.draw.clip'),
    (r'\bReelEditingStyle\b', 'com.rawedit.app.domain.ReelEditingStyle'),
    (r'\bBitmapFactory\.', 'android.graphics.BitmapFactory'),
    (r'\bCircleAvatarCropperDialog\(', 'com.rawedit.app.ui.components.CircleAvatarCropperDialog'),
    (r'\bLayerTimelineCompiler\b', 'com.rawedit.app.domain.LayerTimelineCompiler'),
    (r'\bLayerTimelineCodec\b', 'com.rawedit.app.domain.LayerTimelineCodec'),
    (r'\bLayerTimelineModel\b', 'com.rawedit.app.domain.LayerTimelineModel'),
    (r'\bAspectPresetStore\b', 'com.rawedit.app.domain.AspectPresetStore'),
    (r'\bBigPrimaryButton\(', 'com.rawedit.app.ui.components.BigPrimaryButton'),
    (r'\bLocalContext\b', 'androidx.compose.ui.platform.LocalContext'),
    (r'\bLocalClipboardManager\b', 'androidx.compose.ui.platform.LocalClipboardManager'),
    (r'\bLocalHapticFeedback\b', 'androidx.compose.ui.platform.LocalHapticFeedback'),
    (r'\bDialog\(', 'androidx.compose.ui.window.Dialog'),
    (r'\brememberCoroutineScope\(', 'androidx.compose.runtime.rememberCoroutineScope'),
    (r'(?<![.\w])launch\(', 'kotlinx.coroutines.launch'),
    (r'\blaunch\s*\{', 'kotlinx.coroutines.launch'),
    (r'\bAnnotatedString\b', 'androidx.compose.ui.text.AnnotatedString'),
    (r'\baspectRatio\(', 'androidx.compose.foundation.layout.aspectRatio'),
    (r'\bImage\(', 'androidx.compose.foundation.Image'),
    (r'\.asImageBitmap\(\)', 'androidx.compose.ui.graphics.asImageBitmap'),
    (r'\bContentScale\b', 'androidx.compose.ui.layout.ContentScale'),
    (r'\bDispatchers\.', 'kotlinx.coroutines.Dispatchers'),
    (r'\bSvgSanitizerAndPadding\b', 'com.rawedit.app.domain.SvgSanitizerAndPadding'),
]


def fix_imports():
    for p in (root / "app/src/main/java").rglob("*.kt"):
        s = p.read_text()
        pkg = package_of(s)
        needed = []
        for pattern, fqn in COMMON_IMPORTS:
            if not re.search(pattern, s):
                continue
            if fqn.rsplit(".", 1)[0] == pkg:
                continue
            if f"import {fqn}" in s:
                continue
            needed.append(f"import {fqn}")
        if not needed:
            continue
        lines = s.split("\n")
        idx = [i for i, ln in enumerate(lines) if ln.startswith("import ")]
        if not idx:
            continue
        block = sorted(set([lines[i] for i in idx] + needed))
        lines[idx[0]:idx[-1] + 1] = block
        p.write_text("\n".join(lines))
        print(f"  imports added to {p.name}: {len(needed)}")


# ---------------------------------------------------------------- targeted repairs
def fix_cutout_regex():
    p = root / "app/src/main/java/com/rawedit/app/domain/CutoutMath.kt"
    s = read(p)
    if s is None:
        return
    pattern = re.compile(r'cleaned = SVG_OPEN_REGEX\.replaceFirst\(cleaned\) \{ match ->.*?\n {12}\}', re.S)
    new = r'''cleaned = run {
                val openMatch = SVG_OPEN_REGEX.find(cleaned)
                if (openMatch == null) return@run cleaned
                val tag = COLOR_ATTR_REGEX.replace(openMatch.value, "")
                val rebuilt = if (tag.endsWith("/>")) {
                    tag.dropLast(2) + " color=\"$normalizedTint\" fill=\"$normalizedTint\" />"
                } else {
                    tag.dropLast(1) + " color=\"$normalizedTint\">"
                }
                cleaned.replaceRange(openMatch.range, rebuilt)
            }'''
    if pattern.search(s):
        p.write_text(pattern.sub(lambda m: new, s, count=1))
        print("  CutoutMath regex rewritten")


def fix_groq_key():
    p = root / "app/src/main/java/com/rawedit/app/MainActivity.kt"
    s = read(p)
    if s is None:
        return
    if "groqApiKey = groqApiKey," in s:
        p.write_text(s.replace("groqApiKey = groqApiKey,", "groqApiKey = userProfile.groqApiKey,", 1))
        print("  MainActivity groq key wired from userProfile")


def fix_assetlocker_context():
    p = root / "app/src/main/java/com/rawedit/app/ui/screens/AssetLockerScreen.kt"
    s = read(p)
    if s is None or "context.getSystemService" not in s:
        return
    start = s.find("fun AssetLockerScreen(")
    if start < 0:
        return
    close = s.index(") {", start) + 3
    if "val context = LocalContext.current" in s[start:close + 200]:
        return
    p.write_text(s[:close] + "\n    val context = LocalContext.current" + s[close:])
    print("  AssetLockerScreen got LocalContext")


def fix_circle_button_danger():
    p = root / "app/src/main/java/com/rawedit/app/ui/components/SharedComponents.kt"
    s = read(p)
    if s is None:
        return
    if "dangerTint" in s:
        return
    old_sig = """    borderColor: Color = Color.Transparent,
    enabled: Boolean = true
) {
    val effectiveBg = if (iconTint == RawColors.ErrorCoral && backgroundColor == RawColors.RaisedCard) {
        RawColors.ErrorSoft
    } else {
        backgroundColor
    }"""
    new_sig = """    borderColor: Color = Color.Transparent,
    enabled: Boolean = true,
    isDanger: Boolean = false
) {
    val dangerTint = isDanger && iconTint == RawColors.TextPrimary
    val effectiveBg = if (isDanger || (iconTint == RawColors.ErrorCoral && backgroundColor == RawColors.RaisedCard)) {
        RawColors.ErrorSoft
    } else {
        backgroundColor
    }
    val effectiveTint = if (dangerTint) RawColors.ErrorCoral else iconTint"""
    if old_sig in s:
        s = s.replace(old_sig, new_sig, 1)
        s = s.replace(
            "                tint = iconTint,\n                strokeWidth = if (size <= 36.dp) 1.9.dp else 2.2.dp",
            "                tint = effectiveTint,\n                strokeWidth = if (size <= 36.dp) 1.9.dp else 2.2.dp",
            1,
        )
        p.write_text(s)
        print("  CircleActionButton gained isDanger")


def fix_card_border_color():
    theme = root / "app/src/main/java/com/rawedit/app/ui/theme/Theme.kt"
    s = read(theme)
    if s is None:
        return
    uses = False
    for p in (root / "app/src/main/java").rglob("*.kt"):
        if "RawColors.CardBorder" in p.read_text():
            uses = True
            break
    if not uses or "val CardBorder" in s:
        return
    anchor = "    val DividerStrong = Color(0x26F7ECE6) // rgba(247,236,230,0.15)"
    if anchor in s:
        s = s.replace(anchor, anchor + "\n    val CardBorder = Color(0x26F7ECE6)", 1)
        theme.write_text(s)
        print("  RawColors.CardBorder added")



def fix_ai_router_compat():
    """New AI router files and their call sites drift apart every export."""
    # maskKey helper used by MainActivity for the saved-key toast
    p = root / "app/src/main/java/com/rawedit/app/domain/GeminiWaterfallRouter.kt"
    s = read(p)
    if s is not None and "fun maskKey" not in s:
        s = s.replace("    companion object {", """    companion object {
        fun maskKey(key: String): String {
            val k = key.trim()
            if (k.length <= 8) return "\\u2022\\u2022\\u2022\\u2022"
            return k.take(4) + "\\u2022\\u2022\\u2022\\u2022" + k.takeLast(4)
        }
""", 1)
        p.write_text(s)
        print("  GeminiWaterfallRouter.maskKey added")

    # hasActiveAi used outside the block that declares it
    p = root / "app/src/main/java/com/rawedit/app/ui/screens/KeyframeSheetScreen.kt"
    s = read(p)
    if s is not None and "hasActiveAi" in s:
        decl = "val hasActiveAi = (userProfile?.hasActiveAiKey ?: false) || apiKey.isNotBlank()"
        hoisted = f"\n    {decl}"
        if hoisted not in s:
            anchor = "    val geminiRouter = remember { GeminiWaterfallRouter(context) }"
            if anchor in s:
                s = s.replace(anchor, anchor + hoisted, 1)
                # drop the deeper duplicate declaration
                s = re.sub(r'\n[ \t]{8,}' + re.escape(decl), '', s, count=1)
                p.write_text(s)
                print("  hasActiveAi hoisted to composable scope")

    # UnifiedAiRouter.autoFixLoop exposes onRound, not onProgress
    p = root / "app/src/main/java/com/rawedit/app/ui/screens/KeyframeSheetScreen.kt"
    s = read(p)
    if s is not None and "onProgress = { fixProgress = it }" in s:
        s = s.replace("onProgress = { fixProgress = it }",
                      'onRound = { round, issuesNow ->\n                                                                fixProgress = "Round $round: $issuesNow issues left"\n                                                            }', 1)
        p.write_text(s)
        print("  autoFixLoop onProgress -> onRound")



def fix_broken_string_literals():
    """Models keep emitting Kotlin strings broken across a real newline."""
    fixed_any = False
    for p in (root / "app/src/main/java").rglob("*.kt"):
        s = p.read_text()
        s2 = re.sub(r'(appendLine\("[^"\n]*:)\n(\$\{[^"\n]*\}"\))', r'\1\\n\2', s)
        s2 = re.sub(r'(\bText\(text = "[^"\n]*:)\n(\$\{[^"\n]*\}"\))', r'\1\\n\2', s2)
        if s2 != s:
            p.write_text(s2)
            fixed_any = True
            print(f"  broken string literals joined in {p.name}")
    if not fixed_any:
        pass



BAD_JSON_PROMPT = """appendLine("Return ONLY valid JSON: {"recommendedTolerance": <int 8..90>, "note": "<short 1-line fix note>"}")"""
GOOD_JSON_PROMPT = """appendLine("Return ONLY valid JSON: {\\"recommendedTolerance\\": <int 8..90>, \\"note\\": \\"<short 1-line fix note>\\"}")"""


def fix_unescaped_json_prompt():
    """Router prompt keeps shipping unescaped quotes inside a Kotlin string."""
    for p in (root / "app/src/main/java").rglob("*.kt"):
        s = p.read_text()
        if BAD_JSON_PROMPT in s:
            p.write_text(s.replace(BAD_JSON_PROMPT, GOOD_JSON_PROMPT))
            print(f"  unescaped JSON prompt quotes fixed in {p.name}")


def fix_router_vision_helpers():
    """refineCutoutWithCircles calls helpers the export never writes."""
    p = root / "app/src/main/java/com/rawedit/app/domain/GeminiWaterfallRouter.kt"
    snippet = root / "tools/router_helpers.kt.txt"
    s = read(p)
    if s is None or not snippet.exists():
        return
    changed = False
    if "private fun bitmapToJpegBase64" not in s:
        anchor = "    suspend fun refineCutoutWithCircles("
        if anchor in s:
            s = s.replace(anchor, snippet.read_text() + anchor, 1)
            changed = True
    if "generateWithWaterfall(" in s:
        s = s.replace("generateWithWaterfall(", "generate(", 1)
        changed = True
    if "                mode = GeminiRouteMode.FAST_CHAT," in s:
        s = s.replace("                mode = GeminiRouteMode.FAST_CHAT,", "                routeMode = GeminiRouteMode.FAST_CHAT,", 1)
        changed = True
    if changed:
        p.write_text(s)
        print("  router vision helpers repaired")


def fix_unescaped_appendline_quotes():
    """appendLine(...) bodies keep shipping raw inner quotes that break parsing.

    Quotes inside ${...} template expressions are legal Kotlin and must be left
    alone, so only quotes at template depth 0 are escaped.
    """
    def escape_inner(inner: str) -> str:
        out = []
        i = 0
        depth = 0
        while i < len(inner):
            ch = inner[i]
            if depth == 0 and inner.startswith("${", i):
                depth = 1
                out.append("${")
                i += 2
                continue
            if depth > 0:
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                out.append(ch)
                i += 1
                continue
            if ch == "\\" and i + 1 < len(inner):
                out.append(inner[i:i + 2])
                i += 2
                continue
            if ch == '"':
                out.append('\\"')
                i += 1
                continue
            out.append(ch)
            i += 1
        return "".join(out)

    for p in (root / "app/src/main/java").rglob("*.kt"):
        s = p.read_text()
        lines = s.split("\n")
        changed = False
        for i, line in enumerate(lines):
            marker = 'appendLine("'
            if marker not in line:
                continue
            start = line.index(marker) + len(marker)
            end = line.rfind('")')
            if end <= start:
                continue
            inner = line[start:end]
            if '"' not in inner:
                continue
            fixed = escape_inner(inner)
            if fixed != inner:
                lines[i] = line[:start] + fixed + line[end:]
                changed = True
        if changed:
            p.write_text("\n".join(lines))
            print(f"  inner quotes escaped in {p.name}")


def fix_router_stray_brace():
    """A duplicated closing brace ends GeminiWaterfallRouter early."""
    p = root / "app/src/main/java/com/rawedit/app/domain/GeminiWaterfallRouter.kt"
    s = read(p)
    if s is None:
        return
    bad = "        }\n    }\n\n    }\n\n    /**\n     * Full 100% Audio-to-SRT"
    good = "        }\n    }\n\n    /**\n     * Full 100% Audio-to-SRT"
    if bad in s:
        p.write_text(s.replace(bad, good, 1))
        print("  router stray closing brace removed")


def fix_assetlocker_card_call():
    """The asset card call keeps arriving with an orphaned .onFailure tail."""
    p = root / "app/src/main/java/com/rawedit/app/ui/screens/AssetLockerScreen.kt"
    s = read(p)
    if s is None:
        return
    changed = False
    bad = '''                                    onEditNote = { editingNoteTarget = asset }.onFailure { err ->
                                                toastMsg = err.message ?: "Could not load image for cutout"
                                            }
                                        }
                                    }
                                )'''
    good = '''                                    onEditNote = { editingNoteTarget = asset }
                                )'''
    if bad in s:
        s = s.replace(bad, good, 1)
        changed = True
    cutout_btn = '''        if (!isClip) {
            RawSecondaryButton(
                text = if (isCutout) "Refine PNG Cutout (AI)" else "Make PNG Cutout (AI)",
                height = 42.dp,
                leadingIcon = RawIconType.CUTOUT,
                enabled = !describing,
                onClick = onMakeCutout
            )
        }
'''
    if cutout_btn in s:
        s = s.replace(cutout_btn, "", 1)
        changed = True
    if changed:
        p.write_text(s)
        print("  asset card call repaired")


def fix_chatbot_state_order():
    """ChatbotScreen uses bg state before it is declared."""
    p = root / "app/src/main/java/com/rawedit/app/ui/screens/ChatbotScreen.kt"
    s = read(p)
    if s is None:
        return
    lines = s.split("\n")
    anchor = next((i for i, l in enumerate(lines) if "var messages by remember(currentSessionId, bgUpdateTick)" in l), None)
    if anchor is None:
        return
    wanted = [
        "ChatbotBackgroundManager.activeSessions.collectAsState()",
        "ChatbotBackgroundManager.updateTick.collectAsState()",
        "ChatbotBackgroundManager.lastToast.collectAsState()",
        "var toastMsg by remember",
    ]
    found = []
    for key in wanted:
        idx = next((i for i, l in enumerate(lines) if key in l), None)
        if idx is None:
            continue
        if idx < anchor:
            continue
        found.append((idx, lines[idx]))
    if not found:
        return
    for idx, _ in sorted(found, reverse=True):
        del lines[idx]
    # anchor shifted by however many were removed above it (none, since all were below)
    anchor = next(i for i, l in enumerate(lines) if "var messages by remember(currentSessionId, bgUpdateTick)" in l)
    block = [l for _, l in sorted(found)] + [""]
    lines[anchor:anchor] = block
    p.write_text("\n".join(lines))
    print("  chatbot bg state hoisted above first use")


def fix_enum_body_brace():
    """An enum whose last constant ends in a comment loses its closing brace."""
    p = root / "app/src/main/java/com/rawedit/app/domain/GeminiWaterfallRouter.kt"
    s = read(p)
    if s is None:
        return
    lines = s.split("\n")
    fixed = False
    for i, line in enumerate(lines):
        if not line.strip().startswith("FAST_CHAT"):
            continue
        j = i + 1
        while j < len(lines) and lines[j].strip() == "":
            j += 1
        if j >= len(lines):
            continue
        nxt = lines[j]
        if nxt.startswith(("enum class", "class ", "object ", "data class", "fun ")):
            lines.insert(i + 1, "}")
            fixed = True
            break
    if fixed:
        p.write_text("\n".join(lines))
        print("  GeminiRouteMode closing brace restored")


STEPS = [
    fix_version,
    fix_navigation,
    fix_signing,
    fix_imports,
    fix_cutout_regex,
    fix_groq_key,
    fix_assetlocker_context,
    fix_circle_button_danger,
    fix_card_border_color,
    fix_ai_router_compat,
    fix_broken_string_literals,
    fix_unescaped_json_prompt,
    fix_router_vision_helpers,
    fix_unescaped_appendline_quotes,
    fix_router_stray_brace,
    fix_assetlocker_card_call,
    fix_chatbot_state_order,
    fix_enum_body_brace,
]

for step in STEPS:
    step()
    print("ok:", step.__name__)
