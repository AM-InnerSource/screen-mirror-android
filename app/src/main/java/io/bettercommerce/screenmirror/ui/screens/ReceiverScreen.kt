package io.bettercommerce.screenmirror.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun ReceiverScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "View another device",
        description = "Receiver — coming in M2/M3.\n\nWill discover a sender on the " +
            "local network, decode the incoming H.264 stream, and render it to a " +
            "Surface in real time.",
        onBack = onBack,
    )
}
