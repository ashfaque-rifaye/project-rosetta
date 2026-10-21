# Devpost submission: Project Rosetta

_Copy each section into the matching Devpost field. Items marked ⬜ need you._

---

## Project name
**Project Rosetta**

## Tagline
The Fire TV app that translates "clean your room" into an adventure.

## Tracks and mini challenges
- Primary track: **Fire TV**
- Mini challenges: **AWS Builder**, **Open Source**

## Links
- ⬜ GitHub repo (public, MIT): `https://github.com/<you>/project-rosetta`
- ⬜ Demo video (YouTube, public, < 3 min): `https://youtu.be/...`. Upload `docs/video/rosetta_demo_draft.mp4` (or the narrated final)
- Open-source contribution: `https://github.com/<you>/project-rosetta/tree/main/tv-app/vision-overlay`

---

## Inspiration
Every evening in our homes, and in 55% of families according to a 2025 survey, the same argument happens in front of
the same screen: "Clean your room." "I did!" "That's not clean." The TV is the prize everyone fights over, but it's never
part of the solution. Parental controls can only take time away; chore apps live on phones and make parents approve
every photo.

We asked: what if the biggest shared screen in the home took the parent's side *and* the kid's side at once,
**translating** a vague instruction into something a child can see, do, and feel proud of? That's the Rosetta Stone idea.

## What it does
Rosetta is a native Fire TV app with an AI agent crew behind it.

1. **Home Base.** The Fire TV home is the family's house as a glowing miniature. Rooms with a waiting quest light up;
   focusing a quest glides the camera into that room.
2. **Room Scan → Lost Things.** The kid scans a QR on the TV and snaps their room with any phone or Fire tablet. The
   **Scout** and **Coach** agents find what's out of place and where each thing belongs. On the TV, every item is outlined
   on the real photo, and a stop-motion **Lost Thing** character (a lonely sock, Rex the dinosaur, the brick gang…)
   perches above it.
3. **Proof, item by item.** After tidying, the kid snaps again. The **Judge** compares before and after, gives credit for
   each item that went home, and the **Keeper**, a calm storyteller voice, names exactly what's still lost and where it
   goes ("The jacket is still on the chair; its hook by the window is waiting"). No shaming; mess is just lost things.
4. **The Family Stone.** Each finished quest carves a glowing glyph into a sandstone tablet that siblings fill
   *together*. A complete stone unlocks **Movie Night**, and the TV hands off to the family's streaming app.
5. **Parent Mission Control.** Parents post quests from their phone. Confident verdicts auto-approve; when the Judge
   isn't sure, the **Router** sends it to a parent for a one-tap decision instead of guessing.

A **demo household** ships inside the app, so anyone can try the full flow with just a Fire TV remote, no account or cloud.

## How we built it
- **Fire TV app:** Kotlin + Jetpack Compose for TV (`tv-material`) on Fire OS, D-pad first. A shared data contract with
  the backend; a demo repository replays the crew's real states offline; a cloud repository polls the household API.
  Join QR rendered on-device (ZXing), Keeper voice via TextToSpeech.
- **Agent crew:** Python, **Strands Agents** on **Amazon Bedrock**. *Quest Master* is a Strands agent that chooses which
  specialist tool to call and handles edge cases (not a room, the same photo twice, low confidence). Scout, Judge and
  Keeper are Bedrock Converse calls with images and **forced JSON tool schemas**; Scout returns grounded boxes (0–1000)
  that the TV draws directly. Default model: **Amazon Nova Pro** (vision); the model is a deploy-time setting.
- **AWS:** S3 presigned uploads straight from the phone (auto-deleted after 1 day), S3 event → agent Lambda, DynamoDB
  household document, API Gateway HTTP API, CloudFront for the phone pages, all in one **AWS CDK** stack.
- **Phone pages:** plain HTML/JS: the kid page (join, snap, live crew status) and the parent page.
- **Art:** every image generated in **Google Flow** from our own prompts (miniature house, stop-motion characters,
  carved stone and glyphs, sample room photos), curated and composited by the app. No stock or third-party media.
