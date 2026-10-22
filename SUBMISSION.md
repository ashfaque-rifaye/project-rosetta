# Devpost submission: Project Rosetta

_Copy each section into the matching Devpost field. Items marked ⬜ need you._

---

## Project name
**Project Rosetta**

## Tagline
The Fire TV that turns homework and chores into a family adventure, with an AI tutor that never gives the answer away.

## Tracks and mini challenges
- Primary track: **Fire TV**
- Mini challenges: **AWS Builder**, **Open Source**

## Links
- GitHub repo (public, MIT): `https://github.com/ashfaque-rifaye/project-rosetta`
- ⬜ Demo video (YouTube, public, < 3 min): `https://youtu.be/...`
- Open-source project: `https://github.com/ashfaque-rifaye/compose-vision-overlay`
- Live backend (deployed with AWS CDK): kid page `https://dr8w3l96syzm7.cloudfront.net/kid.html?code=R4KX` · parent page `https://dr8w3l96syzm7.cloudfront.net/parent.html?code=R4KX` (demo PIN 2468)

## Testing instructions (for judges)
See README → "Run it". The quickest path: Android TV emulator (API 36 = Fire OS 16) or any Fire TV / Android TV
device, `./gradlew installDebug` with the two live URLs from the README, reset the demo household with one `curl`,
then open *Fractions homework* and photograph `assets/homework/arjun_fractions.jpg` from any phone. Offline mode
(`./gradlew installDebug` with no URLs) needs no cloud at all: press MENU on a quest to send the bundled photo.

---

## Inspiration
Every evening, in homes like ours, the same two arguments happen in front of the same screen: "Have you done your
homework?" and "Clean your room." Parents nag, kids bargain for TV, and the TV, the biggest shared screen in the home,
is only ever the prize that gets switched off. In a 2025 survey, 55% of families said screen time causes arguments.

We asked: what if the TV took both sides at once, **translating** what a parent says into something a child can see,
do and feel proud of? "Do your homework" becomes "4 of 6 right, two to fix, here's a hint". "Clean your room" becomes
five Lost Things waiting to go home. That's the Rosetta Stone idea: a translator between parents and kids.

## What it does
Rosetta is a native Fire TV app with an AI agent crew behind it. Kids photograph their work with any phone; the agents
read the photo; the TV turns the result into a shared, co-op family game, narrated by the Keeper's warm voice.

1. **Homework Check.** The kid scans the QR on the TV and snaps their worksheet. The **Tutor** agent reads the
   handwriting and marks every answer; on the TV each answer gets a tick or a cross drawn right on the child's writing.
   Every arithmetic verdict is **double-checked with exact maths in code** (fractions included): the model reads, the
   code decides. Wrong answers get a hint about the method, **never the answer**.
2. **Practice that targets the gap.** The **Practice** agent writes fresh questions on exactly the skills that went
   wrong (validated in code: no question repeats the homework, and there is exactly one right option). Kids answer
   with the remote or on their phone; each answer explains the method.
3. **Family Quiz Battle: the Stone Trial.** Built by the **Quizmaster** agent from what each child studied this week and
   pitched at each child's level; turns alternate between siblings. Every right answer ignites a carved glyph on the
   Family Stone, in a **Unity 6** scene with bloom, particles and sparks. Co-op only: the team total, never a ranking.
4. **Room Scan → Lost Things.** The same pipeline works for chores: **Scout** finds what's out of place in a room
   photo; each item becomes a stop-motion character pinned to the real object; the **Judge** checks the "after" photo.
5. **The Family Stone → Movie Night.** Every finished quest carves a glyph; finishing the stone unlocks Movie Night.
6. **Parents stay in the loop, not in the way.** A parent phone page (installable, PIN-protected): confident verdicts
   approve themselves, and when the Tutor or Judge isn't sure the **Router** asks a parent to approve or send it back.
   Parents see the family feed, get push / email / SMS alerts and a weekly **Digest**, and can send a message that pops
   up on the TV and is read aloud by the Keeper.
7. **Growth.** For parents: what's being learned, recurring mistakes, tidy time, self-starts, with the agents' notes.

A demo family ships inside the app, so the whole flow also runs offline with just a remote (it replays real model
outputs recorded on the bundled worksheet photos).

## How we built it
- **Fire TV app:** Kotlin + Jetpack Compose for TV (`tv-material`) on Fire OS, D-pad first. Screens are designed on a
  1440 × 810 canvas scaled to the display, so 720p, 1080p (320 dpi) and 4K Fire TVs look identical. An on-device
  engine for offline play and a cloud repository that polls the API; an **AGENTS LIVE** badge shows which is driving.
- **Agent crew (on AWS):** Python and the **Strands Agents SDK**. *Quest Master* is a Strands agent that decides which
  specialist tool to call for each photo and handles the edge cases (not homework, low confidence, all correct).
  Tutor, Practice, Quizmaster, Scout, Judge, Keeper and Digest are single model calls with images and forced JSON schemas.
- **Model-agnostic by design:** every prompt and schema is written once as an **Amazon Bedrock Converse** tool spec.
  We built and first deployed on **Amazon Nova Pro**; for cost, the deployed crew now runs on **Gemini 2.5 Flash via
  Google Vertex AI**, which reuses the same schemas as response schemas. One CDK flag switches back to Bedrock.
  The AWS Lambdas sign in to Vertex AI **keylessly** through Workload Identity Federation: no key exists anywhere.
- **The Keeper's voice:** a warm storyteller voice from Gemini TTS. The line shows on the TV at once and its voice
  follows seconds later; repeating lines are cached in S3; the TV app bundles clips for its offline lines.
