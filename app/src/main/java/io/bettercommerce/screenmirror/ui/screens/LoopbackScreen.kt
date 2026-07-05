package io.bettercommerce.screenmirror.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bettercommerce.screenmirror.capture.CaptureState
import io.bettercommerce.screenmirror.capture.LoopbackController
import io.bettercommerce.screenmirror.capture.ScreenCaptureService
import io.bettercommerce.screenmirror.monetization.InterstitialAdController

/**
 * Single-device self-test (M2 loopback): captures this screen, encodes to H.264,
 * decodes it, and renders it in the preview below — all in one process. Requires
 * no second phone, so it's the quickest way to confirm the pipeline works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopbackScreen(onBack: () -> Unit) {
    val activity = rememberLoopbackActivity()
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
        // Natural break: show an interstitial to free users (no-op for Pro).
        InterstitialAdController.showIfAvailable(activity)
    }

    // Warm up an interstitial so it's ready when the session ends.
    LaunchedEffect(Unit) { InterstitialAdController.preload(activity) }

    DisposableEffect(Unit) {
        onDispose { LoopbackController.detachSurface() }
    }

    val isRunning = status is CaptureState.Status.Recording ||
        status is CaptureState.Status.Starting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Self-test (loopback)") },
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
                text = "Decoded preview of this screen (expect a nested \"mirror\" effect):",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))

            VideoSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onSurfaceAvailable = { LoopbackController.attachSurface(it) },
                onSurfaceDestroyed = { LoopbackController.detachSurface() },
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (status) {
                    is CaptureState.Status.Idle -> "Tap Start self-test to begin."
                    is CaptureState.Status.Starting -> "Starting…"
                    is CaptureState.Status.Recording -> "Pipeline running ✓"
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
                    Text("  Stop self-test")
                }
            } else {
                Button(
                    onClick = { onStartClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Start self-test")
                }
            }
        }
    }
}

@Composable
private fun rememberLoopbackActivity(): Activity {
    var ctx: Context = LocalContext.current
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("LoopbackScreen must be hosted in an Activity")
}
