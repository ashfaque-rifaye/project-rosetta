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

### Note: host memory (not an Amazon issue)
The dev laptop had <1 GB of free commit with a browser open. Gradle (2 GB heap) and the emulator could not run together, so we switched to `kotlin.compiler.execution.strategy=in-process`, a 1.25 GB guest, and stopped idle daemons. Recorded here so the numbers in this log make sense.
