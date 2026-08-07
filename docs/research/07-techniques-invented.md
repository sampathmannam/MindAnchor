# 07: Techniques Invented for MindAnchor, and What Got Built

> Where surveys 01–06 report what the literature says, this file records what
> we decided to *do* about it — five techniques that do not exist in the
> products surveyed in 03, each answering a specific finding from 01–02, plus
> the OS-level mechanisms that make a decision stick.
>
> Status is stated per technique. Three of the five are still on paper. Saying
> so here is the point: this file is a design record, not a feature list.

---

## The problem the surveys leave behind

Read 01 and 02 together and a shape emerges that none of the products in 03
address.

Interventions work, and then stop working. HeartSteps decayed. Sense2Stop was
null. Meinhardt (CHI 2025) found users desensitize to context-blind prompts.
Haliburton (CHI 2024) found people take deliberate breaks from their own
friction and rebound afterwards. Grüning (PNAS 2023) is the happy exception —
and even there the mechanism is habit decay, which is to say the intervention
works by making itself unnecessary.

Every tool in the landscape survey is built as though its friction is a
constant. One second is one second on day 1 and day 200, at 9am and at 3am,
for someone procrastinating and for someone in the worst hour of their year.
The literature says plainly that this is the wrong shape, and the products
have not caught up.

So the through-line of everything below: **an intervention that cannot read
the moment, and cannot end, will stop working and then keep firing anyway.**

---

## 1. Context-gated friction — BUILT

`app/src/main/java/org/mindanchor/friction/FrictionTone.kt`

**The finding it answers.** Meinhardt et al. (CHI 2025, N=72): intervention
effectiveness during scrolling is context-dependent — sleepiness lowers
reactance, low mood at home slows responsiveness, and users desensitize to
prompts that ignore context. Haliburton et al. (CHI 2024, N=1,039): friction
survives long-term, but people route around it in bursts.

**The technique.** The pause has three tones rather than one setting:

| Tone | When | What the person sees |
|---|---|---|
| `FULL` | First open, or any open inside the sleep window | The full breathing pause |
| `BRIEF` | Second or third open within ten minutes | A shortened pause |
| `FEATHER` | Fourth open onward within ten minutes | A single line, no delay |

**The inversion that makes it novel.** Every self-control tool that adapts at
all escalates: repeat the behaviour, get more resistance. This does the
opposite. Repeated opens in a short window *soften* the friction.

The reasoning is that repetition is not evidence of weak resolve — it is
evidence the pause has already failed at this moment, four times, and a fifth
identical delay teaches only that the tool is scenery. Softening keeps the
tool honest and preserves its force for the moment it can still do something.

The one context that overrides all of it is the sleep window, where friction
stays `FULL` no matter how many times someone has opened the app. That is the
one place in 01's evidence base where the OS-level lever is strongest
(sleep regularity, Windred 2024) and where a person is least likely to be
making the choice they would make awake.

**What it deliberately does not do.** It does not model mood. Discussed in
full in §6.

---

## 2. Behavioural activation at the point of avoidance — NOT BUILT

**The finding it answers.** Linardon (*World Psychiatry* 2024, 176 RCTs):
app effects are small (g≈0.28) and largest when the app carries actual CBT
content rather than generic tips or dashboards. Monge Roffarello (TOCHI 2023):
self-monitoring alone is the weakest class of tool — dashboards are a
documented near-failure.

**The technique.** Behavioural activation is the simplest well-evidenced
treatment for depression: do the small thing, and mood follows action rather
than preceding it. Its hard part is *noticing the moment of avoidance*, which
in therapy is reconstructed days later from memory.

A launcher does not have to reconstruct it. Reaching for a distraction app is
the moment of avoidance, observed live, at the exact second it happens.

So instead of a pause that says "are you sure", the pause offers the smallest
version of something the person said mattered to them, while they are already
holding the phone: *"Two minutes outside?"* — and opening the app anyway is
always one tap away.

**Why it is not built yet.** It needs the person to have written down what
matters to them, which is an onboarding surface that does not exist, and it
needs to not become nagging, which is a tuning problem I do not want to guess
at. The failure mode is severe: a tool that suggests a walk to someone who
cannot get out of bed is a tool that produces one more thing to have failed at
today.

---

## 3. Sensorless phenotyping — NOT BUILT

**The finding it answers.** Müller, Harari et al. (*Scientific Reports* 2021):
a GPS-mobility depression pipeline at AUC 0.82 in N=57 students fell to
**AUC 0.57 — chance — in N=5,262**. The 2025 *JMIR* review: only 1 of 9
sensing studies could distinguish worsening from relapse from recovery.

**The technique.** The literature's response to this has been to add sensors.
The opposite response is available and untried: keep the within-person
baseline, which is the part that generalizes, and **remove the inference
step entirely.**

Show the person their own deviation, in their own observable behaviour, with
no label attached: *"You've been up past 3am four nights this week. You
usually aren't."* Not "you may be experiencing a depressive episode."

The claim is falsifiable and true — it is a count of their own unlocks. The
interpretation is left with the only party who has the context to make it.

**Why it is not built yet.** The framing is the entire product here, and the
framing needs someone with clinical training to sit with it. Delivered wrong,
"you usually aren't" reads as surveillance or as reproach, and the person it
lands hardest on is the person already keeping score against themselves.

---

## 4. Bedtime Zeigarnik release — NOT BUILT

**The finding it answers.** 01 §4: sleep regularity (SRI) beats duration as a
predictor across ~60k people, and is derivable from unlock and screen-state
logs with no wearable. The gap: knowing that late unlocks matter says nothing
about why the person is still holding the phone at 1am.

