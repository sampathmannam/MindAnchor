# SOTA Survey 2: Digital Self-Control, Attention Design, Dark-Pattern Mitigation (2022–2026)

> Research report prepared for MindAnchor. Null results and failed interventions flagged.

## 1. Lyngs line: digital self-control tools

- **Lyngs et al., CHI 2019, "Self-Control in Cyberspace"** — dual-systems review of 367 digital self-control tools (DSCTs); foundational taxonomy (block/hide, self-tracking, goal reminders, reward/punish). Still the reference framework for feature design.
- **Lyngs, Freed et al., CHI 2024 (Best Paper HM), "I finally felt I had the tools to control these urges"** (https://dl.acm.org/doi/10.1145/3613904.3642946) — data from 280 students across ReDD workshops (https://www.redd-project.org/): a reflection-first workshop (articulate goals → then pick tools) produced sustained goal attainment; the *matching* of tool to individual struggle mattered more than any single tool. **Implication:** MindAnchor onboarding should be a goal-elicitation flow that configures features per-user, not a fixed feature set.
- **Lyngs et al. 2024, Danish high-schools pilot** (https://dl.acm.org/doi/fullHtml/10.1145/3677045.3685423) — classroom-scale ReDD adaptation; feasible but effects weaker without individual tailoring.

## 2. Design friction and attention-capture dark patterns

- **Grüning, Riedel & Lorenz-Spreen, PNAS 2023 (one sec)** (https://www.pnas.org/doi/10.1073/pnas.2213114120) — N=280, 6 weeks: a ~6-s breathing delay before opening a target app led users to abandon 36% of open attempts; open attempts fell 37% by week 6. Cheap friction works and effect *grows* (habit decay).
- **Haliburton, Grüning et al., CHI 2024** (https://www.medien.ifi.lmu.de/pubdb/publications/pub/haliburton2024chi/haliburton2024chi.pdf) — N=1,039, ~13.4 weeks in-the-wild: friction remains effective long-term; users mostly target social media, take deliberate "breaks" from the intervention, and rebound quickly after. **Implication:** build friction as toggleable with graceful re-entry, not all-or-nothing; expect and design for breaks.
- **Monge Roffarello, Lukoff & De Russis, CHI 2023, "Defining and Identifying Attention Capture Damaging Patterns"** (https://dl.acm.org/doi/fullHtml/10.1145/3544548.3580729) — typology of 11 attention-capture damaging patterns (ACDPs: infinite scroll, autoplay, pull-to-refresh, time fog, social investment...). **Implication:** MindAnchor can position itself as an OS-level "ACDP blocker" — the typology is a direct requirements checklist.
- **Monge Roffarello & De Russis, TOCHI 2023 meta-analysis** (https://dl.acm.org/doi/abs/10.1145/3571810) — systematic review + meta-analysis of DSCT evaluations: tools reduce use of targeted apps, but evidence for *wellbeing* gains is weak and long-term effects understudied; self-monitoring alone is the weakest class. **Flag:** pure dashboards (Screen Time/Digital Wellbeing style) are a documented near-failure — don't make stats the core feature.
- **Monge Roffarello, De Russis & Lukoff, TOCHI 2025, "Digital Attention Heuristics"** (https://dl.acm.org/doi/full/10.1145/3725215) — 8 Self-Determination-Theory-grounded design heuristics for attention-respecting UI. **Implication:** usable as MindAnchor's design-review rubric.
- **Meinhardt, Purohit, Rukzio et al., CHI 2025, "Scrolling in the Deep"** (https://dl.acm.org/doi/10.1145/3706598.3713187) — N=72, 7 days: intervention effectiveness during infinite scroll is context-dependent — sleepiness lowers reactance (interventions land better), low mood + at-home slows responsiveness; users desensitize to context-blind prompts. **Implication:** scroll interventions should be context-triggered (time of day, session length, location), not fixed-interval, or they burn out.
- **inControl (Monge Roffarello & De Russis, GoodIT 2023)** — nudging vs. interface redesign: redesign (removing feeds) beats overlay nudges for reducing use.

## 3. Notifications beyond Fitz 2019

- **Fitz et al. 2019 baseline**: batching 3×/day improved mood/control; the **zero-notifications arm increased anxiety and FoMO** — full suppression is a failed intervention.
- **Dekker et al., Media Psychology 2024, "Beyond the Buzz"** (https://www.tandfonline.com/doi/full/10.1080/15213269.2024.2334025) — preregistered RCT, N=205, 1 week of disabling notifications: **null on smartphone behavior, and FoMO increased**. Strong replication of "suppression backfires."
- **"Sound of Silence", Computers in Human Behavior 2022** (https://www.sciencedirect.com/science/article/abs/pii/S0747563222001601) — muting notifications increased checking and distress for high-FoMO/need-to-belong users. **Flag: Do-Not-Disturb as commonly shipped is counterproductive for the most vulnerable users.**
- Interruptibility work: Attelia breakpoint delivery cut frustration ~28%; Mehrotra's sender/content models — **sender relationship is the strongest acceptance predictor**; >80%-precision personal interruptibility models. Newer: AttenTrack (2025, arXiv) context+distraction-based attention awareness. **Implication:** sender-tiered batching with predictable delivery windows (e.g., 3×/day for non-human senders, immediate for close contacts), never blanket muting — the single best-evidenced design in this survey.

## 4. Reduction RCTs and mental-health outcomes

- **Brailovskaia et al., J. Exp. Psychology: Applied 2022/2023** — N≈620: reducing use by 1 h/day beat full abstinence — gains in life satisfaction, physical activity, lower depression/anxiety persisted at 4-month follow-up; **abstinence effects decayed faster**. Moderation > detox.
- **Castelo, Kushlev, Ward et al., PNAS Nexus 2025** (https://academic.oup.com/pnasnexus/article/4/2/pgaf017/8016017) — N=467, 2-week mobile-*internet* block (calls/SMS kept): sustained attention +0.24 SD, mental-health symptoms −0.57 SD (larger than typical antidepressant meta-analytic effects), wellbeing +0.46 SD; usage fell 314→161 min/day. **Caveat:** only ~25% complied fully. **Implication:** the active ingredient is mobile *internet content*, not communication — a launcher that preserves calls/messaging while gating feeds mimics the trial's mechanism.
- **Pieh et al., BMC Medicine 2025** (https://bmcmedicine.biomedcentral.com/articles/10.1186/s12916-025-03944-z) — RCT N=111, screen time ≤2 h/day for 3 weeks: improved WHO-5 wellbeing, depressive symptoms, stress, sleep.
- **Grayscale:** Dekker & Baumgartner, Mobile Media & Communication 2024 (https://journals.sagepub.com/doi/10.1177/20501579231212062) — N=84: −20 min/day screen time, better perceived control, less overuse/vigilance/stress, **but unlocks unchanged and null on productivity and sleep**. Earlier work: 22–50 min/day reductions. Cheap, partial win: grayscale kills duration, not checking habits.

## 5. Screen time vs. wellbeing debate — responsible-design takeaway

- **Orben & Przybylski** (Specification Curve Analysis, 355k participants): technology use explains ≤0.4% of wellbeing variance. **Odgers** (2024 Nature review of *The Anxious Generation*): small and mixed associations; causal-epidemic claim unsupported.
- **2025 Delphi consensus statement** (~120 researchers including both camps): 92–97% agreement that heavy use is linked to **sleep problems, attention problems, behavioral-addiction-like patterns**, and girl-specific harms (body dissatisfaction, harassment); evidence for bans/age limits rated *preliminary*.
- **Takeaway for MindAnchor:** don't market "screens cause depression." Defensible framing: population-average effects are small, but (a) sleep, attention, and compulsive-checking harms are consensus, (b) effects are heterogeneous — some users are strongly affected, and interventions can *harm* subgroups (Beyond the Buzz / Sound of Silence). Target mechanisms (sleep displacement, attention capture, compulsion), personalize, and measure per-user outcomes rather than promising universal mental-health gains.

## 6. Launchers and OS-level interventions

- **MinimalistPhone app study** (Computers in Human Behavior: AI 2025, N=57, 14 days; https://www.sciencedirect.com/science/article/pii/S2451958825001149) — alphabetical text-only app list + intent confirmation reduced use and improved emotional experience.
- **Light Phone II experiment, CHI 2026, "Going Light"** (https://dl.acm.org/doi/10.1145/3772318.3791723) — 1-week between-subjects: wellbeing effects of minimal-phone switching **depend on user motivation** — autonomously motivated users benefited; imposed minimalism did not. Structural constraint beats nudges *only* when self-endorsed.
- **Lukoff et al., CHI 2021 (YouTube sense of agency)** (https://dl.acm.org/doi/abs/10.1145/3411764.3445467) — *internal* mechanisms (disable autoplay, hide recommendations) support agency better than external lockouts, especially under specific intentions.
- **MindShift, CHI 2024** (Wu et al., https://arxiv.org/pdf/2309.16639) — LLM-generated, mental-state-tailored persuasive interventions at app-open time outperformed static messages on usage and problematic-use scales. **Implication:** dynamic, context-aware copy in MindAnchor's friction screens.
- **Lyngs et al., CHI 2020 Facebook study** — flagged null: goal reminders caused annoyance/habituation; hiding the newsfeed worked while active but effects vanished on removal — no lasting behavior change after tool removal. Design for *scaffolding while present*, not cure.

## Synthesis for MindAnchor

Strongest-evidence stack:
1. Opt-in friction delays on user-chosen apps (one sec line).
2. Sender-tiered batched notifications — never blanket mute (Fitz/Dekker).
3. Feed/autoplay/infinite-scroll gating at launcher level (ACDP typology, Castelo mechanism).
4. Grayscale scheduling as auxiliary.
5. Goal-elicitation onboarding + per-user tailoring (ReDD, Going Light).
6. Context-aware, adaptive prompts to avoid desensitization (Scrolling in the Deep).

Avoid: notification suppression, stats-only dashboards, imposed abstinence.