- **Trust layer:** `mathcheck.py` computes exact answers (Python `Fraction`) for arithmetic questions and overrides the
  model's verdict; it also recognises classic slips ("you added the bottom numbers too") for precise hints.
  On our test sheets the model alone agreed with the true marking on 10 of 11 answers; with the check, 11 of 11.
- **Unity arena:** Unity 6 LTS, URP, scene built entirely in C# so it builds headless; a companion Fire OS APK (ARM64 +
  x86_64) launched from the TV app with the quiz as an intent extra; answers come back as the activity result.
- **AWS (one CDK stack):** S3 presigned uploads from the phone (deleted after 1 day) → S3 event → agent Lambda; API
  Gateway HTTP API + Lambda; DynamoDB household document; EventBridge schedules for streak nudges and the weekly
  digest; Web Push, SES and SNS for parent alerts; CloudFront for the phone pages.
- **Art:** generated in **Google Flow** from our own prompts, Flow watermark kept; worksheets rendered from code with
  open fonts; sounds synthesized from code.
- **Open source:** `compose-vision-overlay`, a new MIT Compose for TV library that draws AI vision results.

## Challenges we ran into
- **AI that marks homework has to be right.** Models read handwriting very well but can slip on a comparison like
  "which is bigger: 2/3 or 3/5". So arithmetic verdicts are computed in code, and the model does what it's great at.
- **Boxes that land on the right spot.** Moving models changed the coordinate convention (Gemini detects in
  y-x order) and, asked for "the question and its answer", Gemini anchored every box at the page margin. We now ask for
  the child's handwritten answer only, in each model's native order, and convert in one adapter.
- **A real Fire TV is 960 × 540 dp.** Our first emulator was a phone image at TV resolution with a lower effective
  density, so screens that looked right there overflowed on a true Android TV image. Scaling a fixed canvas fixed
  every screen at once.
- **Generated questions need validation:** models favour option A and occasionally offer two right answers; questions
  are shuffled and checked server-side, and invalid ones are regenerated.
- No Fire TV hardware and no Vega SDK on Windows: we built for Fire OS on the Android TV emulator (API 36 = Fire OS 16).
- Bedrock: Anthropic models failed with payment/entitlement errors on our account, which is why the crew was built
  model-agnostic from day one.

## Accomplishments we're proud of
- A live, deployed pipeline: phone photo → S3 → Strands Quest Master → marked homework and practice on the TV in about
  fifteen seconds, narrated in a voice kids actually want to listen to.
- An AI tutor that never gives the answer away, with maths it can't get wrong.
- A co-op family quiz in a real game engine, on a TV, built from what the kids actually studied that week.
- Parents who only step in when the AI is unsure, from their phone.
- 24 offline tests covering the API, uploads, agent policy, learning flow, exact maths, question validation and voice.

## What we learned
- Multimodal models are excellent readers of handwriting and layout; pairing them with small exact checks makes them
  trustworthy enough for children's learning.
- The TV is the right place for the shared moment and the phone is the right camera.
- Keep the agent layer model-agnostic: our schemas moved between two providers without touching a prompt.

## What's next
- An **Amazon Kids "earn time" API** so earned TV time is enforced, not honoured (our top feature request).
- Reading quests (read a page aloud; fluency and comprehension), more subjects, Alexa voice answers in the Quiz Battle,
  and a Vega OS build.

## Built with
kotlin · jetpack-compose · compose-for-tv · fire-tv · fire-os · android · unity · c-sharp · strands-agents · aws-lambda ·
amazon-s3 · amazon-dynamodb · amazon-api-gateway · amazon-eventbridge · amazon-ses · amazon-sns · amazon-cloudfront ·
aws-cdk · amazon-bedrock · amazon-nova · google-vertex-ai · gemini · python · typescript · javascript · google-flow · zxing · okhttp

---

## Product feedback (required field)
Paste [FEEDBACK.md](FEEDBACK.md).

## Feature requests (optional field)
See the table at the end of [FEEDBACK.md](FEEDBACK.md).

## Friction log (optional, up to +10%)
Paste [FRICTION_LOG.md](FRICTION_LOG.md).

## AWS Builder: services used
**Strands Agents SDK** (the Quest Master agent orchestrating the crew) · AWS Lambda (API, agent crew, schedules) · Amazon S3
(presigned photo uploads, voice clips, hosting) · Amazon DynamoDB · Amazon API Gateway (HTTP API) · Amazon EventBridge ·
Amazon SES · Amazon SNS · Amazon CloudFront · AWS CDK (one-command stack) · Amazon Bedrock (Converse API, Amazon Nova Pro:
the crew's schemas are Bedrock tool specs; it ran on Nova Pro until 22 Oct and switches back with `-c aiProvider=bedrock`).
See README → "AWS integrations" for the file-level mapping.

## Open Source: required fields
- **Contribution URL:** `https://github.com/ashfaque-rifaye/compose-vision-overlay`
- **Project repository URL:** `https://github.com/ashfaque-rifaye/project-rosetta`
- **GitHub username:** `ashfaque-rifaye`
- **What we did / how it works / why it matters:** `compose-vision-overlay` is a new MIT-licensed Jetpack Compose library
  for Android TV and Fire TV that renders AI vision results (bounding boxes from multimodal models such as Amazon Nova or
  Gemini) as a 10-foot overlay: staggered spring entrances so viewers follow what the model found, attention pulses for
  unresolved items, and "resolved" exits where an optional badge floats away. It is purely visual, so it never steals
  D-pad focus, and it ships with helpers for the two common box conventions (Nova's x-y 0–1000 grid and Gemini's y-x
  0–1000 grid), unit tests and a sample TV app. Multimodal models now return grounded boxes, but every TV app would
  otherwise rebuild this rendering by hand.
