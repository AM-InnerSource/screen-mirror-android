package io.bettercommerce.screenmirror

import android.app.Application

/**
 * Application entry point. A place to initialise app-wide singletons in later
 * milestones (ads SDK, billing client, logging, etc.).
 */
class ScreenMirrorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO(M6): initialise AdMob + Play Billing here.
    }
}
