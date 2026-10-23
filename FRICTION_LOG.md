# Friction Log: Project Rosetta

Friction we hit while building a Fire TV app with a serverless AWS agent backend for the Amazon Developer **Build, Ship, Shape** hackathon. Every entry was written down when it happened and follows the hackathon format: **task attempted · steps taken · expected vs. actual · severity · workaround · actionable suggestion**.

Severity scale: **Blocker** (no way forward) · **Major** (cost an hour or more, or changes the product) · **Minor** (cost a test cycle) · **Papercut** (annoying, quick to fix).

Also published at https://dr8w3l96syzm7.cloudfront.net/friction-log.html

| # | Area | Entry | Severity |
|---|---|---|---|
| F-01 | Fire TV · Vega | Vega SDK and Vega Virtual Device unavailable on Windows | Major |
| F-02 | Fire TV · Fire OS | No official Fire OS emulator image | Minor |
| F-03 | Fire TV · getting started | SDK Manager silently skipped the TV system image until licenses were accepted | Papercut |
| F-04 | Fire TV · emulator | TV emulator exits silently when the system drive is low on space | Major |
| F-05 | Fire TV · emulator | Host-GPU mode hangs the TV emulator at renderer init | Major |
| F-06 | Fire TV · emulator | A phone image at TV resolution hides Fire TV's real density | Major |
| F-07 | Fire TV · emulator | adb screenrecord captures only 2-5 frames a second | Major |
| F-08 | Fire TV · testing | Background dexopt restarts the app right after install | Minor |
| F-09 | Fire TV · Compose for TV | tv-material 1.0 still gates FilterChip behind an experimental opt-in | Papercut |
| F-10 | Fire TV · Compose for TV | The first Back press only clears focus | Minor |
| F-11 | Fire TV · Media3 | SubtitleView doesn't move a visible cue when padding changes | Minor |
| F-12 | Fire TV · Fire OS / Amazon Kids | No way for an app to grant or gate screen time | Major |
| F-13 | Fire TV · Unity | Unity 6 can't target the 32-bit TV image used for Fire OS 8 | Minor |
| F-14 | Fire TV · Unity | Unity Hub's silent install fails without an interactive admin prompt | Major |
| F-15 | Amazon Bedrock | Models listed as ACTIVE fail with INVALID_PAYMENT_INSTRUMENT, intermittently | Major |
| F-16 | Amazon Bedrock · Nova Pro | The vision bounding-box convention isn't next to the Converse image docs | Papercut |
| F-17 | Amazon Bedrock · Nova Pro | Arithmetic verdicts when marking homework | Major |
| F-18 | Strands Agents SDK | pip --platform silently installs an old Strands for Lambda when building on Windows | Major |
| F-19 | Amazon API Gateway · AWS CDK | HTTP API catch-all route swallows the CORS preflight | Minor |
| F-20 | Amazon Polly | No neural voices for Tamil or Malayalam, though Transcribe understands both | Major |
| F-21 | Amazon Bedrock · Nova 2 Lite | A new account gets 20 requests a minute, so one busy minute throttles an agent crew | Major |
| F-22 | Amazon Bedrock · Nova 2 Lite | Reasoning settings have hidden rules that only show up as errors | Minor |

---

## Fire TV and Fire OS

### F-01: Vega SDK and Vega Virtual Device unavailable on Windows
- **Tool:** Vega SDK (Vega Developer Tools, Vega Virtual Device)
- **Task:** Build the Fire TV app for Vega OS and run it in the Vega simulator.
- **Steps:** Hackathon Resources page → Vega install docs (0.22–0.24) → system requirements.
- **Expected:** An install path for Windows, the most common developer desktop.
- **Actual:** Only macOS 10.15+ and Ubuntu 20.04+ (KVM) are supported; no Windows or WSL2 guidance.
- **Severity:** Major. It pushed us onto Fire OS, so we couldn't demo on Vega at all.
- **Workaround:** Built natively for Fire OS (Kotlin + Compose for TV) and tested on the Android TV emulator, as the FAQ allows.
- **Suggestion:** Ship a Windows build of the Vega Virtual Device, or document a supported WSL2 + nested-virtualization path. At minimum, state Windows status on the hackathon Resources page.

