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
- ⬜ GitHub repo (public, MIT): `https://github.com/<you>/project-rosetta`
- ⬜ Demo video (YouTube, public, < 3 min): `https://youtu.be/...`
- Open-source contribution: `https://github.com/<you>/project-rosetta/tree/main/tv-app/vision-overlay`
- Live backend (deployed with CDK): phone page `https://dr8w3l96syzm7.cloudfront.net/kid.html?code=R4KX`

---

## Inspiration
Every evening, in homes like ours, the same two arguments happen in front of the same screen: "Have you done your
homework?" and "Clean your room." Parents nag, kids bargain for TV, and the TV, the biggest shared screen in the home,
is only ever the prize that gets switched off. In a 2025 survey, 55% of families said screen time causes arguments.

We asked: what if the TV took both sides at once, **translating** what a parent says into something a child can see,
do and feel proud of? "Do your homework" becomes "4 of 6 right, two to fix, here's a hint". "Clean your room" becomes
five Lost Things waiting to go home. That's the Rosetta Stone idea: a translator between parents and kids.

## What it does
Rosetta is a native Fire TV app with an AI agent crew behind it. Kids photograph their work with any phone or Fire
tablet; agents on Amazon Bedrock read the photo; the TV turns the result into a shared, co-op family game.

1. **Homework Check.** The kid scans the QR on the TV and snaps their worksheet. The **Tutor** agent reads the
   handwriting and marks every answer; on the TV each one gets a tick or a cross drawn exactly on the photo. Every
   arithmetic verdict is **double-checked with exact maths in code** (fractions included), so the AI's reading is
   trusted but its sums are verified. Wrong answers get a hint about the method, **never the answer**.
2. **Practice that targets the gap.** The **Practice** agent writes five fresh questions on exactly the skills that
   went wrong (validated in code: no question can repeat the homework, and every option is checked so there is exactly
   one right answer). Kids answer with the remote or on their phone; each answer explains the method.
3. **Family Quiz Battle: the Stone Trial.** Built by the **Quizmaster** agent from what each child studied this week and
   pitched at each child's level, turns alternate between siblings. Every right answer ignites a carved glyph on the
   Family Stone, in a **Unity 6** scene with bloom, particles and sparks. Co-op only: the team total, never a ranking.
4. **Room Scan → Lost Things.** The same pipeline works for chores: **Scout** finds what's out of place in a room
   photo; each item becomes a stop-motion character pinned to the real object; the **Judge** checks the "after" photo
   item by item.
5. **The Family Stone → Movie Night.** Every finished quest carves a glyph. Finishing the stone unlocks Movie Night.
   Homework Hero is the final glyph in the demo family's stone.
6. **Parents stay in the loop, not in the way.** Confident verdicts approve themselves; when the Tutor or Judge isn't
   sure, the **Router** asks a parent (push, email or SMS, PIN-protected approve/reject). Parents get "Arjun finished
   Fractions homework: 4/6 right, then practised adding fractions" and a weekly **Digest** of how habits are growing.
7. **Growth.** For parents: what's being learned, recurring mistakes, tidy time, self-starts, with the agents' notes.

A demo family ships inside the app, so the full flow runs with just a remote; the demo replays real Nova Pro outputs
recorded on the bundled worksheet photos.

## How we built it
- **Fire TV app:** Kotlin + Jetpack Compose for TV (`tv-material`) on Fire OS, D-pad first, TextToSpeech for the Keeper.
  A shared data contract with the backend; an on-device engine for offline play; a cloud repository that polls the API.
- **Agent crew:** Python, **Strands Agents** on **Amazon Bedrock** (Amazon Nova Pro, multimodal). *Quest Master* is a
  Strands agent that decides which specialist tool to call for each photo and handles the edge cases (not homework,
  low confidence, all correct). Tutor, Practice, Quizmaster, Scout, Judge, Keeper and Digest are Bedrock Converse calls
  with forced JSON tool schemas; boxes come back in Nova's 0–1000 grounding and are drawn directly on the TV.
- **Trust layer:** `mathcheck.py` computes exact answers (Python `Fraction`) for arithmetic questions and overrides the
  model's verdict; it also recognises classic slips ("you added the bottom numbers too") for precise hints.
  On our test sheets the model alone agreed with the true marking on 10 of 11 answers; with the check, 11 of 11.
- **Unity arena:** Unity 6 LTS, Universal Render Pipeline, scene built entirely in C# so it builds headless; ships as a
  companion Fire OS APK (ARM64 + x86_64) launched from the TV app with the quiz as an intent extra; answers come back
  as the activity result.
