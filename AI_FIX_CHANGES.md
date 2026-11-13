# AI fix pipeline changes
- domain/KeyframeSheetGenerator.kt: PROBE_JS adds text-overlap, low-contrast, clipped-text and broken-image checks per frame; countIssues(); compressFlagLines().
- domain/GroqClient.kt: autoFixLoop() = patch -> recapture -> compare issue count -> keep only if better, else revert (max 3 rounds); prompt now explains each finding type; findings are de-duplicated per selector.
- domain/HtmlVersionStore.kt (new): file-based version history (last 15) for undo.
- ui/screens/KeyframeSheetScreen.kt: Auto-fix now runs the loop with live round status; "Undo last AI fix" button.
