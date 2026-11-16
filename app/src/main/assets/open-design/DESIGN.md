# OpenDesign DESIGN.md (9-Section Portable Design System)

1. **Brand & Visual Identity**: Local-first, high-contrast editorial design & motion system. Clean negative space, sharp hierarchy, zero clutter.
2. **Color Tokens**:
   - `--bg-primary`: `#0D0D0F` (Obsidian) / `#F6F4EE` (Warm Paper)
   - `--surface-card`: `#17171B` / `#FFFFFF`
   - `--accent-primary`: `#C89F72` (Warm Tan) or active Studio Palette
   - `--text-primary`: `#F3EFE6` / `#141416`
   - `--text-muted`: `#9E9A92`
3. **Typography Scale**: Display `72-120px` bold (`Montserrat` / `Bricolage` / custom font), Subhead `28-40px`, Body `18-24px` (`Inter` / `Poppins`), Mono `14-18px` (`JetBrains Mono`). Never stretch fonts (`scaleX == scaleY`).
4. **Spacing & Grid**: `24px`–`48px` outer safe margins; `16px`–`24px` card gaps; clear visual focal point per frame/artboard.
5. **Components**: Pill kickers, rounded editorial cards (`16-28px` radius), crisp 1px borders, inline SVG icons & vector charts.
6. **Imagery & Media**: `object-fit: cover` or `contain` with explicit dimensions; zero distortion.
7. **Motion & Rhythm (`Motion.md`)**: Deterministic `window.RAW = { duration, width, height, fps: 30, seek(t) }` with continuous smooth easing (`easeOutCubic`, spring overshoot).
8. **Deliverable Modes**:
   - `image` / `poster` (`1080x1080`, `1080x1350`, `1920x1080`, `1080x1920`) -> exported as PNG + HTML
   - `motion` (deterministic HTML motion graphics video -> MP4)
   - `deck` (multi-slide presentation)
   - `prototype` (interactive landing page / dashboard / UI)
9. **Quality Rules**: WCAG AA contrast (>= 4.5:1), zero clipped text, self-contained offline HTML/CSS/SVG/JS.
