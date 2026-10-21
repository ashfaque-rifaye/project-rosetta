# Quest Board: the TV that turns real-world effort into a family adventure

_Spec v1 · 2026-10-21 · **Chosen idea** · Fire TV track + AWS Builder + Open Source (Ring "package quest" as stretch)_
_Deadline: **Fri 2026-10-23, 12:00 PM PT** (Sat 00:30 IST)._
_Origin: the user's Playbook v4, slide 27 ("Quest Board: kids earn TV time by doing real-world quests"). Supersedes GLANCE.md, SHOWRUNNER.md, IDEA.md._

---

## 0. TL;DR

Every evening the same fight happens in front of the same screen: *"Clean your room first." "I did!" "That's not clean."*
**Quest Board puts the TV on the family's side.** The Fire TV home becomes a living, shared quest world.

- An AI **Quest Master** *looks at a kid's room* and turns "clean your room" into **5 concrete, visible micro-quests**.
- It checks the *after* photo item by item, gives **partial credit and a specific nudge** ("almost: the jacket on the chair!"), and awards stars.
- Stars **grow the family's world on the TV**, unlock TV time and the Friday movie pick, and power a cheeky host character who celebrates
  each kid by name. At night the TV plays a **60-second "Quest Recap" show** of the day's wins.

It's not parental control and not a chat bot. The AI does the parent's least favourite job (inspecting, nagging, judging fairly),
and the TV turns effort into something the whole family can see and enjoy.

---

## 1. Prior-art analysis (checked 2026-10-21)

