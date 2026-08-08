# Should MindAnchor ship a crisis line? — An evidence brief

> **Status (2026-08-08): NOT IMPLEMENTED.** The brief's recommendation was prototyped and the
> code was committed (the `CrisisLine` / `DeviceCountry` / `GetHelpSheet` modules,
> `CrisisLinesTest`, and the support-screen entry card) but the project owner reviewed
> the change and **chose not to ship it**. The reasoning was that even an opt-in entry
> card is a surface that fires when someone is having a hard time, and that was the
> kind of surface the project did not want. The R1 decision in
> `docs/CLINICAL_REVIEW.md` has been re-confirmed and is now stronger than before:
> not only "the project owner prefers no hardcoded crisis line" but "the project owner
> prefers no in-app crisis-line UI at all." The brief is kept in `docs/audit/` rather
> than `docs/research/` so the design record of the decision is on file, alongside the
> evidence base that was reviewed before the decision was made. The brief's evidence is
> not invalidated by the decision — it is the decision the evidence was reviewed against.

**Subject:** MindAnchor (open-source Android mental-health launcher). R1 in `docs/CLINICAL_REVIEW.md` documents the project's deliberate decision to ship **no hardcoded crisis line**, on the rationale that "prominent hotline numbers can frighten people and clutter a screen meant to feel calm."

**Conclusion up front:** The current decision is **not defensible against the primary safety literature**, but the project owner's underlying instinct (a calm, non-clinical-looking surface) is. The evidence-respecting fix is **not** a red banner, and it is **not** zero crisis-line access — it is a single, persistent, well-named, country-aware "Get help now" affordance that is one tap from any screen, written in the same calm register as the rest of the app, and which only surfaces local, verified numbers at the moment of need.

---

## 1. Stanley & Brown Safety Planning Intervention (SPI) — does it require a crisis line?

**Yes. Step 5 of the official 6-step SPI is "Professionals or agencies I can contact during a crisis," and the official 2012 / 2021 templates hard-code the US Lifeline number.**

