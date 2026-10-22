# Product feedback: Project Rosetta

For each tool, API and SDK we used: what we used it for · what worked well · what needs work · onboarding · would we build with it again.
Detailed step-by-step problems are in [FRICTION_LOG.md](FRICTION_LOG.md).

---

## Fire TV / Fire OS (Android) + Android TV emulator
- **Used for:** the whole TV app: Kotlin, Compose for TV, D-pad focus, launcher banner, intents to hand off to a streaming app.
- **Worked well:** Fire OS being Android meant our Kotlin/Compose skills transferred directly; the FAQ explicitly allowing the Android TV emulator unblocked us (we have no Fire TV device). Focus handling with `tv-material` surfaces feels right for 10-foot UI.
- **Needs work:**
  - There is no official **Fire OS emulator image**; we had to guess that Android TV API 30 ≈ Fire OS 8 (F-002), and later that the Android TV API 36 x86_64 image ≈ Fire OS 16 (the overview page lists Fire OS 16 as Android 16).
  - The emulator path on Windows was rough: silent exit when the system drive was low on space, a hang in host-GPU mode on an RTX laptop (F-004, F-005).
  - The only 64-bit Android TV images are recent (API 36); the API 30 TV image is 32-bit x86, which Unity 6 can't target, so for a while we tested on a phone image at TV resolution and missed that a real 1080p Fire TV is only 960 × 540 dp (F-018). A short "screen sizes for Fire TV" note in the emulator guide would have caught it.
  - Third-party apps can't **enforce screen time** or **draw over other apps**, so Rosetta's "earned TV time" is honoured by parents rather than enforced. See the feature requests.
- **Onboarding (zero → hello world):** ~1.5 hours including emulator troubleshooting; ~10 minutes for the app itself.
- **Build again?** **Yes.** The living-room screen is the right place for family experiences, and Fire OS's Android base makes it fast.

## Vega SDK / Vega Virtual Device
- **Used for:** evaluated for a Vega OS build.
- **Needs work:** the SDK and simulator require macOS or Ubuntu. **No Windows support**, so we couldn't target Vega at all (F-001).
- **Build again?** Yes, once Windows/WSL2 is supported; React Native for Vega looks promising.

## Jetpack Compose for TV (`androidx.tv:tv-material` 1.0)
- **Used for:** every screen: surfaces with focus scale and borders, typography, animated transitions.
- **Worked well:** declarative focus + `graphicsLayer` made the "camera glides to the room" home screen and the glyph-carving animation short to write.
- **Needs work:** `FilterChip` still needs an experimental opt-in in a "1.0" artifact (F-006). We'd love an official D-pad focus-debugging overlay.
- **Build again?** Yes.

## Media3 (ExoPlayer, media3-ui)
- **Used for:** feasibility tests 1–2 (multi-audio + side-loaded WebVTT playback). Not in the final scope.
- **Worked well:** `MergingMediaSource` and `SubtitleConfiguration` did exactly what the docs say.
- **Needs work:** `SubtitleView` doesn't reposition a visible cue when padding changes (F-007).

## Amazon Devices Builder Tools (MCP)
- **Used for:** Fire TV documentation lookups while building.
- **Worked well:** it's convenient to have device docs inside the coding assistant.
- **Needs work:** more Compose-for-TV examples (most samples are React Native).

## Amazon Bedrock: Converse API + Amazon Nova Pro
- **Used for:** the agent crew's vision: Scout finds out-of-place objects with bounding boxes, Judge compares before/after photos item by item with a confidence score, Keeper writes the spoken line. Forced tool schemas give us strict JSON.
- **Worked well:** Converse with `toolChoice: {tool}` is a clean way to get structured output from a multimodal model; Nova Pro's 0–1000 grounding convention maps straight onto our TV overlay; latency of ~2–4 s per photo is fine for a "the crew is looking…" moment.
- **Needs work:** **model access is confusing**. The console and `ListFoundationModels` show models as ACTIVE that the account can't invoke. Anthropic models failed with `INVALID_PAYMENT_INSTRUMENT` intermittently, one even succeeding once and then failing minutes later (F-008). An "is this model invocable for me?" API would save hours.
- **Onboarding:** ~30 minutes to the first Nova call.
- **Build again?** Yes.

