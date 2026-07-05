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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import io.bettercommerce.screenmirror.MainActivity
import io.bettercommerce.screenmirror.R
import io.bettercommerce.screenmirror.monetization.Entitlements
import io.bettercommerce.screenmirror.network.FrameProtocol
import io.bettercommerce.screenmirror.network.NetworkSender
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
    // Assigned from a background thread in the SENDER path, read from the main thread.
    @Volatile private var encoder: ScreenEncoder? = null

    // Live only in SENDER mode: used by the presenter-pointer overlay to ping the receiver.
    @Volatile private var networkSender: NetworkSender? = null
    // Touched only on the main thread.
    private var focusOverlay: FocusOverlayController? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionLimitRunnable = Runnable { onFreeLimitReached() }
    private var limitReached = false

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

        val mode = intent.getIntExtra(EXTRA_MODE, MODE_FILE)
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, FrameProtocol.PORT)

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

        // Quality scales with the subscription tier (Pro gets higher res/fps/bitrate).
        val config = CaptureConfig.forTier(Entitlements.isProNow)
        val (width, height, dpi) = captureDimensions(config)

        // 3. The SENDER path connects a socket (blocking) before encoding, so it
        //    runs on a background thread. FILE / LOOPBACK start synchronously.
        if (mode == MODE_SENDER && host != null) {
            Thread {
                try {
                    val sender = NetworkSender(host, port).also { it.connect() }
                    networkSender = sender
                    encoder = ScreenEncoder(projection, width, height, dpi, config, sender).also { it.start() }
                    CaptureState.update(CaptureState.Status.Recording("Streaming to $host:$port"))
                    maybeStartSessionTimer()
                    mainHandler.post { showFocusOverlay() }
                    Log.i(TAG, "Streaming -> $host:$port (${width}x$height @ ${config.frameRate}fps)")
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start network sender", t)
                    CaptureState.update(CaptureState.Status.Error(t.message ?: "connect failed"))
                    stopCapture()
                }
            }.start()
            return
        }

        val listener: EncodedFrameListener
        val recordingLabel: String
        if (mode == MODE_LOOPBACK) {
            listener = LoopbackController
            recordingLabel = LOOPBACK_LABEL
        } else {
            val file = newOutputFile()
            listener = MuxerFrameListener(file)
            recordingLabel = file.absolutePath
        }

        try {
            encoder = ScreenEncoder(projection, width, height, dpi, config, listener).also { it.start() }
            CaptureState.update(CaptureState.Status.Recording(recordingLabel))
            maybeStartSessionTimer()
            Log.i(TAG, "Capture started -> $recordingLabel (${width}x$height @ ${config.frameRate}fps)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start encoder", t)
            CaptureState.update(CaptureState.Status.Error(t.message ?: "encoder start failed"))
            stopCapture()
        }
    }

    private fun stopCapture() {
        mainHandler.removeCallbacks(sessionLimitRunnable)
        val hitLimit = limitReached
        limitReached = false

        val finishedPath = (CaptureState.status.value as? CaptureState.Status.Recording)?.outputPath

        hideFocusOverlay()
        networkSender = null

        encoder?.stop()
        encoder = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        when {
            hitLimit -> CaptureState.update(
                CaptureState.Status.LimitReached(
                    "Free sessions are limited to ${FREE_SESSION_LIMIT_MIN} minutes. " +
                        "Upgrade to Pro for unlimited mirroring.",
                ),
            )
            finishedPath != null -> CaptureState.update(CaptureState.Status.Finished(finishedPath))
            CaptureState.status.value !is CaptureState.Status.Error ->
                CaptureState.update(CaptureState.Status.Idle)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Presenter-pointer overlay (SENDER mode only) ----------------------------

    /** Shows the draggable focus bubble. Must run on the main thread. */
    private fun showFocusOverlay() {
        if (focusOverlay != null) return
        val controller = FocusOverlayController(this) { xNorm, yNorm ->
            networkSender?.sendFocus(xNorm, yNorm)
        }
        controller.show()
        focusOverlay = controller
    }

    /** Removes the focus bubble on the main thread from wherever this is called. */
    private fun hideFocusOverlay() {
        val overlay = focusOverlay ?: return
        focusOverlay = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            overlay.hide()
        } else {
            mainHandler.post { overlay.hide() }
        }
    }

    /** Free tier: auto-stop the session after the time limit. Pro is unlimited. */
    private fun maybeStartSessionTimer() {
        if (Entitlements.isProNow) return
        mainHandler.postDelayed(sessionLimitRunnable, FREE_SESSION_LIMIT_MS)
        Log.i(TAG, "Free session timer armed: ${FREE_SESSION_LIMIT_MIN} min")
    }

    private fun onFreeLimitReached() {
        Log.i(TAG, "Free session limit reached — stopping")
        limitReached = true
        stopCapture()
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

    private fun captureDimensions(config: CaptureConfig): Dimensions {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        var width = metrics.widthPixels
        var height = metrics.heightPixels

        // Cap the longer edge per the quality tier (720p free / 1080p pro).
        val longEdge = maxOf(width, height)
        if (longEdge > config.maxLongEdge) {
            val scale = config.maxLongEdge.toFloat() / longEdge
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
        mainHandler.removeCallbacks(sessionLimitRunnable)
        hideFocusOverlay()
        encoder?.stop()
        mediaProjection?.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001

        /** Free-tier session cap. Pro is unlimited. */
        const val FREE_SESSION_LIMIT_MIN = 50L
        private const val FREE_SESSION_LIMIT_MS = FREE_SESSION_LIMIT_MIN * 60 * 1000

        const val ACTION_START = "io.bettercommerce.screenmirror.action.START"
        const val ACTION_STOP = "io.bettercommerce.screenmirror.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"

        const val MODE_FILE = 0
        const val MODE_LOOPBACK = 1
        const val MODE_SENDER = 2

        /** Sentinel label used in [CaptureState] when running the M2 loopback. */
        const val LOOPBACK_LABEL = "loopback"

        /** Starts capture that writes an .mp4 file (M1). */
        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            buildStartIntent(context, resultCode, data, MODE_FILE)

        /** Starts capture that decodes + renders locally (M2 loopback). */
        fun startLoopbackIntent(context: Context, resultCode: Int, data: Intent): Intent =
            buildStartIntent(context, resultCode, data, MODE_LOOPBACK)

        /** Starts capture that streams to [host]:[port] (M3). */
        fun startSenderIntent(
            context: Context,
            resultCode: Int,
            data: Intent,
            host: String,
            port: Int,
        ): Intent = buildStartIntent(context, resultCode, data, MODE_SENDER).apply {
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PORT, port)
        }

        private fun buildStartIntent(
            context: Context,
            resultCode: Int,
            data: Intent,
            mode: Int,
        ): Intent =
            Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_MODE, mode)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
    }
}
