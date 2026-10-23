# Devpost submission: Project Rosetta

_Copy each block into the matching Devpost field, in the order the form asks. Items marked ⬜ need you._

---

## General info

**Project name**
```
Project Rosetta
```

**Elevator pitch** (200 characters max; this one is 120)
```
A Fire TV app where homework, chores and daily habits earn TV time and real pocket money, checked by an AI tutor on AWS.
```

---

## Project story → About the project (Markdown)

```markdown
## Inspiration
Every evening, in millions of homes, the same two questions are asked in front of the same screen: *"Did you finish your homework?"* and *"Can I watch TV now?"* Parents nag, kids bargain, and the TV, the biggest shared screen in the home, is only ever the prize that gets switched off.

We asked a different question: **what if the TV itself could help?** What if it could translate what a parent asks for ("do your homework", "tidy your room", "read 10 pages") into something a child can see, do and feel proud of, and then reward it fairly? That is the Rosetta Stone idea behind the name: a translator between parents and kids.

## What it does
**Rosetta is a native Fire TV app for the whole family, with an AI agent crew running serverless on AWS.** Parents set the day from their phone, kids check in from theirs, and the TV shows everyone how the day is going. **TV time unlocks only when today's must-do tasks are done.**

- **A living home, full of quests.** Home is a 3D-feeling dollhouse; the camera glides to each child's room as you browse homework, chores and daily habits. A TV Time portal shows "6 must-do to go", then bursts open when the work is done or a parent unlocks it from their phone.
- **✦ AI Tutor for homework.** A child photographs a worksheet with any phone. Seconds later the TV shows every answer marked on the child's **own handwriting**, a score ring, and a hint for each mistake: **never the answer**. Arithmetic verdicts are **double-checked with exact maths in code** (the model reads, the code decides). It works for coding class too: the Tutor traces little programs line by line.
- **Practice that makes them think.** A short practice round on exactly the skills that slipped, written as easy reasoning questions for the child's grade, not "1 + 1".
- **Honest photo checks.** Send the wrong photo and Rosetta says what it actually sees ("That's a lovely photo of your room, but I need your Fractions worksheet"). Tidy-ups turn each out-of-place item into a collectible *Lost Thing*, and the after photo is checked again: "5 of 6 back home".
- **Daily habits with voice notes.** *Make your bed*, *read 10 pages*, *prayer time*: kids check in with a tick, a note, a photo or a **voice note**. The AI listens, summarises it and keeps it as a **memory** parents can replay. Every habit has its own streak.
- **Rewards for every child, and real pocket money.** XP, coins, stamps and a star for the Family Goal (nine stars unlock **Movie Night**). Parents decide what each task pays in **real pocket money**; kids save toward a **wish list**, ask from their phone when they have enough, a parent taps **yes**, and the TV updates the balance live.
- **Family Quiz Night in 3D.** A co-op quiz in a **Unity 6** scene, with reasoning questions written for each child's class, subjects and upcoming tests: fractions for one sibling, code tracing for the other.
- **Progress and ✦ Coach's notes.** Per-child dashboards (habit calendar, homework accuracy, pocket money, stars) and notes the AI writes from **real activity only**. No sample data anywhere.
- **Parents in control, from any phone.** No app to install: scan the QR on the TV. Push, email or SMS alerts when something needs them; one-tap approvals; daily tasks, learning profiles, pocket money, wish lists and TV time.

## How we built it
**Fire TV app.** Kotlin + Jetpack Compose for TV on Fire OS, D-pad first, designed on a 1440 × 810 canvas scaled to the panel so 720p, 1080p and 4K Fire TVs look identical. Real-time 3D (Filament via SceneView) for the coins, chest, padlock, trophy and stars; a **Unity 6** companion APK for Quiz Night, launched with the quiz as an intent extra; a looping animated home scene played with Media3.

**Serverless on AWS, end to end (one AWS CDK stack):**

| Layer | AWS service | What it does in Rosetta |
|---|---|---|
| Edge | **Amazon CloudFront + Amazon S3** | Serves the kid and parent phone pages (PWA, Web Push) |
| API | **Amazon API Gateway** (HTTP API) | One API for the TV and the phones |
| Compute | **AWS Lambda** | The API, the agent crew and scheduled jobs |
| Agents | **Strands Agents SDK** | *Quest Master* agent decides which specialist to run for each photo or voice note and handles edge cases |
| Models | **Amazon Bedrock** (Converse API, **Amazon Nova Pro**) | Every prompt and JSON schema is a Bedrock Converse tool spec; the crew was built and first deployed on Nova Pro |
| State | **Amazon DynamoDB** | One household document: kids, quests, habits, rewards, wishes, history |
| Media | **Amazon S3** | Presigned uploads straight from the phone → **S3 event** → agent Lambda; photos deleted after 1 day, memories kept 90 days |
| Schedules | **Amazon EventBridge** | Daily streak nudges and the weekly family digest |
| Alerts | **Amazon SES · Amazon SNS** | Email and SMS for parents (plus Web Push) |
| Delivery | **AWS CDK** | `cdk deploy` creates everything; pay-per-use, scales to zero |

**The agent crew.** AI Tutor (marking), Practice, Quizmaster, Scout (finds out-of-place objects with bounding boxes), Judge (before/after), Listener (voice notes), Proof check, Keeper (the friendly voice) and Coach (notes). Each is a single multimodal call with a forced JSON schema that must also say **what it actually saw or heard**, which is what makes the honest replies possible.

**Trust layer.** `mathcheck.py` computes exact answers with Python fractions and overrides the model's verdict on arithmetic; it also recognises classic slips ("you added the bottom numbers too") for precise hints. On our test sheets the model alone agreed with the true marking on 10 of 11 answers; with the check, 11 of 11.

## Challenges we ran into
- **AI that marks a child's homework has to be right.** Models read handwriting very well but can slip on "which is bigger, 2/3 or 3/5?". So the code decides arithmetic, and the model does what it's great at.
- **Honesty over magic.** Early versions answered a wrong photo with a generic "try again". Every check now returns what it saw, and every number on screen comes from the server's history.
- **Fire TV can't let an app lock other apps.** Earned TV time is honoured inside Rosetta (it opens Prime Video or YouTube and counts the minutes) and parents use Fire TV's own parental controls for the rest. This is our top feature request.
- **A real Fire TV is 960 × 540 dp.** Screens that looked right on a phone image at TV resolution overflowed on a true TV image; scaling one fixed canvas fixed every screen at once.
- **No Fire TV hardware and no Vega SDK on Windows**, so we built for Fire OS 16 on the Android TV emulator and tested on a 4K Android TV.
- **Packaging Strands for Lambda from Windows** silently installed an old version until we built the bundle with `uv` for the Lambda platform.

## Accomplishments that we're proud of
- A live pipeline: phone photo → S3 → Strands agent on Lambda → marked homework and practice on the TV in about fifteen seconds.
- An AI tutor that never gives the answer away, with maths it can't get wrong.
- A family loop that is real end to end: a parent adds a task, a child sends a voice note, the AI checks it, pocket money lands, the wish list fills, a parent approves, and TV time unlocks.
- A co-op family quiz in a real game engine, built from what the kids studied that week.
- 45 offline tests covering the API, habits, voice notes, TV time, wish list, agent policy and exact maths.

## What we learned
- Multimodal models are excellent readers of handwriting; pairing them with small exact checks makes them trustworthy enough for children.
- The TV is the right place for the shared family moment, and the phone is the right camera.
- Keep the agent layer model-agnostic: our prompts and schemas are Bedrock Converse tool specs, so the crew isn't tied to one model.

## What's next
- An **Amazon Kids "earn time" API**, so earned TV time is enforced, not honoured.
- Alexa voice answers during Quiz Night, reading-aloud fluency checks, more subjects, and a Vega OS build.
```

