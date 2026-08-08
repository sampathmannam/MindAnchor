# Breathing Protocols for a 3–10s Launcher Overlay

Primary-source comparison. "Shippable" = one cycle (or short multi-cycle burst) fits a 3–10s launcher overlay.

## 1. Physiological sigh / cyclic sighing
- **Citation:** Balban MY et al. *Cell Reports Medicine* 4(1):100895, 2023. DOI: 10.1016/j.xcrm.2022.100895.
- **Population / dose:** 108 healthy adults, 4-arm remote RCT (NCT05304000), 5 min/day × 28 days. Double nasal inhale (~3–4s + 1–2s sip) + slow mouth-exhale ~6–10s.
- **Effects vs mindfulness:** positive affect +1.91 vs +1.22 (p<0.05; largest gain); state anxiety improved equally across all breathwork arms; resting respiratory rate dropped most in cyclic sighing (p<0.05).
- **Null / cautionary:** **no significant change in HRV or resting heart rate in any arm**; no sleep-quality change. Single-cycle acute use is mechanism-plausible but not the tested dose.
- **Shippable?** Yes — one cycle ≈ 10–20s; 1–3 cycles is the acute form endorsed by the authors.

## 2. Box breathing (4-4-4-4)
- **Citation:** Balban 2023 (one of three breathwork arms). The marketed 4-4-4-4 has no RCT testing it — Balban's box arm was *individualised* via a CO2-tolerance test.
- **Effects:** Improved positive affect and anxiety similarly to other breathwork; **not significantly different from cyclic sighing** on positive affect; smaller respiratory-rate drop.
- **Shippable?** Yes (16s cycle). Evidence does not specifically support the marketed 4-4-4-4 form.

## 3. 6s in / 6s out (current MindAnchor cadence)
- **Closest primary evidence** is the ~6 bpm resonance *band*, not the 1:1 ratio. Bernardi et al., *J Physiol* 2018, confirms 6 bpm maximises RSA/baroreflex sensitivity. A 5-min 6 bpm trial reduced state anxiety, persisting 5 min post. Zhang 2025 with 4s in / 6s out: Cohen's d ≈ −1.46 on state anxiety.
- **Honest gap:** the current 1:1 ratio is the *least* evidence-backed configuration in the band. The 1:2 (shorter inhale, longer exhale) is what consistently moves parasympathetic markers.
- **Shippable?** Yes. Lowest-risk immediate change: flip to ~4s in / 6s out, keep the 10s cycle.

## 4. Resonance frequency ~5.5 bpm (Lehrer)
- **Citations:** Lehrer PM, Vaschillo E, Vaschillo B. *Appl Psychophysiol Biofeedback* 28(1):3–23, 2003; Lehrer PM et al. *Appl Psychophysiol Biofeedback* 45(3):109–129, 2020 (meta-analysis, k≈60 RCTs). Lin IM et al. *Int J Psychophysiol* 91(3):206–211, 2014 — 5.5 bpm at 1:1 I:E gave higher SDNN and LF power than 6 other patterns.
- **Effects:** Meta-analysis reports medium-to-large effects on anxiety, depression, PTSD, asthma, autonomic self-regulation.
- **Null / cautionary:** A 2025 systematic review found **only 2/7 studies showed co-occurring HRV increase *and* reduced perceived stress** — HRV gains did not reliably translate to subjective benefit. Resonance frequency is not stable within individuals; 5.5 bpm is a population default, not personalised.
- **Shippable?** Yes (11s/cycle). Best-understood mechanism (baroreflex resonance) but subjective/affective benefit is weaker and more inconsistent than cyclic sighing.

## 5. Cyclic sighing vs box breathing head-to-head
- Balban 2023 (above). **Cyclic sighing > box breathing on positive affect and respiratory rate; equivalent on state anxiety; both > mindfulness.** Only published RCT pitting them against each other.

