# Ninho Android

- This repository is an independent Kotlin / Jetpack Compose Android project.
- Current scope is environment preparation only. Do not port application features until requested.
- Keep personal PDFs, study history, credentials, model downloads and signing keys out of Git.
- Preserve the pinned Gradle wrapper and its distribution checksum.
- Verify changes with `gradlew.bat :app:assembleDebug :app:lintDebug`.
- Add meaningful unit and device tests when implementing actual behavior. A successful build or lint run is not evidence of a device test.
- Never claim Apple Foundation Models runs on Android. A future local AI backend needs a supported Android engine, model licensing and real device evaluation.
