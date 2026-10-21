# Glance: control Fire TV and speak to the room, with just your face

_Idea proposal · v1 · 2026-10-21 · Fire TV track (+ AWS Builder, Open Source; Ring accessibility as stretch)_
_Deadline: **Fri 2026-10-23, 12:00 PM PT** (Sat 00:30 IST). Status: **proposal, pending go/no-go tests**._
_Working title. Supersedes [SHOWRUNNER.md](SHOWRUNNER.md) (prior art: Google TV Deep Dives) and [IDEA.md](IDEA.md) (Rosetta)._

---

## 1. How we got here (method)

The brief: look at hackathon winners **outside TV** (mobile, tablet, desktop) and find something that works
**better on a TV** than where it started, and that nobody has brought to TV.

### Non-TV winners reviewed
| Competition | Winner | What it does | Platform |
|---|---|---|---|
| Gemini API Developer Competition 2024 ([ai.google.dev/competition](https://ai.google.dev/competition)) | **Gaze Link**: Best Android app | People with ALS communicate with their eyes | Phone |
| 〃 | **VITE VERE**: Most impactful | Step-by-step guidance for people with cognitive disabilities | Phone |
| 〃 | **Jayu**: Best overall | Personal assistant agent controlling the device | Desktop |
| 〃 | **Outdraw AI**: Most creative | Draw-vs-AI party game | Web |
| 〃 | **Everies**: Best ARCore | Scan real objects into interactive characters | Phone AR |
| Kaggle Gemma 3n Impact Challenge 2025 ([Google blog](https://blog.google/innovation-and-ai/technology/developers-tools/developers-changing-lives-with-gemma-3n/)) | **Gemma Vision** (1st) | Assistant for blind users via chest-worn phone camera | Phone |
| 〃 | **Vite Vere Offline** (2nd) | Offline version of the above | Phone |
| 〃 | **3VA** (3rd) | Pictograms → full sentences for a designer with cerebral palsy | Phone/tablet |
| Microsoft Imagine Cup ([winners](https://imaginecup.microsoft.com/winners)) | **FROM YOUR EYES** (2024) | AI vision assistant for visually impaired | Phone |

**Pattern:** the most-awarded category is **AI-powered accessibility**, especially **communication for people who can't speak or move**
(Gaze Link, 3VA, Look-to-Speak style). All of these live on **phones and tablets**. None are on the TV.

---

## 2. The gap (verified 2026-10-21)

- **Fire TV has no eye-gaze or face-gesture control.** Accessibility is VoiceView (screen reader), Screen Magnifier, Text Banner and Alexa voice. ([Amazon: Fire TV accessibility](https://aboutamazon.com/news/devices/accessibility-updates-for-fire-tv-and-amazon-video-customers))
- **Amazon built Eye Gaze, but only for the Fire Max 11 tablet.** It doesn't exist on Fire TV. ([Amazon help: Eye Gaze on Fire tablets](https://digprjsurvey.amazon.com/csad/help/node/TxoDzzO59B6JVzuM64))
- **Amazon's own Fire TV accessibility blueprint (Jul 2026)** says *"Voice is also the most reliable path for customers with motor impairments"* and asks apps to support "accessibility switches, and companion apps". It does not mention eye gaze, AAC or AI. ([Amazon Developer blog](https://developer.amazon.com/apps-and-games/blogs/2026/07/blueprint-series-pt5-accessibility))
  → **Many people with ALS, stroke, cerebral palsy or MS can neither use a remote *nor* speak clearly.** For them Fire TV has no input path at all.
- **Phone tools don't reach the TV:** Google **Look to Speak** (phone only; a UCL study found it *"largely failed to deliver substantial communication benefits"*, citing **high cognitive load**) ([BOIA](https://www.boia.org/blog/googles-new-look-to-speak-app-lets-you-communicate-through-eye-gaze-tracking), [UCL](https://discovery.ucl.ac.uk/id/eprint/10210053)); **Project Gameface** (face gestures → cursor, phone/PC, no TV) ([Tom's Guide](https://www.tomsguide.com/phones/motorola-phones/googles-project-gameface-lets-you-control-games-with-your-face-heres-how)).
- **Dedicated eye-gaze AAC devices cost ~$4,000–$23,000** (e.g. TD Pilot up to ~$10,000 plus the iPad). ([Montana AT Program](https://montech.ruralinstitute.umt.edu/eye-gaze-finally-affordable), [ALS Forums](https://www.alsforums.com/community/threads/tobii-dynavox-i-15-eye-gazer-computer-speech-generator-for-sale-lowered-price.45391/))

**No product found that lets a person control a TV with face/eye gestures *and* use the TV as an AI-assisted voice for talking to the people in the room.**
_Caveat: the Devpost gallery is hidden until judging._

---

## 3. The problem

For someone with ALS, a stroke, cerebral palsy, a spinal cord injury or late-stage MS, the living room TV is often
the main window to the world, and it's **the one screen they can't control**. Someone else has to hold the remote.
And when they want to say something to the family sitting next to them, they need a separate device costing
thousands of dollars, a slow letter board, or a caregiver guessing.

**Why the TV is the right place, not the phone:**
- It's where the person spends hours, and where the family gathers.
- It's **big enough to read from bed or a wheelchair**, and big enough for *everyone in the room to see* what they're saying as they compose it.
- Fire TV is cheap (~$30–50), already in the home, and has speakers for a spoken voice.

---

## 4. What Glance does

**Setup:** any phone (an old one is fine) on a stand, pointed at the user. Scan a QR code on the TV to pair. Done.
The phone's camera reads the face **on the phone itself**. **No video ever leaves the phone**, only gesture events.

### Pillar 1: Control (computer vision)
- Face gestures become TV actions: e.g. **look left/right → move**, **raise eyebrows → select**, **long blink → back**, **open mouth → play/pause**.
- **AI calibration to the person's abilities:** the user shows what they *can* do (some people can only move eyes and brows). Glance measures each gesture's reliability and **picks the most distinct, least tiring set** for that person.
- Browse a Glance hub of rows (favourite shows, apps, YouTube/Prime/Netflix deep links) and play content.

### Pillar 2: Speak (AI does the work)
- The TV shows a **communication board** the whole room can read.
- **Conversation-aware replies:** when a family member speaks to the user, the phone's microphone (opt-in) transcribes it, and the agent offers **3–4 likely replies**. One gesture picks one, and the TV **speaks it aloud** (Polly).
  > Daughter: *"Do you want tea or should I wait till the match ends?"* → Glance suggests **"Tea now, please"** · **"After the match"** · **"No thanks"** · **"Something else…"**
- **Context-aware phrase prediction** even without speech: time of day, what's on TV, and who's in the room ("Turn it up", "Who is that actor?", "I'm tired").
- **Keystroke savings:** a few gestures expand into a full sentence (like 3VA's pictograms → sentences). This directly targets the **cognitive load** that made Look to Speak fail.
- **Own voice (stretch):** pick a Polly voice that suits them.

### Pillar 3: Act (agentic tools)
- "**Call my daughter**" → notifies her phone (SNS). "**I'm uncomfortable / need help**" → urgent caregiver alert with a dedicated emergency gesture.
- "**Turn on subtitles**", "**Play the cricket**" → actions on the TV.
- **Stretch: "Who's at the door?"** → Ring camera tile on the TV, plus a spoken reply through the doorbell. This hits Ring's **accessibility** and **caretaking** priority categories and Amazon's "living room + front door" tagline.
- Sensitive actions always ask for a one-gesture confirmation.

### Pillar 4: Adapt
- **Fatigue-aware:** as gesture accuracy drops later in the day (common with ALS), the agent automatically shows **fewer, bigger targets** and slows timing.
- **Learns personal phrases**, names and routines (AgentCore Memory, opt-in).

---

## 5. Why this is "advanced AI that does work" (not a chat agent, not parental control)

| AI capability | Where |
|---|---|
| Real-time **computer vision** (478-point face landmarks + 52 blendshapes, on-device) | Gesture control |
| **Per-user model selection**: calibrates which gestures *this* body can do reliably | Calibration |
| **Speech understanding** of the room (Transcribe streaming) | Conversation-aware replies |
| **Context-grounded generation** (what was said, time, what's on TV, personal memory) → ranked replies | Speak |
| **Agentic tool use** with confirmations (notify, alert, control TV, Ring) | Act |
| **Adaptive UI driven by measured fatigue** | Adapt |
| Long-term **memory** of phrases, people, routines | Memory |

---

## 6. Can Fire TV do it?

| Piece | How | Status |
|---|---|---|
| TV app | Native Kotlin + Compose for TV (already built). Gesture events drive `FocusManager.moveFocus()` / select / back inside the app | ✅ Existing app shell |
| Media playback | Media3 player (already built, with subtitles) | ✅ |
| Launch other apps | Android intents / deep links to installed apps (YouTube, Prime Video, Netflix) | Standard Android, to verify on emulator |
| Phone camera + CV | Web page using **MediaPipe Face Landmarker** (runs in the phone browser, blendshapes for brow/blink/mouth/gaze) | To build. Needs **HTTPS** for camera access |
| Phone ↔ TV link | **API Gateway WebSocket** relay (or AWS IoT Core MQTT) | To build. The riskiest piece, tested first |
| Speech, generation, voice | Transcribe streaming, **Bedrock Nova** (works on our account), Polly | ✅ Services verified in tests 3–4 |
| Alerts | Amazon SNS | Trivial |
| Ring (stretch) | Ring developer APIs / playground | Unverified, stretch only |

**Honest limit:** a third-party Fire TV app **can't inject remote presses into other apps** (Netflix etc.). Inside Glance, everything is
face-controlled. For other apps, Glance launches them and offers face-controlled playback where possible. Whether Fire OS allows a
third-party **AccessibilityService** to drive other apps is an open question we'd test as a stretch. If it does, Glance becomes a
system-wide input. Either way, **this limit becomes concrete product feedback for Amazon** ("give accessibility input apps a sanctioned key-event API").

---

## 7. Architecture

```
 PHONE (on a stand, facing the user)              AWS                                  FIRE TV (native app)
 ┌──────────────────────────────┐   gesture   ┌──────────────────────────────┐  events  ┌────────────────────────────┐
 │ MediaPipe Face Landmarker    │ ──events──► │ API Gateway WebSocket relay  │ ───────► │ Glance hub (D-pad-free UI) │
 │  (on-device, no video sent)  │             │  + pairing (QR session code) │          │ Communication board        │
 │ Calibration UI               │             ├──────────────────────────────┤ ◄─────── │ Media3 player              │
 │ Mic (opt-in) → Transcribe    │ ──audio───► │ Transcribe streaming         │          │ Speaks replies (Polly)     │
 │   streaming                  │             │ Glance agent (Strands on     │ replies  │ Alerts / confirmations     │
 └──────────────────────────────┘             │  AgentCore): reply ranking,  │ ───────► └────────────────────────────┘
                                              │  phrase prediction, tools:   │
                                              │  notify, alert, tv_control,  │
                                              │  ring (stretch)              │
                                              │ Bedrock Nova · Polly · SNS   │
                                              │ AgentCore Memory (opt-in)    │
                                              └──────────────────────────────┘
```

**Privacy by design:** camera frames never leave the phone; the mic is opt-in and shown on the TV when it's listening;
no transcripts are stored unless the user opts in; caregiver contacts are stored only on the household's own AWS resources.

---

## 8. Hackathon fit

| Requirement / criterion | Glance |
|---|---|
| Runs on Fire OS / Vega | Native Fire OS app ✅ |
| Emulator demo allowed | ✅ (FAQ) |
| Priority categories | **Multi-modal UX** (face + visual + spoken output), **computer vision**, **AI-enhanced viewing** (watching without a remote), **family** (talking with family in the room) |
| Judges' "creative" examples | "multi-modal UX combining voice + D-pad + visual in a single flow" ✅. Ring's creative list names **"accessibility aid"** and **"care-taking"** ✅ |
| Amazon's own priorities | Fills a gap in Amazon's Jul 2026 Fire TV accessibility blueprint; brings Fire tablet "Eye Gaze" to the living room; **EAA/ADA** context |
| Potential impact | People who can't use a remote *or* speak; replaces part of a $4k–$23k device with a phone + Fire TV |
| Quality of idea | Transplants the most-awarded non-TV category (AI accessibility/AAC) to the TV for the first time, with conversation-aware AI that tackles the documented failure of earlier tools (cognitive load) |
| **AWS Builder** | Strands agent on AgentCore, Bedrock (Nova), Transcribe streaming, Polly, SNS, API Gateway WebSocket, DynamoDB, CDK |
| **Open Source** | `glance-gestures`: a face-gesture → D-pad input kit (MediaPipe web + WebSocket + Compose-for-TV receiver), MIT. Usable by *any* Fire TV / Android TV app, which fits Amazon's "support companion-app input" guidance |
| Friction log bonus | Continue `FRICTION_LOG.md` (e.g. no sanctioned accessibility key-injection API on Fire TV) |
| Video rules | We film ourselves (consent). No third-party media. Disclaimer: assistive tool, not a medical device |

---

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Gesture reliability (lighting, angles, involuntary movement) | Per-user calibration; dwell + confirmation; choose few, distinct gestures; test early |
| Phone↔TV latency through the cloud | Target < 300 ms; test first; fall back to AWS IoT Core MQTT if API GW is slow |
| Camera needs HTTPS in the phone browser | Host the phone page on S3 + CloudFront (or Amplify) from day one |
| Transcribe streaming from a browser is fiddly | MVP: push-to-talk clip → Transcribe; streaming as an upgrade |
| Can't control other apps (Netflix) | Be explicit in the demo; deep-link launch; log as product feedback; AccessibilityService as stretch test |
| We are not the target users | Co-design check: describe or show it to one person with lived experience or a speech therapist if at all possible; quote real requirements; avoid medical claims |
| Claude blocked on Bedrock | Nova is fine for English reply suggestions; Claude optional |
| Low RAM / disk on dev laptop | Known mitigations |
| **~2 days left** | Strict MVP below; go/no-go tests first |

---

## 10. MVP vs stretch

**MVP (must ship):**
1. Phone page: camera → MediaPipe → 4 gestures (left, right, select, back) with a simple calibration step.
2. Pairing by QR code; events relayed to the TV in under ~300 ms.
3. TV: Glance hub + communication board fully face-controlled; play a video in our Media3 player.
4. Speak: family speaks (push-to-talk on phone) → transcript → Nova suggests 4 replies → gesture picks one → TV speaks it (Polly).
5. Act: "Call for help" gesture → caregiver gets a notification (SNS).

**Stretch (in order):** fatigue-adaptive board → Transcribe streaming (hands-free) → memory of personal phrases → AccessibilityService system-wide control test → Ring "who's at the door".

---

## 11. Go/no-go tests (today)

| # | Test | Pass means |
|---|---|---|
| G1 | MediaPipe Face Landmarker in the phone browser detects brow-raise, long blink, look-left/right | ≥ 90% correct on 20 tries each, ordinary room light |
| G2 | Phone → AWS WebSocket → TV app moves focus | Median latency < 300 ms |
| G3 | Nova reply suggestions from a spoken question | 4 sensible replies in < 2 s |
| G4 | TV speaks the chosen reply via Polly | Audible and clear |

If G1 or G2 fails by tonight, we stop and reconsider.

---

## 12. Plan

| When | Deliverable |
|---|---|
| **Wed 21** | G1–G4. Phone page on HTTPS, WebSocket relay, TV receives gestures |
| Thu 22 | Communication board + reply loop + caregiver alert + calibration; agent on Strands/AgentCore; README + AWS integrations; OSS kit; **record demo** |
| Fri 23 (IST morning) | Devpost write-up, product feedback, friction log, feature requests; submit well before Sat 00:30 IST |

---

## 13. Three-minute demo script (draft)

| Time | On screen | Criterion |
|---|---|---|
| 0:00–0:20 | "Fire TV's accessibility relies on voice. What if you can't speak *or* hold a remote?" Cost of today's eye-gaze devices | Impact |
| 0:20–0:45 | Phone on a stand → QR pair → 20-second calibration ("show me what you can do") | Design, CV |
| 0:45–1:20 | Browsing and playing a show with eyebrows and glances only | Multi-modal, CV |
| 1:20–2:10 | Family member asks a question → TV shows 4 smart replies → one glance → the TV speaks for the user | AI does the work |
| 2:10–2:35 | Fatigue: the board simplifies itself; emergency gesture → caregiver's phone buzzes | Agentic, caretaking |
| 2:35–3:00 | Architecture, privacy (video never leaves the phone), open-source input kit for any Fire TV app | Tech, AWS Builder, OSS |

---

## 14. Other transplants considered (and why not first)

| From (non-TV winner) | TV version | Why not first |
|---|---|---|
| VITE VERE (cognitive disability guidance) | TV as a daily-routine coach for people with dementia | Strong impact, but overlaps Alexa routines; less CV and less multi-modal |
| Peek Acuity–style phone vision tests | TV as a home vision-screening chart (fixed distance), phone as the response pad | Medical-claim risk; less "agentic AI" |
| Everies (scan objects into characters) | Kids scan toys → toys star in a narrated TV story | Fun, but prior art in AI kids' story apps; weaker impact |
| Outdraw AI (draw vs AI) | Party game on TV | Jackbox-style prior art |
| Gemma Vision / FROM YOUR EYES (blind assist) | AI audio description on TV | DRM blocks third-party streams; limited to our own content |

---

## 15. Open decisions for you

1. **Go:** run G1–G4 today?
2. **Name:** "Glance" is a working title.
3. **Real-user check:** do you know anyone (or a speech therapist) we could describe it to before the demo? Even one quote strengthens "Impact".
4. **Ring stretch:** worth trying if the MVP is done Thursday midday?