---

## Built with (up to 25 tags)
```
kotlin, jetpack-compose, compose-for-tv, fire-tv, fire-os, android, unity, c-sharp, filament, strands-agents, amazon-bedrock, amazon-nova, aws-lambda, amazon-api-gateway, amazon-dynamodb, amazon-s3, amazon-cloudfront, amazon-eventbridge, amazon-ses, amazon-sns, aws-cdk, python, typescript, javascript, media3
```

## "Try it out" links
```
https://dr8w3l96syzm7.cloudfront.net/tv.html
https://github.com/ashfaque-rifaye/project-rosetta
https://dr8w3l96syzm7.cloudfront.net/parent.html?code=DEMO
https://dr8w3l96syzm7.cloudfront.net/kid.html?code=DEMO
https://github.com/ashfaque-rifaye/compose-vision-overlay
```
The first link is the TV app install page on AWS: it hosts the TV app APK (already pointed at the live stack and the DEMO family), the optional Unity Quiz Night APK, and Fire TV / emulator install steps.

## Image gallery (up to 15; JPG/PNG under 5 MB; 3:2)
Upload all 15 from `submission/gallery/`, in file-name order. Each is 1800 × 1200 (3:2) with a caption:
`01-rosetta-on-the-family-tv` · `02-home-living-house-quest-board` · `03-ai-tutor-marks-handwritten-workbook` ·
`04-practice-reasoning-questions` · `05-coding-class-traced-line-by-line` · `06-honest-ai-wrong-photo` ·
`07-room-check-lost-things` · `08-voice-note-daily-habit` · `09-rewards-for-every-child` · `10-pocket-money-and-wish-list` ·
`11-tv-time-unlocked` · `12-family-quiz-night-unity-3d` · `13-progress-and-coach-notes` · `14-parents-scan-the-tv` ·
`15-architecture-serverless-on-aws`

