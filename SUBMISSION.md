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
device, `./gradlew installDebug` with the two live URLs from the README. On a phone, open the parent page (demo PIN
2468) and add a daily task; open the kid page, pick a name and check in; open *Fractions homework* on the TV and
photograph `assets/homework/arjun_fractions.jpg` from any phone. Offline mode
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
Rosetta is a native Fire TV app for the whole family, with AI on AWS behind it. Parents set the day from their phone;
kids check in from theirs; the TV shows everyone how the day is going, and **TV time unlocks only when today's must-do
tasks are done**. Everything the AI does is marked with a ✦, and nothing on screen is made up.

1. **TV time, earned.** Home leads with today's deal: "2 tasks to go". When the must-do tasks are done (and a parent
   says OK, if they asked to), TV time unlocks and Rosetta opens Prime Video, YouTube or Netflix and counts the minutes.
2. **Daily tasks that build habits.** Parents add tasks like *make your bed*, *read 10 pages*, *prayer time*. Kids check
   in with a tick, a short note, a **voice note** or a photo. ✦ The AI transcribes and summarises a voice note and only
   counts it if it's really about the task. Notes and voice notes are kept 90 days as **memories** parents can replay.
   Every task has its own **streak**.
3. **Homework Check.** The kid snaps a worksheet. The **AI Tutor** reads the handwriting and marks every answer with a
   tick or cross drawn on the child's writing. Every arithmetic verdict is **double-checked with exact maths in code**:
   the model reads, the code decides. Wrong answers get a hint about the method, **never the answer**, then a short
   practice round on exactly the skills that slipped.
4. **Honest photo checks.** Rosetta always says what a photo really shows. A worksheet sent for "tidy your room" is
   called out plainly; a room that's already tidy is accepted. For messy rooms, each out-of-place item becomes a
   stop-motion "Lost Thing" pinned to the real object, and the after photo is checked.
5. **Real pocket money and a wish list.** Tasks can pay real money that parents set. Parents add wishes (an Amazon link,
   say); when a child has saved enough they ask from their phone and a parent approves. Coins buy outfits and decor.
6. **Family Quiz Night.** Eight questions of moderate difficulty, written for each child's class, subjects and coming
   tests (which parents enter), with siblings taking turns, in a **Unity 6** scene or natively. Co-op only.
7. **Family Goal → Movie Night.** Every finished task adds a star; nine stars unlock Movie Night.
8. **Parents in control, from any phone.** No app to install: push alerts when something needs them (a check, a wish,
   a TV request), approvals, today at a glance, memories, a learning profile, TV settings and a Fire TV
   parental-controls guide. **Progress** shows a 14-day habit calendar, homework accuracy and ✦ **Coach's notes** the AI
   writes from real activity.
9. **A real opening.** An 8-second intro film made with **Veo 3.1** and an original **Lyria** score.

A demo family ships inside the app, so the whole flow also runs offline with just a remote (it replays real model
outputs recorded on the bundled worksheet photos).

## How we built it
- **Fire TV app:** Kotlin + Jetpack Compose for TV (`tv-material`) on Fire OS, D-pad first. Screens are designed on a
  1440 × 810 canvas scaled to the display, so 720p, 1080p (320 dpi) and 4K Fire TVs look identical. An on-device
  engine for offline play and a cloud repository that polls the API; a small **✦ AI on** badge shows which is driving.
  Kids never see agent names: the crew works behind a plain step list ("✦ Checked 6 answers: 4 right").
- **Agent crew (on AWS):** Python and the **Strands Agents SDK**. *Quest Master* is a Strands agent that decides which
  specialist tool to call for each photo and handles the edge cases (not homework, low confidence, all correct).
  Tutor, Practice, Quizmaster, Scout, Judge, Listener (voice notes), Proof check, Keeper and Coach are single model calls
  with images or audio and forced JSON schemas, each returning what it actually saw or heard.
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
- **AWS (one CDK stack):** S3 presigned photo and voice uploads from the phone (deleted after 1 day; memories kept 90
  days) → S3 event → agent Lambda; API
  Gateway HTTP API + Lambda; DynamoDB household document; EventBridge schedules for streak nudges and the weekly
  digest; Web Push, SES and SNS for parent alerts; CloudFront for the phone pages.
- **Art:** generated in **Google Flow** from our own prompts, Flow watermark kept; the intro film with **Veo 3.1** and
  its score with **Lyria** on Vertex AI; parent portraits with Gemini image generation; worksheets rendered from code
  with open fonts; sounds synthesized from code.
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
- **Fire TV can't let an app lock other apps** or replace the home screen, so earned TV time is honoured inside Rosetta
  (it opens the streaming app and counts the minutes) and parents use Fire TV's own parental controls for the rest.
- **Honesty over magic.** Early versions answered a wrong photo with a generic "take another photo". Every check now
  returns what it actually saw, and every number on screen comes from the server's history: no sample data.
- Bedrock: Anthropic models failed with payment/entitlement errors on our account, which is why the crew was built
  model-agnostic from day one.

## Accomplishments we're proud of
- A live, deployed pipeline: phone photo → S3 → Strands Quest Master → marked homework and practice on the TV in about
  fifteen seconds, narrated in a voice kids actually want to listen to.
- An AI tutor that never gives the answer away, with maths it can't get wrong.
- A co-op family quiz in a real game engine, on a TV, built from what the kids actually studied that week.
- Parents who only step in when the AI is unsure, from their phone.
- A family loop that is real end to end: a parent adds a daily task, a child sends a voice note, the AI checks it,
  real pocket money lands, the streak grows, and TV time unlocks.
- 43 offline tests covering the API, habits, voice notes, TV time, wish list, agent policy, learning flow and exact maths.

## What we learned
- Multimodal models are excellent readers of handwriting and layout; pairing them with small exact checks makes them
  trustworthy enough for children's learning.
- The TV is the right place for the shared moment and the phone is the right camera.
- Keep the agent layer model-agnostic: our schemas moved between two providers without touching a prompt.

## What's next
- An **Amazon Kids "earn time" API** so earned TV time is enforced, not honoured (our top feature request).
- Reading aloud for fluency, more subjects, Alexa voice answers in Family Quiz Night, and a Vega OS build.

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