### F-02: No official Fire OS emulator image
- **Tool:** Fire TV / Fire OS developer tooling
- **Task:** Run the app on something representative of a Fire TV without hardware.
- **Steps:** Searched the Android SDK Manager and the Fire TV docs for a Fire OS system image.
- **Expected:** A Fire OS emulator image, as there once was for Fire tablets.
- **Actual:** Only generic Android TV and Google TV images. We had to infer that Android TV API 30 ≈ Fire OS 8 and API 36 ≈ Fire OS 16.
- **Severity:** Minor
- **Workaround:** Android TV `android-36;android-tv;x86_64`, 1080p, 320 dpi (the same 960 × 540 dp as a 1080p Fire TV).
- **Suggestion:** Publish x86_64 Fire OS emulator images, or a table mapping each Fire OS version to the closest Android TV image.

### F-03: SDK Manager silently skipped the TV system image until licenses were accepted
- **Tool:** Android SDK `sdkmanager` (CLI), on the Fire TV getting-started path
- **Task:** Install `system-images;android-30;android-tv;x86` from the command line.
- **Steps:** `sdkmanager "system-images;android-30;android-tv;x86"` on a fresh SDK.
- **Expected:** The image installs, or a clear "license not accepted" error.
- **Actual:** Exit code 0, the platform installed, the system image missing. It only worked after `sdkmanager --licenses`.
- **Severity:** Papercut
- **Workaround:** `yes | sdkmanager --licenses` first.
- **Suggestion:** Add the license step to the Fire TV command-line quick start.

### F-04: TV emulator exits silently when the system drive is low on space
- **Tool:** Android Emulator with an Android TV image
- **Task:** Boot a TV AVD created with `avdmanager ... -d tv_1080p`.
- **Steps:** `emulator -avd FireTV_API30` from a script, then waited for `sys.boot_completed`.
- **Expected:** The emulator boots, or shows an error.
- **Actual:** The process exited immediately with no window. Only `-verbose` revealed `FATAL | Not enough space to create userdata partition ... need 7372.80 MB`: the default TV AVD reserves a 7.2 GB data partition on C:.
- **Severity:** Major (about 10 minutes lost; it looked like a hang).
- **Workaround:** Moved the AVD to another drive and set `disk.dataPartition.size=4G`.
- **Suggestion:** Recommend a smaller data partition for TV AVDs in the Fire TV emulator guide, and mention `-verbose` for diagnosing silent exits.

### F-05: Host-GPU mode hangs the TV emulator at renderer init
- **Tool:** Android Emulator 36.3.10, `-gpu auto`, Windows laptop with an NVIDIA RTX 4060 (hybrid graphics)
- **Task:** Boot the Android TV API 30 AVD.
- **Steps:** `emulator -avd FireTV_API30 -gpu auto`.
- **Expected:** Boot with hardware rendering.
- **Actual:** The log stops after "Selecting Vulkan device"; qemu stays alive, never registers with adb and shows no window for over 6 minutes.
- **Severity:** Major
- **Workaround:** `-gpu swiftshader_indirect` for the API 30 image; the API 36 x86_64 TV image later ran fine with `-gpu host`.
- **Suggestion:** Document the recommended `-gpu` flag for TV AVDs on Windows laptops with hybrid GPUs.