## Video demo link
⬜ Upload `assets/demo/build/rosetta_demo_v3.mp4` (1080p, 2:56) to YouTube as **Public** or **Unlisted**, then paste the link.

## Upload a file (optional, 35 MB)
Upload `submission/Project-Rosetta-Submission.zip` (22 MB): `1-Project-Deck/` (PDF and PowerPoint, 17 slides) and
`2-Source-Code/Project-Rosetta-Source.zip` (the code; the heavy media stays in the GitHub repo).

---

## Additional info

| Field | Answer |
|---|---|
| Submitter type | ⬜ Individual |
| Organization name | `N/A` |
| Country of residence | ⬜ India |
| Province (Canada) | `N/A` |
| Primary track | **Fire TV** |
| Code repository | `https://github.com/ashfaque-rifaye/project-rosetta` (public, MIT license detected on GitHub) |
| New or existing before 31 Aug 2026 | **New** |
| AWS Builder Mini Challenge | **Yes** |
| Open Source Mini Challenge | **Yes** |
| Age of majority / eligible / not an employee | ⬜ tick all three if true for you |
| Level of learning | **Significant** |

### AWS Builder Mini Challenge: which AWS services did you incorporate and how?
```
Rosetta's backend is serverless on AWS, end to end, in one AWS CDK stack (infra/lib/rosetta-stack.ts):

- Strands Agents SDK: "Quest Master" is a Strands agent that decides which specialist tool to run for every photo or voice note (AI Tutor, Scout, Judge, Listener, Proof check, Keeper, Coach) and handles edge cases such as the wrong photo, low confidence or an already-tidy room (agents/rosetta_agents/crew.py).
- Amazon Bedrock (Converse API, Amazon Nova Pro): every prompt and JSON schema in the crew is written as a Bedrock Converse tool spec with forced tool choice, and Quest Master uses the Strands BedrockModel. The crew was built and first deployed on Nova Pro; Nova's 0-1000 grounding boxes drive the "Lost Things" overlay on the TV (agents/rosetta_agents/vision.py, tutor.py). The model provider is one CDK flag: `npx cdk deploy -c aiProvider=bedrock`.
- AWS Lambda: three functions: the HTTP API, the agent crew (triggered by S3 events) and scheduled jobs.
- Amazon API Gateway (HTTP API): one API for the Fire TV app and the phone pages.
- Amazon S3: presigned uploads straight from a child's phone (photos and voice notes), an S3 event notification starts the agent Lambda; lifecycle rules delete photos after 1 day and keep memories 90 days.
- Amazon DynamoDB: one household document per family (kids, quests, habits, rewards, pocket money, wish list, history).
- Amazon EventBridge: daily streak nudges and a weekly family digest.
- Amazon SES and Amazon SNS: email and SMS alerts to parents (alongside Web Push).
- Amazon CloudFront + S3: the kid and parent phone pages (PWA).
- AWS CDK: the whole stack deploys with one command; pay-per-use, scales to zero.

Development workflow: built with Claude Code as an agentic pair programmer.

The README's "Built on AWS" section maps each service to the file that uses it, with an architecture diagram.
```

### Open Source Mini Challenge
| Field | Answer |
|---|---|
| Contribution URL | `https://github.com/ashfaque-rifaye/compose-vision-overlay` |
| Project repository URL | `https://github.com/ashfaque-rifaye/project-rosetta` |
| GitHub username | `ashfaque-rifaye` |

