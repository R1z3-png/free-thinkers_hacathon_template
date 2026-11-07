# Free Thinkers Hackathon Template

Android starter for the local audio transcription app described in `тз диктофон.docx`.
The project uses Kotlin, Jetpack Compose, and the Gradle Wrapper.

## Requirements

- JDK 25 for the Gradle Kotlin toolchain
- Android SDK Platform 37
- IntelliJ IDEA with Android support, or Android Studio

Open this folder as a Gradle project and let the IDE sync. Select the `app` run configuration
or run `:app:installDebug` on a connected device/emulator.

## Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

GitHub Actions builds the debug app and runs unit tests on every push and pull request.

The three Compose screens and the processing engine are initial scaffolds. Audio import,
model installation, transcription, and JSON save-file flow are not implemented yet.