### F-06: A phone image at TV resolution hides Fire TV's real density
- **Tool:** Android emulator (`android-36;google_apis_playstore` with the `tv_1080p` profile vs `android-36;android-tv;x86_64`)
- **Task:** Check the 10-foot layouts at Fire TV's real screen size.
- **Steps:** Built the screens on a phone image set to 1920 × 1080, then installed the same APK on a true Android TV image.
- **Expected:** The same layout on both.
- **Actual:** A real TV image is 1920 × 1080 at 320 dpi = 960 × 540 dp (like a 1080p Fire TV). The homework screen overflowed and Home showed a single quest card.
- **Severity:** Major (the demo would have looked broken on a real Fire TV).
- **Workaround:** Draw every screen on a fixed 1440 × 810 canvas and scale it to the display (`LocalDensity` override): identical on 720p, 1080p and 4K.
- **Suggestion:** State Fire TV's dp size and density on the "Use an Android TV emulator" page, and recommend an x86_64 TV image.

### F-07: adb screenrecord on the TV emulator captures only 2-5 frames a second
- **Tool:** Android TV emulator (API 36, our Fire OS 16 stand-in), `adb shell screenrecord`
- **Task:** Record a smooth demo video of the app for the submission.
- **Steps:** `adb shell screenrecord --bit-rate 20000000 --time-limit 60 /sdcard/shot.mp4` while driving the app with `adb shell input keyevent`.
- **Expected:** 30 fps footage.
- **Actual:** The recorder encodes inside the emulator and competes with the app: the footage had 2-5 distinct frames a second and the video stuttered. The default TV AVD (4 cores, 2 GB) also drew the app at about 10 fps (`dumpsys gfxinfo`: 97 ms median frame).
- **Severity:** Major for demo videos.
- **Workaround:** The emulator's own host-side recorder, `adb emu screenrecord start --fps 30 <file>.webm`, with the AVD raised to 8 cores and 6 GB: a steady 30 fps and a 32 ms median frame.
- **Suggestion:** Recommend the host-side recorder and a TV-sized AVD profile in the Fire TV emulator docs; many hackathon entrants record their demo this way.

### F-08: Background dexopt restarts the app right after install
- **Tool:** Android TV emulator / Fire OS app install path
- **Task:** Install the debug APK, launch it, and drive the flow with `adb shell input keyevent`.
- **Steps:** `adb install`, `am start`, then scripted key presses.
- **Expected:** Key presses reach the app.
- **Actual:** About 30 s after install the system ran `dexopt` on the new APK and restarted the app process; the scripted keys landed on the launcher (Settings opened). Nothing in our log explained it.
- **Severity:** Minor (it cost a test cycle and looked like a crash).
- **Workaround:** `adb shell cmd package compile -m speed -f <package>` right after install, then launch.
- **Suggestion:** Mention this in the Fire TV "test with adb" docs, especially for automated testing and demo recording.

### F-09: tv-material 1.0 still gates FilterChip behind an experimental opt-in
- **Tool:** `androidx.tv:tv-material:1.0.0` (Compose for TV)
- **Task:** D-pad-selectable chips.
- **Steps:** Used `FilterChip` from the 1.0 "stable" artifact.
- **Expected:** A stable API compiles without opt-ins.
- **Actual:** Compile error "This tv-material API is experimental", with no hint in the Fire TV Compose docs.
- **Severity:** Papercut
- **Workaround:** `@file:OptIn(ExperimentalTvMaterial3Api::class)`.
- **Suggestion:** List which tv-material components are still experimental in Fire TV's Compose for TV guide.

### F-10: On a TV image, Compose spends the first Back press on clearing focus
- **Tool:** Jetpack Compose for TV (`tv-material`), Android TV API 36 image
- **Task:** Leave a screen with one press of the remote's Back button.
- **Steps:** A screen with a focused card and a `BackHandler`; press Back once.
- **Expected:** The `BackHandler` runs on the first press.
- **Actual:** The first press only removed focus from the card; the second press navigated. On a phone image it behaved as expected, so we only saw it on a TV image.
- **Severity:** Minor (testers pressing Back once think the app is stuck).
- **Workaround:** Intercept `KEYCODE_BACK` in a root `onPreviewKeyEvent` and navigate on key-up.
- **Suggestion:** Document the TV focus-on-Back behaviour next to `BackHandler` in the Fire TV Compose guide, or offer an opt-out.

