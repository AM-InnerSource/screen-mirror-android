package io.bettercommerce.screenmirror.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Settings",
        description = "Settings — coming in M5/M6.\n\nWill hold quality (resolution/fps), " +
            "free vs Pro status, subscription management, and the privacy policy link.",
        onBack = onBack,
    )
}
