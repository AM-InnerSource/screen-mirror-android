package io.bettercommerce.screenmirror.monetization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * A standard banner ad. Renders nothing for Pro users, so callers can drop it in
 * unconditionally and it disappears the moment the user upgrades.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val isPro by Entitlements.isPro.collectAsStateWithLifecycle()
    if (isPro) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdIds.BANNER
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
