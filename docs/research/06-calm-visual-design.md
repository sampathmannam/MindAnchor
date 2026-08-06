# SOTA Survey 6: Evidence-Based Calm Visual Design (for the home screen)

> Research report for MindAnchor's visual redesign. Confidence flags inline.

## 1. Color & affect

- **Valdez & Mehrabian (1994)**, *J. Exp. Psych: General* — valence and arousal are driven far more by **saturation and brightness than hue**: pleasure rises with brightness (strongest) and saturation; arousal rises with saturation and with *darkness*. Hue effects small (blue/green/purple most pleasant, yellow least). Modern syntheses broadly confirm (Frontiers 2025 color-emotion synthesis; saturation-arousal replicated in real-life images, 2024).
- **Blue/short-wavelength calm**: blue ambient lighting reached post-stress baseline ~3× faster than white in one small study (PLOS ONE; n=12 — **preliminary, flag**). Pale colors relax more than vivid ones; green foliage color independently associated with physiological relaxation across cultures.
- **Dark vs light mode**: genuinely mixed. Light mode slightly better legibility (positive-polarity advantage); dark mode shows **no measured visual-fatigue advantage** in 2025 controlled tests, but is strongly *preferred* (~68%) in low ambient light. Comfort is real; health claims are not.

**Implication**: never high saturation. Low-chroma, mid-to-high-lightness colors (HSL S≤30–35%): desaturated blue-greens, sage, dusty lavender, warm sand. No pure black or pure white. Follow system dark/light.

## 2. Nature imagery & Attention Restoration Theory

- **Lee et al. 2015**, *J. Env. Psych* — a **40-second view of a flowering green-roof photo on a screen** restored sustained attention vs a concrete roof (n=150). The key precedent: a glance-length nature image measurably helps.
- **Browning et al. 2020**, *Frontiers in Psychology* (175 experiments): simulated nature via photos/video improves mood and reduces stress; **real nature > simulated for positive affect, but simulated nature reliably reduces negative affect** — exactly the target here. Confirmed by 2024 digital-vs-actual meta-analysis and 2025 video-restorative review.
- Ulrich (1984, 1991) lineage: vegetation/water views lower physiological stress within minutes; "blue space" scenes among the most restorative.

**Implication**: a static or very-slowly-crossfading stylized nature layer (vegetation, sky, water) is the single most evidence-backed visual move. Static is fine — Lee 2015 used a static photo.

## 3. Fractals, complexity, curvature

- **Taylor/Hagerhall fractal fluency**: preference peaks at mid-complexity fractal dimension **D≈1.3–1.5** (clouds, coastlines, branching) — replicated across ages/cultures (Robles 2020). The famous "**60% stress reduction**" figure comes from one small qEEG study — **weak, never independently replicated at that magnitude; directional only**.
- **1/f spatial statistics**: natural scenes' amplitude spectra are processed fluently and preferred. Solid.
- **Curvature > angularity**: Bar & Neta 2006 (angular shapes activate amygdala); Chuquichambi 2022 meta-analysis (~100 studies): curvature preference **reliable but moderate**, strongest for abstract shapes. Curved interiors also lower stress response (2022).

**Implication**: rounded everything (radius ≥16–24dp); background of 2–4 large overlapping soft organic shapes at low opacity approximating mid-complexity; avoid both flat emptiness and busy pattern.

## 4. Calm technology / motion policy

- Weiser & Brown's calm-tech principle (periphery-first) is design doctrine, not RCT evidence. Ambient-display studies (DeLight etc.) suggest slowly-changing stimuli can lower stress without capturing attention — **small studies, precedent-grade**.
- Attention research is unequivocal the other way: **abrupt onset and fast motion capture attention** (Yantis lineage).

**Implication — motion policy**: default static/near-static. Permitted: crossfades over 30–120s, gradient drift below ~1% screen movement/sec, opt-in breathing pulse. Forbidden: loops <10s, parallax, particles, anything moving while the user reads. Respect reduce-motion settings.

## 5. Circadian-aware color

- **BYU Night Shift RCT (2021, Sleep Health, n=167, actigraphy): no sleep benefit** from warm-shifting; blue-blocker RCTs similarly minimal. Time-of-day themed UIs have no direct outcome studies.

**Implication**: dawn/day/dusk/night palettes are justified as an *affective/aesthetic* feature (lower brightness + saturation in the evening aligns with the arousal literature) — never marketed as a sleep treatment.

## 6. Typography

- Rounded letterforms rated more pleasant and read faster than angular (2023); processing fluency links easy reading to positive affect (Reber et al. 2004). **No direct typeface→stress evidence — indirect only.**

**Implication**: rounded humanist sans, generous line-height, thin-weight large clock.

## 7. Design precedent (calm apps)

Headspace: warm gradients + rounded blobs. Calm: slow nature scenes, deep blue night palette. Portal: full-bleed slow nature, near-zero UI. Endel: generative slow-drifting desaturated blobs + grain. Wind-down modes: desaturate + dim (the saturation-arousal link, weaponized gently). Common language: soft multi-stop gradients, organic blobs, film grain, rounded type, no hard edges.

## Recommended direction: "slow sky"

A generative time-of-day gradient sky with organic horizon shapes; optional stylized-nature layer later.

- **Palettes (vertical gradient top→bottom, S≤35%)**:
  - Dawn (≈6h): slate `#2E3440` → mauve `#8B7D8B` → pale peach `#D8B4A0`
  - Day (≈12h): soft blue-green `#A8C4C4` → warm off-white `#DDE8DB`
  - Dusk (≈19h): deep slate `#3B4252` → lavender `#7A6A8A` → muted terracotta `#C89F8C`
  - Night (≈23h–5h): near-black blue `#0D1321` → `#1B263B`, text `#C9C5BC`
- **Shapes**: 2–3 large soft low-opacity rounded forms near the bottom edge suggesting hills/foliage; 20dp+ radii everywhere.
- **Motion**: palette interpolates continuously with clock time (imperceptible drift); nothing else moves.
- **Theme**: follow system dark/light — dark lowers lightness, never raises saturation.

**Evidence confidence**: strong — desaturation/low-arousal color, nature micro-exposures, rounded shapes; moderate — mid-complexity texture, ambient slow change; weak — fractal stress magnitude, night-shift sleep claims, typography-stress.

Key sources: Valdez & Mehrabian 1994 · Lee et al. 2015 (sciencedirect.com/science/article/pii/S0272494415000328) · Browning et al. 2020 + 2024/2025 virtual-nature meta-analyses · Robles et al. 2020 (nature.com/articles/s41599-020-00648-y) · Chuquichambi et al. 2022 (nyaspubs.onlinelibrary.wiley.com/doi/full/10.1111/nyas.14919) · Bar & Neta 2006 · BYU Night Shift RCT 2021 · Reber, Winkielman & Schwarz 2004.
