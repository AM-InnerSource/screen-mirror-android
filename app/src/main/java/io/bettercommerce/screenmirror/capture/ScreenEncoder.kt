package io.bettercommerce.screenmirror.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Captures a [MediaProjection] into a hardware H.264 encoder and muxes the
 * result into an MP4 file — the M1 "capture proof".
 *
 * Pipeline:  VirtualDisplay -> Surface -> MediaCodec (H.264) -> MediaMuxer -> .mp4
 *
 * The encoder is driven from its own thread which drains output buffers until an
 * end-of-stream marker arrives after [stop] signals the input.
 */
class ScreenEncoder(
    private val mediaProjection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val densityDpi: Int,
    private val outputFile: File,
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var drainThread: Thread? = null

    private var trackIndex = -1
    private var muxerStarted = false

    /** Set once the caller asks to stop, so the drain loop can finish cleanly. */
    @Volatile private var stopping = false

    fun start() {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        codec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenMirror",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            /* callback = */ null,
            /* handler = */ null,
        )

        drainThread = Thread(::drainLoop, "ScreenEncoderDrain").also { it.start() }
    }

    /**
     * Signals end-of-stream, waits for the encoder to flush the remaining frames,
     * then releases everything. Safe to call once.
     */
    fun stop() {
        if (stopping) return
        stopping = true
        try {
            codec?.signalEndOfInputStream()
        } catch (t: Throwable) {
            Log.w(TAG, "signalEndOfInputStream failed", t)
        }
        // Give the drain loop a moment to write the trailing frames + moov atom.
        drainThread?.join(DRAIN_JOIN_TIMEOUT_MS)
        release()
    }

    private fun drainLoop() {
        val codec = this.codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (true) {
                val index = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output yet. If we're not stopping, keep waiting.
                        if (stopping) {
                            // Encoder may still emit EOS shortly after the signal;
                            // continue looping until we actually see it.
                        }
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Format is known only now — this is when we may add the track.
                        val muxer = this.muxer ?: break
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    index >= 0 -> {
                        val encoded = codec.getOutputBuffer(index)
                        // Codec config data (SPS/PPS) is carried in the format; skip it here.
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted && encoded != null) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer?.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        codec.releaseOutputBuffer(index, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Encoder drain loop failed", t)
            CaptureState.update(CaptureState.Status.Error(t.message ?: "encode error"))
        }
    }

    private fun release() {
        try {
            virtualDisplay?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "virtualDisplay release failed", t)
        }
        try {
            codec?.stop()
            codec?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "codec release failed", t)
        }
        try {
            if (muxerStarted) muxer?.stop()
            muxer?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "muxer release failed", t)
        }
        inputSurface?.release()
        virtualDisplay = null
        codec = null
        muxer = null
        inputSurface = null
    }

    companion object {
        private const val TAG = "ScreenEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1 // seconds between keyframes
        private const val BIT_RATE = 6_000_000 // 6 Mbps — fine for 720p
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val DRAIN_JOIN_TIMEOUT_MS = 2_000L
    }
}
