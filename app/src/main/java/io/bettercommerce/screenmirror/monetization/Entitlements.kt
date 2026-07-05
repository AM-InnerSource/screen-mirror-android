package io.bettercommerce.screenmirror.monetization

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for whether the user has "Pro" (ad-free, unlocked).
 *
 * Backed by SharedPreferences and mirrored into a [StateFlow] the UI observes.
 * It is set either by [BillingManager] on a verified purchase, or by the debug
 * "Simulate Pro" toggle in Settings (so gating can be tested without Play Console).
 */
object Entitlements {

    private const val PREFS = "entitlements"
    private const val KEY_PRO = "is_pro"

    private var prefs: SharedPreferences? = null

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** True right now, for synchronous checks (e.g. whether to show an ad). */
    val isProNow: Boolean get() = _isPro.value

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _isPro.value = p.getBoolean(KEY_PRO, false)
    }

    fun setPro(pro: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PRO, pro)?.apply()
        _isPro.value = pro
    }
}
