# OpenDesign Motion.md (Deterministic HTML Motion Graphics Engine)

- **Contract**: Every motion graphic deliverable MUST expose:
  `window.RAW = { duration, width, height, fps: 30, seek(t) { ... } }`
- **Pure Function of `t`**: Build all DOM once on load. Inside `seek(t)`, only update `style.transform`, `style.opacity`, `style.clipPath`, and `textContent` as pure clamped functions of `t`.
- **Forbidden**: CSS `@keyframes`, `transition`, `animation`, `requestAnimationFrame`, `setTimeout`, `Math.random()`.
- **Continuous Motion**: Every frame from `0` to `duration` must have subtle ambient life (gentle parallax, scale breathing, vector stroke draw) and zero blank gaps between scenes.
- **Aspect Ratios**: Support `1920x1080` (16:9), `1080x1920` (9:16), `1080x1350` (4:5), `1080x1080` (1:1), or any custom `width x height`.
