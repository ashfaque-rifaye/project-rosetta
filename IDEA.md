# Project Rosetta: Idea Decision & Architecture

_v1 · 2026-10-18 · deadline Fri 2026-10-23 12:00 PM PT (Sat 00:30 IST)_

## 1. Verdict on the playbook ideas

The playbook gets the platform constraints right: DRM, no overlays, Matter only for apps that support it, gated Alexa+ tooling. The problem is that two of its top picks **depend on exactly those constraints**:

| Idea | Fatal / serious issue |
|---|---|
| **CouchPilot** | Its value is interrupting you *while you watch Netflix*. With no overlay permission, banners only appear inside our own hub app, so nobody sees them during a movie. The Fire TV judges will spot that immediately. Alexa+ on Fire TV already shows Ring feeds on the TV. It also needs Ring + calendar + agent + sync, which is three products in 5 days. |
| **Encore** | The magic happens on the **phone** (ACRCloud). The Fire TV app is only a "wall", so Tech Implementation for *Fire TV* is weak. The demo needs licensed music. Shazam already keeps a history. |
| **CouchQuest** | Fun, but LG already gave $100K to a family AI-story game in 2024, so it isn't unique. The impact case is weak. |
| **Sideline** | Heavy CV in a short window. The playbook itself rates it "less original". |
| **Memory Theater** | **The strongest base**: our own content (no DRM or licensing), family, CV, and an emotional demo. It's weak on "idea" because Amazon Photos and Google Photos already do slideshows. |

**What to do with it:** take Memory Theater's safe foundation and give it the one thing nobody has: **translation between generations**. That's where the name Rosetta comes from.

---

## 2. The idea: **Rosetta — your family's stories, in every grandchild's language**

### The problem (specific and real)
- India has the world's largest diaspora, around 35M people. Hundreds of millions of families worldwide are split across languages.
- Grandparents tell stories in Tamil, Malayalam, Hindi or Bengali. Grandchildren raised in the US, UK or Gulf often **can't understand them**.
- Families already record these moments as WhatsApp voice notes and phone videos. They sit unwatched because half the family can't follow them, and when the grandparent is gone, the stories are effectively lost.
- Existing tools don't solve it. StoryWorth and Remento produce text books in one language. HeyGen and ElevenLabs are dubbing tools for creators. Amazon Photos on Fire TV is a slideshow with no language layer. **No product turns family recordings into a multilingual show on the living-room TV.**

