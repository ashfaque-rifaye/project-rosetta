# Friction Log: Project Rosetta

Submitted with the Devpost entry (worth up to a 10% judging bonus). One entry per snag, written down when it happens.

Severity: **Blocker** · **Major** · **Minor** · **Papercut**

---

### F-001: Vega SDK / Vega Virtual Device unavailable on Windows
- **Tool:** Vega SDK (Vega Developer Tools, VVD)
- **Task:** Build the Fire TV app for Vega OS and run it in the Vega simulator.
- **Steps:** Read the hackathon Resources page → Vega install docs (0.22–0.24).
- **Expected:** An install path for Windows, the most common developer desktop OS.
- **Actual:** System requirements list only macOS 10.15+ and Ubuntu 20.04+ (KVM). No Windows or WSL2 guidance.
- **Severity:** Major. It pushed us onto Fire OS, so we can't demo on Vega at all.
- **Workaround:** Build natively for Fire OS (Kotlin + Compose for TV) and test on the Android TV emulator, as the FAQ allows.
- **Suggestion:** Ship a Windows build of VVD, or document a supported WSL2 + nested-virtualization path. At minimum, state Windows status on the hackathon Resources page.

### F-002: No official Fire OS emulator image
- **Tool:** Fire TV / Fire OS tooling
- **Task:** Run the app on something representative of a Fire TV without hardware.
- **Steps:** Looked for a Fire OS system image in the Android SDK Manager and in Amazon docs.
- **Expected:** A "Fire OS 8" emulator image (as there once was for Fire tablets).
- **Actual:** Only generic Android TV and Google TV images. We picked `android-30;android-tv;x86` because Fire OS 8 is based on Android 11, which is a guess on our part.
- **Severity:** Minor
- **Workaround:** Android TV API 30 image, 1080p, density 320 (matches a 1080p Fire TV Stick at 960×540 dp).
- **Suggestion:** Publish a Fire OS emulator image, or at least a doc page saying which AOSP TV image best mirrors each Fire OS version.

### F-003: SDK Manager silently skipped the system image until licenses were accepted
- **Tool:** Android SDK `sdkmanager` (CLI)
- **Task:** Install `system-images;android-30;android-tv;x86` from the command line.
- **Expected:** Install, or a clear "license not accepted" error.
- **Actual:** Exit code 0, platform installed, system image missing. It only worked after `sdkmanager --licenses`.
- **Severity:** Papercut. Not Amazon's tool, but it's on the Fire TV getting-started path.
- **Workaround:** `yes | sdkmanager --licenses` first.
- **Suggestion:** Amazon's Fire TV CLI quick-start could include the license step.

### F-004: TV emulator silently fails to start when the system drive is low on space
- **Tool:** Android Emulator (Android TV image), on the Fire TV getting-started path
- **Task:** Boot the TV emulator created with `avdmanager ... -d tv_1080p`.
- **Steps:** `emulator -avd FireTV_API30` launched from a script → waited 6 min for `sys.boot_completed`.
- **Expected:** Boot, or a visible error.
- **Actual:** The process exited immediately with no window. Only `-verbose` revealed `FATAL | Not enough space to create userdata partition ... need 7372.80 MB`. The default TV AVD reserves a 7.2 GB data partition, and avdmanager places it on C: (`disk.dataPartition.path=<temp>`).
- **Severity:** Major (lost ~10 minutes and looked like a hang)
- **Workaround:** Moved the AVD to another drive, set `disk.dataPartition.size=4G`, and removed the `<temp>` data path.
- **Suggestion:** The Fire TV docs could recommend a smaller data partition for TV AVDs and mention `-verbose` for diagnosing silent exits.

### F-005: Host-GPU mode hangs the TV emulator at renderer init
- **Tool:** Android Emulator 36.3.10, `-gpu auto`, NVIDIA RTX 4060 Laptop (Vulkan 1.4)
- **Task:** Boot the Android TV API 30 AVD.
- **Actual:** The log stops after "Selecting Vulkan device". qemu stays alive at ~350 MB, never registers with adb, and shows no window, for over 6 min.
- **Severity:** Major
- **Workaround:** `-gpu swiftshader_indirect`. That got past rendering, then hit host memory limits (see the note below; not an Amazon issue).
- **Suggestion:** Document the recommended `-gpu` flag for TV AVDs on Windows laptops with hybrid GPUs.

