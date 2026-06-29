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

## Next step
Scaffold the project (Milestone **M0**), then proceed milestone by milestone.
