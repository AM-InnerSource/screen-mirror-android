# Release Guide — ScreenMirror

Everything needed to ship to Google Play. Items marked **⚠️ REQUIRED** must be
done before a production release.

## 1. Signing (done)

- Upload key: `upload-keystore.jks` (git-ignored). **Back this up securely** —
  losing it means you cannot update the app on Play.
- Credentials: `keystore.properties` (git-ignored).
- Build a signed bundle: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
- Build a signed APK:   `./gradlew assembleRelease`

> For production, enrol in **Play App Signing** (Google manages the app signing
> key; you keep the upload key). The current key works as the upload key.

## 2. ⚠️ REQUIRED — real AdMob ids

The app currently uses Google's **test** ad ids. Before release:

1. Create an AdMob account + app; get your **App ID** and ad unit ids.
2. Replace the App ID in `app/src/main/AndroidManifest.xml`
   (`com.google.android.gms.ads.APPLICATION_ID`).
3. Replace `BANNER` / `INTERSTITIAL` in
   `app/src/main/java/io/bettercommerce/screenmirror/monetization/AdIds.kt`.

Shipping test ids to production violates AdMob policy.

## 3. ⚠️ REQUIRED — EU/UK consent (UMP)

For personalized ads in the EU/UK you must gather consent with Google's User
Messaging Platform (UMP) SDK before initializing ads. Not yet integrated — add
`com.google.android.ump:user-messaging-platform` and a consent flow, or configure
non-personalized ads only.

## 4. ⚠️ REQUIRED — subscriptions in Play Console

1. Upload the AAB to an **internal testing** track (subscriptions need an uploaded app).
2. Create a subscription with product id **`pro_monthly`** (must match
   `BillingManager.PRO_PRODUCT_ID`).
3. Add license testers so you can test purchases without being charged.

## 5. ⚠️ REQUIRED — store listing & policies

- **Privacy Policy:** host `PRIVACY_POLICY.md` at a public URL; add it to the Play
  listing and in the app's Settings (placeholder URL currently in `SettingsScreen`).
- **Data Safety form:** see `PLAY_DATA_SAFETY.md`.
- **Screen-recording disclosure:** the listing must explain that the app records
  the screen and why (Play scrutinizes MediaProjection apps).
- App icon (adaptive icon present), feature graphic, screenshots.

## 6. App content

- `versionName` / `versionCode` in `app/build.gradle.kts` (bump each release).
- Target SDK 35 (meets current Play requirements).

## 7. Anti-"stalkerware" note

Keep capture **visible and consensual**: the system prompt + ongoing notification
must always be present. Never add silent/background capture — it triggers a ban.
