# ScreenMirror (Android → Android)

A native Android app that mirrors one Android phone's screen to another in real
time over local WiFi — high-speed, low-latency, view-only. Free tier is ad-supported;
a Pro subscription removes ads and unlocks higher quality.

- **Capture → encode → stream → decode → render**, all with hardware codecs.
- **Auto-pairing** on the local network (no typing IP addresses).
- **Monetization** built in (AdMob + Google Play Billing).

> Full spec & milestone plan: [PROJECT_PLAN.md](./PROJECT_PLAN.md) ·
> Release steps: [RELEASE.md](./RELEASE.md)

---

## Features

| Area | What it does |
|---|---|
| **Sender** | Captures this device's screen (`MediaProjection`), hardware-encodes H.264 (`MediaCodec`), streams to a receiver over TCP. |
| **Receiver** | TCP server; decodes the incoming H.264 and renders it to the screen. Advertises itself for auto-discovery. |
| **Self-test** | Runs the full capture→encode→decode→render loop on a single device (no 2nd phone needed). |
| **Pairing** | NSD/mDNS: the Sender lists nearby receivers to tap — or enter an IP manually. |
| **Quality tiers** | Free = 720p/30fps · Pro = 1080p/60fps, with low-latency codec hints. |
| **Monetization** | Banner + interstitial ads (free), `pro_monthly` subscription, free/Pro gating. |

---

## Tech stack

- **Language / UI:** Kotlin, Jetpack Compose (Material 3)
- **Capture / codec:** `MediaProjection`, `MediaCodec` (hardware H.264/AVC), `MediaMuxer`
- **Transport:** raw TCP sockets over the local network
- **Discovery:** `NsdManager` (mDNS)
- **Monetization:** Google Mobile Ads (AdMob), Google Play Billing v7
- **Build:** Gradle (Kotlin DSL) + version catalog · **min SDK 24**, target SDK 35

---

## Prerequisites

- **Android Studio** (latest stable) — bundles a compatible JDK 17.
  - Or standalone: **JDK 17** + **Android SDK** (platform 35, build-tools 35+).
- An **Android device (API 24+)** with USB debugging, or an emulator.
- To exercise **phone-to-phone** mirroring you need **two devices on the same WiFi**
  (a single device can run the **Self-test** and see the ad/Pro behavior).

---

## Getting started

### Option A — Android Studio (recommended)

1. **Clone:**
   ```
   git clone https://github.com/AM-InnerSource/screen-mirror-android.git
   ```
2. **File → Open** the `screen-mirror-android` folder.
3. Let Gradle sync. Android Studio auto-creates `local.properties` (SDK path) and,
   if missing, the Gradle wrapper.
4. Pick a device/emulator and press **▶ Run**.

### Option B — Command line

1. Ensure the SDK is discoverable. Either set `ANDROID_HOME`, or create
   `local.properties` in the repo root:
   ```
   sdk.dir=/absolute/path/to/Android/Sdk
   ```
   (git-ignored — never commit it.)
2. Build a debug APK:
   ```
   ./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`
3. Install on a connected device:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Common Gradle commands

| Command | Purpose |
|---|---|
| `./gradlew assembleDebug` | Build the debug APK |
| `./gradlew installDebug` | Build + install on a connected device |
| `./gradlew assembleRelease` | Build a (signed, if configured) release APK |
| `./gradlew bundleRelease` | Build a release **AAB** for Play |
| `./gradlew lint` | Run Android Lint |

Release signing is optional for local dev — see [RELEASE.md](./RELEASE.md). Without
`keystore.properties`, release builds are produced unsigned.

---

## How to test

**Single device (self-test):** Home → **Self-test (1 phone)** → **Start** →
accept the record prompt → you'll see a live decoded preview of your own screen.

**Two devices (real mirror):**
1. Both phones on the **same WiFi** (no "AP isolation" / guest network).
2. **Phone B:** *View another device* → **Start listening** (note its IP).
3. **Phone A:** *Mirror this device* → tap Phone B in **Nearby receivers**
   (or type its IP) → **Start streaming** → accept the prompt.
4. Phone A's screen appears on Phone B.

**Monetization:** ads use Google **test** units, so they show without an AdMob
account. Settings → **Simulate Pro** toggles the ad-free/Pro experience.

---

## Project structure

```
app/src/main/java/io/bettercommerce/screenmirror/
├── MainActivity.kt            # Compose entry point
├── ScreenMirrorApp.kt         # Application: init ads/billing/entitlements
├── capture/                   # screen capture + codecs
│   ├── ScreenCaptureService   # mediaProjection foreground service
│   ├── ScreenEncoder          # MediaProjection → MediaCodec (H.264)
│   ├── VideoDecoder           # H.264 → Surface
│   ├── EncodedFrameListener    # encoder output sink (file / loopback / network)
│   ├── LoopbackController      # in-process encode→decode (self-test)
│   ├── MuxerFrameListener      # write .mp4
│   └── CaptureConfig / CaptureState
├── network/                   # streaming + discovery
│   ├── NetworkSender          # stream H.264 over TCP
│   ├── NetworkReceiver        # TCP server → decode → render
│   ├── FrameProtocol          # wire format
│   ├── SenderDiscovery        # mDNS browse (find receivers)
│   └── ReceiverAdvertiser     # mDNS register (advertise)
├── monetization/              # AdMob + Play Billing + entitlements
└── ui/                        # Compose screens, navigation, theme
```

---

## Architecture (data flow)

```
SENDER                                   RECEIVER
MediaProjection → VirtualDisplay          TCP server (NetworkReceiver)
  → MediaCodec (H.264 encode)               → MediaCodec (H.264 decode)
  → NetworkSender ── TCP / WiFi ──────────►  → render to Surface
       ▲                                        ▲
       └── mDNS discovery / pairing ────────────┘
```

The encoder is decoupled from its destination via `EncodedFrameListener`, so the
same capture pipeline feeds a file (M1), an on-device decoder (self-test), or the
network (real mirror).

---

## Contributing

1. **Branch** off `main`: `git checkout -b my-feature`.
2. Keep changes focused; match the surrounding Kotlin style (the repo uses the
   official Kotlin code style — `kotlin.code.style=official`).
3. **Build must pass:** `./gradlew assembleDebug` (and `assembleRelease` if you
   touch build config).
4. Prefer verifying on a real device — codec behavior varies across hardware.
5. Open a PR against `main` describing what you changed and how you tested it.

**Do not commit:** `local.properties`, `keystore.properties`, `*.jks`/`*.keystore`,
or anything under `build/` (all git-ignored).

**Good first areas:** UI polish, additional quality presets, reconnection UX,
audio mirroring, QR-code pairing, and the release TODOs in [RELEASE.md](./RELEASE.md).

---

## Status

Milestones **M0–M7** are implemented: scaffold, capture, on-device loopback,
phone-to-phone streaming, auto-pairing, quality/latency tuning, monetization, and
release prep (signing, privacy policy, docs). See [PROJECT_PLAN.md](./PROJECT_PLAN.md).

Before a production Play release, complete the required items in
[RELEASE.md](./RELEASE.md) (real AdMob ids, EU consent, Play Console subscription).

## License

Proprietary — © BetterCommerce. All rights reserved. (Update if you intend an
open-source license.)