### F-11: Media3 SubtitleView doesn't move an already-visible cue when its padding changes
- **Tool:** `androidx.media3:media3-ui:1.8.0`, recommended by the Fire TV docs for playback
- **Task:** Lift subtitles above an on-screen control bar while the bar is visible.
- **Steps:** `subtitleView.setBottomPaddingFraction()` when the bar appears.
- **Expected:** The current cue moves up.
- **Actual:** The cue stays put until the next cue arrives, overlapping the bar.
- **Severity:** Minor
- **Workaround:** Render cues ourselves in Compose from `Player.Listener.onCues`.
- **Suggestion:** A Fire TV sample showing Compose-rendered captions with D-pad interaction.

### F-12: No way for an app to grant or gate screen time on Fire TV
- **Tool:** Fire OS (Fire TV), Amazon Kids
- **Task:** Unlock a family's TV time only after today's tasks are done ("no TV until homework is finished").
- **Steps:** Looked for a launcher-replacement option, an app-usage API and an Amazon Kids API for granting time.
- **Expected:** Some sanctioned way for a parent-approved app to grant screen time.
- **Actual:** Fire TV doesn't allow replacing the home screen, and no API lets an app lock or time-limit other apps. Amazon Kids profiles have time limits, but nothing an app can grant ("earned 45 minutes").
- **Severity:** Major for this use case (the core promise can only be honoured, not enforced).
- **Workaround:** Rosetta unlocks TV time inside the app, launches Prime Video, YouTube or Netflix and counts the minutes; the parent page walks parents through Fire TV's parental controls (PIN-protected app launches) for the rest.
- **Suggestion:** An Amazon Kids "grant screen time" API that a parent-approved app can call (our #1 feature request).

### F-13: Unity 6 can't target the 32-bit TV image used for Fire OS 8
- **Tool:** Unity 6 Android player, Android TV emulator `android-30;android-tv;x86`
- **Task:** Run the Unity Quiz Night companion on our Fire OS 8 stand-in.
- **Steps:** Built the Unity APK and installed it on the API 30 TV image.
- **Expected:** It installs and runs.
- **Actual:** Unity 6 supports ARM64, ARMv7 and x86_64 only; the Fire OS 8-like TV image is 32-bit x86, so the APK has no matching ABI there.
- **Severity:** Minor
- **Workaround:** An x86_64 AVD (API 36, 1080p) for the Unity companion; ARMv7 + ARM64 for real Fire TV devices.
- **Suggestion:** Pairs with F-02: an official x86_64 Fire OS emulator image would cover native and Unity apps.

### F-14: Unity Hub's silent install fails without an interactive admin prompt
- **Tool:** Unity Hub CLI (`--headless install`), Unity 6000.3 LTS, Windows 11
- **Task:** Install the Unity editor and Android Build Support for the Fire OS companion app.
- **Steps:** `Unity Hub.exe -- --headless install --version 6000.3.x --module android`.
- **Expected:** An unattended install.
- **Actual:** The 7 GB download completed, then the install failed with "The Windows elevation prompt was cancelled or timed out" because no one was there to accept UAC.
- **Severity:** Major (about 30 minutes, and it nearly filled the system drive).
- **Workaround:** Ran the downloaded, signed installers silently as the current user and unpacked the NDK/JDK by hand.
- **Suggestion:** Document the minimum Unity setup for Fire OS (modules, ABIs) on the Fire TV game developer page.

## Amazon Bedrock and the Strands Agents SDK

### F-15: Bedrock models listed as ACTIVE fail with INVALID_PAYMENT_INSTRUMENT, intermittently
- **Tool:** Amazon Bedrock (Converse API), Anthropic models via AWS Marketplace, us-east-1
- **Task:** Call a Claude model from the agent crew.
- **Steps:** `list_foundation_models` and `list_inference_profiles` show 15 Claude models as ACTIVE; probed each with a 5-token Converse call.
- **Expected:** Either the listed models are invocable, or the listing says which ones aren't.
- **Actual:** Everything is listed as ACTIVE, but invocation fails three different ways: "not available for this account", `INVALID_PAYMENT_INSTRUMENT`, and one model that **succeeded once, then failed** with `INVALID_PAYMENT_INSTRUMENT` about 3 minutes later.
- **Severity:** Major. It blocked the planned model, and the intermittent success looked like a bug in our code.
- **Workaround:** Built the crew on **Amazon Nova Pro** (first-party, no Marketplace subscription) and kept every prompt model-agnostic.
- **Suggestion:** Return per-model invocability (entitlement + Marketplace status) from `ListFoundationModels`, and fail consistently instead of letting a first call through.

### F-16: Nova's bounding-box convention isn't next to the Converse image docs
- **Tool:** Amazon Bedrock, Amazon Nova Pro (Converse with image input)
- **Task:** Get grounded boxes for out-of-place objects to draw on the TV.
- **Steps:** Followed the Converse API image examples and asked for boxes in a tool schema.
- **Expected:** The coordinate system documented with the image block.
- **Actual:** The convention (integers on a 0–1000 grid, `[x1, y1, x2, y2]`) is only in the Nova user guide, so our first schema guessed pixels.
- **Severity:** Papercut
- **Workaround:** State the 0–1000 convention in the tool schema description and normalise in code.
- **Suggestion:** A "grounding" example on the Converse API page for each vision model.

### F-17: Amazon Nova Pro arithmetic when marking homework
- **Tool:** Amazon Bedrock, Amazon Nova Pro (Converse, image + forced tool schema)
- **Task:** Mark a child's handwritten fractions worksheet.
- **Steps:** Sent the worksheet photo with a marking schema (question, child's answer, correct?, hint).
- **Expected:** Correct verdicts on simple fraction comparisons.
- **Actual:** Every handwritten answer was transcribed correctly, but one verdict was wrong ("which is bigger, 2/3 or 3/5?" marked incorrect); asking for working first didn't fully fix it. Generated quiz options were biased toward option A and once had two correct choices.
- **Severity:** Major for an education product (a wrong mark can mislead a child).
- **Workaround:** Exact checking in code for arithmetic (Python `Fraction`, `mathcheck.py`); server-side shuffling and validation of generated questions, regenerating when too few pass.
- **Suggestion:** A Bedrock sample showing "model reads, code verifies" for education, and an optional calculator tool for Nova.

### F-18: pip --platform silently installs an old Strands Agents for Lambda when building on Windows
- **Tool:** Strands Agents SDK, packaged for AWS Lambda with `pip install --platform manylinux2014_x86_64 --only-binary=:all:`
- **Task:** Bundle the latest strands-agents into a Lambda zip without Docker.
- **Steps:** Ran the pip command on Windows 11 into a target folder, zipped it, deployed with CDK.
- **Expected:** The latest strands-agents for Linux.
- **Actual:** pip evaluates dependency markers for the build machine, so `mcp`'s `pywin32; sys_platform == "win32"` dependency had no Linux wheel and pip backtracked to strands-agents 1.1.0 without an error. The Lambda then failed at import time on a module that only exists in newer releases.
- **Severity:** Major (silent; only visible at runtime).
- **Workaround:** `uv pip install --python-platform x86_64-manylinux2014 --python-version 3.12 --only-binary :all:`, which resolves markers for the target platform.
- **Suggestion:** A "package Strands Agents for Lambda" page (or an official Lambda layer) that warns about cross-platform resolution.

## AWS services

### F-19: API Gateway HTTP API catch-all route swallows the CORS preflight
- **Tool:** Amazon API Gateway HTTP API via AWS CDK (`corsPreflight` + an `ANY /{proxy+}` route)
- **Task:** Let the phone pages (CloudFront origin) call the API.
- **Steps:** `new apigw.HttpApi(..., { corsPreflight })` plus `addRoutes({ path: "/{proxy+}", methods: [ANY] })`.
- **Expected:** With `corsPreflight` set, API Gateway answers `OPTIONS` itself.
- **Actual:** The `ANY` route also matches `OPTIONS`, so the preflight reached the Lambda, which returned 404, and every browser call failed with only "CORS policy" in the console.
- **Severity:** Minor
- **Workaround:** The Lambda answers `OPTIONS` with 204 and the CORS headers.
- **Suggestion:** A synth-time warning (or a note in the `HttpApi` docs) when an `ANY` route coexists with `corsPreflight`.

### F-20: Amazon Polly has no neural voice for Tamil or Malayalam
- **Tool:** Amazon Polly (with Amazon Transcribe for comparison)
- **Task:** Speak family messages in a grandparent's language (for example, an English reply read out in Tamil).
- **Steps:** Listed Polly voices by language; tested Transcribe language identification on the same languages.
- **Expected:** Voices for the major Indian languages Transcribe understands.
- **Actual:** Transcribe handles ta-IN, ml-IN, te-IN, kn-IN and more, but among Indian languages Polly speaks only hi-IN and en-IN.
- **Severity:** Major for families in South India.
- **Workaround:** Tamil and Malayalam as on-screen text; Hindi and English get a voice.
- **Suggestion:** Neural voices for Tamil, Malayalam, Telugu and Bengali: hundreds of millions of speakers, many of them Fire TV customers in India.

### F-21: Amazon Nova 2 Lite starts at 20 requests a minute
- **Tool:** Amazon Bedrock, Amazon Nova 2 Lite (`us.` cross-region inference profile), called from AWS Lambda and the Strands Agents SDK
- **Task:** Move the whole agent crew to a small, low-cost model and run a family's homework, room check and voice note back to back.
- **Steps:** Deployed with `us.amazon.nova-2-lite-v1:0`; ran our live end-to-end test (about six model calls per photo: the Quest Master's turns plus the specialists).
- **Expected:** A quota in line with Nova Lite (200 cross-region requests a minute on the same account).
- **Actual:** `ThrottlingException` on the fourth flow. Service Quotas shows 20 requests a minute for Nova 2 Lite on both the `us.` and `global.` profiles, against 8,000,000 tokens a minute, so requests run out long before tokens do. A voice note was transcribed but couldn't be summarised.
- **Severity:** Major for any agent product (one user action fans out into several calls).
- **Workaround:** The Quest Master uses the `global.` profile (its own quota), specialists use `us.`, retries go up to six, and a throttled call overflows to Amazon Nova Lite.
- **Suggestion:** Raise the default request quota for Nova 2 Lite to match Nova Lite, or name the quota in the `ThrottlingException` message so developers know which one to request.

### F-22: Nova 2 Lite reasoning settings have rules that only errors reveal
- **Tool:** Amazon Bedrock Converse, Amazon Nova 2 Lite `reasoningConfig`
- **Task:** Turn on a little extended reasoning for marking homework and tracing code, with a forced tool choice for strict JSON.
- **Steps:** Tried `maxReasoningEffort` low, medium and high, each with `toolChoice: {tool}` and our usual `temperature` and `maxTokens`.
- **Expected:** One documented table of which inference settings go with which effort.
- **Actual:** `high` fails with "`maxTokens` must be unset"; `medium` sometimes spends the whole budget thinking and returns no tool call; Nova Pro rejects `reasoningConfig` outright. `low` with forced tool choice works well (our sample sheets marked exactly like the answer key).
- **Severity:** Minor
- **Workaround:** `low` effort only for marking, no `temperature` when reasoning, and a retry without reasoning if the model refuses it or ends without an answer.
- **Suggestion:** A compatibility table on the Nova 2 reasoning page (effort × maxTokens × temperature × toolChoice), and a validation error that names the model when reasoning isn't supported.