### What Rosetta does
1. **Capture (phone):** scan the QR code on the TV. Any relative drops in a video or voice note from their phone. No app to install.
2. **Understand (AWS pipeline):** spoken-language identification, then transcription (Tamil, Malayalam, Telugu, Hindi, Bengali, Kannada, Marathi and more, all supported by Transcribe batch).
3. **Translate with care (Bedrock):** a translation that *keeps the meaning*. Idioms, kinship terms and cultural references become **"Rosetta Notes"** (for example, *"Paati" means father's mother; "kolam" is the rice-flour drawing at the doorstep*).
4. **Produce (AWS):** subtitles in every family language, an English (or Hindi) **dubbed audio track**, chapters, an episode title, a synopsis, and face clusters for "who is that?".
5. **Watch (Fire TV, native):** it shows up as an episode in the family's own channel, for example *"Paati's Stories, S1E3: The Monsoon of 1971"*. Each viewer profile auto-selects its own subtitle and audio language. Pausing shows Rosetta Notes for the current moment. A D-pad press on a face shows who it is.
6. **Reply (the loop):** a grandchild records a reply on their phone in English. Rosetta translates it into Tamil, and it appears on **the grandparent's Fire TV** in their language. **Two households, one channel, two languages.** This is what makes the demo stick.

### Why it wins each criterion
| Criterion | Rosetta |
|---|---|
| **Tech implementation** | Native Fire OS app (Kotlin + Compose for TV + Media3 multi-track playback). A real multi-service AWS media pipeline. A TV↔phone real-time link. |
| **Design** | A 10-foot UI that looks like a real streaming service. D-pad first, with per-profile languages. The phone handles only the jobs it's better at (upload, mic, typing). |
| **Potential impact** | A specific, named audience (diaspora families, ~280M migrants worldwide). A Fire TV Appstore-ready product. It also gives Amazon a reason to place Fire TVs in grandparents' homes in India. |
| **Quality of idea** | Not on the "obvious" list. Hits **AI-enhanced viewing + family entertainment + multi-modal UX + computer vision**: 4 of the 6 priority categories. |

### Explicitly out of scope (protects the timeline)
- No AI animation of photos (cost, uncanny results, and it dilutes the story).
- No voice cloning of grandparents. It's ethically sensitive, so we use clear, labelled synthetic dub voices and the original audio is always one click away.
- No dependence on overlays, other apps' streams, Alexa+ add-on tooling, or the remote's mic (third-party Fire TV apps can't rely on it; voice goes through the phone).

---

## 3. Architecture: built on the stack Fire TV actually runs

### Platform choice: **Fire OS native (Android), Kotlin**
- Fire OS is Android-based. The most native and robust path is **Kotlin + Jetpack Compose for TV** (`androidx.tv:tv-material`), the same direction as Amazon's own `hello-world-fire-tv` sample.
- **Media3 ExoPlayer** is the playback engine that Fire OS streaming apps use. It handles multiple subtitle tracks (WebVTT side-loaded via `SubtitleConfiguration`) and multiple audio tracks (original + dubs via `MergingMediaSource`), with `TrackSelector` driven by each viewer profile's language. This is the same model Prime Video uses for languages.
- It works on **Windows** with Android Studio and the Android TV emulator, which the FAQ explicitly accepts. Vega OS needs macOS or Ubuntu, so it's out of reach here. We'll log that as a **friction-log entry** and list "Vega port" on the roadmap.
- minSdk 25 (Fire OS 6+). The real-device target is Fire OS 7/8.

```
 PHONE (web companion, no install)          FIRE TV (native Kotlin app)
 ┌──────────────────────────┐               ┌───────────────────────────────┐
 │ scan QR → join family    │               │ Compose for TV UI             │
 │ upload video/voice note  │               │  • Family channel / episodes  │
 │ record reply / ask       │               │  • Profiles (lang per viewer) │
 └──────┬───────────┬───────┘               │ Media3 ExoPlayer              │
        │presigned  │ WebSocket             │  • orig + dub audio tracks    │
        ▼ S3 PUT    ▼                       │  • WebVTT subs per language   │
 ┌──────────────────────────────────────┐   │ Rosetta Notes / Who-is-that   │
 │ API Gateway (REST + WebSocket)       │◄──┤ couch-link pairing (QR)       │
 └──────┬───────────────────────────────┘   └───────────────────────────────┘
        ▼
 S3 (raw) ─EventBridge─► Step Functions "story pipeline"
        1 Transcribe   (auto language ID, ta/ml/hi/te/bn/en-IN …)
        2 Bedrock      (Claude: translation + Rosetta Notes + chapters + title)
        3 Polly        (dub track: en-IN / hi-IN neural voices)
        4 Lambda+ffmpeg(mux dub audio, write WebVTT per language, thumbnails)
        5 Rekognition  (face clusters → "who is that?")
        6 DynamoDB     (family, episodes, profiles, notes)  + S3 (media out)
        ▼
 "Ask Rosetta": Strands agent (Bedrock) with tools over transcript/notes/people
 IaC: AWS CDK  ·  Dev tooling: Claude Code + Amazon Devices Builder Tools MCP
```

### Language coverage (checked against the AWS docs, 2026-10-18)
- **Transcribe batch:** hi, ta, ml, te, kn, mr, bn, gu, pa, or (all -IN) + en-IN. ✅
- **Polly:** neural voices for **Hindi and Indian English only**. No Tamil or Malayalam voices, so **dubs go *into* English or Hindi**. For grandparents, replies arrive as **subtitles in their own script**, plus a Hindi voice where that fits. That's honest and still works.
- **Bedrock (Claude):** translates between all of these.

---

## 4. Compliance check: every requirement mapped

| Requirement | How Rosetta meets it |
|---|---|
| Runs on Fire OS / Vega | Native Fire OS APK |
| Video shows it on a device or emulator | Android TV emulator (FAQ-approved). A real Fire TV Stick if available. |
| Video < 3 min, public YouTube/Vimeo, English | Script in §6. English narration. Tamil audio subtitled. |
| No 3rd-party trademarks, copyrighted music or footage | Only our own family recordings. Royalty-free or no music. |
| **Consent** of anyone filmed | Written OK from every family member who appears |
| GitHub repo with all source + setup/run instructions | Monorepo: `tv-app/`, `companion/`, `infra/` (CDK), `pipeline/`, README |
| Public + OSS license visible in About | MIT, set in the GitHub About panel |
| Text description | Devpost write-up including **one real number** (see §7) |
| Product feedback on every tool | Running notes from day 1 (Android Studio/emulator, Compose for TV, Media3, Builder Tools MCP, Transcribe, Bedrock, Polly, Rekognition, Step Functions, CDK, Vega SDK attempt) |
| AWS Builder mini challenge | Bedrock + Strands + Transcribe + Polly + Rekognition + Step Functions + API GW WebSocket + DynamoDB + S3 + CDK. README "AWS integrations" table pointing at the code. |
| Open Source mini challenge | **`couch-link`** (MIT): a QR + WebSocket TV↔phone pairing kit (Kotlin TV side + JS phone side + CDK backend). Plus one PR to an AmazonAppDev sample repo. |
| Friction log (up to +10%) | `FRICTION_LOG.md` filled in as things happen: task, steps, expected vs actual, severity, workaround, suggestion |
| Feature requests (optional) | e.g. remote mic for 3rd-party apps, Vega SDK on Windows, overlay permission |
| Newly created during the window | Created 2026-10-18 onward. Git history shows it. |
| Outside-work / IP | **Personal laptop, personal time, personal accounts. Check your employer's outside-activity / IP policy (the playbook flags AT&T) before publishing.** |

---

## 5. Risks and day-1 feasibility tests (prove these in the first hours)

| # | Test | Pass means | If it fails |
|---|---|---|---|
| 1 ✅ | Compose-for-TV app runs on Android TV emulator; D-pad focus works | Shell app with a focusable grid | **Passed 2026-10-18** (`docs/screens/day1-home-*`) |
| 2 ✅ | Media3 plays MP4 + **2 side-loaded WebVTT + 1 extra audio track** and switches them | Track switching from code | **Passed 2026-10-18**: MergingMediaSource (video + separate dub file) gives 2 audio groups; 3 side-loaded VTT; D-pad chips switch them; Tamil and Devanagari render; subtitles drawn in Compose from `onCues` (`docs/screens/day1-player-*`) |
| 3 | Transcribe on a 60-s Tamil/Hindi clip with language ID | Correct language + usable transcript | Pass the language code explicitly (family sets it at upload) |
| 4 | Bedrock call (Claude) from Lambda in your region | JSON with translation + notes | Try another region / model access request |
| 5 | Phone ↔ TV over API GW WebSocket via QR | Phone upload makes the TV refresh in < 2 s | Polling every 3 s (invisible in demo) |
| 6 | Polly en-IN neural dub of 60 s synced by segment timestamps | Dub roughly lip-time aligned | Subtitles-only plus a dub "narration" mode |

**Biggest remaining risk:** total scope for one person in 5 days. Cut lines, in order: Rekognition "who is that" → Strands "Ask Rosetta" → reply loop to a second TV (show it in the emulator as a second profile instead). The core that must ship is upload → pipeline → native multilingual playback with Rosetta Notes.

---

## 6. Three-minute demo script

| Time | On screen | Criterion |
|---|---|---|
| 0:00–0:20 | Grandmother tells a story in Tamil on a phone video. Her US grandson shrugs: "I don't understand Paati." | Impact |
| 0:20–0:50 | Fire TV: scan the QR, the phone uploads, and the TV shows "Rosetta is preparing your episode" with live pipeline steps. | Tech, design |
| 0:50–1:40 | The episode plays with English dub and subtitles. Pause shows a Rosetta Note ("kolam…"). D-pad onto a face: "Thatha, 1974". Switch profile and it's Tamil subtitles. | AI viewing, CV, design |
| 1:40–2:20 | The grandson records a reply in English and it appears on Paati's TV in Tamil. | Idea (the loop) |
| 2:20–2:45 | "Ask Rosetta: where did Paati grow up?" The agent answers from the family archive. | Multi-modal, agent |
| 2:45–3:00 | Architecture slide, couch-link OSS, one real number. | Tech, impact |

## 7. "One real number" (validate with one real user, per the playbook)
Ask 5–10 relatives or friends in diaspora families: *"Do you have recordings of elders you can't fully understand?"* Report the result honestly, e.g. "7 of 9 said yes."

## 8. Five-day build plan

| Day | Date | Ship |
|---|---|---|
| 1 | Sun 18 | Feasibility tests 1–4 · repo + CDK skeleton · FRICTION_LOG started |
| 2 | Mon 19 | Office hours 9 AM PT (ask about the Media3 multi-track pattern + video rules) · pipeline end-to-end (Transcribe → Bedrock → Polly → S3/Dynamo) |
| 3 | Tue 20 | TV app: family channel, episode page, player with tracks + Rosetta Notes, profiles |
| 4 | Wed 21 | Phone companion + couch-link pairing + upload + reply loop · Rekognition if on time |
| 5 | Thu 22 | Record real family footage (with consent) · demo video · README · feedback · friction log · OSS PR |
| — | Fri 23 | Buffer · submit by **Fri evening IST** (well before the 00:30 IST Saturday cutoff) · open and test every link |