**Description (what, how, why):**
```
What: two new open-source (MIT) repositories created during the hackathon. compose-vision-overlay is a Jetpack Compose library for Android TV and Fire TV that draws AI vision results (bounding boxes from multimodal models such as Amazon Nova on Bedrock) as a 10-foot overlay. Project Rosetta itself is open source too: the Fire TV app, the Strands agent crew, the CDK stack and the phone pages.

How it works: you pass the image and a list of detections; the overlay maps each box onto the image as it is fitted on screen, with staggered spring entrances so viewers can follow what the model found, attention pulses for unresolved items, and "resolved" exits where a badge floats away. It is purely visual, so it never steals D-pad focus, and it ships helpers for the common 0-1000 box conventions (x-y and y-x), unit tests and a sample TV app.

Why it matters: multimodal models now return grounded boxes, but every TV app would otherwise hand-build this rendering and its focus-safe animation. Rosetta uses it to pin "Lost Things" onto the real objects in a child's room photo.
```

### Feature requests (optional)
```
1. Amazon Kids "earn time" API (Critical): let an approved family app grant or extend a child profile's screen time. Rosetta's reward loop would become enforced instead of honour-based, turning restrict-only parental controls into motivation.
2. Sanctioned family overlay for approved apps (Important): a small, rate-limited banner API, so "Movie Night unlocked!" can appear while the family watches another app.
3. Vega SDK and Vega Virtual Device on Windows / WSL2 (Important): we couldn't target Vega at all from Windows.
4. Official Fire OS emulator images, x86_64 with Fire TV's real density and launcher (Important): testing on "Android TV ≈ Fire OS" is guesswork.
5. Amazon Bedrock: per-model "invocable for me" flag in ListFoundationModels (Important): models listed as ACTIVE that the account can't call cost us hours.
6. Amazon Polly neural voices for Tamil, Malayalam, Telugu and Bengali (Important): Fire TV has huge audiences in India.
```

### Friction log (optional, up to +10%): the field takes a URL
```
https://dr8w3l96syzm7.cloudfront.net/friction-log.html
```
20 entries on Fire TV, Fire OS, Compose for TV, Media3, Amazon Kids, Unity on Fire OS, Amazon Bedrock, the Strands Agents SDK,
API Gateway with the CDK, and Amazon Polly, each with task, steps, expected vs. actual, severity, workaround and suggestion.
The same log is `FRICTION_LOG.md` in the repo.

### Project testing link
```
https://dr8w3l96syzm7.cloudfront.net/tv.html
```

### Testing instructions or credentials
```
Everything runs live on AWS. Use the public DEMO family (Mom, Dad, Arjun in Grade 3, Meera in Grade 1). Parent PIN: 2468 (test data only).

1. Install the TV app from https://dr8w3l96syzm7.cloudfront.net/tv.html
   - Fire TV: enable Developer options > Install unknown apps, install the free "Downloader" app, enter
     dr8w3l96syzm7.cloudfront.net/downloads/rosetta-tv-demo.apk and install. Optional 3D Quiz Night:
     dr8w3l96syzm7.cloudfront.net/downloads/rosetta-quiz-night.apk
   - Or the Android TV emulator (system-images;android-36;android-tv;x86_64 = Fire OS 16): adb install rosetta-tv-demo.apk
2. Parent phone: https://dr8w3l96syzm7.cloudfront.net/parent.html?code=DEMO, sign in as Mom with PIN 2468.
   Tap "Unlock TV time now" and watch the TV portal open ("Lock it again" resets it).
3. Kid phone: https://dr8w3l96syzm7.cloudfront.net/kid.html?code=DEMO, pick Arjun. On the TV open "Fractions homework",
   then photograph any worksheet from the kid page: the AI Tutor marks it on the TV in about 15 seconds and sets practice.
   Send a photo of anything else and Rosetta says what it actually sees.
4. Daily task: send a voice note for "Read 10 pages" from the kid page; it appears as a memory on the parent page.
5. Pocket money: add a small wish on the parent page; when the child has saved enough they tap "Ask Mom & Dad" and Mom approves.
   The TV's Treasure screen updates.
6. Quiz Night: bottom bar on the TV > Quiz Night (8 questions written for the two children; opens the Unity game if installed).

The DEMO family is shared, so other testers may be using it at the same time. "Start the family over" is at the bottom of the
parent page. To build from source instead, see the README (./gradlew installDebug with the live URLs and -ProsettaCode=DEMO).
```