## Strands Agents SDK
- **Used for:** Quest Master, the coordinator agent that picks Scout / Judge / Router / Complete / Keeper tools based on the quest state and handles edge cases (not a room, same photo twice, low confidence).
- **Worked well:** `@tool` on plain Python functions plus a Bedrock model is very little code, and our specialists stayed testable as normal functions (we test the same policy deterministically offline).
- **Needs work:** packaging for Lambda without Docker took some trial and error. `pip install --platform manylinux…` evaluates dependency markers for the *build* machine, so on Windows the `mcp` dependency demanded `pywin32` and pip silently fell back to strands-agents 1.1.0 (no Gemini provider); `uv pip install --python-platform x86_64-manylinux2014` resolves correctly (F-017). A documented "package Strands for Lambda" recipe (or a Lambda layer) would help.
- **Also worked well:** swapping the model provider was one line (`BedrockModel` → `GeminiModel`) with the same `@tool` functions, which is exactly what a model-agnostic agent SDK should feel like.
- **Build again?** Yes.

## Google Vertex AI: Gemini 2.5 Flash + Gemini TTS (third-party, used since 22 Oct)
- **Used for:** the crew's model calls (Tutor, Practice, Quizmaster, Scout, Judge, Keeper, Digest, Quest Master via Strands) and the Keeper's voice, called from AWS Lambda through Workload Identity Federation (no keys).
- **Worked well:** the same JSON schemas we wrote as Bedrock tool specs work as Gemini response schemas; the TTS voices are warm enough for a children's storyteller; keyless federation from AWS roles worked first time once the subject mapping was right.
- **Needs work:** the default AWS attribute mapping puts the full assumed-role ARN in `google.subject`, which exceeds Google's 127-byte limit for Lambda roles (F-019); Gemini TTS has a small per-minute quota by default (F-020); box quality depended on prompt wording (F-021).
- **Build again?** Yes, behind the same provider switch.

## Unity 6 (6000.3 LTS, URP) as a Fire OS companion app
- **Used for:** the Stone Trial: the family quiz on a carved stone with bloom, particles and igniting glyphs. The TV app launches it with the quiz as an intent extra and receives the answers as the activity result.
- **Worked well:** building the whole scene from C# means it builds headless from a script; ARM64 + x86_64 in one APK covers Fire TV hardware and emulators.
- **Needs work:** IL2CPP code stripping removed colliders created only from code (`stripEngineCode = false` and `link.xml` fixed it); launching with the launcher intent's `NEW_TASK` flag silently cancels the activity result; Unity 6 can't target 32-bit x86, which rules out the API 30 Android TV image; the Hub's installer needed an admin prompt nobody saw (we ran the signed installers silently).
- **Build again?** Yes, for moments that deserve a real game engine.

## AWS Lambda · Amazon S3 · Amazon DynamoDB · API Gateway (HTTP API) · Amazon CloudFront · AWS CDK
- **Used for:** S3 presigned uploads straight from the kid's phone (1-day lifecycle for privacy), S3 event → agent Lambda, a single-document household model in DynamoDB, an HTTP API for the TV and phones, and CloudFront for HTTPS phone pages. Everything is in one CDK stack.
- **Worked well:** CDK's `BucketDeployment` with `Source.data` let us inject the API URL into the web config at deploy time; S3 events → Lambda is the simplest possible trigger for an agent run.
- **Needs work:** first-time CDK on a Windows laptop with a nearly full C: drive needed the npm cache redirected.
- **Build again?** Yes.

## Amazon Polly / Amazon Transcribe
- **Used for:** feasibility tests (Polly Hindi voice, Transcribe language ID: hi-IN at confidence 1.00 in 18 s).
- **Needs work:** **no Tamil/Malayalam neural voices** in Polly although Transcribe understands them (F-009).

## Google Flow (asset generation; not an Amazon tool)
- **Used for:** all artwork (miniature home, Lost Things characters, Family Stone, demo room photos).
- **Note:** a visible watermark is mandatory in our region; we kept it.

---

# Feature requests

| # | Request | Why it matters for Rosetta | Priority |
|---|---|---|---|
| 1 | **Amazon Kids "earn time" API**: let an approved family app grant or extend a child profile's screen time | Rosetta's reward loop could become *enforced* instead of honour-based; it turns restrict-only parental controls into motivation | **Critical** |
| 2 | **Sanctioned family overlay** (e.g. a small, rate-limited banner API for approved apps) | Show "Movie night unlocked!" or "the Keeper has a new quest" while the family watches another app | Important |
| 3 | **Vega SDK + Vega Virtual Device on Windows / WSL2** | We couldn't target Vega at all from Windows | Important |
| 4 | **Official Fire OS emulator images** (x86_64, with Fire TV's real density and launcher) | Testing on "Android TV ≈ Fire OS" is guesswork, and judges ask to see the app "on a Fire TV" | Important |
| 5 | **Bedrock: per-model invocability in `ListFoundationModels`** | Hours lost to models that are "ACTIVE" but not callable | Important |
| 6 | **Polly neural voices for Tamil, Malayalam, Telugu, Bengali** | Fire TV has huge audiences in India | Important |