### F-006: tv-material 1.0 "stable" still gates FilterChip behind an experimental opt-in
- **Tool:** `androidx.tv:tv-material:1.0.0` (Compose for TV)
- **Task:** Language chips in the player (D-pad selectable).
- **Actual:** Compile error "This tv-material API is experimental" for `FilterChip`, with no hint in the Fire TV Compose docs.
- **Severity:** Papercut
- **Workaround:** `@file:OptIn(ExperimentalTvMaterial3Api::class)`
- **Suggestion:** Fire TV's Compose-for-TV guide could list which tv-material components are still experimental.

### F-007: Media3 `SubtitleView` doesn't move an already-visible cue when its bottom padding changes
- **Tool:** `androidx.media3:media3-ui:1.8.0`, which Fire TV docs recommend for playback
- **Task:** Lift subtitles above an on-screen control bar while the bar is visible.
- **Expected:** `setBottomPaddingFraction()` repositions the current cue.
- **Actual:** The cue stays put until the next cue arrives, so it overlaps the bar.
- **Severity:** Minor
- **Workaround:** Hide `SubtitleView` and render cues ourselves in Compose from `Player.Listener.onCues`. This turned out better anyway, since it lets us make individual words focusable for Rosetta Notes.
- **Suggestion:** A Fire TV sample showing Compose-rendered captions with D-pad interaction.

### F-008: Bedrock Claude access flips from working to `INVALID_PAYMENT_INSTRUMENT` with no console signal
- **Tool:** Amazon Bedrock (Converse API), Anthropic models via AWS Marketplace, us-east-1
- **Task:** Call Claude for translation (feasibility test 4).
- **Steps:** `list_foundation_models` / `list_inference_profiles` show 15 Claude models as ACTIVE. Probed each with a 5-token Converse call.
- **Expected:** Either the listed models are invocable, or the listing says which ones aren't.
- **Actual:** The listing shows everything as ACTIVE. Invocation fails three different ways: "not available for this account" (5.x family), `INVALID_PAYMENT_INSTRUMENT` (Sonnet/Haiku 4.x), and Opus 4.5/4.6 **succeeded once, then failed with `INVALID_PAYMENT_INSTRUMENT` ~3 minutes later** on the real request.
- **Severity:** Major. It blocks the core feature, and the intermittent success makes it look like a code bug.
- **Workaround:** Validated the pipeline on Amazon Nova Pro (first-party, no Marketplace). Fix the payment method to unlock Claude.
- **Suggestion:** Return per-model *invocability* (entitlement + Marketplace status) from `ListFoundationModels`, and fail consistently rather than allowing a first call through.

### F-009: Polly has no neural voice for Tamil or Malayalam (Transcribe supports both)
- **Tool:** Amazon Polly
- **Task:** Dub family stories *into* a grandparent's language (e.g. English reply → Tamil).
- **Actual:** Transcribe batch understands ta-IN, ml-IN, te-IN, kn-IN and others, but Polly speaks only hi-IN and en-IN among Indian languages.
- **Severity:** Major for our use case (the reply loop to Tamil grandparents is subtitles-only).
- **Workaround:** Tamil replies are delivered as subtitles; Hindi gets a voice.
- **Suggestion:** Tamil, Malayalam, Telugu, Bengali neural voices. That's hundreds of millions of speakers, many of them Fire TV customers in India.

### F-010: App process replaced by background dexopt right after install, so D-pad input landed on the launcher
- **Tool:** Android TV emulator (API 30) / Fire OS app install path
- **Task:** Install the debug APK, launch it, and drive the flow with `adb shell input keyevent`.
- **Actual:** About 30 s after install, the system ran `dexopt` on the new APK and restarted the app process. Our scripted
  key presses then went to the TV launcher (Settings opened). No crash or log line in our app explained it.
- **Severity:** Minor (it cost a test cycle and looked like a crash)
- **Workaround:** `adb shell cmd package compile -m speed -f <pkg>` right after install, then launch.
- **Suggestion:** Mention this in the Fire TV "test with adb" docs, especially for automated demo recording.

