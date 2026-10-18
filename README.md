# Rosetta: your family's stories, in every grandchild's language

Rosetta is a native **Fire TV** app plus an AWS media pipeline. It turns family videos and voice notes, recorded in Tamil, Malayalam, Hindi and other languages, into a multilingual family channel on the living-room TV. You get subtitles and dubbed audio for every viewer, cultural "Rosetta Notes", and replies that travel back to grandparents in their own language.

> Built for the Amazon Developer "Build, Ship, Shape" hackathon: **Fire TV track** + AWS Builder and Open Source mini challenges.

## Repository layout

| Path | What |
|---|---|
| `tv-app/` | Native Fire OS app: Kotlin, Jetpack Compose for TV, Media3 ExoPlayer |
| `companion/` | _(coming)_ phone companion: QR pairing, upload, replies |
| `infra/` | _(coming)_ AWS CDK stack: S3, Step Functions, Transcribe, Bedrock, Polly, Rekognition, DynamoDB, API Gateway |
| `IDEA.md` | Product decision, architecture, compliance map, demo script |
| `FRICTION_LOG.md` | Developer-experience friction log |

## Run the Fire TV app

### Prerequisites
- Android SDK (Android Studio or command-line tools), JDK 17+
- An Android TV system image. We use `system-images;android-30;android-tv;x86`; Fire OS 8 is based on Android 11 / API 30.

### On the emulator
```bash
sdkmanager --licenses
sdkmanager "system-images;android-30;android-tv;x86"
avdmanager create avd -n FireTV_API30 -k "system-images;android-30;android-tv;x86" -d tv_1080p
emulator -avd FireTV_API30
cd tv-app
./gradlew installDebug
adb shell am start -n app.rosetta.tv/.MainActivity
```
Use the arrow keys / Enter (or `adb shell input keyevent DPAD_RIGHT`) as the Fire TV remote.

### On a real Fire TV
Enable **Settings → My Fire TV → Developer options → ADB debugging**, then:
```bash
adb connect <fire-tv-ip>:5555
cd tv-app && ./gradlew installDebug
```

## License
MIT. See [LICENSE](LICENSE).
