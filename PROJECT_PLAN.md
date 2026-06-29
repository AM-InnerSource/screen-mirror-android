# ScreenMirror — Android-to-Android Screen Mirroring App

> Project plan & specification. Status: **Planning** (no code yet).
> Last updated: 2026-06-29

---

## 1. Product Summary

A native Android app that mirrors one Android phone's screen to another Android
phone in real time over local WiFi — **view-only** in v1, high-speed (low latency).
Monetized via ads (free tier) and a Pro subscription (no ads, higher quality).

**Tagline:** *Mirror any Android screen to another — fast, wireless, no cables.*

---

## 2. Scope

### In scope (v1)
- View-only mirroring (Sender screen → Receiver display)
- Local WiFi / same-network connection
- Hardware-accelerated H.264 encode/decode
- Device pairing via QR code + on-network discovery
- Free tier with ads + Pro subscription

### Out of scope (v1 — deferred)
- ❌ Remote control / touch injection (needs Shizuku/ADB/root → v2)
- ❌ Internet/remote mirroring across networks (WebRTC TURN server → v2)
- ❌ iOS / cross-platform
- ❌ Audio mirroring (v1 is video-only; audio is a v1.5 add-on)

---

## 3. Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | **Kotlin** | Modern Android standard |
| UI | **Jetpack Compose** | Fast, declarative UI |
| Screen capture | `MediaProjection` + `VirtualDisplay` | Only supported capture API |
| Encode/Decode | `MediaCodec` (H.264, hardware) | Required for "high-speed" |
| Transport | **WebRTC** (local) — fallback raw RTP/UDP | Low latency, congestion control |
| Discovery | NSD / mDNS + QR code pairing | No external server needed on LAN |
| Ads | Google Mobile Ads (AdMob) + UMP consent | Free-tier monetization |
| Billing | Google Play Billing Library | Subscriptions (required by Play) |
| Min SDK | API 24 (Android 7.0) | MediaProjection mature; ~95% devices |
| Target SDK | Latest (Play requirement) | |

---

## 4. Architecture

```
SENDER device                            RECEIVER device
┌───────────────────────┐               ┌───────────────────────┐
│ MediaProjection        │ capture       │  Network receiver      │
│   → VirtualDisplay      │               │     ↓                  │
│   → Surface             │               │  MediaCodec decoder    │
│ MediaCodec encoder      │  H.264 frames │     ↓                  │
│   (H.264, HW)           │ ────────────► │  Render to Surface     │
│   ↓                     │  WebRTC/UDP   │  (Compose/SurfaceView) │
│ Network sender          │   over WiFi   │                        │
└───────────────────────┘               └───────────────────────┘
        ▲                                         ▲
        └──── Pairing: QR code + mDNS discovery ──┘
```

**Module breakdown:**
- `:app` — UI, navigation, pairing, settings, paywall
- `:capture` — MediaProjection + encoder (sender side)
- `:render` — decoder + surface rendering (receiver side)
- `:transport` — WebRTC/socket signaling + streaming
- `:billing` — subscription entitlement + feature gating
- `:ads` — AdMob wrapper + consent

---

## 5. Monetization

### Free vs Pro
| Feature | Free (ads) | Pro (subscription) |
|---|---|---|
| Resolution | 720p | 1080p / 60fps |
| Session length | 10 min cap | Unlimited |
| Ads | Banner + interstitial | None |
| Paired devices | 1 | Multiple |
| Low-latency mode | — | ✅ |

### Ad placement rules
- ✅ Banner on home/settings; interstitial **after** a session ends; rewarded for temporary unlocks
- ❌ Never overlay ads on the live mirror view (policy + UX)

### Pricing (placeholder — to finalize)
- Monthly + annual (annual discounted). Optional one-time "lifetime" unlock.

---

## 6. Compliance Checklist (mirror apps = extra scrutiny)

- [ ] Privacy Policy page (capture + ads data) — **mandatory**
- [ ] Play Console Data Safety form filled
- [ ] UMP consent dialog for personalized ads (GDPR / India DPDP)
- [ ] Screen-capture disclosed; capture only after system prompt
- [ ] Both devices show clear "mirroring active" indicator (anti-stalkerware)
- [ ] No silent/background capture
- [ ] Play Billing used for all digital purchases (no Stripe/PayPal in-app)

---

## 7. Milestones

| # | Milestone | Deliverable |
|---|---|---|
| M0 | Project scaffold | Buildable Compose app, navigation skeleton |
| M1 | Capture proof | Capture local screen → encode → save/preview |
| M2 | Local loopback | Encode → decode → render on same device |
| M3 | Two-device mirror | Sender → Receiver over WiFi (manual IP) |
| M4 | Pairing UX | QR code + mDNS discovery |
| M5 | Quality & latency | Bitrate/fps tuning, reconnection handling |
| M6 | Monetization | AdMob + Play Billing + feature gating |
| M7 | Compliance + polish | Privacy policy, Data Safety, icons, store assets |
| M8 | Release | Signed AAB, internal testing track → production |

---

## 8. Key Risks

| Risk | Impact | Mitigation |
|---|---|---|
| JS bridge too slow (if RN) | Kills latency | Use native Kotlin (decided) |
| Play Store rejection (mirror = sensitive) | No launch | Strict compliance, visible indicators |
| DRM content shows black | User confusion | Document as expected behavior |
| Latency over weak WiFi | Poor UX | WebRTC congestion control, adaptive bitrate |
| Device codec fragmentation | Crashes | Capability checks + fallbacks |
| Low ad revenue | Weak income | Push Pro conversion; rewarded ads |

---

## 9. Open Decisions

- [ ] Final app name + branding
- [ ] WebRTC vs raw RTP/UDP for v1 transport
- [ ] Subscription pricing tiers
- [ ] Include audio mirroring in v1 or defer
- [ ] Backend needed? (only if remote/internet mirroring or license server)

---

## 10. Next Step

When ready to build: start at **M0 — scaffold** the native Kotlin + Compose
project, then proceed milestone by milestone.
