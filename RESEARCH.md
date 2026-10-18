# Project Rosetta — Hackathon Research & Brainstorm

_Compiled 2026-10-18 from the Devpost overview, rules, resources, updates, and FAQ pages._

## The hackathon at a glance

| Item | Detail |
|---|---|
| Name | Build, Ship, Shape: Amazon Developer Hackathon |
| **Submission deadline** | **Fri Oct 23, 2026, 12:00 PM PT** (that's 12:30 AM IST on Sat Oct 24) |
| Judging | Nov 9–20, 2026 · winners ~Dec 3 |
| Tracks | Fire TV · Alexa+ · Bee (wearable) · Ring |
| Mini challenges | AWS Builder ($5k + $5k credits) · Open Source ($5k + $5k credits) |
| Win cap | One track prize + one mini-challenge prize per project |
| Office hours #2 | **Mon Oct 19, 9:00 AM PT** (bring questions) |
| AWS credits | Gone (ran out Oct 7). Budget for small Bedrock spend yourself. |

### Fire TV prizes
1st $25k + $15k AWS credits + meeting with the Amazon team · 2nd $15k + $5k · 3rd $4k + $1k

## Fire TV track: what's required

- A demo-ready app that runs on **Fire OS or Vega OS**. Any framework: React Native, web, Kotlin/Java.
- The demo video must show it running on a real Fire TV **or an emulator**. The FAQ says plainly that the **Android TV emulator is fine** and no physical device is needed at any stage.
- **Priority categories:** AI-enhanced viewing, sports, fitness, family entertainment, multi-modal UX, computer vision.
- The rules list ideas judges see as **obvious**: a basic streaming UI, a simple video player, a remote-controlled game.
- And ideas they see as **creative**: AI viewing that adapts to household patterns, CV fitness using a camera, and multi-modal UX that mixes **voice + D-pad + visuals in one flow**.

### Platform reality on Windows
- **Vega SDK / Vega Virtual Device: macOS or Ubuntu only** (needs KVM). Not an option on Windows unless you use a Linux VM or dual boot.
- **Fire OS path = Android.** Use Android Studio and the Android TV emulator. This works on Windows today. **This is our path.**
- Best starter: `AmazonAppDev/react-native-multi-tv-app-sample`. It's one React Native codebase with drawer nav, content grid, hero, and video player, and it also targets Vega later.
- Native alternative: `hello-world-fire-tv` (Kotlin + Jetpack Compose).
- Amazon Devices Builder Tools (an MCP server + Agent Skills) can be plugged into Claude Code to help with scaffolding and debugging. Worth installing, and it also counts as a "tool used" in the feedback section.

## Bee track: verdict = skip
The rules and FAQ both say you need a **real Bee device or an Apple Watch running Bee**. Developer Mode only unlocks after you pair a device, and the repo and video must show **live** Bee data. There's no simulator. Without the hardware it's a non-starter, so don't spend time on it.

## Judging (4 criteria, equally weighted)
1. **Tech implementation**: how well it's built and how well it uses the platform
2. **Design**: a coherent product, with interaction designed for TV (10-foot UI, D-pad focus)
3. **Potential impact**: a credible, specific customer need that could ship to the Fire TV Appstore
4. **Quality of idea**: creative, not obvious

Plus **up to a 10% bonus for friction logs**. Cheap points, so log every snag from day 1.

Stage 1 is pass/fail on fit with the track and the required tech.

Judges to keep in mind: VP Fire TV Experience, **Sr Mgr Fire TV Voice & AI**, Director Fire TV Devices, Vega/Callstack founder, and **Sr TPM at NBA** (a sports angle lands well).

## Submission checklist
- [ ] Text description (what it does, how it works)
- [ ] GitHub repo: public with an OSS license visible in About, **or** private and shared with `knmeiss, giolaq, anishamalde, mosesroth, emersonsklar, chris-trag` + testing@devpost.com. Clear setup and run instructions.
- [ ] Demo video **under 3 min**, public on YouTube or Vimeo, in English, running on the emulator or device. No copyrighted music or footage.
- [ ] Product feedback for **every** tool/SDK used (used for · worked well · needs work · onboarding · would you build again)
- [ ] Tracks and mini challenges entered
- [ ] AWS Builder: describe the AWS services used, inside the feedback answer
- [ ] Open Source: contribution URL, repo URL, GitHub username, plus what/how/why
- [ ] Optional: feature requests (with priority)
- [ ] Optional: **friction log** (task, steps, expected vs actual, severity, workaround, suggestion) → up to 10% bonus

---

## Brainstorm: ideas that fit the name "Rosetta"

"Rosetta" points to *translation*, and that fits the AI-enhanced viewing category: **a TV co-viewer that translates whatever is on screen** (a language, sports jargon, or adult-level content made simple for kids).

### Idea A — Rosetta: Learn a language from whatever you watch ⭐ recommended
A Fire TV player that turns video into language practice.
- **Dual subtitles**: target language and native language, stacked.
- **D-pad word-picking**: pause, move focus across the subtitle words with the D-pad, press OK, and a card shows meaning, pronunciation (Polly), and an example sentence (Bedrock).
- **"Ask Rosetta" (voice)**: ask by voice "what did she just say / why that word?". The answer draws on the transcript around that moment.
- **Phone companion (multi-modal)**: scan a QR code on the TV to pair a phone. The phone becomes the mic, a vocab notebook, and a quiz pad. Covers voice + D-pad + visuals in one flow.
- **Household adaptation**: profiles track known and unknown words, and later scenes highlight words you looked up before (spaced repetition while you watch).
- Content: CC-BY Blender open movies (*Sintel*, *Tears of Steel*) ship with multi-language subtitles, so there's no copyright risk.
- Categories hit: AI-enhanced viewing, family/education, multi-modal UX.
- Impact story: tens of millions of language learners already use "watch shows with subtitles" as a study method, and the TV has had no good tool for it.

### Idea B — Rosetta Sports: the explainer for new fans
An AI co-viewer for a game: "explain that play", rule explainers, stats in plain English, commentary in your language, and a kid mode.
- Pros: the NBA judge, and sports is a priority category.
- Cons: **getting footage legally is hard** (we'd have to use a synthetic or open play-by-play feed with no real broadcast video), so the demo is less visceral.

### Idea C — Rosetta Family: one screen, many languages
Multi-generational households where grandparents and kids don't share a language. Each viewer gets their own subtitle and dub language (phone audio as the personal channel), with AI-simplified explanations for kids.
- Strong impact story (immigrant families, India's multilingual homes). The per-phone audio channel is the hard, risky part.

### Idea D — Rosetta Sign (CV + accessibility)
The phone camera watches you sign, and the TV teaches ASL/ISL with real-time CV feedback.
- Highest "creative" score (CV + multi-modal + accessibility), but **the biggest risk in 5 days**.

### Recommendation
**Idea A as the core, with Idea C's "per-viewer language" as a stretch.** A is the most buildable in 5 days, demos well in 3 minutes, avoids copyright problems, and hits 3 priority categories plus the multi-modal "creative" example word for word.

## Mini challenges to stack
- **AWS Builder**: Bedrock (Claude) for explanations and quizzes, Polly for pronunciation, Transcribe for the voice question, and Lambda + API Gateway as the backend. Use multiple services, not a single Bedrock call, to land in the "creative" bucket.
- **Open Source**: publish the dual-subtitle + D-pad word-focus component as a standalone MIT RN-TV library (e.g. `react-native-tv-subtitle-picker`). Better yet, open a PR adding TV focus support to an existing subtitle library.

## Draft 5-day plan (deadline Fri Oct 23, 12 PM PT)
| Day | Date | Goal |
|---|---|---|
| 1 | Sun Oct 18 | Lock idea. Android Studio + Android TV emulator. Clone multi-tv sample and run it. Start friction log. |
| 2 | Mon Oct 19 | Office hours 9 AM PT. Video player + dual subtitles + D-pad word focus. |
| 3 | Tue Oct 20 | Lambda + Bedrock + Polly backend. Word cards. "Ask Rosetta". |
| 4 | Wed Oct 21 | Phone companion (QR pair, voice, vocab). Profiles/known words. Polish 10-foot UI. |
| 5 | Thu Oct 22 | Record demo video, README, feedback, friction log, OSS component. |
| — | Fri Oct 23 AM | Buffer + submit before 12 PM PT. Add reviewers if repo is private. |

## Open questions for you
- Solo or team?
- Do you have an AWS account (Bedrock access in us-east-1/us-west-2)?
- Comfortable with React Native/TypeScript, or prefer Kotlin?
- Do you own any Fire TV device? (Not required, but nice for demo polish.)
