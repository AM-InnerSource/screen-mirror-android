package io.bettercommerce.screenmirror.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.bettercommerce.screenmirror.ui.theme.ScreenMirrorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMirrorThisDevice: () -> Unit,
    onViewAnotherDevice: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("ScreenMirror") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Android → Android screen mirroring over WiFi",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onMirrorThisDevice,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Filled.ScreenShare, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  Mirror this device  (Sender)")
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onViewAnotherDevice,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Filled.CastConnected, contentDescription = null)
                Text("  View another device  (Receiver)")
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text("  Settings")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ScreenMirrorTheme {
        HomeScreen({}, {}, {})
    }
}
