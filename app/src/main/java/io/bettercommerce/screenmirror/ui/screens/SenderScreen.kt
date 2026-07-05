package io.bettercommerce.screenmirror.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bettercommerce.screenmirror.capture.CaptureState
import io.bettercommerce.screenmirror.capture.ScreenCaptureService
import io.bettercommerce.screenmirror.network.FrameProtocol
import io.bettercommerce.screenmirror.network.SenderDiscovery

/**
 * M3 Sender screen: connect to a Receiver by IP and stream this device's screen
 * to it over WiFi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(onBack: () -> Unit) {
    val activity = rememberSenderActivity()
    val status by CaptureState.status.collectAsStateWithLifecycle()

    var host by remember { mutableStateOf("") }

    // Presenter pointer: needs the "draw over other apps" permission so the focus
    // bubble can float above whatever the sender is showing while streaming.
    fun overlayAllowed() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity)
    var overlayGranted by remember { mutableStateOf(overlayAllowed()) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { overlayGranted = overlayAllowed() }
    fun requestOverlayPermission() {
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}"),
            )
        )
    }

    // Auto-discover receivers advertising on the local network (M4 pairing).
    val discovery = remember { SenderDiscovery(activity) }
    val discovered by discovery.receivers.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                activity,
                ScreenCaptureService.startSenderIntent(
                    activity, result.resultCode, data, host.trim(), FrameProtocol.PORT,
                ),
            )
        } else {
            CaptureState.update(CaptureState.Status.Idle)
        }
    }

    fun launchProjection() {
        val manager =
            activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { launchProjection() }

    fun onStartClicked() {
        if (host.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjection()
        }
    }

    fun onStopClicked() {
        activity.startService(ScreenCaptureService.stopIntent(activity))
    }

    val isStreaming = status is CaptureState.Status.Recording ||
        status is CaptureState.Status.Starting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mirror this device") },
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
            verticalArrangement = Arrangement.Center,
        ) {
            StatusCard(status)
            Spacer(Modifier.height(16.dp))

            PresenterPointerCard(
                granted = overlayGranted,
                onEnable = { requestOverlayPermission() },
            )
            Spacer(Modifier.height(24.dp))

            // Discovered receivers (auto-pairing) — tap to fill in the address.
            if (!isStreaming) {
                Text(
                    text = if (discovered.isEmpty()) "Searching for receivers on WiFi…"
                    else "Nearby receivers — tap to select:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                discovered.forEach { receiver ->
                    Card(
                        onClick = { host = receiver.host },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(receiver.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${receiver.host}:${receiver.port}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Receiver IP address") },
                placeholder = { Text("e.g. 192.168.1.42") },
                singleLine = true,
                enabled = !isStreaming,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Or open \"View another device\" on the other phone and pick it above.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            if (isStreaming) {
                Button(
                    onClick = { onStopClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("  Stop streaming")
                }
            } else {
                Button(
                    onClick = { onStartClicked() },
                    enabled = host.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Cast, contentDescription = null)
                    Text("  Start streaming")
                }
            }
        }
    }
}

/**
 * Explains the presenter-pointer feature and, until granted, offers a button to
 * turn on the "draw over other apps" permission it needs.
 */
@Composable
private fun PresenterPointerCard(granted: Boolean, onEnable: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Presenter pointer", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (granted) {
                    "Ready. While streaming, drag the floating dot over anything and " +
                        "double-tap it to highlight that spot on the viewer's screen."
                } else {
                    "Let ScreenMirror draw a small floating dot over other apps. " +
                        "While streaming, drag it and double-tap to highlight a spot for the viewer."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (!granted) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onEnable) { Text("Enable presenter pointer") }
            }
        }
    }
}

@Composable
private fun StatusCard(status: CaptureState.Status) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (title, detail) = when (status) {
                is CaptureState.Status.Idle ->
                    "Ready" to "Enter the Receiver's IP, then Start streaming. Android will ask permission to record your screen."
                is CaptureState.Status.Starting ->
                    "Connecting…" to "Connecting to the receiver and starting capture."
                is CaptureState.Status.Recording ->
                    "Streaming" to status.outputPath
                is CaptureState.Status.Finished ->
                    "Stopped" to "Streaming ended."
                is CaptureState.Status.LimitReached ->
                    "Time limit reached" to status.message
                is CaptureState.Status.Error ->
                    "Error" to status.message
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Resolves the hosting [Activity] from the composition context. */
@Composable
private fun rememberSenderActivity(): Activity {
    var ctx: Context = androidx.compose.ui.platform.LocalContext.current
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("SenderScreen must be hosted in an Activity")
}