### Feedback Q1: Which developer tools, APIs and SDKs did you use, and for what?
```
- Fire TV / Fire OS and the Android TV emulator (API 36 = Fire OS 16): the whole TV app.
- Jetpack Compose for TV (tv-material): every screen, D-pad focus and animation.
- Media3 ExoPlayer: the intro film and the living home scene.
- Unity 6 LTS (URP): Family Quiz Night as a companion Fire OS app.
- Filament / SceneView: real-time 3D rewards on the TV.
- Strands Agents SDK: the Quest Master agent that orchestrates the crew.
- Amazon Bedrock (Converse API, Amazon Nova Pro): the crew's model layer and JSON tool schemas.
- AWS Lambda, Amazon API Gateway, Amazon S3, Amazon DynamoDB, Amazon EventBridge, Amazon SES, Amazon SNS, Amazon CloudFront, AWS CDK: the serverless backend.
- Amazon Devices Builder Tools (MCP): Fire TV documentation lookups while coding.
```

### Feedback Q2: What worked well?
```
- Fire OS: being Android meant Kotlin and Compose skills transferred directly; the FAQ allowing the Android TV emulator unblocked us without hardware.
- Compose for TV: declarative focus plus graphicsLayer made the camera that glides between rooms short to write.
- Strands Agents: @tool on plain Python functions plus a Bedrock model is very little code, and the specialists stay testable as normal functions.
- Amazon Bedrock Converse: forced tool choice is a clean way to get strict JSON from a multimodal model; Nova Pro's 0-1000 grounding maps straight onto the TV overlay; 2-4 s per photo.
- AWS CDK: one stack, one command; S3 events → Lambda is the simplest possible trigger for an agent run; BucketDeployment injected the API URL into the web config at deploy time.
- Unity 6: building the whole scene from C# made headless builds easy; one APK for ARM64 and x86_64.
```

### Feedback Q3: What needs work?
```
- No official Fire OS emulator image, and no Vega SDK on Windows.
- Apps can't grant or enforce screen time on Fire TV (our #1 request: an Amazon Kids earn-time API).
- adb screenrecord on the TV emulator only manages 2-5 fps; the emulator's own recorder is the fix but isn't documented for Fire TV.
- Bedrock: models listed as ACTIVE that the account can't invoke; the vision box convention isn't documented next to Converse images.
- Strands Agents: packaging for Lambda from Windows silently pulled an old version; a Lambda layer or recipe would help.
- tv-material 1.0 still needs an experimental opt-in for FilterChip; Compose on Android TV spends the first Back press clearing focus.
- Polly lacks neural voices for Tamil and Malayalam, although Transcribe understands both.
```

### Feedback Q4: Onboarding (zero to hello world)?
```
- Fire TV app on the emulator: about 1.5 hours including emulator troubleshooting; about 10 minutes for the app itself.
- Amazon Bedrock: about 30 minutes to the first Nova Pro call with boto3.
- Strands Agents: under an hour to a working agent with tools; packaging for Lambda took longer.
- AWS CDK: about an hour to the first deployed stack (npm cache had to move off a full C: drive).
- Unity 6 for Fire OS: half a day, mostly installer and build-target issues.
```

### Feedback Q5: Would you build with these again?
```
Yes. The living-room TV is the right screen for family moments, Fire OS's Android base makes it fast to build for, and a serverless AWS backend with Strands agents and Bedrock let one developer ship a real multimodal product in days. With an earn-time API for Amazon Kids profiles, Rosetta could enforce what it can only honour today.
```

### Which AI tools have you leveraged?
```
Claude Code (Anthropic) as a pair programmer for the Kotlin, Python, TypeScript and Unity code; Amazon Bedrock (Amazon Nova Pro) through the Strands Agents SDK in the product itself; generative image, video and music tools for the artwork, intro film and score (credited in CREDITS.md).
```

### Did you gain AI value you can use in your career?
```
Yes. Building a multimodal agent crew that children and parents can trust taught us how to pair models with exact checks, force structured output with tool schemas, keep the agent layer model-agnostic, and run it all serverless on AWS with infrastructure as code.
```
