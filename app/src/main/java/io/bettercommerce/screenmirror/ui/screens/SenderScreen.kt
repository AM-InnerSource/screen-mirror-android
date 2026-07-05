package io.bettercommerce.screenmirror.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.bettercommerce.screenmirror.capture.CaptureState
import io.bettercommerce.screenmirror.capture.ScreenCaptureService

/**
 * M1 Sender screen: request the screen-capture grant, start/stop the capture
 * service, and show where the proof-of-capture MP4 was written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(onBack: () -> Unit) {
    val context = LocalContextActivity()
    val status by CaptureState.status.collectAsStateWithLifecycle()

    // Launcher for the mandatory system "Start recording?" dialog.
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ContextCompat.startForegroundService(
                context,
                ScreenCaptureService.startIntent(context, result.resultCode, data),
            )
        } else {
            CaptureState.update(CaptureState.Status.Idle)
        }
    }

    fun launchProjection() {
        val manager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    // On Android 13+ the foreground-service notification needs runtime permission.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless — capture still works; only the notification visibility
        // depends on the grant.
        launchProjection()
    }

    fun onStartClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjection()
        }
    }

    fun onStopClicked() {
        context.startService(ScreenCaptureService.stopIntent(context))
    }

    val isRecording = status is CaptureState.Status.Recording ||
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
            Spacer(Modifier.height(32.dp))

            if (isRecording) {
                Button(
                    onClick = { onStopClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Text("  Stop capture")
                }
            } else {
                Button(
                    onClick = { onStartClicked() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                    Text("  Start capture")
                }
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
                    "Ready" to "Tap Start capture. Android will ask for permission to record your screen."
                is CaptureState.Status.Starting ->
                    "Starting…" to "Acquiring screen projection."
                is CaptureState.Status.Recording ->
                    "Recording" to "Capturing to:\n${status.outputPath}"
                is CaptureState.Status.Finished ->
                    "Saved ✓" to "Proof-of-capture MP4 written to:\n${status.outputPath}"
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

/**
 * Resolves the current [Activity] from the composition's context. The projection
 * launcher and service starts need an Activity context.
 */
@Composable
private fun LocalContextActivity(): Activity {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    error("SenderScreen must be hosted in an Activity")
}