- **Open source:** `vision-overlay`, an MIT Compose library extracted from Room Scan, which draws AI detection boxes on
  Android TV / Fire TV with staggered entrances, attention pulses and "resolved" animations.

## Challenges we ran into
- No Fire TV hardware and no Vega SDK on Windows: we built for Fire OS and tested on an Android TV API 30 emulator, which needed several workarounds (disk, GPU, memory). All are in our friction log.
- Bedrock model access: Anthropic models listed as ACTIVE but failed with payment/entitlement errors on our account, so
  we designed the crew to be model-agnostic and run on Amazon Nova Pro.
- Making AI feedback *kind*: the Keeper's prompt forbids shaming and comments on character; mess is framed as Lost Things.
- Making AI art not look like AI art: we chose crafted, physical styles (miniature model, stop-motion felt, carved
  sandstone) and let the app supply the motion.

## Accomplishments we're proud of
- A complete, polished TV experience you can run with only a remote.
- Item-level proof shown to the whole family, which turns "is it done?" from an argument into a shared moment.
- Low-confidence verdicts escalate to a parent instead of the AI guessing.
- An offline test suite that runs the whole quest flow (API + uploads + crew policy) with stubbed vision.

## What we learned
- Multimodal grounding is now good enough to *plan* a chore, not just pass/fail it.
- The TV is a different design problem from a phone: lean-back, shared, D-pad. The phone is better as a camera than
  as the main screen.

## What's next
- An **Amazon Kids "earn time" API** so earned TV time is enforced, not honoured (our #1 feature request).
- More quest types (dishes, plants, homework with hints not answers), Ring "package detective" quests, a nightly recap
  episode, and a Vega OS build.

## Built with
kotlin · jetpack-compose · compose-for-tv · fire-tv · fire-os · android · amazon-bedrock · amazon-nova · strands-agents ·
aws-lambda · amazon-s3 · amazon-dynamodb · amazon-api-gateway · amazon-cloudfront · aws-cdk · python · typescript ·
javascript · google-flow · zxing · okhttp

---

## Product feedback (required field)
Paste [FEEDBACK.md](FEEDBACK.md) (per-tool: used for / worked well / needs work / onboarding / build again, including the
AWS services and how we used them).

## Feature requests (optional field)
See the table at the end of [FEEDBACK.md](FEEDBACK.md): Amazon Kids earn-time API (Critical), sanctioned family overlay
(Important), Vega SDK on Windows (Important), Fire OS emulator images (Nice-to-have), Bedrock invocability API
(Important), Polly Indian-language voices (Important).

## Friction log (optional, up to +10%)
Paste [FRICTION_LOG.md](FRICTION_LOG.md).

## AWS Builder: services used
Amazon Bedrock (Converse API, Amazon Nova Pro multimodal) · Strands Agents SDK · AWS Lambda · Amazon S3 · Amazon DynamoDB ·
Amazon API Gateway (HTTP API) · Amazon CloudFront · AWS CDK. See README → "AWS integrations" for file-level mapping.

## Open Source: required fields
- **Contribution URL:** ⬜ `https://github.com/<you>/project-rosetta/tree/main/tv-app/vision-overlay`
- **Project repository URL:** ⬜ `https://github.com/<you>/project-rosetta`
- **GitHub username:** ⬜
- **What we did / how it works / why it matters:** `vision-overlay` is a new MIT-licensed Jetpack Compose library for
  Android TV and Fire TV that renders AI vision results (bounding boxes from models like Amazon Nova) as a 10-foot
  overlay: staggered spring entrances so viewers follow what the model found, attention pulses for unresolved items,
  and "resolved" exits where an optional badge floats away. It's purely visual, so it never steals D-pad focus.
  Multimodal models can now return grounded boxes, but every TV app would otherwise rebuild this rendering by hand.
