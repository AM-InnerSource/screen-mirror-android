# Project-specific ProGuard/R8 rules.

# --- Google Play Billing ---
# The billing library ships its own consumer rules, but keep the public API and
# our data classes defensively.
-keep class com.android.billingclient.api.** { *; }

# --- Google Mobile Ads (AdMob) ---
# The ads SDK ships consumer rules; keep the public entrypoints defensively.
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# --- Our capture/network models sent across the encode/decode/socket boundary ---
-keep class io.bettercommerce.screenmirror.capture.CaptureConfig { *; }
-keep class io.bettercommerce.screenmirror.network.** { *; }

# Kotlin metadata / coroutines are handled by the AGP defaults.