### F-011: Bedrock vision bounding-box convention isn't stated next to the Converse image docs
- **Tool:** Amazon Bedrock, Amazon Nova Pro (Converse with image input)
- **Task:** Get grounded boxes for objects to draw on the TV.
- **Actual:** The Nova grounding convention (integers on a 0–1000 grid, `[x1, y1, x2, y2]`) is in the Nova user guide but
  not in the Converse API image examples, so the first schema guessed pixels.
- **Severity:** Papercut
- **Workaround:** State the 0–1000 convention in the tool schema description and normalize in code.
- **Suggestion:** A "grounding" example on the Converse API page for each vision model.

### Note: host memory (not an Amazon issue)
The dev laptop had <1 GB of free commit with a browser open. Gradle (2 GB heap) and the emulator could not run together, so we switched to `kotlin.compiler.execution.strategy=in-process`, a 1.25 GB guest, and stopped idle daemons. Recorded here so the numbers in this log make sense.

### F-012: Unity Hub's silent install fails without an interactive admin prompt
- **Tool:** Unity Hub CLI (`--headless install`), Unity 6000.3 LTS, Windows 11
- **Task:** Install the Unity editor and Android Build Support on a second drive for the Fire OS companion app.
- **Actual:** The download completed (about 7 GB staged on C:), then the install failed with "The Windows elevation prompt was cancelled or timed out" because no one was there to accept UAC.
- **Severity:** Major. It cost about 30 minutes and nearly filled the system drive.
- **Workaround:** Run the downloaded, signed installers as the current user (`__COMPAT_LAYER=RunAsInvoker`, `/S /D=E:\Unity\Editors\...`), and unpack the NDK/JDK zips by hand.
- **Suggestion (to Amazon, for Fire TV game developers):** Document the minimum Unity setup for Fire OS (modules, x86_64 vs ARM64) on the Fire TV game docs page.

### F-013: Unity 6 can't target the Android TV image we used for Fire OS
- **Tool:** Unity 6 Android player, Android TV emulator `android-30;android-tv;x86`
- **Actual:** Unity 6 supports ARM64, ARMv7 and x86_64 only, and the recommended Fire OS 8-like TV image is 32-bit x86, so the Unity APK can't run there.
- **Severity:** Minor
- **Workaround:** A second, x86_64 AVD (API 36, 1080p) for testing the Unity companion; ARM64 for real Fire TV devices.
- **Suggestion:** Pairs with F-002: an official x86_64 Fire OS emulator image would cover native and Unity apps.

### F-014: HTTP API catch-all route swallows CORS preflight
- **Tool:** Amazon API Gateway HTTP API (CDK `corsPreflight` + `ANY /{proxy+}` route)
- **Task:** Let the phone page (CloudFront origin) call the API.
- **Expected:** With `corsPreflight` configured, API Gateway answers `OPTIONS` itself.
- **Actual:** The `ANY` route also matches `OPTIONS`, so the preflight went to the Lambda, which returned 404, and every browser call failed. The console only says "CORS policy".
- **Severity:** Minor
- **Workaround:** The Lambda answers `OPTIONS` with 204 and the CORS headers.
- **Suggestion:** A warning in the CDK `HttpApi` docs (or a synth-time warning) when an `ANY` route coexists with `corsPreflight`.

### F-015: Amazon Nova Pro arithmetic when marking homework
- **Tool:** Amazon Bedrock, Amazon Nova Pro (Converse, image + forced tool schema)
- **Task:** Mark a child's handwritten fractions worksheet.
- **Actual:** Transcription of every handwritten answer was correct, but one verdict was wrong ("which is bigger, 2/3 or 3/5?" marked incorrect). Asking for step-by-step working first did not fully fix it. Generated quiz options were also biased to option A and once contained two correct choices.
- **Severity:** Major for an education product (a wrong mark can mislead a child).
- **Workaround:** Exact checking in code for arithmetic (Python `Fraction`), server-side shuffling and validation of generated questions, and retrying generation when too few pass.
- **Suggestion:** A Bedrock sample showing "model reads, code verifies" for education use cases; and an optional calculator tool in Nova's built-in tool set.
