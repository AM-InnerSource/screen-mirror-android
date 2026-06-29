# ScreenMirror (Android → Android)

A native Android app that mirrors one Android phone's screen to another in real
time over local WiFi. View-only in v1, high-speed (low latency), monetized with
ads (free tier) and a Pro subscription.

> **Status:** Planning. See [PROJECT_PLAN.md](./PROJECT_PLAN.md) for the full
> specification, architecture, milestones, and compliance checklist.

## Stack
- Kotlin + Jetpack Compose
- `MediaProjection` + `MediaCodec` (hardware H.264) + WebRTC
- AdMob (ads) + Google Play Billing (subscriptions)

## Project status — M0 complete (scaffold)
A buildable Kotlin + Jetpack Compose app with a navigation skeleton:
Home → Sender / Receiver / Settings (the feature screens are placeholders that
later milestones fill in).

```
app/src/main/java/io/bettercommerce/screenmirror/
├── ScreenMirrorApp.kt        # Application class (SDK init later)
├── MainActivity.kt           # Compose entry point
└── ui/
    ├── ScreenMirrorApp.kt     # NavHost / navigation graph
    ├── navigation/            # route constants
    ├── screens/               # Home + placeholder feature screens
    └── theme/                 # Material 3 theme
```

## How to build / run
This machine has **no JDK / Android SDK / Android Studio** installed, so the app
cannot be compiled here — it is meant to be opened in Android Studio.

1. Install **Android Studio** (bundles a JDK 17 + Android SDK).
2. **File → Open** this folder (`screen-mirror-android`).
3. Android Studio will sync Gradle and **generate `gradle/wrapper/gradle-wrapper.jar`**
   and `gradlew`/`gradlew.bat` automatically (these binaries are intentionally not
   committed). If syncing from the CLI instead, run `gradle wrapper` once.
4. Run on an emulator or device. To build an APK:
   `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

> `local.properties` (pointing at the SDK) is created by Android Studio and is
> git-ignored — do not commit it.

## Next step
**M1 — capture proof:** implement `MediaProjection` screen capture + `MediaCodec`
H.264 encode on the Sender screen.
