package io.bettercommerce.screenmirror.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bettercommerce.screenmirror.capture.CaptureState
import io.bettercommerce.screenmirror.capture.LoopbackController
import io.bettercommerce.screenmirror.capture.ScreenCaptureService
import io.bettercommerce.screenmirror.monetization.InterstitialAdController

/**
 * Single-device self-test (loopback): captures this screen, encodes to H.264,
 * decodes it, and renders it back near-fullscreen (expect a nested "mirror"
 * effect). Same immersive, aspect-correct, responsive preview as the receiver.
 */
@Composable
fun LoopbackScreen(onBack: () -> Unit) {
    val activity = rememberLoopbackActivity()
    val status by CaptureState.status.collectAsStateWithLifecycle()
    val videoSize by LoopbackController.videoSize.collectAsStateWithLifecycle()
    var controlsVisible by remember { mutableStateOf(true) }

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
        controlsVisible = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjection()
        }
    }

    fun onStopClicked() {
        activity.startService(ScreenCaptureService.stopIntent(activity))
        controlsVisible = true
        // Natural break: show an interstitial to free users (no-op for Pro).
        InterstitialAdController.showIfAvailable(activity)
    }

    LaunchedEffect(Unit) { InterstitialAdController.preload(activity) }

    DisposableEffect(Unit) {
        onDispose { LoopbackController.detachSurface() }
    }

    val isRunning = status is CaptureState.Status.Recording ||
        status is CaptureState.Status.Starting
    val showControls = controlsVisible || !isRunning

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = isRunning) { controlsVisible = !controlsVisible },
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
                onSurfaceAvailable = { LoopbackController.attachSurface(it) },
                onSurfaceDestroyed = { LoopbackController.detachSurface() },
            )
        }

        // --- Top overlay: back + status ---
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
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = when (status) {
                            is CaptureState.Status.Recording -> "Self-test running ✓"
                            is CaptureState.Status.Starting -> "Starting…"
                            is CaptureState.Status.LimitReached ->
                                (status as CaptureState.Status.LimitReached).message
                            else -> "Self-test (loopback)"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // --- Center info card before running ---
        if (!isRunning) {
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
                    Text("Single-device self-test", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (status) {
                            is CaptureState.Status.LimitReached ->
                                (status as CaptureState.Status.LimitReached).message
                            is CaptureState.Status.Error ->
                                "Error: ${(status as CaptureState.Status.Error).message}"
                            else ->
                                "Captures and re-displays this screen (expect a nested \"mirror\" " +
                                    "effect). Tap the preview while running to hide the controls."
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

@Composable
private fun rememberLoopbackActivity(): Activity {
    var ctx: Context = LocalContext.current
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("LoopbackScreen must be hosted in an Activity")
}