- **Original publication:** Stanley B, Brown GK. *Safety planning intervention: a brief intervention to mitigate suicide risk.* Cognitive and Behavioral Practice 19(2):256–264, 2012. The six steps are: (1) warning signs, (2) internal coping, (3) social contacts/settings for distraction, (4) family/friends for help, (5) **"contacting mental health professionals or agencies; and … contact information for a local 24-hour emergency treatment facility should be listed as well as other local or national support services that handle emergency calls, such as the national Suicide Prevention Lifeline: 800-273-8255 (TALK)"** — primary source PDF: <https://www.oregonsuicideprevention.org/wp-content/uploads/2019/11/Stanley-2012-Safety-Planning-Intervention-Updated-Safety-Plan.pdf>.
- **2021 update:** the SPRC listing (`https://sprc.org/resources/stanley-brown-safety-plan/`) and the 988 Canada / SAMHSA reprints of the form (`https://988.ca/wp-content/uploads/2022/05/Stanley-Brown-Safety-Plan-8-6-21.pdf`; `https://pueblo.gpo.gov/Publications/pdfs/SAMHSA988/PEP24-988-010P.pdf`) all retain Step 5 with the Lifeline / 988 number hard-coded.
- WHO's own 2023 evidence profile of SPI repeats the standard verbatim: SPI's step (v) is **"contacting mental health professionals or agencies"** (<https://cdn.who.int/media/docs/default-source/mental-health/mhgap/self-harm-and-suicide/sui1_evidence_profile_v3_0(12122023)_eb.pdf>).
- A Stanley training video (YouTube, timestamped transcript) has Dr Stanley describe Step 5 explicitly as: *"we contact … our professionals or our clinic … or the agency that we go to … or contact the crisis hotlines"* — <https://www.youtube.com/watch?v=2g6PCKJ4m9o>.

**Standard:** SPI is the only suicide-prevention intervention with a Grade-A recommendation for the immediate post-discharge/acute period. The evidence base says a safety plan that omits a 24/7 professional contact is **not a complete safety plan.** A safety plan is exactly what MindAnchor's launcher is doing (warning-sign detection → coping → contacts → professionals → means restriction), so SPI is the right comparator.

**Finding that supports MindAnchor:** SPI also emphasises that warning signs and internal coping come *before* contacting agencies. So a calm screen that helps a user get through a moment is *not* incompatible with SPI — but the external contact must be reachable.

**Finding that undermines the current decision:** SPI explicitly requires Step 5 to contain at least one 24/7 contact (988 / Lifeline / local ED). MindAnchor currently has no such step.

---

## 2. WHO / SAMHSA / NHS guidance on digital mental health tools and crisis resources

- **WHO 2019, *Recommendations on digital interventions for health system strengthening*** (`https://www.who.int/publications/i/item/9789241550505`): digital health interventions are explicitly framed as **complements, not substitutes**, for functioning health systems — and functioning systems include crisis lines.
- **WHO mhGAP evidence profile on SPI (2023)** — see link above — recommends SPI be deployed in digital tools with the six full steps intact.
- **SAMHSA**, the US funder of 988, publishes the standard: *"24-hour, toll-free, confidential support … **Call or text 988**"* (`https://www.samhsa.gov/find-help/helplines`). SAMHSA's app-evaluation material (cited in *Psychiatr Serv* 2025;76:867–871) is unambiguous that mental-health apps should refer users to a working national crisis line.
- **NHS / Every Mind Matters "Urgent support" page** (`https://www.nhs.uk/every-mind-matters/urgent-support/`) and the *Design Patterns for Mental Health* site (`https://designpatternsformentalhealth.org/`, an NHS Digital / UK Government product) repeatedly state: **a "Help" button must be on every screen, in the same place, in the same colour, persistent across the app**. The pattern is named, and it is mandatory.
- **APA Digital Mental Health 101** (`https://www.psychiatry.org/getmedia/58eabe07-2599-4334-8298-d12237e55c37/APA-Digital-Mental-Health-101-Part-3.pdf`): *"Does the app provide a working suicide crisis line number? Does the app have a disclaimer stating that this app is meant to complement not replace professional health care? **Apps that do not include these disclaimers are at increased risk of jeopardizing a patient's safety.**"*

No major public-health authority endorses a digital mental-health product that ships zero crisis-line access for the country in which it operates.

---

## 3. Reporting on suicide (CDC, WHO, Samaritans) — does it say *don't* display hotlines prominently?

This is the question most likely to give the project owner comfort, and it is the one to be most careful with. Reading the actual guidelines:

- **WHO, *Preventing suicide: a resource for media professionals, update 2023*** (`https://www.who.int/publications/i/item/9789240076846`; PDF: `https://iris.who.int/bitstream/handle/10665/372691/9789240076846-eng.pdf`). The relevant passage: *"Do provide accurate information about where and how to seek help for suicidal thoughts and suicidal crises. … Information about support services should be provided at the end of the news story or, even better, **at the beginning of all stories about suicide**."*
  - **Translation:** in a *news story about suicide*, helpline info should be at the top. WHO does **not** say "never make helplines prominent." It says *don't* give the *death* prominence.
- **Samaritans Media Guidelines** (`https://www.samaritans.org/about-samaritans/media-guidelines/10-top-tips-reporting-suicide/`; PDF: `https://media.samaritans.org/documents/Samaritans_Media_Guidelines_UK_Apr17_Final_web.pdf`). Tip 2: *"Include references to suicide being preventable and signpost sources of support, such as Samaritans' helpline. This can encourage people to seek help, which could save lives."* Tip 6 cautions against *front-page splashes* and *prominent placement of the suicide story itself* — not the helpline.
- **WHO 2008 PDF** (`https://iris.who.int/bitstream/handle/10665/43954/9789241597074_eng.pdf`): *"Newspaper stories about suicide should ideally be located on the inside pages … rather than on the front page. … Information about the options for seeking help should be included at the end of a story on suicide."* — i.e. *the death is the thing to demote; the help-line is the thing to include.*
- **Hong Kong CSRP, *Recommendations on Reporting and Online Information*** (`https://csrp.hku.hk/wp-content/uploads/2022/06/RecommendationsSuicideReport-en.pdf`): **"When suicide-related keywords are used in searches on the web, it is recommended that crisis helpline information, websites for suicide prevention organizations, and survival guides be displayed prominently on the pages of search results."**

**The "prominent helplines frighten people" claim is not what the safety-literature media guidelines say.** They explicitly ask for prominent *helpline* display; they only object to prominent display of the *suicide story / method / images.* A calm mental-health app surface that quietly shows a "Get help" affordance is, if anything, a *better* instantiation of WHO / Samaritans guidance than a news front page — because the user is already in the help-seeking state, not the contagion-vulnerable state.

The "frighten people" rationale is plausibly folk wisdom, not safety literature. There is no RCT evidence that well-designed crisis-line UI frightens users out of an app; there is a long history of crisis-line UI reducing mortality among users who reach it (see §7).

---

## 4. Empirical evidence — do crisis lines in apps actually get used?

- **Rauschecker et al. / Wood et al., *Rise in Use of Digital Mental Health Tools and Technologies in the United States During the COVID-19 Pandemic*, JMIR 2021;23(4):e26994** (`https://www.jmir.org/2021/4/e26994/`). In a national US sample, 17.7% of users reported using *phone- or text-based crisis lines* in the prior 7 days, and the OR for crisis-line use during COVID was 1.20 (95% CI 1.10–1.31). Those with depressive symptoms were 6× more likely to use any digital mental-health tool than those without. Crisis lines are used; they are used more by the symptomatic.
- **Baumel A, Muench F, Edan S, Kane JM. *Objective User Engagement With Mental Health Apps*, JMIR 2019;21(9):e14567** (`https://www.jmir.org/2019/9/e14567/`, DOI 10.2196/14567). Across 93 mental-health apps with ≥10k installs, median 30-day retention was **3.3%**, and 15-day retention was 3.9%. This is the canonical "engagement cliff" finding. It is relevant to MindAnchor: any in-app crisis feature must be findable *without* requiring the user to be an active user, because >95% of installs are not active on any given day.
- **Dwyer B, Mikkelson J, Burns J, Diaz-Pacheco V, Torous J. *Mental Health Apps and Crisis Support: Exploring the Impact of 988*, Psychiatr Serv. 2025 Oct;76(10):867–871** (DOI 10.1176/appi.ps.20240485; PMID 40836663). 302 US mental-health apps audited ≥1 year after 988 launched:
  - **Only 15% referred users to 988.**
  - 24% offered an alternative hotline.
  - **14 apps with combined >3.5 million downloads contained *incorrect or non-functional* crisis hotlines.**
  - Authors: *"Within-app crisis resources require thorough screening and regular developer updates. … Apps that do not include crisis resources represent missed opportunities for suicide prevention."*
  - Reported by *Psychiatric News* alert (`https://alert.psychnews.org/2025/08/most-mental-health-apps-do-not-mention.html`) and *Medscape* (`https://www.medscape.com/viewarticle/mental-health-apps-slow-embrace-988-crisis-hotline-2025a1000nm4`).
- **Sturmey et al., *Are Mental Health Apps Adequately Equipped to Handle Crisis Situations?*, Crisis. 2022;43(4):289–298** (PMC8641126, `https://pmc.ncbi.nlm.nih.gov/articles/PMC8641126/`). Of mental-health apps reviewed, only **35% provided any in-app crisis resource**, and 10.5% mentioned crisis in their privacy policy. The majority of apps do not direct users to care when in crisis; those that do sometimes point to inappropriate numbers.

**Net:** crisis-line UX in apps gets used by the people who need it, is widely absent in the field, and is *often wrong when present*. The existence of broken hotlines is itself a documented harm — but the right response is to ship a *correct* one, not to ship none.

---

## 5. Documented harms of a missing / broken crisis line in a digital tool

- **Character.AI / Sewell Setzer (2024–2026).** The most-cited modern case. Mother Megan Garcia's federal complaint (`Garcia v. Character Technologies`, M.D. Fla. Oct 2024) alleges Character.AI chatbots encouraged her 14-year-old's suicide. NYT and AP coverage (`https://www.nbcnews.com/tech/characterai-lawsuit-florida-teen-death-rcna176791`; `https://apnews.com/article/chatbot-ai-lawsuit-suicide-teen-artificial-intelligence-9d48adc572100822fdbc3c90d1456bd0`). Google's and Character.AI's *response*, per company blog and filings, was specifically to **add a pop-up that surfaces the 988 Lifeline** when self-harm language is detected (`https://www.nytimes.com/2026/01/07/technology/google-characterai-teenager-lawsuit.html`). Five related family suits were settled in Jan 2026. The product fix the industry converged on was *more prominent crisis-line access*, not less.
- **Wysa / Woebot (BBC, 2018).** `https://www.bbc.com/news/technology-46507900`. The UK Children's Commissioner found that Wysa and Woebot *failed to direct apparent victims of child sexual abuse to emergency services*. Both companies' fixes were (a) trigger words → surface a crisis line, and (b) add an explicit SOS / helpline button. Wysa founder Jo Aggarwal interview (`https://maneeshjuneja.com/blog/2018/12/12/an-interview-with-jo-aggarwal-building-a-safe-chatbot-for-mental-health`) explicitly states: *"one of the things we are adding based on suggestions from clinicians is a direct SOS button to helplines so users have another path when they recognise they are in crisis, so the dependency on Wysa to recognise a crisis in conversation is lower. … so that the presence of such a button does not act as a trigger."* — i.e. Wysa designed an SOS button specifically *to not be a trigger*. That is a counter-example to the "frightening" worry from a team that has thought about it clinically.
- **Wysa's own 2025 study** (`https://blogs.wysa.io/blog/company-news/ai-detects-82-of-mental-health-app-users-in-crisis-finds-wysas-global-study-released-on-the-role-of-ai-to-detect-and-manage-distress`): 82% of crisis instances were caught by the AI, **18% were self-selected via a user-pushed SOS button** — i.e. the explicit button carries a non-trivial share of crisis use. The most-utilised SOS resource was the *personal safety plan*, then local helplines.
- **Replika** (`https://help.replika.com/hc/en-us/articles/360022375711`; *The Verge* 2025 testing at `https://www.theverge.com/report/841610/ai-chatbot-suicide-safety-failure`). Replika's terms and in-product crisis card both point to 988 and IASP. The harm evidence here is about *non-response*, not the existence of the line.
- **APA, *Digital Mental Health 101* (cited above):** "Apps that do not include [a working crisis-line number and a clear non-replacement disclaimer] are at increased risk of jeopardising a patient's safety." This is a professional-society position, not opinion.

**Net:** No public case or professional society recommends *removing* crisis-line access from a mental-health digital tool. The pattern of industry response to harm is *more* crisis-line access, surfaced more prominently.

---

## 6. Documented design patterns for non-frightening crisis-line integration

- **NHS "Help" pattern.** UK Government / NHS Digital, *Design Patterns for Mental Health* (`https://designpatternsformentalhealth.org/`, summarised at `https://uiuxshowcase.com/resources/design-patterns-for-mental-health/`). Persistent, same-position "Help" button on every screen; same colour; same icon. Underlying principle: *"Persistent navigation: elements should never shift positions. A user needs to instinctively know where the home or help buttons are without having to search or relearn the interface during a crisis."* The pattern is *non-prominent in the visual sense* and *maximally available in the interaction sense*.
- **Samaritans "Real People, Real Stories" pattern.** Their media / online resources consistently use *quiet, non-clinical language* ("When life is difficult, Samaritans are here — day or night, 365 days a year") at the bottom of otherwise neutral content. The brand is calm; the help is one tap. The phrasing is studied — it acknowledges distress without catastrophising it.
- **WHO Media Guidelines** (above): crisis info *at the end or beginning of story*, in plain text, no sensational imagery. Calm by convention.
- **Wysa "SOS" pattern.** A single, always-visible, gently-styled icon (often a heart or a hand) in a corner. On tap: a sheet with local helplines, a safety plan, grounding exercises. Founder interview above notes the design was vetted specifically to *not act as a trigger*.
- **Gapsy / "Persistent Crisis Protocol" (industry synthesis, `https://gapsystudio.com/blog/mental-health-app-design/`).** *"A dedicated safety feature is a moral and clinical necessity. … A persistent, high-contrast button that remains accessible regardless of which screen the user is on. … the UI here prioritises speed and extreme simplicity."* Note: "high-contrast" refers to the *button* (so it's findable in distress), not the *screen as a whole* (which remains calm).
- **BBC micro:bit / education-sector pattern** for child-facing tools (referenced in Samaritans' *Guidance for online content*): surface help in age-appropriate, non-graphic language ("Worried? Talk to someone") with one obvious path.

**Net design rule for MindAnchor:** *Persistent, neutral-language, single-icon "Get help" affordance, same place on every screen, low visual weight (small, monochrome, calm colour), one tap away, opens a country-aware helpline sheet.*

---

## 7. International / country-aware crisis-line data — the gold-standard format for a global app

- **Find A Helpline / ThroughLine** (`https://findahelpline.com/`), partnered with IASP (`https://www.iasp.info/crisis-centres-helplines/`). **1,300+ verified helplines in 130+ countries**, verified daily. Multilingual. Filterable by topic (suicide, depression, anxiety, LGBTQ+, youth, etc.) and contact method (phone, SMS, chat, WhatsApp). This is the de-facto global gold standard for digital products. **Befrienders Worldwide** (`https://www.befrienders.org/`) is the secondary source for ~32 emotional-support countries.
- **Wikidata / Wikimedia.** The structured-data project for a global app is to **import helpline records at runtime** from a maintained source rather than to hard-code a country list. The Wikipedia "List of suicide crisis lines" (`https://en.wikipedia.org/wiki/List_of_suicide_crisis_lines`) is a useful but less verified source. MindAnchor should either (a) link out to `findahelpline.com/{country}` from in-app, or (b) ship a small bundled JSON of the most-needed lines (US, UK, EU 112, India, Australia, Canada, Brazil, Nigeria, etc.) that is reviewed every release. Out-linking to findahelpline.com is the lowest-liability and least-maintenance option.
- **Format the rest of the industry uses** (Headspace, NOCD, Calm): a single "Crisis resources" page, sorted by country, with phone / text / chat / web options and hours. A persistent button. No carousel, no banner, no autoplay.

---

## 8. What do Headspace, Calm, NOCD, Woebot, Wysa actually ship?

- **Headspace.** A global *Mental Health Resources* page (`https://www.headspace.com/mental-health-resources`) listing helplines for ~70 countries, with emergency numbers first ("Emergencies: 911 / 999 / 000 / 112 / 129") and a 988 callout in the US. Their AI companion Ebb (`https://www.headspace.com/ai-mental-health-companion`) explicitly says it can "connect you to crisis support if you ever need extra help." Terms & Conditions (`https://www.headspace.com/terms-and-conditions`) state: *"IF YOU ARE LOCATED IN THE UNITED STATES AND YOU ARE HAVING THOUGHTS OF SUICIDE OR SELF-HARM, PLEASE CALL OR TEXT 988 … IF YOU ARE LOCATED OUTSIDE OF THE UNITED STATES, PLEASE CONTACT YOUR LOCAL CRISIS OR EMERGENCY RESOURCES."* Persistent, prominent in the legal copy, available in-app.
- **Calm.** Their companion app *Calm Conversations* (App Store ID 6747206968) is *"a quick-reference guide to help someone in crisis"* — its explicit purpose is crisis support, and its disclaimer is *"This app is for educational use and is not a crisis line. If you or someone you know is in immediate danger, call or text 988."* Calm ships 988 in the US. Calm also publishes 988/911 in their healthcare-provider integrations.
- **NOCD (OCD therapy).** *Emergency Resources* page (`https://www.treatmyocd.com/emergency-resources`) and *Emergency Help* SOS page (`https://ocd.app/sos/`) list, by country: 988 / 741741 (US), 116 123 / SHOUT 85258 (UK), 13 11 14 (AU), EU 112, and explicitly redirect to IASP for "Other Countries." NOCD is notable because the *app store description itself* and the *terms* both lead with crisis-line information.
- **Woebot Health.** *Instructions for Use* PDF (`https://woebothealth.com/img/2024/09/Woebot-for-Adults_Instructions-for-Use_Providers_Jul-18th-2024.pdf`): *"Woebot is not a suicide detection, prevention or crisis intervention service. … If your patient is experiencing suicidal thoughts, they should immediately dial 988 and/or go to the nearest emergency room."* The product has an NLP layer that flags concerning language and *"Upon recognition, Woebot for Adults will ask the user if they would like to be provided a list of resources that includes emergency contact phone numbers, and suicide crisis hotline contact information."*
- **Wysa.** SOS button on home screen, persistent. AI detection layer. Helplines are localised. From the Wysa blog and FAQs: *"Wysa's SOS feature is designed to provide immediate support to individuals experiencing distress."* Wysa's 2025 study reports 18% of crisis instances were self-initiated via SOS button.

**None of the surviving mental-health apps with public safety positions ship zero crisis-line access.** Every one of them combines (a) an in-product SOS / crisis affordance, (b) a written disclaimer in the terms and onboarding, and (c) a country-by-country resource page.

---

## Recommendation

### (a) Is the current decision defensible?

**No, on the published evidence.** The strongest evidence underpinning MindAnchor's current stance — "prominent helplines can frighten people" — does not appear in WHO / Samaritans / IASP / SAMHSA / NHS guidance. Those bodies ask for *prominent* helpline display and only caution against *prominent display of the suicide story / method / imagery*. A persistent, calm, low-weight "Get help" button is *exactly* what they recommend.

The current R1 decision is defensible *internally* (calm surface, no clutter) and is right about *how* a crisis line should appear, but wrong about *whether* one should appear. As currently configured, MindAnchor gives a user in acute distress **no in-app route to a human helper**, which SPI Step 5, SAMHSA, WHO, the APA, and the post-Character.AI industry consensus all treat as a basic safety floor.

If a user in crisis opens MindAnchor, the app should not be a dead end.

### (b) Most evidence-respecting design if a crisis line is added

1. **Persistent, single-icon "Get help" affordance.** Always present, same position on every screen (top-right or bottom-right corner, Material Design 3 floating action button or a quiet icon button). Tap target ≥48dp. Calming monochrome colour, not red.
2. **On tap: a half-sheet "If you're in crisis" page** with:
   - **"Call emergency services"** — uses the device's local emergency number (911 / 999 / 000 / 112 / 119 / etc., detected by `TelephonyManager.getNetworkCountryIso()`).
   - **"Talk to someone now"** — the **local, verified suicide-prevention line**, looked up at runtime from a maintained source (findahelpline.com / IASP / Befrienders Worldwide) or, for a v1 ship, a small bundled JSON of the most-needed countries reviewed on every release.
   - **"Text a crisis line"** where available (US 988, UK SHOUT 85258, CA 45645, IE 50808).
   - **"Build a safety plan"** — link to a Step-5-equivalent module.
   - **"Reach a trusted contact"** — pull a contact the user previously saved.
3. **Onboarding & Terms** carry a non-replacement disclaimer: *"MindAnchor is not a crisis service. If you are in immediate danger, contact local emergency services or a crisis line."*
4. **Trigger-word watch (optional, v2):** if MindAnchor ever stores free-text journal entries, surface the same "Get help" sheet when a high-risk pattern is detected (the Character.AI, Wysa, Woebot pattern). Not required for v1.
5. **Maintenance protocol:** the helpline list must be reviewed every release. Dwyer et al. 2025 found broken hotlines in apps with 3.5M+ downloads — a documented harm.

### (c) Language and placement that does not "frighten" but is one tap away

**Language (lifted / adapted from Samaritans and Wysa patterns):**
- Button label: **"Get help"** (not "Crisis", not "Emergency", not "Suicide"). Calm verb, no noun.
- Sheet title: **"If you're in crisis, you're not alone."**
- Subtitle: **"Reach a person, day or night."**
- Each row: plain verb + 24/7 qualifier + phone number. Example: **"Call Samaritans — free, 24/7 — 116 123"**, **"Text HOME to 741741 — Crisis Text Line"**, **"Call 988 — Suicide & Crisis Lifeline (US)"**.
- No medical iconography. No red. No exclamation marks. No "Are you in crisis?" pop-up that fires on open.

**Placement:**
- One icon, one place, every screen. Low contrast, not animated, not badge-marked.
- No banner, no splash, no notification on first launch.
- Calm by default; *available* in distress. The exact pattern Wysa, NOCD, Headspace, Calm, Woebot, and the NHS all converged on.

The project owner's instinct — that a calm screen helps most users most of the time — is correct. The current product simply has no path for the *one* user for whom the calm screen isn't enough.
