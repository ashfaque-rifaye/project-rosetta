# Project Rosetta

### The TV that translates "do your homework" and "clean your room" into a family adventure.

_Master spec · v2 · 2026-10-21 · **Single source of truth** for the build and the submission._
_Amazon "Build, Ship, Shape" hackathon · **Fire TV track** + **AWS Builder** + **Open Source** mini challenges._
_Deadline **Fri 2026-10-23, 12:00 PM PT** (= Sat 00:30 IST). Concept from the user's Playbook v4, slide 27 ("Quest Board"), expanded here._
_Superseded explorations, kept for history: `IDEA.md` (family-story channel), `SHOWRUNNER.md`, `GLANCE.md`, `QUEST_BOARD.md`._

> **22 Oct 2026 update: education first.** The owner moved the focus to learning (their original slide already said
> "parents nag about chores *and homework*"). New: Homework Check (Tutor agent + exact maths checks), Practice on the
> skills that slipped, and the family Quiz Battle ("Stone Trial", Unity) that replaced the Mess Boss raids. Chores stay
> as one quest type. The AWS stack is **deployed** (owner approved on 22 Oct). The sections below are the original
> chores-first spec, kept for the reasoning and prior-art work; current status and decisions are in `HANDOFF.md`.

---

## Contents
1. [Why "Rosetta"](#1-why-rosetta)
2. [The problem](#2-the-problem)
3. [Prior-art & ideation search](#3-prior-art--ideation-search-done-2026-10-21)
4. [What makes Rosetta different](#4-what-makes-rosetta-different)
5. [The experience: 9 features](#5-the-experience-9-features)
6. [Design direction (from your inspirations)](#6-design-direction-from-your-inspirations)
7. [TV screens, one by one](#7-tv-screens-one-by-one)
8. [The AI: a crew of agents](#8-the-ai-a-crew-of-agents)
9. [Fire TV architecture](#9-fire-tv-architecture)
10. [Visual assets: Google Flow + licensed media](#10-visual-assets-google-flow--licensed-media)
11. [Privacy, child safety, ethics](#11-privacy-child-safety-ethics)
12. [Hackathon compliance: every requirement](#12-hackathon-compliance-every-requirement)
13. [Judging criteria → how we score](#13-judging-criteria--how-we-score)
14. [MVP, stretch, cut list](#14-mvp-stretch-cut-list)
15. [Plan to the deadline](#15-plan-to-the-deadline)
16. [3-minute demo film](#16-3-minute-demo-film)
17. [Risks](#17-risks)
18. [What I need from you](#18-what-i-need-from-you)

---

## 1. Why "Rosetta"

The Rosetta Stone let two worlds understand each other. **Rosetta translates between parents and kids:**

| Parent says… | Rosetta translates it into… |
|---|---|
| "Clean your room." *(vague, fight-starting)* | **5 concrete micro-quests** with the exact items highlighted on the TV |
| "Is it done?" *(judgement, argument)* | **Item-by-item proof**: "4 of 5, just the jacket left!" |
| "No more TV until…" *(punishment)* | **Progress**: stars that build the family's world and unlock movie night |
| "I'm tired of checking." | **Autopilot**: parents only see the cases the AI is unsure about |

One sentence for judges: **Rosetta is a Fire TV app where an AI crew turns household chores into a shared family adventure on the biggest screen in the home.**

---

## 2. The problem

- **Screen time is the #1 daily household fight.** In 55% of families it causes frequent or occasional arguments, and 46% of parents struggle to enforce agreed limits ([Axa / Sotomo, Aug 2025](https://www.bluewin.ch/en/news/55-percent-of-families-argue-about-screen-time-2823146.html)). One US survey estimates parents spend **~96 hours a year** fighting with K-5 kids over screen time ([Talker Research for AngelQ, Mar 2025](https://talkerresearch.com/how-long-do-parents-spend-fighting-with-their-kids-over-screen-time/); headline estimate, method unverified).
- **"Clean your room" is a vague instruction**, and for many kids (especially with ADHD) a big fuzzy task is paralysing. Breaking it into concrete steps works, but parents rarely have the energy to do that every night.
- **Today's tools either restrict or create more work.** Amazon Kids can limit time but can't let kids *earn* it. Chore apps make parents approve every photo (reviewers call Joon's approvals "burdensome").
- **The TV is the prize, yet it's never part of the solution.** Every chore app lives on a phone; the TV just gets switched off.

**Users:** families with kids aged 5–12 (primary); siblings; kids who benefit from concrete steps and visible rewards.

---

## 3. Prior-art & ideation search (done 2026-10-21)

We searched app stores, Devpost/MLH/hackathon showcases, Product Hunt, news and research.
**The core loop is not new. We must not claim it is.**

### Hackathon / ideation projects
| Project | Where | What it does | Overlap | What it lacks |
|---|---|---|---|---|
| **Chorémon** ([GitHub](https://github.com/pfarell/choremon), [Hack Indy recap](https://buttondown.com/indyhackers/archive/we-hacked-the-planet-and-heres-who-won/)) | Hack Indy 2026: **MLH Best Use of Gemini API** | Photo of a mess → AI checklist of items; AR coin/tile cleaning modes; XP, streaks, family leaderboard; snarky raccoon host "Rascal" (ElevenLabs voice) | **High**: vision checklist + host character | No TV, no screen-time reward, no before/after verification, no parent flow, no recap, competitive leaderboard |
| **ChoreCheck** ([news, Jul 2026](https://www.whidbeynewstimes.com/2026/07/21/kid-creates-every-parents-dream-app/)) | Kid-built app | AI compares the chore photo to a parent-approved reference photo; restores screen time | Medium | Phone-only; pass/fail; no world, no TV |
| **Bonby** ([Devpost](https://devpost.com/software/bonby)) | Devpost | AI task manager + app blocker; parents allocate tasks with rewards | Medium | Phone app blocker; no vision planning |
| **Germinators** ([Snap Spectacles showcase](https://developers.snap.com/spectacles/spectacles-community/hackathon-showcase)) | Spectacles hackathon | AR chore game scoring cleaned surface/time | Low | AR glasses; no AI verification |
| Research: *Gamification and Family Housework Applications* ([IFIP](https://dl.ifip.org/hal-01758462v1)) | Academic | Gamification can affect family interaction **positively and negatively** | – | A design warning we take seriously (co-op over competition) |

### Commercial apps
| App | Platform | Feature | Gap |
|---|---|---|---|
| ChoreSplit ([site](https://choresplit.com/)) | Phone | AI photo verification | Pass/fail, phone-only |
| Chore Lock ([site](https://getchorelock.com/)) | iPhone | Apple Screen Time lock + AI photo check | Phone-only |
| Chore Champ ([App Store](https://apps.apple.com/us/app/id6502973506)), Ally | Phone | Limits devices until chores done; AI/photo verification | Phone-only |
| Piggy, Chirp, Earn Screen Time ([Piggy](https://piggyscreentime.app/)) | iPhone | Photo proof → parent approves → minutes | Manual review |
| Joon ([App Store](https://apps.apple.com/app/apple-store/id1482225056)) | Phone/tablet | Quests + virtual pet; parent approves each task | Approval burden; solo |
| ScreenCoach ([coverage](https://noosatoday.com.au/news/13-10-2020/kids-screen-time-coach)) | Phone/PC + **hardware box for TVs** | Earn time | Needs extra hardware; no AI |
| Amazon Kids Parent Dashboard ([listing](https://apps.appfollow.io/ios/amazon-kids-parent-dashboard/6471528064?country=us)) | Fire TV/tablets | Limits, bedtime, "Learn First" | **Restrict-only**; no earn-time API |
| Chore-chart apps (Chore Pad, MyChoreBoard, Chorsee, QuestSpark…) | iPhone/iPad | Charts, stars | **None run on Fire TV / Android TV** |

### Added 2026-10-21 (raids, residents)
| Project | Overlap | Difference |
|---|---|---|
| **Everies** (Gemini API Competition 2024, Best ARCore) / Talkify / Preferred Networks CHI'25 | Everyday objects get faces, personality, voice | Ours: objects are *tidying targets* verified by vision, then move into a shared TV house |
| HabitHero, ChoreChomp, ChoreSplit | XP, co-op goals, AI photo checks | None has a **co-op boss raid whose HP falls with AI-verified put-aways** |

### Verdict
- **Taken:** "AI looks at a mess and lists items" (Chorémon), "earn screen time with photo proof" (many), "snarky AI host" (Chorémon's Rascal).
- **Not found anywhere:** a **TV-native, shared-screen** family experience; **before/after item-level verification with partial credit shown to the whole family**; a **co-op world** built by siblings together; an **AI-produced nightly recap show**; **confidence-routed parent autopilot**; **Amazon ecosystem** tie-ins (Fire TV + Fire tablet + Ring + Amazon Kids).
- _Limits of the search: the Devpost gallery for this hackathon is hidden until judging, and no search covers every app store._

---

## 4. What makes Rosetta different

| | Chorémon | Chore apps (ChoreSplit, Joon, Chore Lock…) | **Rosetta** |
|---|---|---|---|
| Screen | Phone/AR | Phone | **Fire TV (shared family screen)** + any phone/Fire tablet as camera |
| Vision | Lists the mess | Pass/fail on one photo | **Plans** (before) **and verifies item by item** (after), partial credit, specific nudges, boxes on the big screen |
| Social model | Competitive leaderboard | Solo | **Co-op**: siblings build one world together (per the IFIP warning) |
| Parent load | None | Approve every task | **Autopilot**: only low-confidence cases reach the parent |
| Reward | XP | Minutes | Stars → **world grows** → movie night, TV-time tokens, real-world rewards |
| Storytelling | Host quips | – | **Nightly AI recap show** + **Quest Passport** of illustrated stamps |
| Ecosystem | – | Apple Screen Time | **Fire TV, Fire tablet, Ring (package quests), Amazon Kids** (feature request) |
| AI architecture | Single model + fallbacks | Single check | **Crew of agents** with tools, memory and confidence routing |

---

## 5. The experience: 9 features

### ① Room Scan: AI that *plans* the chore ⭐ hero
Kid taps **Start** on a phone or Fire tablet → snaps **before** → the TV fills with the photo, **glowing outlines** around each item,
and a checklist: *🧦 3 socks · 🧸 toys on the rug · 🧥 jacket on the chair · 🛏️ bed · 📚 books*. Each item is worth a star.

### ② Proof: item-by-item verification
Snap **after** → items tick off **one by one on the TV** with a satisfying animation → anything left stays lit with a kind nudge
(*"So close, the jacket!"*) → partial stars now, the rest when finished. Same-room and "nothing changed" checks prompt a retake (coaching, not policing).

### ③ The Family World: an isometric home map (immersive home screen)
The TV home is a **stylized isometric 3D island-house** (inspired by SideQuest's 3D map). Rooms are **pins** (bedroom, kitchen, balcony),
quests glow where they live, siblings' avatars stand in the rooms they're working on. Completing quests **builds the world**:
a treehouse, a lighthouse, a garden. **One shared goal** for the family ("Light the lighthouse: 100 ⭐") unlocks **Movie Night**.

### ④ Pip, the host
A warm, cheeky character who **speaks on the TV** about what the camera *actually* saw:
*"Arjun, that bed is so flat a pancake would be jealous!"* Kind, never shaming. Grounded in the scan, guarded for kids.

### ⑤ Reward Reveal
Stars fly from the checklist into the world; a **reward chest** opens with a **deterministic, earned** prize (new avatar item, world piece).
_No randomness, no paid loot boxes: we borrow the visual richness of game lobbies, not their gambling mechanics._

### ⑥ Quest Passport (inspired by Parky's award-winning stamps)
Every completed quest type earns an **illustrated stamp** in the kid's passport (Room Rescuer, Plant Guardian, Package Detective…),
plus a **Day Card** at bedtime. This is the "keep them coming back" loop.

### ⑦ Rosetta Recap: the nightly show
At wind-down, the TV plays a **60-second AI-produced episode**: before→after reveals, stars counting up, today's world piece being built,
Pip narrating. Effort becomes something the family watches together. Photos are used only with the parent's opt-in.

### ⑧ Parent Mission Control (phone/web)
- **Post quests by voice or text** ("Meera, water the balcony plants") → the agent writes steps, picks a star value, puts it on the TV.
- **Autopilot:** confident results auto-approve; only unsure ones ask the parent ("Is this bed made? ✓ / ✗").
- **Weekly insight:** "Meera starts homework quests late; try a 10-minute starter quest at 5 pm." Difficulty and step size adapt per child.
- **Live agent view** (inspired by *AI Mission Control*): see the crew at work on each quest.

### ⑨ Front-door quests with Ring (stretch)
Ring sees a **package delivered** → TV: *"Package Detective: bring it inside!"* → completed when Ring sees the porch clear.
Matches Ring's "package/delivery management" and Amazon's "living room + front door + cloud". **Only if Ring access works.**

_Also stretch: **Homework quests**, where a worksheet photo gets **hints, not answers** ("look at Q3 again").

---

## 6. Design direction (from your inspirations)

| Inspiration | What we take | Where it shows up |
|---|---|---|
| **Parky** ([Shipaton 2026, 1st "Keep Them Coming Back"](https://apps.shipaton.com/app/parky-theme-park-tracker)) | Illustrated **stamps**, a "Pass", day timeline, shareable **day cards**, "it watches for you" alerts; warm cream + orange retro palette; confident serif headlines ("Less guessing. More park day.") | Quest Passport, Day Card, Recap end card, tagline style |
| **Nixtio: Kids Learning App** ([shot](https://dribbble.com/shots/27203122-Kids-Learning-App-UI-Gamified-Education)) | **Stylized 3D characters**, coins, avatar store, neon-lime on near-black, chunky rounded cards | Kid avatars, avatar shop, star/coin counters |
| **Nixtio: SideQuest social map** ([shot](https://dribbble.com/shots/27819535-Social-Discovery-Events-Mobile-App)) | **Isometric 3D map** with floating pins and avatar bubbles | The Family World home screen |
| **uixNinja: AI Mission Control** ([shot](https://dribbble.com/shots/27597654-AI-Mission-Control)) | Dark **agent node graph**, live activity feed, insight cards | Parent Mission Control; "crew at work" panel during Room Scan |
| **uixNinja: Bets casino** ([shot](https://dribbble.com/shots/25319397-Bets-casino)), **Morethan Games** ([profile](https://dribbble.com/MorethanGames)), **Bang Studio** ([profile](https://dribbble.com/Bangdesign)) | Rich **dark game lobby**, 3D hero banners, glowing **collectible character cards** with rarity frames, reveal animations | Home hero banner, avatar/collectible cards, Reward Reveal (non-random) |
| **Greater** ([profile](https://dribbble.com/greater-studio)) | Bold **editorial illustration**, collage, risograph texture | Passport stamps, Recap title cards |
| **Erik Padamans** ([profile](https://dribbble.com/ErikPadamans)) | Clean, quiet SaaS layouts | Parent web console |

### Design tokens (TV-first, 10-foot)
- **Base:** Ink `#0B0D14` · Night `#141826` · Surface `#1C2133`
- **Accents:** Lime `#C6F432` (action/focus, Nixtio) · Sun `#FF8A3D` (rewards, Parky) · Cream `#FFF4E2` (passport paper) · Sky `#6BC6FF` (world) · Berry `#FF5C8A` (Pip)
- **Type:** Display: **Fredoka** or **Baloo 2** (friendly, Google Fonts, OFL); UI: **Plus Jakarta Sans** (OFL). Minimum 24sp on TV.
- **Focus:** lime 3dp ring + 1.06× scale + soft glow; never colour-only (also shape/scale) for accessibility.
- **Motion:** springy but short (≤ 400 ms), respects reduced-motion (Amazon's accessibility blueprint).
- **Safe zones:** 5% overscan margins (48dp at 960×540dp).

---

## 7. TV screens, one by one

| # | Screen | Contents | D-pad |
|---|---|---|---|
| S1 | **Home: Family World** | Isometric home map; room pins with quest badges; avatars; family goal ring ("72/100 ⭐ to Movie Night"); today's quests rail | Pins ↔, rail ↕ |
| S2 | **Quest card** | Big illustrated card, steps, star value, "Start on your phone" QR | Start / Back |
| S3 | **Room Scan, live** | Photo full-screen with animated outlines; checklist; "crew at work" mini-panel (Scout → Coach → Pip) | Items ↕ |
| S4 | **Proof** | After-photo; ticks animate one by one; leftover items glow; Pip line | Retake / Done |
| S5 | **Reward Reveal** | Stars fly to world; earned chest opens; new piece placed on the map | OK |
| S6 | **Quest Passport** | Cream paper book; illustrated stamps; streaks; Day Card | Pages ↔ |
| S7 | **Movie Night** | Lighthouse lights up; kid's pick; launches the title via deep link | Choose |
| S8 | **Rosetta Recap** | 60-s episode (Media3 playlist: photos with motion, Pip narration, stamp reveals) | Play/Pause |
| S9 | **Pairing** | QR + 4-letter code to join phones/tablets | – |

**Phone/Fire tablet web (kid):** Start quest → camera → before → after. Big buttons, avatar on top.
**Phone web (parent):** Mission Control: post quest, approve unsure items, weekly insight, agent activity.

---

## 8. The AI: a crew of agents

| Agent | Job | Input → Output |
|---|---|---|
| **Scout** | Find what needs doing in the before photo | Image → items with labels, boxes, difficulty |
| **Coach** | Turn items into kid-sized micro-steps, adjusted by age & history | Items + child profile → ordered steps, star values |
| **Judge** | Verify the after photo item by item; same-room & no-change checks | Before + after + items → per-item status + confidence |
| **Pip (Host)** | Write a short, kind, specific line; speak it | Result → line → neural voice |
| **Producer** | Assemble the nightly Recap (sequence, captions, narration) | Day's events → episode playlist |
| **Parent Co-pilot** | Voice/text → structured recurring quest; weekly insight; difficulty adaptation | Parent request + history → quest / insight |
| **Router** | Confidence routing: auto-approve vs ask parent | Judge confidence → decision |

- **Orchestration:** Strands Agents SDK on Amazon Bedrock **AgentCore** (Runtime + **Memory** for per-child patterns). *Pending your credit instructions.*
- **Models:** Amazon **Nova Pro** (multimodal vision/reasoning; works on our account today). Claude on Bedrock is optional once the billing block is lifted. Guardrails for kid-safe text.
- **Voice:** Amazon Polly neural (Pip).
- **Structured outputs:** forced tool schemas (pattern proven in our feasibility test 4).
- Every agent's output is visible in Mission Control, so judges *see* the multi-agent system working.

### Scan contract (agent → TV)
```json
{
  "quest_id": "q_room_rescue_arjun_1021",
  "photo_url": "https://…/before.jpg",
  "items": [
    {"id": "socks",  "label": "3 socks by the bed",  "box": [0.12,0.61,0.28,0.80], "stars": 1, "status": "todo"},
    {"id": "jacket", "label": "jacket on the chair", "box": [0.66,0.30,0.82,0.58], "stars": 1, "status": "todo"}
  ],
  "steps": ["Socks into the basket", "Toys into the bin", "Jacket on the hook", "Straighten the bed", "Books on the shelf"],
  "host_line": "Five things between you and a lighthouse, Arjun. Go!",
  "confidence": 0.86
}
```

---

## 9. Fire TV architecture

```
 KID: PHONE / FIRE TABLET (web, HTTPS)     CLOUD (AWS; on hold)                              FIRE TV (native Kotlin app)
 ┌──────────────────────────┐ photo   ┌──────────────────────────────────────────────┐ push ┌───────────────────────────────┐
 │ Start · before · after   │ ──────► │ API Gateway REST (presigned upload)          │ ───► │ Compose for TV + tv-material   │
 └──────────────────────────┘         │ API Gateway WebSocket (live TV updates)      │      │ S1 World · S3 Scan · S4 Proof  │
 PARENT: PHONE (web)                  │ Agent crew (Strands on AgentCore + Memory)   │      │ S5 Reveal · S6 Passport        │
 ┌──────────────────────────┐ quests  │ Bedrock Nova Pro · Guardrails · Polly        │      │ S7 Movie Night (deep links)    │
 │ Mission Control          │ ──────► │ Transcribe (parent voice) · Rekognition blur │      │ S8 Recap (Media3)              │
 └──────────────────────────┘ ◄────── │ DynamoDB · S3 (24-h auto-delete) · SNS       │      │ Pip voice playback             │
                                      │ (stretch) Ring API: package events           │      └───────────────────────────────┘
                                      └──────────────────────────────────────────────┘
 IaC: AWS CDK · Dev tooling: Claude Code + Amazon Devices Builder Tools MCP
```

- **Fire OS native:** Kotlin, Jetpack Compose for TV, Media3, minSdk 25; tested on the Android TV API 30 emulator (Fire OS 8 base); FAQ-approved.
- **Already built and verified:** app shell + D-pad focus (test 1), Media3 multi-track player + Compose-drawn overlays (test 2).
- **Offline demo mode:** a bundled "demo household" with pre-computed scan results, so the app and video work even without cloud. Also judge-friendly for testing (rules: the project must be testable free of charge until judging ends).
- **Platform limits, stated honestly:** no system-wide app locking (TV-time tokens are honoured, not enforced; Amazon Kids remains the lock); no camera on most Fire TVs (the camera is the family's phone/tablet). Both become **feature requests**.

---

## 10. Visual assets: Google Flow + licensed media

### Goal
A premium, **designed** look (stylized 3D characters, isometric world, illustrated stamps) that reads as *crafted*, not "AI slop".
**Honesty rule:** AI-generated assets are fine and will be disclosed in the README credits. **Product footage in the demo is always the real app running.**
Any staged/AI B-roll (e.g. the opening "nightly fight") is labelled *"dramatization"* on screen.

### Tools
| Tool | Use | Notes |
|---|---|---|
| **Google Flow** (Veo for video, Imagen for stills) | Character turnarounds, isometric world tiles, stamp illustrations, the opening B-roll | Run under **your** Google account (age 18+, supported country). **Free tier ≈ 50 credits/day; paid tiers higher.** Reports say a **visible watermark** may apply in India except on Ultra plans, plus an invisible SynthID watermark on all outputs ([MagicHour](https://magichour.ai/blog/google-flow), [veo3ai](https://www.veo3ai.io/blog/is-google-flow-free)). Confirm commercial-use terms in Google's Generative AI terms before publishing. **I can't operate your Flow account; I'll write the prompts and you run them** (or you sign in inside the in-app browser and I drive it with your OK) |
| Amazon Nova Canvas | Alternative stills generator on AWS | Only once credits are cleared |
| Figma / hand edits | Clean-up, consistent palette, text (never let AI render text) | |

### "Natural, not AI-looking" rules
1. **Stylized over photoreal:** soft-3D "toy" characters and an isometric diorama world look intentional; photoreal kids look uncanny.
2. **One style bible:** a fixed prompt prefix, palette, and lighting for every asset; reuse the first approved character as the reference image.
3. **Character sheets first** (front/¾/side, 4 expressions) → consistency across screens.
4. **No text in generated images**; all text is rendered by the app in our fonts.
5. **Human curation:** generate 4–8, pick 1, retouch.
6. **Motion from the app, not the generator:** Compose animations (parallax, springs) over still layers feel native and run smoothly on Fire TV.

### Starter prompts (Flow / Imagen)
- **Pip (host):** *"Soft 3D toy-style character, a small round fox-like mascot with big friendly eyes and a tiny explorer scarf, matte vinyl material, studio lighting, warm rim light, plain cream background, front view, cheerful expression, high detail, no text."* (then ¾, side, and 4 expressions)
- **Family World tile:** *"Isometric 3D diorama of a cozy two-storey family house on a small floating island, cutaway walls showing bedroom, kitchen and balcony, soft clay-like materials, warm evening light, gentle pastel palette with lime and orange accents, tilt-shift, no people, no text."*
- **World pieces:** same prefix + *"a small wooden lighthouse"* / *"a treehouse"* / *"a vegetable garden"*, on transparent or plain background for compositing.
- **Passport stamp:** *"Vintage travel-stamp illustration, risograph texture, two-colour (orange and navy) on cream paper, icon of a sock basket, circular badge shape, no text."*
- **Opening B-roll (dramatization):** *"Warm cinematic shot of a living room at dusk, a TV glowing, a parent and a child mid-discussion near a messy bedroom doorway, naturalistic lighting, 35mm, shallow depth of field."* (no brand logos; labelled dramatization)

### Licensed real-world media (use only these)
| Source | OK? | Rules |
|---|---|---|
| **Our own photos/video** (our room, our hands, adult actors) | ✅ Best | Written consent from anyone shown |
| **Pexels / Pixabay** | ✅ | Free incl. commercial, attribution optional (we credit anyway); no recognizable-person endorsement; no logos ([Pixabay licence](https://pixabay.com/en/service/license/)) |
| **Wikimedia Commons** (CC0 / CC-BY / CC-BY-SA) | ✅ | Credit per licence; keep the licence URL |
| **NASA / US-Gov public domain** | ✅ | No logos, no implied endorsement |
| **YouTube: only videos marked "Creative Commons – CC BY"** | ✅ with credit | Standard-licence YouTube videos are **not** reusable, even with credit |
| **Google Images search results** | ❌ | Copyrighted by default; only if the specific source grants a licence |
| Commercial music, brand logos, Fire TV/Amazon trademarks in assets | ❌ | Hackathon rule: no third-party trademarks or copyrighted music without permission |

Everything used gets a line in **`CREDITS.md`** (asset → source URL → licence → where used). The hackathon FAQ allows *"properly-attributed, brief supporting footage"* but asks entrants to **check with organizers** on specifics, so we'll keep third-party footage minimal.

---

## 11. Privacy, child safety, ethics

- **No kid accounts.** Parent-managed household; kids are a first name + avatar.
- **Photos:** faces auto-blurred before analysis/display; stored with a **24-hour auto-delete**; kept for the Recap only if a parent opts in.
- **Coach, not cop:** language is encouraging; no shaming; disputes go to the parent, never to a punishment.
- **Co-op by default** (IFIP research warns gamification can hurt family interaction); sibling comparison off by default.
- **No gambling mechanics:** reward reveals are deterministic and earned; no paid randomness.
- **Kid-safe generation:** Guardrails on all host text; no open-ended chat with kids.
- **COPPA-aware:** minimal data, parental consent, no ads, no third-party sharing.
- **AI disclosure:** generated art and the AI host are disclosed in-app and in the README.

---

## 12. Hackathon compliance: every requirement

_Status as of 2026-10-21 evening (IST). ✅ done · 🟡 ready, needs your action · ⬜ not done._

### Eligibility & timing
| Requirement | Status |
|---|---|
| Registered on Devpost, eligible country | ⬜ **you**: confirm registration |
| Submit by **Fri Oct 23, 12:00 PM PT** (Sat 00:30 IST) | ⬜ target Fri morning IST |
| Created during the window | ✅ repo history starts 2026-10-18 |
| Original work, assets owned/licensed | ✅ all art generated by us in Google Flow ([CREDITS.md](CREDITS.md)); no stock or third-party media |

### Fire TV track
| Requirement | Status |
|---|---|
| Demo-ready app on Fire OS | ✅ native Kotlin / Compose for TV app, full flow verified on the emulator |
| Video shows it on a Fire TV device or emulator | ✅ raw emulator recording `docs/video/rosetta_tv_flow_raw.mp4` |
| Priority categories | ✅ family entertainment · computer vision · AI-enhanced viewing · multi-modal (phone camera + D-pad + voice) |

### Submission package
| Item | Status |
|---|---|
| Text description | ✅ [SUBMISSION.md](SUBMISSION.md) |
| GitHub repo with source, assets, run instructions | 🟡 repo complete locally; **you**: create a GitHub repo, push, make public (MIT shows in About) |
| Demo video < 3 min, public YouTube/Vimeo, English | 🟡 `docs/video/rosetta_demo_draft.mp4` (1:40, captioned). **You**: upload; optionally add narration |
| No third-party trademarks or music | ✅ none used (silent track) |
| Product feedback for every tool | ✅ [FEEDBACK.md](FEEDBACK.md) |
| Feature requests (optional) | ✅ in FEEDBACK.md |
| Friction log (optional, up to +10%) | ✅ [FRICTION_LOG.md](FRICTION_LOG.md), 11 entries |
| Testable free until judging ends | ✅ demo household runs offline with a remote |

### AWS Builder
| Requirement | Status |
|---|---|
| AWS services with documented integrations | ✅ code + README table (Bedrock/Nova, Strands, Lambda, S3, DynamoDB, API GW, CloudFront, CDK) |
| Working in the cloud | 🟡 CDK synth passes, offline tests pass; **not deployed** (AWS on hold per your instruction). Deploy = 4 commands in README |
| Described in the feedback field | ✅ |

### Open Source
| Requirement | Status |
|---|---|
| New OSS project with a licence | ✅ `tv-app/vision-overlay` (MIT), used by the app |
| Contribution URL, repo URL, username, description | 🟡 text ready in SUBMISSION.md; URLs after you push |

## 13. Judging criteria → how we score

| Criterion (25% each) | Rosetta's case |
|---|---|
| **Tech implementation** | Native Fire OS stack; multimodal vision with item-level grounding; agent crew with tools, memory and confidence routing; real-time phone↔TV sync; offline demo mode |
| **Design** | Immersive isometric world; 10-foot UI; D-pad-perfect focus; one-tap kid flow; parent autopilot; accessibility (contrast, reduced motion, VoiceView labels) |
| **Potential impact** | 55% of families argue about screen time; replaces nagging with a fair, visible system; ADHD-friendly steps; Fire TV Appstore-ready; Amazon Kids synergy |
| **Quality of idea** | Moves a phone-only category to the shared family screen and goes beyond it (planning vision, co-op world, AI recap show, Ring quests). We're **honest about prior art**, which shows judges we understand the ecosystem |
| **Bonus (≤ 10%)** | Detailed friction log, kept while building |

---

## 14. MVP, stretch, cut list

**MVP (must ship):**
S1 World (static isometric art + pins + goal ring) · S2 Quest card · S3 Room Scan · S4 Proof · S5 Reward Reveal · Pip voice lines ·
kid web capture page · pairing · **demo household mode** (pre-computed scans) · parent page: post quest + approve unsure item.
**Cloud path (Scout/Judge/Pip live) as soon as AWS credits are cleared.**

**Stretch (in order):** S6 Passport → S8 Recap → S7 Movie Night deep link → Coach adaptation + Memory → Mission Control agent graph → face blur → Ring package quest → homework quests.

**Cut first:** Ring, homework, weekly insight.

---

## 15. Plan to the deadline

| When (IST) | Deliverable |
|---|---|
| **Wed 21, evening** | Style bible + Flow prompts to you; TV app: design tokens, S1 World layout, S3/S4 overlay component (= OSS project) with demo data |
| **Thu 22, morning** | S2, S5 + Pip; kid capture web page; pairing; demo household mode end-to-end on emulator |
| **Thu 22, afternoon** | Cloud agents (**if credits cleared**): Scout/Judge/Pip on Nova + Polly; WebSocket live updates. Else: polish + stretch S6/S8 |
| **Thu 22, night** | Record app footage; edit the 3-minute film; README, CREDITS, FEEDBACK, feature requests, OPEN_SOURCE |
| **Fri 23, morning** | Devpost submission, video upload, repo public + licence check, test every link. **Submit by ~noon IST** (deadline Sat 00:30 IST) |

---

## 16. 3-minute demo film

| Time | Shot | Purpose |
|---|---|---|
| 0:00–0:12 | *Dramatization:* "Clean your room." "I did!" Stat on screen: **55% of families argue about screen time** | Impact hook |
| 0:12–0:30 | Real app: TV wakes into the **Family World**; Pip: "Two quests left before movie night!" | Design, immersion |
| 0:30–1:20 | **Room Scan → Proof:** snap on tablet → TV outlines 5 items → tidy → 4/5 + "the jacket!" → 5/5 | CV, AI does work |
| 1:20–1:40 | **Reward Reveal:** stars fly, lighthouse piece builds; Passport stamp | Delight, retention |
| 1:40–2:05 | **Parent Mission Control:** voice-posted quest appears on TV; one unsure item approved in one tap; agent crew visible | Agentic, parent-light |
| 2:05–2:30 | **Rosetta Recap:** 20-s excerpt of the nightly show | AI-enhanced viewing |
| 2:30–3:00 | Architecture (Fire OS native + agent crew on AWS), privacy (24-h delete, face blur, co-op), OSS component, feature requests | Tech, AWS Builder, OSS |

No copyrighted music. Narration by us (or Polly), English. Captions on.

---

## 17. Risks

| Risk | Mitigation |
|---|---|
| **AWS credits on hold** → no live AI yet | Build everything with demo-household data + the same JSON contracts; switch to live agents the moment credits are cleared |
| Nova Pro detection/box quality on real rooms | Test first when allowed; fall back to item list without boxes, or Rekognition boxes + Nova reasoning |
| Flow visible watermark (India) / terms | Verify on your account; Nova Canvas or hand-made/licensed art as fallback |
| Asset style inconsistency | Style bible + reference image + curation |
| Prior art (Chorémon etc.) | Lead with what's new (TV, co-op world, item-level proof, autopilot, recap, Ring); cite prior art honestly |
| Kids in footage | Adult actors / hands only / blurred, or AI dramatization labelled as such |
| Low RAM / disk on laptop | Known mitigations (AVD on E:, small heaps) |
| Employer IP | Personal accounts/time; check policy before going public |
| **~1.5 days** | MVP + demo mode first; strict cut list |

---

## 18. What I need from you

1. **AWS / AI credits:** how and when to use them (everything cloud-side waits for this).
2. **Google Flow:** run the prompts in §10 on your account (or sign in inside the in-app browser and approve me driving it). Tell me if you see a visible watermark.
3. **Photos:** 3–4 messy → tidy room pairs (same angle), ideally adult hands only.
4. **Choices:** Pip's species/personality · world theme (island house / space station / jungle) · tagline.
5. **Confirm:** Devpost registration done; employer policy checked; OK to make the repo **public (MIT)** at submission.
