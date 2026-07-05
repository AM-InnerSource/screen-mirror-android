package io.bettercommerce.screenmirror.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bettercommerce.screenmirror.monetization.BillingManager
import io.bettercommerce.screenmirror.monetization.Entitlements

/**
 * Settings + paywall. Shows current plan, the Pro upgrade (via Play Billing), and
 * a debug-only toggle to simulate Pro so free/Pro gating can be exercised without
 * Play Console.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val activity = rememberSettingsActivity()
    val isPro by Entitlements.isPro.collectAsStateWithLifecycle()
    val price by BillingManager.priceText.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isPro) "ScreenMirror Pro" else "Free plan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isPro) {
                            "No ads · 1080p/60fps · unlimited sessions · multiple devices"
                        } else {
                            "Ads · 720p · 50-min sessions. Upgrade to unlock everything."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!isPro) {
                Button(
                    onClick = { BillingManager.launchPurchase(activity) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Text(
                        if (price != null) "Upgrade to Pro — $price / month"
                        else "Upgrade to Pro",
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Real purchases require the app on Play Console; the button is wired but inactive until then.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // --- Debug controls ---
            Row(
                label = "Simulate Pro (debug)",
                checked = isPro,
                onCheckedChange = { Entitlements.setPro(it) },
            )
            Text(
                "Toggle to preview the ad-free Pro experience without buying.",
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                    runCatching { activity.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Privacy Policy")
            }
        }
    }
}

/** Replace with the public URL where PRIVACY_POLICY.md is hosted before release. */
private const val PRIVACY_POLICY_URL = "https://bettercommerce.io/screenmirror/privacy"

@Composable
private fun Row(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun rememberSettingsActivity(): Activity {
    var ctx: Context = LocalContext.current
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("SettingsScreen must be hosted in an Activity")
}
