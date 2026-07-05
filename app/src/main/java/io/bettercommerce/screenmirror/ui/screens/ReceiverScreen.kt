package io.bettercommerce.screenmirror.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.bettercommerce.screenmirror.network.FrameProtocol
import io.bettercommerce.screenmirror.network.NetworkReceiver
import io.bettercommerce.screenmirror.network.ReceiverAdvertiser

/**
 * Receiver screen. Acts as a TCP server; while receiving it shows the incoming
 * screen as a near-fullscreen, aspect-correct preview ("phone under phone").
 *
 * A single persistent [VideoSurface] fills the screen behind everything so the
 * decoder's render target never changes; overlays (pairing card, controls) are
 * drawn on top and — while receiving — toggle on tap for an immersive view. The
 * aspect ratio comes from the sender's real video size, so it's never stretched
 * and adapts to any screen (phone/tablet, portrait/landscape).
 */
@Composable
fun ReceiverScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var receiverStatus by remember { mutableStateOf(NetworkReceiver.Status.STOPPED) }
    var videoSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val ipAddress = remember { NetworkReceiver.localIpAddress() }
    val receiver = remember {
        NetworkReceiver(
            port = FrameProtocol.PORT,
            onStatus = { status -> receiverStatus = status },
            onVideoSize = { w, h -> videoSize = w to h },
        )
    }
    val advertiser = remember { ReceiverAdvertiser(context) }
    var controlsVisible by remember { mutableStateOf(true) }

    fun startListening() {
        receiver.start()
        advertiser.register(FrameProtocol.PORT, Build.MODEL ?: "ScreenMirror")
    }

    fun stopListening() {
        advertiser.unregister()
        receiver.stop()
        receiverStatus = NetworkReceiver.Status.STOPPED
        videoSize = null
        controlsVisible = true
    }

    DisposableEffect(Unit) {
        onDispose {
            advertiser.unregister()
            receiver.detachSurface()
            receiver.stop()
        }
    }

    val isReceiving = receiverStatus == NetworkReceiver.Status.RECEIVING
    val isListening = receiverStatus == NetworkReceiver.Status.LISTENING || isReceiving
    // Controls always show unless we're actively receiving and the user hid them.
    val showControls = controlsVisible || !isReceiving

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = isReceiving) { controlsVisible = !controlsVisible },
    ) {
        // Persistent, aspect-fit video layer.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val ar = videoSize?.let { (w, h) -> w.toFloat() / h } ?: (9f / 16f)
            val boxAr = maxWidth.value / maxHeight.value
            val videoModifier = if (ar <= boxAr) {
                Modifier.fillMaxHeight().aspectRatio(ar)
            } else {
                Modifier.fillMaxWidth().aspectRatio(ar)
            }
            VideoSurface(
                modifier = videoModifier,
                onSurfaceAvailable = { receiver.attachSurface(it) },
                onSurfaceDestroyed = { receiver.detachSurface() },
            )
        }

        // --- Top overlay: back + address/status ---
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScrimIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                val label = when (receiverStatus) {
                    NetworkReceiver.Status.RECEIVING -> "Receiving ✓  ·  ${ipAddress ?: ""}"
                    NetworkReceiver.Status.LISTENING -> "Waiting for a sender…"
                    else -> "View another device"
                }
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // --- Center pairing card (only before a stream is flowing) ---
        if (!isReceiving) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("This device's address", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (ipAddress != null) "$ipAddress : ${FrameProtocol.PORT}" else "Not on WiFi?",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (receiverStatus) {
                            NetworkReceiver.Status.LISTENING -> "Waiting for a sender to connect…"
                            NetworkReceiver.Status.STOPPED -> "Tap Start listening, then pick this device on the sender."
                            NetworkReceiver.Status.ERROR -> "Error — is another instance already listening?"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // --- Bottom control button ---
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
            ) {
                if (isListening) {
                    Button(
                        onClick = { stopListening() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("  Stop listening")
                    }
                } else {
                    Button(
                        onClick = { startListening() },
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
}

@Composable
private fun ScrimIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
    ) {
        IconButton(onClick = onClick) { content() }
    }
}
