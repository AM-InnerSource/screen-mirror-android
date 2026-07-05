package io.bettercommerce.screenmirror.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Bridges the capture pipeline to a local decoder for the M2 loopback proof:
 * encoded frames from [ScreenEncoder] are decoded and rendered onto a [Surface]
 * supplied by the Receiver UI — all on the same device.
 *
 * The encoder (drain thread) and the UI (main thread) touch this from different
 * threads, so surface/decoder access is guarded by [lock]. The decoder is created
 * lazily once *both* the surface and the encoder's output format are available.
 */
object LoopbackController : EncodedFrameListener {

    private val lock = Any()
    private var surface: Surface? = null
    private var pendingFormat: MediaFormat? = null
    private var decoder: VideoDecoder? = null

    /** UI calls this when its SurfaceView surface is created. */
    fun attachSurface(newSurface: Surface) = synchronized(lock) {
        surface = newSurface
        maybeCreateDecoder()
    }

    /** UI calls this when its surface is destroyed / screen left. */
    fun detachSurface() = synchronized(lock) {
        surface = null
        decoder?.release()
        decoder = null
    }

    override fun onFormat(format: MediaFormat) = synchronized(lock) {
        pendingFormat = format
        maybeCreateDecoder()
    }

    private var frameCount = 0

    override fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
        val d = decoder
        if (d == null) {
            Log.w(TAG, "frame dropped: no decoder (surface=${surface != null}, format=${pendingFormat != null})")
            return@synchronized
        }
        try {
            d.decode(buffer, info)
            if (frameCount++ % 30 == 0) Log.i(TAG, "decoded frame #$frameCount")
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed", t)
        }
    }

    override fun onEnded() = synchronized(lock) {
        decoder?.release()
        decoder = null
        pendingFormat = null
    }

    private fun maybeCreateDecoder() {
        val s = surface
        val f = pendingFormat
        if (decoder == null && s != null && f != null) {
            Log.i(TAG, "creating decoder ${f.getInteger(MediaFormat.KEY_WIDTH)}x${f.getInteger(MediaFormat.KEY_HEIGHT)}")
            decoder = try {
                VideoDecoder(s, f)
            } catch (t: Throwable) {
                Log.e(TAG, "decoder create failed", t)
                CaptureState.update(CaptureState.Status.Error(t.message ?: "decoder error"))
                null
            }
        }
    }

    private const val TAG = "LoopbackController"
}