## 6. 4-7-8 (Weil)
- **Best primary source:** Vierra J, Boonla O, Prasertsri P. *Physiological Reports* 10(13):e15389, 2022. 43 healthy adults, 6 cycles × 3 sets: HR −5.37 bpm, SBP −4.11 mmHg, normalised HF-HRV +21.88% (p=0.014) — a parasympathetic shift.
- **Other small RCTs:** Aktaş & İlgin, *Obes Surg* 2023 — lower state anxiety than deep-breathing and control post-bariatric. SFU 8-week thesis — no overall effect on trait anxiety; only a high-adherence subgroup (≥4×/week) showed a medium effect.
- **Null / cautionary:** No large, well-powered RCT. BYU direct comparison: 6 bpm raised HRV more than 4-7-8 with small-to-medium effects. The 4-7-8 *ratio* itself has no demonstrated superiority — the benefit is the long exhale, shared with every pattern here.
- **Shippable?** Yes (19s/cycle). The 7s hold is the part with the thinnest evidence.

---

## Recommendation: ship the **physiological sigh** as the default overlay

**Single most evidence-backed 3–10s protocol: the physiological sigh** (one cycle ≈ 10–20s; 1–3 cycles within ~30s). Justification is **Balban et al. 2023, *Cell Reports Medicine*, DOI 10.1016/j.xcrm.2022.100895** — the only head-to-head RCT in this dose range, n=108 healthy adults, four arms including box breathing and mindfulness. Cyclic sighing produced the largest positive-affect gain (+1.91 vs +1.22 for mindfulness, p<0.05), the largest drop in resting respiratory rate, and the only persistent physiological marker of parasympathetic shift. The authors explicitly endorse the 1–3-cycle acute form for in-the-moment use.

**Overlay behaviour:** inhale ~3–4s, top-up sip ~1–2s, slow mouth-exhale ~6–8s ≈ 10–14s/cycle. Run 1 cycle by default; up to 3 cycles if the user keeps the overlay open.

**Honest caveats to surface in-app:** (a) Balban 2023 found **no significant HRV or resting heart-rate change in any arm** — claim "calmer," not "physiological"; (b) effect size is real but modest; (c) the proven dose is 5 min/day, not a single cycle.

**If the existing 6/6 implementation is kept, the lowest-risk upgrade is to flip the ratio to ~4s in / 6s out.** Same 10s cycle length, inherits both the resonance-band evidence (Bernardi 2018; Lin 2014) and the exhale-as-parasympathetic-drive evidence (Zhang 2025) at no implementation cost.

---

## Sources (URLs)

- Balban 2023: https://www.cell.com/cell-reports-medicine/pdf/S2666-3791(22)00474-8.pdf
- Bernardi 2018 (slow breathing review): https://pmc.ncbi.nlm.nih.gov/articles/PMC5709795/
- 6 bpm anxiety poster: https://orbi.umons.ac.be/bitstream/20.500.12907/55918/1/Poster.pdf
- Zhang 2025 (4s/6s, midfrontal alpha): https://www.frontiersin.org/journals/human-neuroscience/articles/10.3389/fnhum.2025.1605862/full
- Lehrer 2020 meta-analysis: https://pubmed.ncbi.nlm.nih.gov/32638235/
- Lin 2014 (5.5 bpm): https://pubmed.ncbi.nlm.nih.gov/24380741/
- 2025 SPB systematic review (null finding): https://his.diva-portal.org/smash/get/diva2:2073598/FULLTEXT01.pdf
- RF stability null: https://www.nature.com/articles/s41598-021-87867-8
- Vierra 2022 (4-7-8): https://pmc.ncbi.nlm.nih.gov/articles/PMC9277512/
- Aktaş & İlgin 2023 (4-7-8 RCT): https://pubmed.ncbi.nlm.nih.gov/36480101/
- SFU 4-7-8 thesis: https://summit.sfu.ca/_flysystem/fedora/2023-08/etd22539.pdf
- BYU square/4-7-8/6 bpm comparison: https://scholarsarchive.byu.edu/cgi/viewcontent.cgi?article=11705&context=etd
- Grüning 2023 PNAS (one sec) — note: tests a delay mechanism, not breathing physiology: https://www.pnas.org/doi/10.1073/pnas.2213114120
