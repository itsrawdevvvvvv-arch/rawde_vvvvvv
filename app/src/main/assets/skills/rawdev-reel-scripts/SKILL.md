---
name: rawdev-reel-scripts
description: Write, polish and TTS-format short Hinglish Instagram reel and YouTube scripts in the RAWDEV creator style (playful, punchy, comment-keyword CTA, "Byeeee" sign-off). Use whenever the user asks for a reel script, hook ideas, script variations, a rewrite of their raw script or a competitor's script "in my style", a promo for a tool/app/video, or says "TTS" (ElevenLabs v3 audio-tag formatting). Trigger even if they only paste a raw script and say "make it better", "shorten this", or "TTS".
---

# RAWDEV reel script skill

Creator: RAWDEV. Audience: mobile creators and editors who want free AI tools, tutorials and growth tips. Voice: a friendly, energetic, slightly funny creator talking straight to one viewer.

## Language rules
- Drafts, hooks, notes and chat replies: **Hinglish in Roman script** (or English). Never Devanagari in drafts.
- Devanagari appears **only** inside a TTS output (see TTS section).
- Emojis: a few, at the hook and the payoff. Not on every line.

## Default reel script shape (short reel, ~60-70 words, 20-25 sec)
1. **Hook (first 3 sec):** problem or result first, never "Hello guys". One strong line.
2. **Payoff lines:** 3-4 short lines, one idea each, easy to cut with visuals.
3. **CTA:** `Link chahiye? Comment karo "<keyword>", main DM kar dunga.`
4. **Close:** `Baki follow bhi kar lena. Byeeeeeee!` (keep the stretched "Byeeeeeee").

Length rules:
- "Short reel" or a word limit (e.g. 60-70) means obey it and report the word count.
- If the user wants a 5-8 minute YouTube script, write the full length with steps, an example, a pattern interrupt every 20-30 sec, a recap, and an open loop to the next video.
- When asked to shorten or improve a raw script, **never cut the main tutorial steps or important parts**. Tighten the hook and wording, keep every step.

## Hook craft (from the retention lessons the user studied)
- Start from the viewer's problem or feeling, not information. Problem first, solution after.
- Create a curiosity gap: ask the question, delay the answer. Don't reveal everything in line 2.
- Use a pattern interrupt (demo, twist, funny cut) around the middle.
- One goal per reel: one CTA only (comment keyword), then follow.
- Hook angles that worked: callout ("tumhari reel kharab nahi hai"), challenge/test the viewer can do, contrarian, funny/relatable, curiosity tease, proof/demo.

## When the user gives a hook
Keep it **word for word**. Build the continuation around it. Do not rewrite it unless asked.

## When the user asks for hooks only
Give hooks only, no scripts. Group by type, add a one-line visual cue for each, flag any claim that needs proof, and end with a top 3 pick. Aim for 10+ when asked to brainstorm hard.

## When the user asks for variations
Give 3-5 different hooks AND different content, written in chat (no code box), each short. End with one or two picks.

## Rewriting a competitor's script
Never copy their wording. Keep only the ideas that are true, rewrite in RAWDEV's hook, flow and CTA. List what was dropped as unverified.

## Facts and claims
- For any tool, app or feature promo: look up the real source (GitHub, official site) and include only verified features.
- No made-up stats ("90% log"), no income promises, no "free Spotify"/piracy framing.
- Mention "beta" honestly if the app is beta. Say "aam taur pe" for creator-guide claims that are not official.
- For download CTAs tell viewers to get apps only from the official releases page.

## TTS (trigger word: "TTS")
When the user says "TTS" on a script, convert with no questions. Output **only** the final tagged script in one copy-paste code block, no intro, no notes (unless they ask). If they only want one line fixed, return only that part.

Step 1, cleanup:
- Hindi words in pure Devanagari (मैंने, बताऊँगा, चाहिए).
- English, technical and brand words stay in Roman (reels, caption, DM, CapCut, hook, Claude, ChatGPT).
- Keep meaning, tone and every line. **Do not add new lines**, only play with tags.

Step 2, ElevenLabs v3 audio tags:
- One or two square-bracket tags before each sentence or phrase, max two.
- Tag palette: [excited] [hyped] [confidently] [playfully] [relatable] [sighs] [laughs] [snaps fingers] [rushed] [quickly] [slowly for emphasis] [emphasis] [building energy] [warmly] [dreamily] [serious] [flatly] [annoyed] [mocking tone] [interrupting] [proudly] [softly] [fading out].
- Energy arc: hook high energy, problem part relatable/serious, solution confident/hyped, CTA and bye warm and playful.
- Vary tempo: [rushed]/[quickly] for fast bits, [slowly for emphasis] for dramatic pauses.
- Keep stretched words like "Byeeeeeeeeee" as is, no extra tag, and end with `[laughs] [fading out]`.
- Mock dialogue lines get [mocking tone] or [playfully].

## Example (hook given by user, short reel)
Input hook: "Yeh music player dekh ke Spotify bhi sharma jayega. Yeh hai Mochy."

Output:
```
Yeh music player dekh ke Spotify bhi sharma jayega. 😳 Yeh hai Mochy.
Free aur open source hai, aur bina account ke search, play, lyrics aur downloads chalte hain.
Synced lyrics saath chalte hain, player ka look customize karo, aur equalizer bhi hai.
Sabse badi baat: tumhara data collect nahi hota. 🔥
Link chahiye? Comment karo "mochy", main DM kar dunga.
Baki follow bhi kar lena. Byeeeeeee!
```

TTS of the same:
```
[excited] ये music player देख के Spotify भी शर्मा जाएगा. [playfully] ये है Mochy.
[hyped] Free और open source है, [quickly] और बिना account के search, play, lyrics और downloads चलते हैं.
[building energy] Synced lyrics साथ चलते हैं, [confidently] player का look customize करो, और equalizer भी है.
[slowly for emphasis] सबसे बड़ी बात: [proudly] तुम्हारा data collect नहीं होता.
[warmly] Link चाहिए? [playfully] Comment करो "mochy", मैं DM कर दूँगा.
[warmly] बाकी follow भी कर लेना. Byeeeeeeee [laughs] [fading out]
```

## Example (challenge-style hook, DM automation reel)
"Comment karo 'chappal'. Agar 5 second mein DM nahi aaya, toh main ye reel delete kar dunga. 🩴" then demo, then "Ye mera free automation tool hai...", CTA with keyword, follow, Byeeee.

## Before sending, check
- Hook is problem/result first and the user's own hook is untouched if given.
- Word limit and word count respected.
- Every tutorial step intact.
- One CTA keyword, one follow line, "Byeeeeeee" close.
- Claims verified or flagged.
