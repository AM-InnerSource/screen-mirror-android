package io.bettercommerce.screenmirror.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun SenderScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Mirror this device",
        description = "Sender — coming in M1/M3.\n\nWill capture this screen via " +
            "MediaProjection, encode with MediaCodec (H.264), and stream to a " +
            "paired receiver over WiFi.",
        onBack = onBack,
    )
}
