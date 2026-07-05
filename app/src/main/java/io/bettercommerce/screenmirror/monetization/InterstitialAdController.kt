package io.bettercommerce.screenmirror.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Loads and shows a single interstitial ad, shown to free users at natural
 * breaks (e.g. after a mirroring session ends). Pro users never trigger it.
 */
object InterstitialAdController {

    private const val TAG = "InterstitialAd"

    private var ad: InterstitialAd? = null
    private var loading = false

    /** Loads an interstitial ahead of time so it's ready to show instantly. */
    fun preload(context: Context) {
        if (Entitlements.isProNow || ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            AdIds.INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                    Log.w(TAG, "interstitial load failed: ${error.message}")
                }
            },
        )
    }

    /**
     * Shows the interstitial if one is ready and the user is not Pro; otherwise
     * invokes [onDone] immediately. Reloads the next ad afterwards.
     */
    fun showIfAvailable(activity: Activity, onDone: () -> Unit = {}) {
        if (Entitlements.isProNow) {
            onDone()
            return
        }
        val current = ad
        if (current == null) {
            onDone()
            preload(activity)
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                preload(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                onDone()
            }
        }
        current.show(activity)
    }
}
