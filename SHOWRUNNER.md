# Showrunner: an AI that produces TV for the people watching

_Idea proposal · v1 · 2026-10-21 · Fire TV track + AWS Builder + Open Source mini challenges_
_Deadline: **Fri 2026-10-23, 12:00 PM PT** (Sat 00:30 IST). Status: **proposal, pending go/no-go test**._

> This replaces the "Rosetta family-story channel" idea in [IDEA.md](IDEA.md), which was dropped:
> it needed personal family videos we don't have, it raised privacy concerns, and it was a pipeline rather than an agent.
> The hackathon research in [RESEARCH.md](RESEARCH.md) and the feasibility results in [docs/FEASIBILITY.md](docs/FEASIBILITY.md) still apply.

---

## 1. One-line pitch

**You ask your Fire TV for a show, not a search result.** An agent researches real archival footage, watches it,
edits it, writes and voices a host narration at the viewers' level, adds subtitles in their languages, and plays
a finished episode natively on Fire TV.

> "Make us 15 minutes on how volcanoes create new islands, for Arjun (8) and Paati, with Tamil subtitles."

---

## 2. The problem

The TV is the biggest screen in the house and the most passive one.

1. **Finding something to watch is broken.**
   US viewers spend **~12 minutes** searching for something to watch (up from 10.5 in 2023), the global average is ~14 min,
   and **19% abandon** the session if the search fails. 46% say it's getting harder.
   ([Gracenote / Nielsen "State of Play" 2025](https://informitv.com/2025/11/05/it-is-becoming-harder-to-decide-what-to-watch/))
2. **Curiosity hits a dead end on the TV.**
   A child asks "how do volcanoes make new islands?" Great real footage exists (NASA alone holds thousands of
   public-domain videos *with captions*), but it is raw, long, scattered, English-only, and not made for an 8-year-old.
   Nobody turns it into a coherent 12-minute show that a family can watch together from the couch.

**Who it's for:** families with kids, curious adults, and multilingual households (subtitles per viewer).

---

## 3. What already exists (prior art, checked 2026-10-20)

| Product | What it does | Why Showrunner is different |
|---|---|---|
| **Alexa+ on Fire TV: "Jump to scene"** (Dec 2025) | Describe a movie moment and it jumps there on Prime Video. Built on Bedrock with Nova + Claude. ([TechCrunch](https://techcrunch.com/2025/12/03/amazon-fire-tvs-new-ai-feature-lets-you-jump-to-scenes-by-describing-them-to-alexa/), [About Amazon](https://www.aboutamazon.com/news/devices/fire-tv-alexa-plus-jump-to-the-scene)) | It *navigates* existing titles. Showrunner *produces a new episode* from many sources. We avoid "ask about this moment" features because Amazon already shipped them. |
| **Group "what to watch" apps** (WatchLitz, Chooza, Movie Club, PlotTwisted…) ([roundup](https://www.gmapswidget.com/?p=13264)) | Friends vote or swipe on phones | Phone voting over existing catalogues. Nothing is produced. |
| **Channel 1** ([Hollywood Reporter](https://www.hollywoodreporter.com/business/digital/ai-tv-news-channel-1-news-1235544580/)) | Personalized AI-anchored **news** repackaged from reporting | News only, with generated anchors. Not on-demand, not family/education, no per-viewer level or language. |
| **Fairground AI Creator TV on Roku** ([Time Out](https://www.timeout.com/usa/news/roku-just-launched-a-channel-devoted-entirely-to-ai-generated-content-082126), [PYMNTS](https://www.pymnts.com/news/artificial-intelligence/2026/ai-just-got-its-first-24-7-tv-channel/)) | 24/7 fully AI-*generated* video channel | Synthetic content. Showrunner uses **real footage** and grounds every claim in it. |
| **Spotify DJ** (music) | An AI host between songs, personalised | The closest *pattern*: "Spotify DJ for TV", but with real video and an editing agent. |

| ⚠️ **Gemini on Google TV: "Deep dives"** (CES Jan 2026, rolling out Mar 2026, US/Canada) ([Google blog](https://blog.google/products-and-platforms/platforms/google-tv/new-gemini-features-march-2026/), [TechCrunch](https://techcrunch.com/2026/03/24/google-tv-new-gemini-features-keep-fans-updated-on-sports-teams-deep-dives-visual-answers/), [Android Authority](https://www.androidauthority.com/google-tv-deep-dive-sports-briefs-3651541/)) | **Narrated, interactive visual overviews "simplified for the whole family"** on the TV, with guided follow-up questions; also narrated "sports briefs" | **Very close to Showrunner's core.** Differences (real archival footage, per-viewer level/language, steering memory) are incremental. Fire TV judges will know this feature. |
| ⚠️ **NotebookLM Video Overviews** (Jul 2025) ([Google Workspace](https://workspaceupdates.googleblog.com/2025/07/video-overviews-studio-panel-updates-notebooklm.html)) | Narrated explainer videos built from your sources, steerable by audience ("for high-schoolers"), 80+ languages | Same "AI produces a narrated explainer" pattern, on web, not TV |

> **Update 2026-10-21: prior-art verdict.** Showrunner as described above is essentially *Google TV's Gemini Deep Dives for Fire TV*.
> That hurts the "Quality of the Idea" criterion. The production engine is still useful, but the product needs a different angle.
> See the chat discussion / next revision.

**Original gap claim (now superseded):** no product we found has an agent that **produces an on-demand TV episode from real archival footage**,
tailored to the specific viewers (age, language) and grounded in the sources.
_Caveat: the Devpost project gallery is hidden until judging, so we can't rule out another entrant with a similar idea._

---

## 4. What Showrunner does (user flow)

1. **Ask:** by voice or text (remote keyboard, or phone in a later phase). Pick the viewers (first name, age band, language) and a length.
2. **Watch it being made:** the TV shows the agent working in real time: *"Searching NASA… found 14 clips → watching 6 → picked 5 moments → writing narration → fact-checking → voicing → subtitling in Tamil"*.
3. **Watch the episode:** it plays natively, with chapters, a host voice between real clips, per-viewer subtitles, and source credits at the end.
4. **Steer with the remote** during playback: **Go deeper · Skip · Explain that · More like this**. The agent uses these signals to plan the **next** episode.
5. **It becomes a channel:** over time a personal channel builds up, e.g. *"Arjun's Earth & Space"*. This is the judges' "adapts to household patterns" example.

**What it is not:** not a chatbot (the output is a finished show), not parental controls, not a recommender over existing catalogues.

---

## 5. Why it is "advanced AI that does work"

The agent does the work a small TV production team would do:

| Step | Human equivalent | AI capability used |
|---|---|---|
| Research | Researcher searching archives | Tool use: archive search APIs, query planning, relevance ranking |
| Watch | Producer screening footage | **Computer vision**: multimodal model looks at key frames / short video; reads captions |
| Edit | Editor choosing in/out points | Reasoning over transcript timings + visual content into an **edit decision list (EDL)** |
| Write | Scriptwriter | Narration bridging clips, adjusted to **age level** |
| Fact-check | Fact-checker | A **critic agent** verifies every narration claim against source captions; unsupported claims are removed (grounding, no hallucinated facts) |
| Voice | Narrator | Amazon Polly neural TTS |
| Translate | Subtitler | Per-viewer subtitle languages |
| Remember | Channel programmer | AgentCore Memory: what each viewer watched, skipped, asked to go deeper on |

---

## 6. Can Fire TV do it? Yes, with standard platform pieces

- **Native Fire OS app** (already built): Kotlin + Jetpack Compose for TV + Media3 ExoPlayer, running on the Android TV API 30 emulator (Fire OS 8 base). The FAQ says an emulator is acceptable.
- **Playback without re-rendering:** an episode is an EDL. Media3 plays a **playlist of `MediaItem`s with `ClippingConfiguration`** (in/out points), streamed straight from the archive's MP4 URLs. Narration segments are Polly audio over a title card or a still frame. Nothing is re-encoded in the cloud.
- **Subtitles:** our player already side-loads multiple WebVTT tracks, renders them in Compose and switches language with the D-pad (feasibility test 2 ✅).
- **No platform limits hit:** everything plays inside our own app. No DRM'd third-party streams, no overlays, no Alexa+ gated tooling.
- **Privacy:** viewers are a first name, age band and language, stored locally on the TV. No camera, no microphone recording stored, no personal media, no accounts.

### Content source verified (2026-10-20)
NASA Image and Video Library API: `https://images-api.nasa.gov`, **no API key needed**.

```
GET /search?q=volcano%20eruption&media_type=video   -> 3 hits, e.g. GSFC_20171211_Tonga_m12800_NewIsland1 "The Birth of a New Island"
GET /asset/{nasa_id}                                -> ~orig.mp4, ~large.mp4, ~medium.mp4, ~mobile.mp4, ~small.mp4, ~preview.mp4, .vtt, metadata.json
GET /captions/{nasa_id}                             -> URL of the WebVTT captions
```
Captions with timings come for free, so the agent can reason over "what is said when" without running Transcribe.
**Coverage is topic-dependent**: strong for space, Earth science, weather and climate; thin elsewhere (see Risks).

Later sources: Internet Archive (public-domain films, API), Wikimedia Commons (CC video, API with license metadata).

---

## 7. Architecture

```
 FIRE TV (native Kotlin app)                                 AWS (us-east-1)
 ┌─────────────────────────────────┐   POST /episodes   ┌──────────────────────────────────────────────┐
 │ Ask screen (voice/text, viewers)│ ─────────────────► │ API Gateway (REST + WebSocket progress)      │
 │ "Producing…" live progress      │ ◄───── progress ── │                                              │
 │ Episode player (Media3):        │                    │ Showrunner agent (Strands, AgentCore Runtime)│
 │  • EDL playlist w/ clipping     │ ◄──── EDL JSON ─── │  ├ Researcher  → search_archive (NASA, IA…)  │
 │  • narration segments (Polly)   │                    │  ├ Screener    → get_captions, look_at(t)    │
 │  • per-viewer WebVTT            │                    │  │               (Nova multimodal = CV)       │
 │ Steering: Deeper/Skip/Explain   │ ── signals ──────► │  ├ Editor      → build EDL (in/out points)   │
 │ Channel row per viewer          │                    │  ├ Writer      → narration at age level      │
 └─────────────────────────────────┘                    │  ├ Fact-checker→ verify claims vs captions   │
                                                        │  ├ Voice       → Polly (en-IN / hi-IN / en)  │
                                                        │  └ Subtitler   → translate cues per language │
                                                        │ AgentCore Memory (household/viewer prefs)    │
                                                        │ S3 (narration audio, VTT, EDL) · DynamoDB    │
                                                        └──────────────────────────────────────────────┘
 IaC: AWS CDK · Dev tooling: Claude Code + Amazon Devices Builder Tools MCP
```

### Episode EDL (contract between agent and TV)
```json
{
  "id": "ep_volcano_islands_001",
  "title": "How Volcanoes Build New Islands",
  "viewers": [{"name": "Arjun", "age": "kid", "lang": "en"}, {"name": "Paati", "age": "senior", "lang": "ta"}],
  "chapters": [{"at": 0, "title": "A new island appears"}],
  "segments": [
    {"type": "narration", "audio": "s3://…/n1.mp3", "card": "s3://…/n1.jpg", "text": {"en": "…", "ta": "…"}},
    {"type": "clip", "src": "https://images-assets.nasa.gov/video/…~medium.mp4", "in": 42.0, "out": 75.0,
     "subs": {"en": "s3://…/c1.en.vtt", "ta": "s3://…/c1.ta.vtt"}, "credit": "NASA Goddard"}
  ],
  "sources": [{"nasa_id": "GSFC_20171211_Tonga_m12800_NewIsland1", "title": "The Birth of a New Island"}]
}
```

### Models (current account status, see `docs/FEASIBILITY.md`)
- **Amazon Nova Pro:** works on the account now. Multimodal (image/video input), so it handles screening and writing.
- **Claude on Bedrock:** blocked by `INVALID_PAYMENT_INSTRUMENT` until the account's payment method is fixed. Preferred for writing and non-English subtitles: in our test, Nova Pro made meaning errors in Tamil.
- The model ID is configuration, never hard-coded.

---

## 8. What we keep from work already done

| Built | Reused as |
|---|---|
| Native Fire TV app shell, D-pad home (test 1 ✅) | Channel home + Ask screen |
| Media3 player: merged audio, side-loaded WebVTT, Compose subtitles, auto-hiding language bar (test 2 ✅) | Episode player (add clipping playlist + narration segments) |
| Transcribe spike (test 3 ✅) | Fallback captions for sources without VTT |
| Bedrock structured-output spike (test 4 ⚠️) | Writer / translator / fact-checker tool pattern |
| Emulator on E:, low-memory build settings | Unchanged |
| Friction log (9 entries) | Continues |

---

## 9. Hackathon fit

| Requirement / criterion | Showrunner |
|---|---|
| Fire TV: runs on Fire OS / Vega | Native Fire OS app ✅ |
| Demo on device or emulator | Android TV API 30 emulator (FAQ-approved) ✅ |
| Priority categories | **AI-enhanced viewing** (core), **family entertainment**, **computer vision** (screening footage), **multi-modal UX** (voice ask + D-pad steering + visual progress) |
| Judges' "creative" examples | "AI-enhanced viewing that adapts to household patterns" ✅ · "multi-modal UX combining voice + D-pad + visual in a single flow" ✅ |
| Tech implementation | Native TV stack + multi-agent system + grounded generation |
| Design | Lean-back: ask once, watch. 10-foot UI. Steering with four remote buttons |
| Potential impact | 12-min search problem; turns public archives into family TV; Fire TV Appstore-ready |
| Quality of idea | Not on any "obvious" list; no direct prior art found |
| **AWS Builder** | Strands multi-agent on **AgentCore** (Runtime + Memory), Bedrock (Nova multimodal), Polly, Transcribe, S3, DynamoDB, API Gateway WebSocket, CDK. Matches the "multi-service pipeline / agent orchestration" bar |
| **Open Source** | `media3-edl-player`: play an EDL (clips + narration + per-language subs) on Android TV / Fire TV, MIT. Or `couch-link` (QR + WebSocket pairing) |
| No third-party trademarks / copyrighted media in video | Public-domain NASA footage, credited; no NASA logo or endorsement implied; no music |
| Friction log bonus (≤10%) | Continuing `FRICTION_LOG.md` |

---

## 10. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Archive coverage is topic-dependent (NASA ≈ space/Earth/weather) | Some requests return weak footage | Scope v1 to science & nature; the agent tells you honestly when footage is thin; add Internet Archive / Wikimedia later |
| Production takes minutes | Waiting breaks lean-back | Show the live "producing" screen (the agent's work is the demo); pre-produce the next episode in the background |
| Edit quality (bad in/out points) | Episode feels choppy | Cut on caption boundaries; Screener verifies with key frames; minimum clip length |
| Hallucinated narration | Wrong facts to kids | Fact-checker agent: narration may only state what the source captions support |
| Claude blocked by billing | Weaker non-English subtitles | Use Nova for English-first demo; fix payment method to unlock Claude |
| NASA media usage guidelines | Disqualification or IP issue | Read guidelines fully before building; credit sources; no logos/insignia; no endorsement implied |
| NASA CDN range/streaming behaviour for clipping | Seeking into long MP4s is slow | Use `~medium`/`~mobile` renditions; cache selected clips to S3 if needed |
| Low RAM / C: disk on dev laptop | Builds or emulator fail | Already mitigated (AVD on E:, smaller heaps); stop emulator during heavy builds |
| **Time: ~2 days left** | Unfinished demo | Strict MVP + cut list below; go/no-go test first |

---

## 11. Scope: MVP vs stretch

**MVP (must ship):**
1. Ask screen on TV (text via remote keyboard; viewers = name, age band, language).
2. Agent: NASA search → captions → Nova screens & selects → EDL → narration (Polly) → fact-check → subtitles (English + one other language).
3. TV: live "producing" progress → episode playback (clips + narration + subtitles) → credits.
4. Steering buttons logged to memory; next suggestion uses them.

**Stretch (in order):** AgentCore Memory across sessions → phone companion (QR) for voice ask and per-viewer subtitles → Internet Archive / Wikimedia sources → Claude for translations → background pre-production of "next episode".

**Cut first if late:** phone companion, extra sources, memory persistence (keep in-session).

---

## 12. Plan (2 days)

| When | Deliverable | Gate |
|---|---|---|
| **Wed 21 (today)** | **Go/no-go test:** Python agent turns one question into an EDL from NASA footage (search → captions → Nova selection → narration → Polly), and the TV app plays that EDL with clipping + narration + subtitles | If the episode isn't convincing by tonight, stop and fall back |
| Wed 21 night | Agent on Strands + AgentCore Runtime; API Gateway; "producing" screen on TV | End-to-end from the TV ask |
| Thu 22 | Fact-checker, second subtitle language, steering → memory, polish 10-foot UI; README, AWS integrations section, OSS component; **record demo video** | Video under 3 min |
| Fri 23 AM IST | Devpost write-up, product feedback, friction log, feature requests; submit **well before** Sat 00:30 IST | Re-open and test every link |

---

## 13. Three-minute demo script (draft)

| Time | On screen | Criterion |
|---|---|---|
| 0:00–0:20 | The 12-minute search problem; a kid's question with no good answer on TV | Impact |
| 0:20–0:45 | Ask: "15 min on volcanoes making islands, for Arjun (8), Tamil subs for Paati" | Design, multi-modal |
| 0:45–1:20 | Live production screen: searching, watching clips (key frames shown), editing, fact-checking | Tech, AI depth |
| 1:20–2:10 | The episode plays: host narration, real NASA clips, Tamil subtitles; "Go deeper" pressed | AI viewing, family |
| 2:10–2:35 | The next episode builds on "go deeper"; Arjun's channel row appears | Adapts to household |
| 2:35–3:00 | Architecture (Strands + AgentCore + Bedrock + Polly), OSS component, one real number | Tech, AWS Builder |

---

## 14. Open decisions for you

1. **Go/no-go:** build the test today?
2. **Name:** "Showrunner" is a working title (the repo folder stays "Project Rosetta").
3. **Billing:** fix the AWS payment method to unlock Claude (needed for non-English quality)?
4. **Demo topics:** pick 2–3 questions for the video (suggested: volcanic islands, hurricanes from space, how rockets land).
