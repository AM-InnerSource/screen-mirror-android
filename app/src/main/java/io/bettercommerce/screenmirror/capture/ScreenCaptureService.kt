package io.bettercommerce.screenmirror.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import io.bettercommerce.screenmirror.MainActivity
import io.bettercommerce.screenmirror.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Foreground service that holds the [MediaProjection] and runs [ScreenEncoder].
 *
 * Modern Android requires screen capture to run inside a foreground service of
 * type `mediaProjection`, and (API 34+) the service must already be in the
 * foreground *before* the projection is acquired — hence the ordering in
 * [onStartCommand].
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var encoder: ScreenEncoder? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // User revoked capture (or the system stopped it) — tear down.
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopCapture()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Int.MIN_VALUE || resultData == null) {
            CaptureState.update(CaptureState.Status.Error("Missing projection permission data"))
            stopSelf()
            return
        }

        CaptureState.update(CaptureState.Status.Starting)

        // 1. Enter the foreground FIRST (required before acquiring projection on API 34+).
        startForegroundWithNotification()

        // 2. Acquire the projection.
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            CaptureState.update(CaptureState.Status.Error("Could not start screen projection"))
            stopSelf()
            return
        }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, null)

        // 3. Work out capture dimensions (capped to 720p for M1) and start encoding.
        val (width, height, dpi) = captureDimensions()
        val outputFile = newOutputFile()
        try {
            encoder = ScreenEncoder(projection, width, height, dpi, outputFile).also { it.start() }
            CaptureState.update(CaptureState.Status.Recording(outputFile.absolutePath))
            Log.i(TAG, "Capture started -> ${outputFile.absolutePath} (${width}x$height)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start encoder", t)
            CaptureState.update(CaptureState.Status.Error(t.message ?: "encoder start failed"))
            stopCapture()
        }
    }

    private fun stopCapture() {
        val finishedPath = (CaptureState.status.value as? CaptureState.Status.Recording)?.outputPath

        encoder?.stop()
        encoder = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        if (finishedPath != null) {
            CaptureState.update(CaptureState.Status.Finished(finishedPath))
        } else if (CaptureState.status.value !is CaptureState.Status.Error) {
            CaptureState.update(CaptureState.Status.Idle)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Foreground notification -------------------------------------------------

    private fun startForegroundWithNotification() {
        val channelId = ensureNotificationChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScreenMirror is capturing")
            .setContentText("Your screen is being recorded")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Screen capture",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Shown while your screen is being mirrored" },
                )
            }
        }
        return CHANNEL_ID
    }

    // --- Helpers -----------------------------------------------------------------

    private data class Dimensions(val width: Int, val height: Int, val dpi: Int)

    private fun captureDimensions(): Dimensions {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        var width = metrics.widthPixels
        var height = metrics.heightPixels

        // Cap the longer edge to 720p for M1 to keep any encoder happy.
        val longEdge = maxOf(width, height)
        if (longEdge > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / longEdge
            width = (width * scale).roundToInt()
            height = (height * scale).roundToInt()
        }

        // Many encoders require even dimensions.
        width -= width % 2
        height -= height % 2

        return Dimensions(width, height, metrics.densityDpi)
    }

    private fun newOutputFile(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "capture_$stamp.mp4")
    }

    override fun onDestroy() {
        // Defensive: make sure nothing is left running.
        encoder?.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_LONG_EDGE = 1280

        const val ACTION_START = "io.bettercommerce.screenmirror.action.START"
        const val ACTION_STOP = "io.bettercommerce.screenmirror.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        /** Builds the intent that starts capture with the projection grant. */
        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
    }
}