| Product | Platform | What it does | Gap Quest Board fills |
|---|---|---|---|
| **ChoreSplit** ([site](https://choresplit.com/)) | Phone | Kids photo-prove chores; **AI verifies**; $9.99/mo | Pass/fail check only, phone only, no shared world, no planning |
| **Chore Lock** ([site](https://getchorelock.com/)) | iPhone (Apple Screen Time) | Locks apps until chores are done; **AI photo check** on paid plans | Phone-only enforcement; no TV; no coaching |
| **Piggy / Chirp / Earn Screen Time** ([Piggy](https://piggyscreentime.app/), [Chirp](https://apps.apple.com/us/app/chirp-kids-earn-screen-time/id6760570585)) | iPhone | Photo proof → **parent approves** → minutes | Manual review burden on parents |
| **Joon** ([App Store](https://apps.apple.com/app/apple-store/id1482225056), [review](https://www.choosingtherapy.com/joon-app-review/)) | Phone/tablet | Quests + virtual pet (Doter); parent approves each task; ADHD focus; $12.99/mo | Reviewers call approval "burdensome"; solo, phone-only; no vision |
| **ScreenCoach** ([coverage](https://noosatoday.com.au/news/13-10-2020/kids-screen-time-coach)) | Phone/PC + **hardware "Box" for TVs** | Earn time with chores; multi-device limits | TV support needs extra hardware; no AI, no world |
| **Habitica** | Phone/web | RPG for habits | Adult-oriented, self-reported |
| **Amazon Kids Parent Dashboard** ([listing](https://apps.appfollow.io/ios/amazon-kids-parent-dashboard/6471528064?country=us)) | Fire TV/tablets | Time limits, bedtime, **"Learn First"** (learning before entertainment), pause | Restricts only; **no "earn time" mechanic**; no third-party API |
| Chore-chart apps (Chore Pad, MyChoreBoard, Chorsee…) ([search](https://apps.apple.com/app/id384854237)) | iPhone/iPad | Charts, stars, rewards | **None run on Fire TV / Android TV** |

### Verdict
- "Earn screen time for chores" and "AI photo verification" **already exist on phones**. We must not pitch those as the novelty.
- **Not found anywhere:**
  1. a **TV-native** quest experience on the shared family screen;
  2. AI vision that **plans** the chore (itemized, visual micro-quests with boxes on the TV) instead of judging pass/fail;
  3. a **co-op family world** that grows on the TV from real-world effort;
  4. an **AI-produced nightly recap show** of the kids' wins;
  5. **Amazon ecosystem** tie-ins (Fire TV + Fire tablet camera + Ring + Amazon Kids).
- _Caveat: the Devpost gallery is hidden until judging, and our searches can't cover every app store listing._

---

## 2. The problem

1. **Screen-time battles are daily and exhausting.** Parents nag, kids bargain, and "is it done?" turns into an argument about standards.
2. **"Clean your room" is a vague instruction.** For a 7-year-old (and especially kids with ADHD), a big fuzzy task is paralysing. The fix is breaking it into concrete steps, which parents rarely have the energy to do every night.
3. **Existing tools only restrict or require constant parent review.** Joon reviewers call approving every task burdensome; Amazon Kids can only *limit* time.
4. **The TV is the prize, but it's never part of the solution.** All chore apps live on phones; the TV just gets switched off.

**Users:** parents of kids 5–12 (primary), siblings, and kids with ADHD who benefit from concrete steps and immediate visible rewards.

---

## 3. Product pillars

### Pillar 1: **Room Scan: AI that plans the chore** ⭐ hero feature
1. The kid taps **"Start quest"** on a phone or Fire tablet and snaps a **before** photo.
2. The **Quest Master** (Bedrock multimodal vision) finds what needs doing and returns **itemized micro-quests with bounding boxes**:
   > 🧦 3 socks by the bed · 🧸 toys on the rug · 🧥 jacket on the chair · 🛏️ unmade bed · 📚 books on the floor
3. The **TV shows the photo big, with each item highlighted** and a checklist. The whole family can see the plan.
4. The kid snaps an **after** photo. The AI checks **item by item**, ticks off what's done, shows what's left on the TV ("so close, the jacket!"), and gives **partial stars**.
5. A **"looks done?" sanity rule:** if before and after look identical, or the after photo isn't the same room, the agent asks for a retake. That's lightweight anti-cheat without surveillance.

### Pillar 2: **The Family World: immersive TV home**
- The TV home is a living, illustrated **world** (e.g. *Skyharbor*, a floating island village). Every quest **builds something**: a bridge, a lighthouse, a garden.
- **Co-op, not competition:** siblings build the *same* world. A shared goal ("Light the lighthouse: 100 family stars") unlocks **Family Movie Night**.
- The world animates when the TV wakes up: clouds, lights, the characters the kids have earned.
- Each kid has an avatar and a corner of the island that shows their streaks.

### Pillar 3: **The Host: a character with personality**
- A cheeky host character ("Pip", working name) **speaks on the TV** (Polly neural voice) and reacts to what the vision *actually saw*:
  > "Arjun! That bed is so flat a pancake would be jealous. Two stars!"
- Grounded in the scan result, kid-safe (Bedrock Guardrails), never mean, never shaming.

### Pillar 4: **Quest Recap: a nightly 60-second show**
- At wind-down time the TV plays an **AI-produced recap**: before → after reveals, stars counted up, the world piece that got built, and a host voice-over.
- Turns effort into a moment the whole family watches together. This is "AI-enhanced viewing" of the family's own day.
- Photos are used only with the parent's opt-in and are **auto-deleted** after the recap (see Privacy).

### Pillar 5: **Rewards that matter**
- **TV time tokens:** shown as a timer on the TV. *Honest limit:* a third-party app can't lock other apps on Fire TV. Tokens are honoured by parents, with the Amazon Kids Parent Dashboard as the lock. We'll file this as a feature request: **"Amazon Kids Earn-Time API"**.
- **Enforceable inside our app:** Family Movie Night pick (the TV launches the chosen title), new world pieces, host costumes, avatar items.
- **Custom real-world rewards** set by parents (park trip, choosing dinner).

### Pillar 6: **Parent co-pilot (agentic)**
- **Post quests by voice or text:** "Meera should feed the cat every evening." The agent creates a recurring quest with age-appropriate steps and a star value.
- **Disputes in one tap:** parents only see the cases where the AI isn't sure (low confidence). Everything else auto-approves. This removes Joon's approval burden.
- **Weekly insight:** "Meera starts homework quests late but finishes them; try a 10-minute starter quest at 5 pm." The agent **adapts difficulty and step size** per child. This is the judges' "adapts to household patterns" example.

### Pillar 7 (stretch): **Front-door quests with Ring**
- Ring detects a **package delivered** → an auto-quest "Delivery Detective: bring the package inside" appears on the TV → it's verified when the package is gone.
- Hits Ring's "package/delivery management" creative example and Amazon's "living room + front door + cloud" tagline. **Only if Ring API access works.**

### Pillar 8 (stretch): **Homework quests**
- A photo of a worksheet → the AI marks it and gives **hints, not answers** ("check question 3 again: what's 7×8?") → quest: "fix 2 mistakes".

---

## 4. Why it's advanced AI that does work

| Capability | Where |
|---|---|
| **Multimodal vision with grounding** (objects + bounding boxes) | Room Scan |
| **Before/after visual comparison**, item-level reasoning, partial credit | Verify |
| **Task decomposition** (vague chore → concrete micro-steps), the pattern behind award-winning **VITE VERE** | Room Scan, Parent co-pilot |
| **Confidence-aware autonomy** (auto-approve vs escalate to parent) | Disputes |
| **Personalized adaptation** from history (difficulty, step size, timing) | Weekly insight |
| **Grounded character generation** (lines based on what was seen) + Guardrails | Host |
| **Automated video production** (recap from photos, narration, music-free) | Quest Recap |
| **Agent orchestration** (Quest Master agent with tools, on AgentCore, with Memory) | Whole backend |

---

## 5. User journeys (demo-ready)

**A. Evening room quest (hero, ~60 s in the demo)**
TV: "Arjun, today's quest: Room Rescue ⭐⭐⭐⭐⭐" → Arjun snaps the room on the tablet → TV shows the room with 5 glowing boxes →
he tidies → snaps again → TV ticks 4 of 5, highlights the jacket → Pip: "So close!" → final snap → 5/5 → stars fly into the world → a lighthouse piece builds.

**B. Parent posts a quest (~15 s)**
Parent says on the phone: "Meera, water the plants on the balcony." → the agent makes a quest with steps + 2 stars → it appears on the TV board.

**C. Movie night unlock (~20 s)**
The family hits 100 stars → the lighthouse lights up → "Movie Night unlocked: Meera's pick" → she chooses with the D-pad → the TV launches it.

**D. Quest Recap (~25 s)**
The TV plays a 60-second show of today's before/afters, narrated by Pip.

---

## 6. Fire TV feasibility

| Piece | Implementation | Status |
|---|---|---|
| TV app | Native Kotlin + Jetpack Compose for TV (existing app shell, D-pad focus) | ✅ Built (tests 1–2) |
| Room Scan view | Compose image + Canvas overlay drawing boxes from normalized coordinates | Straightforward |
| World | 2D layered illustrated scene in Compose (pre-made art + unlockable pieces, simple animations) | To build. Art pre-generated (Nova Canvas or licensed assets) |
| Host voice | Polly neural voice audio played in the app | ✅ Polly verified |
| Recap show | Media3 playlist: photos with Ken Burns + narration audio (existing player) | Reuse player |
| Launch movie | Android intent / deep link to installed apps | Standard Android |
| Phone/tablet camera page | HTTPS web page (camera needs a secure context) on S3 + CloudFront; works on Fire tablets and phones | To build |
| Phone ↔ TV | API Gateway WebSocket (or AWS IoT Core) | To build (test first) |
| Vision | Bedrock **Amazon Nova Pro** (multimodal) | ✅ Account access verified; detection quality to test |

**Platform limits, stated honestly:** no system-wide app locking (rewards are honoured, not enforced); no camera on most Fire TVs
(the camera is the kid's phone or tablet). Both become product feedback for Amazon.

---

## 7. Architecture

```
 KID PHONE / FIRE TABLET (web, HTTPS)      AWS (us-east-1)                                 FIRE TV (native Kotlin app)
 ┌───────────────────────────┐  photo   ┌───────────────────────────────────────────┐  push  ┌──────────────────────────────┐
 │ Start quest · snap before │ ───────► │ API Gateway REST (presigned S3 upload)     │ ─────► │ Quest Board (D-pad)           │
 │ snap after · see checklist│          │ API Gateway WebSocket (live TV updates)    │        │ Room Scan view (boxes)        │
 └───────────────────────────┘          │                                           │        │ Family World (immersive)      │
 PARENT PHONE (web)                     │ Quest Master agent (Strands, AgentCore)    │        │ Host "Pip" voice (Polly)      │
 ┌───────────────────────────┐ quests   │  tools: scan_room · verify_after ·         │        │ Movie Night launcher          │
 │ Post quest (voice/text)   │ ───────► │   plan_quest · award_stars · build_world · │        │ Quest Recap player (Media3)   │
 │ Resolve disputes · insight│ ◄─────── │   escalate_to_parent · compose_recap ·     │        └──────────────────────────────┘
 └───────────────────────────┘          │   adapt_difficulty                         │
                                        │ Bedrock: Nova Pro (vision/reasoning),       │
                                        │   Nova Canvas (world art, optional),        │
                                        │   Guardrails (kid-safe host lines)          │
                                        │ Polly · Transcribe (parent voice) ·         │
                                        │ Rekognition (face blur) · SNS               │
                                        │ DynamoDB (family, quests, stars, world)     │
                                        │ S3 (photos: lifecycle auto-delete ≤ 24 h)   │
                                        │ AgentCore Memory (per-child patterns)       │
                                        │ (stretch) Ring API: package events          │
                                        └───────────────────────────────────────────┘
 IaC: AWS CDK · Dev tooling: Claude Code + Amazon Devices Builder Tools MCP
```

### Room Scan contract (agent → TV)
```json
{
  "quest_id": "q_room_rescue_arjun_1021",
  "photo": "https://…/before.jpg",
  "items": [
    {"id": "socks", "label": "3 socks by the bed", "box": [0.12, 0.61, 0.28, 0.80], "stars": 1, "status": "todo"},
    {"id": "jacket", "label": "jacket on the chair", "box": [0.66, 0.30, 0.82, 0.58], "stars": 1, "status": "todo"}
  ],
  "host_line": "Five things between you and a lighthouse, Arjun. Go!",
  "confidence": 0.86
}
```

---

## 8. Privacy and child safety (by design)

- **Kids don't have accounts.** The household is parent-managed. Kids are a first name + avatar only.
- **Photos:** auto-blur faces (Rekognition) before analysis or display; stored in S3 with a **lifecycle rule that deletes them within 24 h**; only kept longer if a parent opts in for the recap.
- **No surveillance framing:** the AI is a coach ("let's find what's left"), never a judge of the child. Disputes go to the parent, not punishment.
- **Kid-safe generation:** Bedrock Guardrails on all host lines; no open chat with kids.
- **Data stays in the household's backend**; no ads, no third-party sharing. COPPA-aware design (parent consent, minimal data).

---

## 9. Hackathon fit

| Criterion | Quest Board |
|---|---|
| Runs on Fire OS / Vega | Native Fire OS app ✅ (emulator demo, FAQ-approved) |
| Priority categories | **Family entertainment**, **computer vision**, **AI-enhanced viewing** (world + recap show), **multi-modal UX** (camera + voice + D-pad + spoken host) |
| Judges' "creative" examples | "AI-enhanced viewing that **adapts to household patterns**" ✅ · "multi-modal UX… voice + D-pad + visual in a single flow" ✅ · Ring: "package/delivery management", "event-based triggers" (stretch) ✅ |
| Tech implementation | Native TV stack + multimodal grounding + agent with tools + real-time sync |
| Design | 10-foot immersive world; 10-second-understandable loop; kid-friendly, parent-light |
| Potential impact | Daily family pain; reduces parent review burden (Joon's pain point); ADHD-friendly step breakdown; Fire TV Appstore-ready, Amazon Kids synergy |
| Quality of idea | Moves a phone-only category to the shared screen and goes further: planning vision, co-op world, AI recap show |
| **AWS Builder** | Strands agent on **AgentCore** (Runtime + Memory), Bedrock (Nova Pro, Nova Canvas, Guardrails), Polly, Transcribe, Rekognition, API Gateway (REST + WebSocket), DynamoDB, S3 lifecycle, SNS, CDK |
| **Open Source** | `compose-tv-vision-overlay`: draw AI detection boxes + checklists on Compose for TV; or `couch-link` QR/WebSocket pairing kit. MIT |
| Product feedback / feature requests | "Amazon Kids Earn-Time API"; sanctioned launcher/overlay for family apps; Vega SDK on Windows; Bedrock model-access clarity |
| Friction log | Continue `FRICTION_LOG.md` (9 entries so far) |
| Video rules | Our own room footage; kids only with parental consent (or adult actors / faces blurred); no music without rights |

---

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| **Nova Pro box/detection quality** on messy rooms | Test first (G1). If boxes are weak: item list without boxes, or Rekognition labels for boxes + Nova for reasoning |
| Before/after comparison errors | Item-level checks with confidence; low confidence escalates to parent; retake prompt |
| Cheating (photo of another room, old photo) | Same-room check (visual similarity), timestamp, retake request; framed as coaching, not policing |
| Rewards can't be enforced on other apps | Be explicit; enforceable in-app rewards; feature request to Amazon |
| Phone/tablet camera needs HTTPS | Host on CloudFront from day one |
| World art takes time | Pre-made simple layered art; Nova Canvas only if access works and time allows |
| Claude blocked on Bedrock | Nova Pro is multimodal and works on our account |
| Kids in demo video | Use our own room; adult plays the kid, or blurred faces; written consent |
| Employer IP | Build on personal time, personal laptop, personal accounts; the playbook currently lives on a work SharePoint, so **check the outside-work policy** |
| **~2 days left** | Strict MVP + cut list; go/no-go tests tonight |

---

## 11. MVP vs stretch

**MVP (must ship):**
1. TV: Quest Board home (2 kids), quest detail, **Room Scan view with boxes + checklist**, star animation, simple Family World that unlocks pieces, host voice lines.
2. Kid web page (phone/Fire tablet): start quest → before photo → after photo.
3. Agent: `scan_room` (items + boxes), `verify_after` (item-level + partial credit + nudge), `award_stars`, `build_world`, host line (Nova + Polly).
4. Live TV updates via WebSocket.
5. Parent web page: post a quest (text), resolve a low-confidence dispute.

**Stretch (in order):** Movie Night unlock + app launch → Quest Recap show → parent voice quests (Transcribe) → weekly insight + adaptive difficulty (AgentCore Memory) → face blur → Ring package quest → homework quests.

---

## 12. Go/no-go tests (tonight)

| # | Test | Pass means |
|---|---|---|
| Q1 | Nova Pro on 3 real room photos → items + bounding boxes | ≥ 4 sensible items per photo; boxes roughly on target |
| Q2 | Nova Pro before/after comparison | Correctly marks done vs not-done items on a staged tidy-up |
| Q3 | Phone page (HTTPS) → upload → AWS → TV app updates live | < 2 s from snap to the TV showing the result (excluding model time) |
| Q4 | Host line + Polly voice on TV | Funny, kind, specific to the scan; plays on the emulator |

**Needed from you for Q1–Q2:** 3–4 phone photos of a real messy room (any room), then the same spot after tidying. Ours, not the kids'.

---

## 13. Plan

| When | Deliverable |
|---|---|
| **Wed 21 (tonight)** | Q1–Q2 (vision quality) + Q3 skeleton (HTTPS page + WebSocket) |
| Thu 22 | TV screens (Board, Room Scan, World, host), agent on Strands/AgentCore, parent page; CDK; README + AWS integrations; OSS component; **record demo Thu night** |
| Fri 23 (IST morning) | Devpost write-up, product feedback, friction log, feature requests; submit well before Sat 00:30 IST |

---

## 14. Three-minute demo script (draft)

| Time | On screen | Criterion |
|---|---|---|
| 0:00–0:15 | "Clean your room." "I did!" The nightly fight in front of the TV | Impact |
| 0:15–0:30 | The TV wakes into the Family World: today's quests, Pip says hi | Design, immersion |
| 0:30–1:30 | **Room Scan:** snap → TV shows 5 boxed items → tidy → 4/5 + "the jacket!" → 5/5 → stars → lighthouse builds | CV, AI does work |
| 1:30–1:50 | Parent posts "water the plants" by voice → appears on the TV; one low-confidence dispute resolved in one tap | Agentic, parent-light |
| 1:50–2:15 | Family goal reached → Movie Night unlocked → kid picks with D-pad | Family, multi-modal |
| 2:15–2:40 | Quest Recap: 30-second AI-produced show of today's wins | AI-enhanced viewing |
| 2:40–3:00 | Architecture (Strands + AgentCore + Nova + Polly), privacy (auto-delete, face blur), OSS, feature request to Amazon | Tech, AWS Builder |

---

## 15. What we reuse from earlier work

| Built | Becomes |
|---|---|
| Native Fire TV app shell + D-pad home | Quest Board home |
| Media3 player + Compose overlays | Quest Recap player, Room Scan overlays |
| Bedrock structured-output pattern (forced tool schema) | `scan_room` / `verify_after` JSON contracts |
| Polly verified | Host voice |
| Dev bucket, emulator on E:, low-memory build settings | Unchanged |
| Friction log | Continues |

---

## 16. Open decisions

1. **Name:** "Quest Board" (from your slide) or something more playful?
2. **World theme:** floating island village, space station, or jungle?
3. **Host:** Pip (working name), and which personality: cheeky, calm, or superhero?
4. **Test photos:** can you take 3–4 messy/tidy room pairs tonight?
5. **Ring stretch:** attempt only if the MVP is done Thursday midday?