- **AWS (one CDK stack):** S3 presigned uploads from the phone (deleted after 1 day) → S3 event → agent Lambda;
  API Gateway HTTP API + Lambda; DynamoDB household document; EventBridge schedules for streak nudges and the weekly
  digest; Web Push, SES and SNS for parent alerts; CloudFront for the phone pages.
- **Phone pages:** plain HTML/JS: snap, live crew status, the Tutor's marking, practice and quiz answering, hype emotes.
- **Art:** generated in **Google Flow** from our own prompts (miniature house, stop-motion characters, carved stone and
  glyphs, stamps), Flow watermark kept; worksheets rendered from code with open fonts; sounds synthesized from code.
- **Open source:** `vision-overlay`, an MIT Compose library that draws AI detection boxes on Android TV / Fire TV.

## Challenges we ran into
- **AI that marks homework has to be right.** Nova Pro read every handwritten answer correctly but once misjudged
  "which is bigger: 2/3 or 3/5". So arithmetic verdicts are now computed in code, and the model does what it's great at:
  reading the page.
- **Hints that don't mislead.** The model sometimes guessed the wrong slip; recognised slips now get exact hints and the
  model otherwise sticks to the method.
- **Generated questions need validation:** models favour option A and occasionally offer two right answers; questions
  are shuffled and checked server-side, and invalid ones are regenerated.
- No Fire TV hardware and no Vega SDK on Windows: we built for Fire OS on emulators. Unity 6 can't target 32-bit x86,
  so we added an x86_64 test image.
- Bedrock: Anthropic models failed with payment/entitlement errors on our account, so the crew is model-agnostic and
  runs on Amazon Nova Pro.

## Accomplishments we're proud of
- A live, deployed pipeline: phone photo → S3 → Strands Quest Master on Nova Pro → marked homework and practice on the
  TV and phone in about ten seconds.
- An AI tutor that never gives the answer away, with maths it can't get wrong.
- A co-op family quiz in a real game engine, on a TV, built from what the kids actually studied that week.
- 20 offline tests covering the API, uploads, agent policy, learning flow, exact maths and question validation.

## What we learned
- Multimodal models are excellent readers of handwriting and layout; pairing them with small exact checks makes them
  trustworthy enough for children's learning.
- The TV is the right place for the shared moment and the phone is the right camera.

## What's next
- An **Amazon Kids "earn time" API** so earned TV time is enforced, not honoured (our top feature request).
- Reading quests (read a page aloud; fluency and comprehension), more subjects, Alexa voice answers in the Quiz Battle,
  and a Vega OS build.

## Built with
kotlin · jetpack-compose · compose-for-tv · fire-tv · fire-os · android · unity · c-sharp · amazon-bedrock · amazon-nova ·
strands-agents · aws-lambda · amazon-s3 · amazon-dynamodb · amazon-api-gateway · amazon-eventbridge · amazon-ses ·
amazon-sns · amazon-cloudfront · aws-cdk · python · typescript · javascript · google-flow · zxing · okhttp

---

## Product feedback (required field)
Paste [FEEDBACK.md](FEEDBACK.md).

## Feature requests (optional field)
See the table at the end of [FEEDBACK.md](FEEDBACK.md).

## Friction log (optional, up to +10%)
Paste [FRICTION_LOG.md](FRICTION_LOG.md).

## AWS Builder: services used
Amazon Bedrock (Converse API, Amazon Nova Pro multimodal) · Strands Agents SDK · AWS Lambda · Amazon S3 · Amazon DynamoDB ·
Amazon API Gateway (HTTP API) · Amazon EventBridge · Amazon SES · Amazon SNS · Amazon CloudFront · AWS CDK.
See README → "AWS integrations" for file-level mapping.

## Open Source: required fields
- **Contribution URL:** ⬜ `https://github.com/<you>/project-rosetta/tree/main/tv-app/vision-overlay`
- **Project repository URL:** ⬜ `https://github.com/<you>/project-rosetta`
- **GitHub username:** ⬜
- **What we did / how it works / why it matters:** `vision-overlay` is a new MIT-licensed Jetpack Compose library for
  Android TV and Fire TV that renders AI vision results (bounding boxes from models like Amazon Nova) as a 10-foot
  overlay: staggered spring entrances so viewers follow what the model found, attention pulses for unresolved items,
  and "resolved" exits where an optional badge floats away. It's purely visual, so it never steals D-pad focus.
  Multimodal models can now return grounded boxes, but every TV app would otherwise rebuild this rendering by hand.
