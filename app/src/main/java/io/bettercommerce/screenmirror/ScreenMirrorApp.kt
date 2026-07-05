package io.bettercommerce.screenmirror

import android.app.Application
import com.google.android.gms.ads.MobileAds
import io.bettercommerce.screenmirror.monetization.BillingManager
import io.bettercommerce.screenmirror.monetization.Entitlements
import io.bettercommerce.screenmirror.monetization.InterstitialAdController

/**
 * Application entry point. Initialises app-wide monetization singletons.
 */
class ScreenMirrorApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Entitlement first, so ad/billing logic can read Pro state immediately.
        Entitlements.init(this)

        // AdMob. MobileAds does its own background init; safe to call on main.
        MobileAds.initialize(this) {}
        InterstitialAdController.preload(this)

        // Play Billing (connects + reconciles existing subscriptions).
        BillingManager.init(this)
    }
}
