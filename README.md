# Nafas (Android)

A breathing and meditation app. The interface is a web app in `app/src/main/assets/www/index.html`,
wrapped in a native Android shell that adds text-to-speech, reminders, file saving and a
foreground service that keeps sessions playing with the screen off.

The GitHub workflow `.github/workflows/android.yml` builds a signed APK on every push.
Step-by-step instructions (Arabic): see `README-AR.md`.

Build locally: `./gradlew assembleRelease` (JDK 17, Android SDK 34).
