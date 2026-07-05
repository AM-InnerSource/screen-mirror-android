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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.bettercommerce.screenmirror.network.FrameProtocol
import io.bettercommerce.screenmirror.network.NetworkReceiver

/**
 * M3 Receiver screen: acts as a TCP server. Shows this device's IP so the Sender
 * knows where to connect, then decodes the incoming H.264 stream onto the preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(onBack: () -> Unit) {
    var receiverStatus by remember { mutableStateOf(NetworkReceiver.Status.STOPPED) }
    val ipAddress = remember { NetworkReceiver.localIpAddress() }
    val receiver = remember {
        NetworkReceiver(FrameProtocol.PORT) { status -> receiverStatus = status }
    }

    DisposableEffect(Unit) {
        onDispose {
            receiver.detachSurface()
            receiver.stop()
        }
    }

    val isListening = receiverStatus == NetworkReceiver.Status.LISTENING ||
        receiverStatus == NetworkReceiver.Status.RECEIVING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View another device") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("This device's address", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (ipAddress != null) "$ipAddress : ${FrameProtocol.PORT}"
                        else "Not on WiFi?",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (receiverStatus) {
                            NetworkReceiver.Status.LISTENING -> "Waiting for a sender to connect…"
                            NetworkReceiver.Status.RECEIVING -> "Receiving ✓"
                            NetworkReceiver.Status.STOPPED -> "Tap Start listening, then enter this IP on the sender."
                            NetworkReceiver.Status.ERROR -> "Error — is another instance already listening?"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            VideoSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onSurfaceAvailable = { receiver.attachSurface(it) },
                onSurfaceDestroyed = { receiver.detachSurface() },
            )

            Spacer(Modifier.height(16.dp))

            if (isListening) {
                Button(
                    onClick = {
                        receiver.stop()
                        receiverStatus = NetworkReceiver.Status.STOPPED
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("  Stop listening")
                }
            } else {
                Button(
                    onClick = { receiver.start() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null)
                    Text("  Start listening")
                }
            }
        }
    }
}
