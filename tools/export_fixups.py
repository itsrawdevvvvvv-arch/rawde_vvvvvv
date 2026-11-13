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
]

for step in STEPS:
    step()
    print("ok:", step.__name__)
