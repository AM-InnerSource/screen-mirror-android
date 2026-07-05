package io.bettercommerce.screenmirror.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bettercommerce.screenmirror.capture.CaptureState
import io.bettercommerce.screenmirror.capture.LoopbackController
import io.bettercommerce.screenmirror.capture.ScreenCaptureService

/**
 * M2 Receiver screen: local loopback. Captures this screen, encodes to H.264,
 * decodes it, and renders the decoded frames into the preview below — proving the
 * full capture -> encode -> decode -> render pipeline works on a single device.
 *
 * (Expect a "hall of mirrors" effect while previewing, since we are decoding the
 * very screen we are displaying. That is the loopback working.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(onBack: () -> Unit) {
    val activity = rememberActivity()
    val status by CaptureState.status.collectAsStateWithLifecycle()

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                activity,
                ScreenCaptureService.startLoopbackIntent(activity, result.resultCode, data),
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjection()
        }
    }

    fun onStopClicked() {
        activity.startService(ScreenCaptureService.stopIntent(activity))
    }

    // Always release the render surface when leaving the screen.
    DisposableEffect(Unit) {
        onDispose { LoopbackController.detachSurface() }
    }

    val isRunning = status is CaptureState.Status.Recording ||
        status is CaptureState.Status.Starting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local loopback (M2)") },
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
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = "Decoded preview of this device's screen:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            // The decoded frames render here.
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                LoopbackController.attachSurface(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                LoopbackController.detachSurface()
                            }
                        })
                    }
                },
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (status) {
                    is CaptureState.Status.Idle -> "Tap Start loopback to begin."
                    is CaptureState.Status.Starting -> "Starting…"
                    is CaptureState.Status.Recording -> "Mirroring locally ✓"
                    is CaptureState.Status.Finished -> "Stopped."
                    is CaptureState.Status.Error -> "Error: ${(status as CaptureState.Status.Error).message}"
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            if (isRunning) {
                Button(
                    onClick = { onStopClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("  Stop loopback")
                }
            } else {
                Button(
                    onClick = { onStartClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Start loopback")
                }
            }
        }
    }
}

/** Resolves the hosting [Activity] from the composition context. */
@Composable
private fun rememberActivity(): Activity {
    var ctx: Context = LocalContext.current
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("ReceiverScreen must be hosted in an Activity")
}