**The technique.** The Zeigarnik effect — unfinished tasks intrude on
cognition until closed — has a known release valve: Masicampo & Baumeister
(2011) showed that *writing a plan* for the unfinished task removes the
intrusion as effectively as finishing it.

Nobody has put that at the point where phones actually cost people sleep. The
1am scroll is frequently not a craving for the feed; it is one unclosed loop
that will not sit still. So: at wind-down, one line — what is still open? —
captured, put away, and handed back the next morning at a time the person can
do something about it.

Two evidence-based mechanisms, joined at the point where a launcher can see
both, which is a place no app can stand.

**Why it is not built yet.** It requires the wind-down surface and the morning
return path to both exist and to be trusted. A prompt that asks what is
unfinished and then loses the answer is worse than no prompt.

---

## 5. Intervention expiry — NOT BUILT

**The finding it answers.** Habituation is the most consistent finding in the
whole of 01 — HeartSteps decay, Sense2Stop nulls, Meinhardt's desensitization,
Grüning's own mechanism. And 02's meta-analysis: tools reduce targeted app use
while evidence for wellbeing gains stays weak.

**The technique.** Every product in survey 03 accumulates. None of them
removes a feature because it stopped working.

Give each pause an expiry. If a pause has not changed what the person does
over some window — same opens, same duration, the delay simply absorbed — it
retires itself and says so plainly: *"This pause hasn't been doing anything
for a while. I've turned it off. You can turn it back on."*

**Why this is the most contrarian idea here.** It is a growth-negative
feature. It reduces engagement on purpose, and it publicly admits the product
failed at something. That is exactly why no funded product ships it, and
exactly why an app with no backend, no analytics and nothing to sell can.

**Why it is not built yet.** "Did this change behaviour" is a causal question
answered with observational data from one person, and the naive version — open
counts before and after — will call random drift a success or a failure at
roughly the rate a coin would. Getting this wrong retires a pause that was
working. It needs a real effect-detection design, not a threshold I invented.

---

## 6. The technique we refused: mood detection

This came up directly when the project moved from launcher toward OS, on the
reasoning that more system access would unlock more capability. For most
things that is true — §7 is the proof of it. For mood it is not, and the
distinction matters enough to write down.

The barrier to inferring mental state from a phone is **epistemic, not
technical**. Root access does not make AUC 0.57 into AUC 0.82. The signal is
not sitting behind a permission; the surveys say it largely is not there, at
least not in a form that transfers between people.

And the asymmetry of being wrong is brutal in one direction. A tool that
wrongly decides someone is fine simply does nothing. A tool that wrongly
decides someone is in crisis intrudes at the worst possible moment, on the
person least able to absorb it — and a person who has been wrongly flagged
once has learned the tool does not know them, which spends the credibility
every other feature here runs on.

So MindAnchor observes what it can count and never names what it cannot. Late
unlocks are late unlocks. Repeat opens are repeat opens. §1 gates on the clock
and on a counter, both of which are facts.

---

## 7. What OS-level access actually bought

The move from launcher to OS was worth making — just not for §6. Three
mechanisms landed, and each one closes a gap that survey 05 identified.

### Device Owner suspension — BUILT

`admin/DeviceOwner.kt`, `admin/SuspensionGuard.kt`

Everything else in this launcher is advisory: a pause you can walk through, a
hidden app still reachable by typing its name. That is correct for most people
and useless for the hours when a person's own judgement is the thing that has
gone.

`DevicePolicyManager.setPackagesSuspended()` is the one Android mechanism that
makes a decision made while calm survive a decision made while not. Granted
once over adb on a phone with no accounts signed in:

```
adb shell dpm set-device-owner org.mindanchor/org.mindanchor.admin.MindAnchorDeviceAdmin
```

A deliberately high bar, and it should be.

**The safety rule is the real work.** `SuspensionGuard` is a pure function,
holds the most tests in the repository, and exists because a suspended dialer
at 3am is a person unable to call anyone — which is the exact hour this app is
built for. The dialer, the messaging app, Settings, the emergency package and
this launcher itself can never be suspended, whatever anyone chooses,
including on tablets where the dialer lookup returns null. `release()` lifts
suspensions before handing ownership back, so nobody is left with apps stuck
off and nothing on the phone able to free them.

### System-wide grayscale — BUILT

`grayscale/Grayscale.kt`

Survey 05 §4: the clean `ZenDeviceEffects` path needs API 35; the fallback is
the `Settings.Secure` daltonizer in monochromacy mode behind
`WRITE_SECURE_SETTINGS`, grantable by one adb command. This is grayscale for
the *whole phone*, not a filter over one app — the thing no launcher can do
from inside its own window. Every write is wrapped; a failure here must not
take down a launcher.

### The always-open list — BUILT

`launcher/AppActionsDialog.kt`, feeding `SuspensionGuard.alsoNeverSuspend`

"Distracting" and "must reach me at 3am" are not opposites, and treating them
as one axis is a design error with real consequences. Someone on call marks
their work messenger as distracting for perfectly good reasons; enforced quiet
hours would then close the channel their job runs through — for shift workers,
carers, medics, anyone whose employer reaches them on a consumer app.

So the two properties are separate. An app can carry a pause *and* be exempt
from every suspension and every quiet hour. This is also the one line of
defence against the failure mode where a wellbeing tool costs somebody their
job, which would end their use of it and deserve to.

---

## What this adds up to

Three things are running: friction that reads the moment, a phone that can
hold a decision, and a screen that can go grey. Three are written down and not
built, each blocked on something specific and named above — an onboarding
surface, a clinician, and an effect-detection design I am not willing to
approximate.

The honest summary of the state of the art, from 01–03, is that this field
knows its interventions decay and ships them as constants anyway. Everything
here is an attempt to build the other thing.
